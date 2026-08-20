package net.dutymod.framework;

import com.sun.management.GarbageCollectionNotificationInfo;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * Watches the garbage collector, so a freeze can be blamed on it or cleared of it.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Duty could time everything it does and still not explain a stutter, because the most common
 * cause of one is not in Duty's code at all: the collector stops every thread, and whatever was
 * running at that moment gets the blame. That is exactly how a 0.5ms culling pass shows up in a
 * report as a 45ms spike. Without this class the only honest thing to say about such a spike is
 * "something stopped the world", which is not a finding, it is a shrug.
 *
 * <h2>The number that actually settles the argument</h2>
 *
 * <p>"Heap is 91% used" sounds alarming and means very little on its own -- most of that can be
 * garbage the collector has simply not got round to yet. The number worth having is the
 * <em>live set</em>: how much is still reachable immediately after a major collection. That is
 * recorded here, from the post-collection heap usage the JVM hands to the notification, and it is
 * the difference between "this needs a bigger heap" and "this heap is fine and the collector is
 * just pausing badly".
 *
 * <h2>Cost</h2>
 *
 * <p>A notification per collection, which is a handful per second at worst, handled on the JVM's
 * own listener thread. Nothing is polled and nothing is allocated per collection beyond the
 * notification the JVM was already building.
 *
 * <h2>A caveat worth stating in the report</h2>
 *
 * <p>The duration reported here is the collection's duration, which equals the stop-the-world
 * pause only for collectors whose collections <em>are</em> pauses -- G1's young and full
 * collections, Serial, Parallel. For a mostly-concurrent collector such as ZGC or Shenandoah the
 * duration covers concurrent work the game ran straight through, so a long one is not a freeze.
 * {@link #mostlyConcurrent()} answers which case applies so findings can be worded honestly rather
 * than reporting a 200ms ZGC cycle as a 200ms stall.
 */
public final class DutyGc {

    public static final String ENABLED = "framework.gc_monitor";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static final AtomicLong COUNT = new AtomicLong();
    private static final AtomicLong MAJOR_COUNT = new AtomicLong();
    private static final AtomicLong TOTAL_PAUSE_MILLIS = new AtomicLong();
    private static final AtomicLong WORST_PAUSE_MILLIS = new AtomicLong();
    private static final AtomicLong LONG_PAUSES = new AtomicLong();

    /** Heap still reachable after the most recent major collection; -1 until one happens. */
    private static final AtomicLong LIVE_SET_BYTES = new AtomicLong(-1L);

    /** Heap in use after the previous collection, the baseline the next one measures against. */
    private static final AtomicLong LAST_HEAP_AFTER = new AtomicLong(-1L);

    /** Bytes allocated between collections, summed. See the note in {@link #record}. */
    private static final AtomicLong ALLOCATED_BYTES = new AtomicLong();

    /** When collection monitoring started, so the total above can be turned into a rate. */
    private static volatile long startedNanos;

    private static final AtomicReference<String> WORST_CAUSE = new AtomicReference<>("");

    /** Above this, a pause is long enough for a person to see the window stop repainting. */
    private static final long LONG_PAUSE_MILLIS = 100L;

    private static final List<String> COLLECTORS = new ArrayList<>();

    private DutyGc() {}

    /**
     * Starts listening. Safe to call more than once and safe to fail.
     *
     * <p>Everything here is best-effort: {@code com.sun.management} is a HotSpot extension, and a
     * JVM without it should cost Duty a missing section of a report and nothing else.
     */
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            DutyConfig.register(ENABLED, true,
                    "Record garbage collection pauses. This is what lets a report tell the\n"
                            + "difference between Duty being slow and the collector stopping the\n"
                            + "game while Duty happened to be running. Costs one callback per\n"
                            + "collection.");
            if (!DutyConfig.get(ENABLED)) {
                return;
            }
            startedNanos = System.nanoTime();
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                COLLECTORS.add(bean.getName());
                if (bean instanceof NotificationEmitter emitter) {
                    emitter.addNotificationListener(LISTENER, DutyGc::isGcNotification, null);
                }
            }
        } catch (Throwable t) {
            // A report without a GC section is a smaller loss than a mod that fails to load.
            DutyLog.warn("GC monitoring unavailable: " + t);
        }
    }

    private static boolean isGcNotification(javax.management.Notification notification) {
        return GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION
                .equals(notification.getType());
    }

    private static final NotificationListener LISTENER = (notification, handback) -> {
        try {
            GarbageCollectionNotificationInfo info = GarbageCollectionNotificationInfo
                    .from((CompositeData) notification.getUserData());
            record(info);
        } catch (Throwable t) {
            // Never let a monitoring callback propagate into the JVM's listener thread.
        }
    };

    private static void record(GarbageCollectionNotificationInfo info) {
        long millis = info.getGcInfo().getDuration();
        COUNT.incrementAndGet();
        TOTAL_PAUSE_MILLIS.addAndGet(millis);

        // How much was allocated since the last collection: what the heap held going into this one,
        // less what it held coming out of the previous one. The pair of readings is already being
        // handed to us, so this costs a subtraction on a callback that was going to run anyway.
        //
        // Worth having because it is the number a memory module is actually judged on. Pause times
        // say whether the collector is coping; the allocation rate says how much work it is being
        // given, and that is the thing deduplicating block states and interning tags is meant to
        // reduce. A report could previously say collection looked healthy while the game churned
        // through hundreds of megabytes a second.
        final long before = heapBefore(info);
        final long previousAfter = LAST_HEAP_AFTER.getAndSet(heapAfter(info));
        if (previousAfter >= 0L && before > previousAfter) {
            ALLOCATED_BYTES.addAndGet(before - previousAfter);
        }

        if (millis >= LONG_PAUSE_MILLIS) {
            LONG_PAUSES.incrementAndGet();
        }

        // Keep the worst pause and the cause that produced it together, so the report can say what
        // triggered it rather than only how long it took. Racing here would at worst pair a
        // duration with a neighbouring cause, which is not worth a lock on a JVM callback.
        long previous;
        while (millis > (previous = WORST_PAUSE_MILLIS.get())) {
            if (WORST_PAUSE_MILLIS.compareAndSet(previous, millis)) {
                WORST_CAUSE.set(info.getGcCause() + " (" + info.getGcName() + ")");
                break;
            }
        }

        if (isMajor(info)) {
            MAJOR_COUNT.incrementAndGet();
            LIVE_SET_BYTES.set(heapAfter(info));
        }
    }

    private static boolean isMajor(GarbageCollectionNotificationInfo info) {
        String action = info.getGcAction();
        return action != null && action.toLowerCase(Locale.ROOT).contains("major");
    }

    /** {@return total heap in use just before the collection started, across every heap pool} */
    private static long heapBefore(GarbageCollectionNotificationInfo info) {
        long used = 0L;
        for (var entry : info.getGcInfo().getMemoryUsageBeforeGc().entrySet()) {
            String pool = entry.getKey().toLowerCase(Locale.ROOT);
            if (pool.contains("metaspace") || pool.contains("code") || pool.contains("compressed")) {
                continue;
            }
            MemoryUsage usage = entry.getValue();
            if (usage != null) {
                used += usage.getUsed();
            }
        }
        return used;
    }

    /** {@return total heap in use once the collection finished, across every heap pool} */
    private static long heapAfter(GarbageCollectionNotificationInfo info) {
        long used = 0L;
        for (var entry : info.getGcInfo().getMemoryUsageAfterGc().entrySet()) {
            String pool = entry.getKey().toLowerCase(Locale.ROOT);
            // Metaspace and code cache are reported alongside the heap pools and are not part of
            // the live set the heap size has to accommodate.
            if (pool.contains("metaspace") || pool.contains("code") || pool.contains("compressed")) {
                continue;
            }
            MemoryUsage usage = entry.getValue();
            if (usage != null) {
                used += usage.getUsed();
            }
        }
        return used;
    }

    /**
     * {@return whether the collectors in use do most of their work concurrently}
     *
     * <p>Decides whether a long recorded duration means a long freeze. Named rather than inferred
     * from pause lengths because the two are indistinguishable after the fact.
     */
    public static boolean mostlyConcurrent() {
        for (String name : COLLECTORS) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("z") || lower.contains("shenandoah")) {
                return true;
            }
        }
        return false;
    }

    /** {@return bytes allocated between collections so far, or 0 before two collections have run} */
    public static long allocatedBytes() {
        return ALLOCATED_BYTES.get();
    }

    /**
     * {@return allocation rate in bytes per second, or -1 when it cannot be known}
     *
     * <p>Needs at least two collections, because the measurement is the gap between one ending and
     * the next beginning.
     */
    public static double allocationBytesPerSecond() {
        final long started = startedNanos;
        final long allocated = ALLOCATED_BYTES.get();
        if (started == 0L || allocated <= 0L || COUNT.get() < 2L) {
            return -1.0d;
        }
        final double seconds = (System.nanoTime() - started) / 1.0e9d;
        return seconds > 0.0d ? allocated / seconds : -1.0d;
    }

    public static long count() {
        return COUNT.get();
    }

    public static long majorCount() {
        return MAJOR_COUNT.get();
    }

    public static long totalPauseMillis() {
        return TOTAL_PAUSE_MILLIS.get();
    }

    public static long worstPauseMillis() {
        return WORST_PAUSE_MILLIS.get();
    }

    public static long longPauses() {
        return LONG_PAUSES.get();
    }

    public static String worstCause() {
        return WORST_CAUSE.get();
    }

    /** {@return reachable heap after the last major collection, or -1 if none has happened} */
    public static long liveSetBytes() {
        return LIVE_SET_BYTES.get();
    }

    /** {@return the collectors in use, for the report header} */
    public static List<String> collectors() {
        return List.copyOf(COLLECTORS);
    }
}

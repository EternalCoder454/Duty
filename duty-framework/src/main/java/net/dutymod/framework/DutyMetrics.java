package net.dutymod.framework;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Measuring what Duty costs, and saying so.
 *
 * <h2>Why this exists</h2>
 *
 * <p>Every module had grown its own way of answering "is this actually helping". Culling kept
 * {@code lastPassMillis} on the task object, the structure watchdog tracked its own elapsed time,
 * FixerUpper's {@code measure_time} package timed startup with bespoke mixins, and none of them
 * could be read together or turned off. Worse, most of the numbers were written and never read --
 * the culling timings sat there for months with nothing displaying them.
 *
 * <p>A performance change that cannot be measured is a guess. This gives every module the same two
 * primitives, one place to read them from, and one switch to turn the whole thing off.
 *
 * <h2>Cost when it is off</h2>
 *
 * <p>Off by default, and off means off: {@link Timer#begin()} returns immediately on a
 * {@code static volatile boolean} read, and {@link Timer#end} returns on the same check without
 * touching a clock or a counter. That matters because these are meant to go on per-frame and
 * per-tick paths, where {@link System#nanoTime()} itself is the expensive part -- it is a VDSO call
 * on Linux and a QPC on Windows, tens of nanoseconds, which is real money at sixty frames a second
 * across thousands of entities.
 *
 * <p>The handles are meant to be held in a {@code static final} field, so the map lookup happens
 * once at class-init and never on the measured path:
 *
 * {@snippet :
 * private static final DutyMetrics.Timer CULL_PASS = DutyMetrics.timer("client.culling.pass");
 *
 * long started = CULL_PASS.begin();
 * try {
 *     runPass();
 * } finally {
 *     CULL_PASS.end(started);
 * }
 * }
 *
 * <p>{@link Timer#open()} gives the same thing as a try-with-resources at the cost of one small
 * allocation per call, which is fine off the hot paths and is not fine on them.
 *
 * <h2>Threads</h2>
 *
 * <p>Duty measures on the render thread, the server thread and its own worker threads, sometimes
 * for the same timer. Counts and totals go through {@link LongAdder}, which is built for exactly
 * this: many writers, a reader that only wants the sum. The rolling average is a plain
 * {@code volatile double} updated without a lock -- two threads finishing at once can lose one
 * sample from the average, which is not worth a CAS loop on a number whose only job is to be
 * displayed.
 */
public final class DutyMetrics {

    /** Whether to measure at all. */
    public static final String ENABLED = "framework.metrics";

    /** Seconds between automatic reports to the log. 0 disables the periodic report. */
    public static final String REPORT_SECONDS = "framework.metrics_report_seconds";

    /**
     * Read once into a plain field rather than through {@link DutyConfig} per call.
     *
     * <p>A config read is a map lookup and a parse. On a per-frame timer that would cost more than
     * the thing being measured, which would make the measurement a lie.
     */
    private static volatile boolean enabled;

    private static final Map<String, Timer> TIMERS = new ConcurrentHashMap<>();
    private static final Map<String, Counter> COUNTERS = new ConcurrentHashMap<>();

    private static volatile boolean initialized;
    private static volatile Thread reporter;

    private DutyMetrics() {}

    /**
     * Registers the options and reads them.
     *
     * <p>Safe to call repeatedly and from anywhere; the first call wins. Modules do not need to
     * call it -- asking for a timer does it -- but a module that wants the options present in
     * {@code duty.properties} before it is asked for anything can.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        DutyConfig.register(ENABLED, false,
                "Measure what Duty's own work costs and report it. Off by default because the\n"
                        + "timers sit on per-frame and per-tick paths, where reading the clock is\n"
                        + "itself measurable. Turn it on when deciding whether something is worth\n"
                        + "keeping, not while playing.");
        DutyConfig.register(REPORT_SECONDS, 0,
                "Seconds between automatic performance reports in the log. 0 reports only when\n"
                        + "something asks. Ignored while " + ENABLED + " is false.");

        enabled = DutyConfig.get(ENABLED);
        if (enabled) {
            startReporterIfConfigured();
        }

        // Without this, editing framework.metrics in the file and reloading would update the map
        // and leave this cached copy stale -- the file would say one thing and the game do another,
        // which is worse than not supporting reload at all.
        DutyConfig.onReload(() -> {
            enabled = DutyConfig.get(ENABLED);
            if (enabled) {
                startReporterIfConfigured();
            }
            DutyLog.info("Duty measurement is now " + (enabled ? "on" : "off") + " (config reload).");
        });
    }

    /** {@return whether measurement is on} */
    public static boolean enabled() {
        return enabled;
    }

    /**
     * Turns measurement on or off at runtime.
     *
     * <p>Exists so a command can switch it on for a few minutes without a restart. Turning it on
     * does not retroactively fill in what was not measured, so the first report afterwards covers
     * only what has happened since.
     */
    public static void setEnabled(boolean value) {
        init();
        enabled = value;
        if (value) {
            startReporterIfConfigured();
        }
    }

    /**
     * {@return the timer with this name, creating it if needed}
     *
     * <p>Names are dotted and start with the module, e.g. {@code client.culling.pass} or
     * {@code server.structure.search}. The report groups on the first segment, so a name without
     * one lands in a group called after itself.
     */
    public static Timer timer(String name) {
        init();
        return TIMERS.computeIfAbsent(name, Timer::new);
    }

    /** {@return the counter with this name, creating it if needed} */
    public static Counter counter(String name) {
        init();
        return COUNTERS.computeIfAbsent(name, Counter::new);
    }

    /** Forgets every recorded sample, keeping the handles valid. */
    public static void reset() {
        for (Timer timer : TIMERS.values()) {
            timer.reset();
        }
        for (Counter counter : COUNTERS.values()) {
            counter.reset();
        }
    }

    /**
     * {@return every timer that has recorded at least one sample, most expensive first}
     *
     * <p>Sorted by total time rather than by average: a cheap thing done constantly is usually the
     * problem, and sorting by average hides it behind whatever is called twice.
     */
    public static List<Timer> timers() {
        List<Timer> out = new ArrayList<>(TIMERS.values());
        out.removeIf(t -> t.count() == 0);
        out.sort(Comparator.comparingLong(Timer::totalNanos).reversed());
        return out;
    }

    /** {@return every counter that has been touched, largest first} */
    public static List<Counter> counters() {
        List<Counter> out = new ArrayList<>(COUNTERS.values());
        out.removeIf(c -> c.value() == 0);
        out.sort(Comparator.comparingLong(Counter::value).reversed());
        return out;
    }

    /**
     * {@return a human-readable report, or a line explaining why it is empty}
     *
     * <p>Formatted for a log file and for chat, so it stays inside a sensible width and does not
     * rely on colour or alignment beyond spaces.
     */
    public static String report() {
        init();
        StringBuilder out = new StringBuilder(512);
        out.append("Duty performance report\n");

        if (!enabled) {
            out.append("  measurement is off; set ").append(ENABLED).append("=true to collect it");
            return out.toString();
        }

        List<Timer> timers = timers();
        List<Counter> counters = counters();
        if (timers.isEmpty() && counters.isEmpty()) {
            out.append("  nothing recorded yet");
            return out.toString();
        }

        if (!timers.isEmpty()) {
            out.append(String.format(Locale.ROOT, "  %-38s %9s %10s %9s %9s%n",
                    "timer", "calls", "total", "avg", "worst"));
            for (Timer t : timers) {
                out.append(String.format(Locale.ROOT, "  %-38s %9d %9.1fms %7.3fms %7.3fms%n",
                        trim(t.name(), 38), t.count(), t.totalNanos() / 1.0e6,
                        t.averageMillis(), t.maxNanos() / 1.0e6));
            }
        }

        if (!counters.isEmpty()) {
            if (!timers.isEmpty()) {
                out.append('\n');
            }
            out.append(String.format(Locale.ROOT, "  %-38s %9s%n", "counter", "value"));
            for (Counter c : counters) {
                out.append(String.format(Locale.ROOT, "  %-38s %9d%n",
                        trim(c.name(), 38), c.value()));
            }
        }

        // Trailing newline from the loop; the caller decides how to end the message.
        if (out.charAt(out.length() - 1) == '\n') {
            out.setLength(out.length() - 1);
        }
        return out.toString();
    }

    /** Writes {@link #report()} to the log, one line per entry so log viewers can filter it. */
    public static void reportToLog() {
        for (String line : report().split("\n")) {
            DutyLog.info(line);
        }
    }

    private static String trim(String name, int width) {
        return name.length() <= width ? name : name.substring(name.length() - width);
    }

    /**
     * Starts the periodic reporter, if one is configured and not already running.
     *
     * <p>A daemon thread rather than a tick hook: the framework deliberately knows nothing about
     * Minecraft, and a report that stops when the server pauses would miss the case where the
     * server is too busy to tick, which is exactly when the numbers matter.
     */
    private static synchronized void startReporterIfConfigured() {
        if (reporter != null) {
            return;
        }
        int seconds = DutyConfig.getInt(REPORT_SECONDS, 0, 3600);
        if (seconds <= 0) {
            return;
        }

        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(seconds * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (enabled) {
                    reportToLog();
                }
            }
        }, "Duty metrics reporter");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        reporter = thread;
    }

    /**
     * How long something takes, and how often.
     *
     * <p>Held by the caller in a static field. See the class docs for why the map is not consulted
     * per call.
     */
    public static final class Timer {
        private static final double EMA_WEIGHT = 0.1d;

        private final String name;
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final AtomicLong maxNanos = new AtomicLong();
        private volatile double recentMillis;

        private Timer(String name) {
            this.name = name;
        }

        /**
         * {@return a token to hand back to {@link #end}, or 0 when measurement is off}
         *
         * <p>Deliberately returns a primitive rather than an object: this is called from render and
         * tick paths where an allocation per call would be the most expensive thing about
         * measuring.
         */
        public long begin() {
            return enabled ? System.nanoTime() : 0L;
        }

        /** Records a sample that started at {@code started}. Does nothing if measurement is off. */
        public void end(long started) {
            if (!enabled || started == 0L) {
                return;
            }
            record(System.nanoTime() - started);
        }

        /**
         * Records a sample directly, for callers that already know the duration.
         *
         * <p>Checks {@code enabled} like the rest, so "off" means no accumulation anywhere rather
         * than only on the {@link #begin()} path. A caller that timed something for its own reasons
         * and wants the number kept regardless should hold its own field, as the culling pass does.
         */
        public void record(long nanos) {
            if (!enabled || nanos < 0) {
                // Off, or a clock that went backwards, or an end() paired with a begin() from
                // before measurement was switched on. None of those is a sample.
                return;
            }
            count.increment();
            totalNanos.add(nanos);
            maxNanos.accumulateAndGet(nanos, Math::max);

            double millis = nanos / 1.0e6;
            double previous = recentMillis;
            recentMillis = previous == 0.0d ? millis : previous + EMA_WEIGHT * (millis - previous);
        }

        /**
         * {@return a handle that records when closed}
         *
         * <p>For try-with-resources. Allocates, so prefer {@link #begin()}/{@link #end} anywhere
         * that runs per frame, per tick or per entity.
         */
        public Section open() {
            return new Section(this, begin());
        }

        public String name() {
            return name;
        }

        public long count() {
            return count.sum();
        }

        public long totalNanos() {
            return totalNanos.sum();
        }

        public long maxNanos() {
            return maxNanos.get();
        }

        /** {@return the mean over every sample, in milliseconds} */
        public double averageMillis() {
            long calls = count();
            return calls == 0 ? 0.0d : (totalNanos() / 1.0e6) / calls;
        }

        /**
         * {@return a rolling average of the recent samples, in milliseconds}
         *
         * <p>What a debug-screen line wants. The all-time mean stops moving after a few thousand
         * samples, so it cannot show that something just got slower.
         */
        public double recentMillis() {
            return recentMillis;
        }

        public void reset() {
            count.reset();
            totalNanos.reset();
            maxNanos.set(0);
            recentMillis = 0.0d;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s: %d calls, %.3fms avg, %.3fms worst",
                    name, count(), averageMillis(), maxNanos() / 1.0e6);
        }
    }

    /** The open half of a {@link Timer#open()}; closing it records the sample. */
    public static final class Section implements AutoCloseable {
        private final Timer timer;
        private final long started;

        private Section(Timer timer, long started) {
            this.timer = timer;
            this.started = started;
        }

        @Override
        public void close() {
            timer.end(started);
        }
    }

    /**
     * How many of something happened.
     *
     * <p>For the numbers that are not durations: entities culled, chunks skipped, packets
     * compressed. Unlike timers these keep counting whether or not measurement is on, because the
     * cost is one {@link LongAdder} increment and the number is usually wanted precisely when
     * nobody thought to switch measuring on first.
     */
    public static final class Counter {
        private final String name;
        private final LongAdder value = new LongAdder();

        private Counter(String name) {
            this.name = name;
        }

        public void increment() {
            value.increment();
        }

        public void add(long amount) {
            value.add(amount);
        }

        public String name() {
            return name;
        }

        public long value() {
            return value.sum();
        }

        public void reset() {
            value.reset();
        }

        @Override
        public String toString() {
            return name + ": " + value();
        }
    }
}

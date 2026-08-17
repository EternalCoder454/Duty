package net.dutymod.server.save;

import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moves {@code level.dat} and player data writes off the thread that is trying to tick the game.
 *
 * <p>Vanilla writes both synchronously from the server thread, so every autosave stalls the world
 * for as long as the disk takes. That is the periodic hitch on a save.
 *
 * <p><b>Single-threaded on purpose.</b> Writes to the same file must not overlap, and one worker
 * gives ordering for free: a save submitted later cannot land before an earlier one. A pool would
 * be faster and wrong.
 *
 * <p>Two corrections to the mod this idea came from (FastAsyncWorldSave):
 *
 * <ul>
 *   <li>Its worker is a <em>daemon</em> thread with no shutdown handling, so quitting the game
 *       immediately after a save kills the write in flight and loses it. Duty's worker is
 *       non-daemon and {@link #flush()} is called when the server stops, with a JVM shutdown hook
 *       as a backstop for the paths that do not go through it.
 *   <li>It is silent, so there is no way to tell whether it is doing anything. Duty logs the first
 *       write it takes over and the totals at shutdown -- see the class note below on why that
 *       matters more than it sounds.
 * </ul>
 *
 * <p>The safety argument for writing asynchronously at all: both paths write to a temporary file
 * and then call {@code Util.safeReplaceFile}, which swaps it in atomically. An interrupted write
 * therefore leaves the previous save intact rather than a truncated one. The worst case is losing
 * the most recent save, not a corrupted world -- and the flush on shutdown is what keeps even that
 * from happening in normal use.
 */
public final class AsyncWorldSave {
    public static final String ENABLED = "server.async_world_save";

    private static final AtomicLong SUBMITTED = new AtomicLong();
    private static final AtomicLong COMPLETED = new AtomicLong();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Duty world save");
        // Not a daemon: a daemon dies with the JVM mid-write, which is exactly the save you most
        // wanted to keep.
        thread.setDaemon(false);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    static {
        DutyConfig.register(ENABLED, true,
                "Write level.dat and player data on a background thread instead of stalling the\n"
                        + "server thread. Both paths write to a temp file and swap it in atomically,\n"
                        + "so an interrupted write leaves the previous save intact rather than a\n"
                        + "broken one, and Duty waits for outstanding writes when the server stops.");
        Runtime.getRuntime().addShutdownHook(new Thread(AsyncWorldSave::flush, "Duty world save flush"));
    }

    private AsyncWorldSave() {}

    /** Forces the registration above to run. */
    public static void init() {}

    public static boolean enabled() {
        init();
        return DutyConfig.get(ENABLED);
    }

    /**
     * Runs {@code write} on the save worker.
     *
     * <p>The first call logs, which is the point: a mixin that applies and then never fires looks
     * identical in the log to one that was never applied. One line the first time a save is
     * actually taken over is the difference between "installed" and "working".
     */
    public static void submit(String what, Runnable write) {
        SUBMITTED.incrementAndGet();
        DutyLog.infoOnce("async_save.live",
                "Async world save is live; " + what + " is now written off the server thread.");
        WORKER.execute(() -> {
            try {
                write.run();
            } catch (Throwable t) {
                // Never let a save failure kill the worker; the next save must still get through.
                DutyLog.warn("Async save of " + what + " failed: " + t);
            } finally {
                COMPLETED.incrementAndGet();
            }
        });
    }

    /** Waits for outstanding writes. Called on server stop and again from the shutdown hook. */
    public static void flush() {
        long pending = SUBMITTED.get() - COMPLETED.get();
        if (pending <= 0 && WORKER.isShutdown()) {
            return;
        }
        if (pending > 0) {
            DutyLog.info("Waiting for " + pending + " world save write(s) to finish.");
        }
        WORKER.shutdown();
        try {
            if (!WORKER.awaitTermination(30, TimeUnit.SECONDS)) {
                DutyLog.warn("World save writes did not finish within 30s; "
                        + (SUBMITTED.get() - COMPLETED.get()) + " outstanding.");
            } else if (SUBMITTED.get() > 0) {
                DutyLog.info("World save worker finished; " + COMPLETED.get() + " write(s) completed.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

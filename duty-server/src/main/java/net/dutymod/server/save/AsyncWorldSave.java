package net.dutymod.server.save;

import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;
import net.dutymod.framework.DutyMetrics;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    /**
     * What the writes cost, off-thread.
     *
     * <p>The counters above exist to answer "is anything outstanding at shutdown"; these answer a
     * different question -- whether the single worker can keep up. If the mean write approaches the
     * autosave interval, moving the work off the server thread has only moved where the backlog
     * forms.
     */
    private static final DutyMetrics.Timer WRITE = DutyMetrics.timer("server.save.write");
    private static final DutyMetrics.Counter SAVES = DutyMetrics.counter("server.save.count");

    /**
     * How long leaving a world spends waiting for those writes.
     *
     * <p>Separate from {@link #WRITE} because it answers a user-visible question rather than a
     * throughput one: this is time the server thread is stopped, on the path where the window can
     * be seen to stop responding.
     */
    private static final DutyMetrics.Timer FLUSH = DutyMetrics.timer("server.save.flush");

    /**
     * Bound on the world-stop wait.
     *
     * <p>Short on purpose. Outstanding writes at this point are the level and player data just
     * submitted, which is a handful of small files; if that has not finished in five seconds the
     * disk is in trouble and holding the game hostage for another twenty-five will not help. The
     * writes are not cancelled -- they keep running on the worker, and the shutdown hook waits for
     * them properly before the JVM exits.
     */
    private static final int FLUSH_TIMEOUT_SECONDS = 5;

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
        Runtime.getRuntime().addShutdownHook(new Thread(AsyncWorldSave::shutdown, "Duty world save flush"));
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
        SAVES.increment();
        DutyLog.infoOnce("async_save.live",
                "Async world save is live; " + what + " is now written off the server thread.");
        try {
            WORKER.execute(() -> {
                long started = WRITE.begin();
                try {
                    write.run();
                } catch (Throwable t) {
                    // Never let a save failure kill the worker; the next save must still get through.
                    DutyLog.warn("Async save of " + what + " failed: " + t);
                } finally {
                    // Timed here rather than around submit(): submit returns the moment the
                    // work is queued, so timing it would measure the handoff and report that
                    // saving is free. What matters is how long the write actually takes, which
                    // is what decides whether the queue can keep up with autosave.
                    WRITE.end(started);
                    COMPLETED.incrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            // The worker only refuses work after shutdown(), i.e. the JVM is on its way out. Losing
            // the save is the one outcome worth avoiding here, so write it on the calling thread
            // instead. Blocking a thread that is already shutting down costs nothing.
            DutyLog.warn("World save worker is shut down; writing " + what + " inline.");
            try {
                write.run();
            } catch (Throwable t) {
                DutyLog.warn("Inline save of " + what + " failed: " + t);
            } finally {
                COMPLETED.incrementAndGet();
            }
        }
    }

    /**
     * Waits for outstanding writes, then leaves the worker running.
     *
     * <p>Called when a server stops. In singleplayer that is every time you leave a world, which is
     * the whole reason this does not shut the executor down: the worker is a {@code static final}
     * created once per JVM, so a {@code shutdown()} here is permanent. Leaving one world would kill
     * async saving for every world joined afterwards in the same session, and -- because
     * {@link #submit} hands work straight to the executor -- turn each later save into a
     * {@link RejectedExecutionException} on the server thread. Shutting down is
     * {@link #shutdown()}'s job, and only the JVM hook calls it.
     *
     * <p>Waiting is still correct: the world must not unload with its own writes outstanding. But
     * the wait is now for exactly the queued work and nothing else. Submitting a no-op barrier and
     * waiting on that is what makes it exact -- the worker is single-threaded and FIFO, so the
     * barrier cannot run until every write queued before it has finished.
     *
     * <p>Timed, because this blocks the server thread and blocking the server thread is the thing
     * this class exists to stop doing. If leaving a world ever hitches, {@code server.save.flush}
     * says whether this was it.
     */
    public static void flush() {
        long pending = SUBMITTED.get() - COMPLETED.get();
        if (pending <= 0 || WORKER.isShutdown()) {
            return;
        }
        DutyLog.info("Waiting for " + pending + " world save write(s) to finish.");

        long started = FLUSH.begin();
        try {
            Future<?> barrier = WORKER.submit(() -> {});
            barrier.get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            DutyLog.warn("World save writes did not finish within " + FLUSH_TIMEOUT_SECONDS + "s; "
                    + (SUBMITTED.get() - COMPLETED.get()) + " still outstanding. They keep running;"
                    + " the shutdown hook waits for them again before the JVM exits.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | RejectedExecutionException e) {
            DutyLog.warn("World save flush failed: " + e);
        } finally {
            FLUSH.end(started);
        }
    }

    /**
     * Waits for outstanding writes and stops the worker for good. JVM shutdown only.
     *
     * <p>The longer timeout is deliberate: this is the last chance for a save to reach the disk,
     * and the alternative to waiting is losing it.
     */
    private static void shutdown() {
        flush();
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

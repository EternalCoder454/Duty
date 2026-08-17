package ca.spottedleaf.starlight.common.thread;

import ca.spottedleaf.starlight.common.config.Config;
import net.dutymod.framework.DutyLog;
import net.dutymod.server.flowsched.executor.ExecutorManager;

import java.util.concurrent.atomic.AtomicInteger;

public class GlobalExecutors {

    private static final boolean FORCE_ENABLED = Boolean.getBoolean("scalablelux.force_enabled");

    public static final boolean ENABLED =
            SchedulingUtil.isExternallyManaged() || FORCE_ENABLED || Config.PARALLELISM > 1;

    /**
     * The scheduler, created the first time something actually schedules through it.
     *
     * <h2>Why this is not a static field any more</h2>
     *
     * <p>{@link ExecutorManager}'s constructor does not merely size a pool -- it constructs and
     * {@code start()}s every worker thread. As a plain {@code static final} on this class that ran
     * as soon as anything touched {@code GlobalExecutors}, and {@link #ENABLED} is read by
     * {@code StarLightInterface} on the ordinary lighting path, so class-init always happened.
     *
     * <p>That is wasted work on exactly the setup this is built for. C2ME overwrites
     * {@link SchedulingUtil#scheduleTask} to route lighting through its own prioritised scheduler,
     * so when C2ME is installed nothing ever reaches the field below -- and a machine with 32
     * logical processors was starting ten parked worker threads that could never receive a task.
     * They block on a semaphore rather than spinning, so the cost was stacks and scheduler
     * bookkeeping rather than CPU, but it was cost for nothing.
     *
     * <p>Holder idiom rather than a null check: the JVM already guarantees a class is initialised
     * once, on first use, without a lock on the read path.
     */
    private static final class Holder {
        private static final AtomicInteger COUNTER = new AtomicInteger();

        static final ExecutorManager INSTANCE = new ExecutorManager(Config.PARALLELISM, thread -> {
            thread.setDaemon(true);
            thread.setName("duty-lighting-" + COUNTER.getAndIncrement());
        });

        static {
            DutyLog.info("Light engine scheduling " + Config.PARALLELISM + " thread(s) of its own.");
        }
    }

    /** {@return the scheduler, starting its threads on first call} */
    public static ExecutorManager prioritizedScheduler() {
        return Holder.INSTANCE;
    }

    static {
        // Through DutyLog rather than System.out. Upstream prints these, which on a modded client
        // means they miss the log file's formatting and cannot be filtered by module -- and this
        // one matters, because "who is scheduling lighting" is the first question when it
        // misbehaves.
        if (SchedulingUtil.isExternallyManaged()) {
            DutyLog.info("Light engine scaling is managed by another mod (C2ME or similar); "
                    + "no scheduling threads of our own will be started.");
        } else if (FORCE_ENABLED) {
            DutyLog.info("Light engine scaling forced on via -Dscalablelux.force_enabled.");
        } else if (ENABLED) {
            DutyLog.info("Light engine scaling is on.");
        } else {
            DutyLog.info("Light engine scaling is off (server.starlight_parallelism resolves to 1).");
        }
    }
}

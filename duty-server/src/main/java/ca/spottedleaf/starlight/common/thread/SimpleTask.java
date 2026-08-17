package ca.spottedleaf.starlight.common.thread;

import net.dutymod.server.flowsched.executor.LockToken;
import net.dutymod.server.flowsched.executor.Task;

import java.util.Objects;

public class SimpleTask extends Task {

    private final Runnable task;
    private final LockToken[] lockTokens;

    public SimpleTask(Runnable task, LockToken[] lockTokens) {
        this.task = Objects.requireNonNull(task, "task");
        this.lockTokens = Objects.requireNonNull(lockTokens, "lockTokens");
    }

    @Override
    public void run(Runnable releaseLocks) {
        try {
            this.task.run();
        } finally {
            releaseLocks.run();
        }
    }

    @Override
    public void propagateException(Throwable t) {
        ca.spottedleaf.starlight.common.ScalableLuxEntrypoint.LOGGER.error(
                "Light scheduling task failed", t);
    }

    @Override
    public LockToken[] lockTokens() {
        return this.lockTokens;
    }

}

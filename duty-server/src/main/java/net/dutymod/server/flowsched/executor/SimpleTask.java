package net.dutymod.server.flowsched.executor;

import java.util.Objects;

public class SimpleTask extends Task {
    private static final LockToken[] EMPTY_TOKENS = new LockToken[0];

    private final Runnable wrapped;

    public SimpleTask(Runnable wrapped) {
        this.wrapped = Objects.requireNonNull(wrapped);
    }

    @Override
    public void run(Runnable releaseLocks) {
        try {
            wrapped.run();
        } finally {
            releaseLocks.run();
        }
    }

    @Override
    public void propagateException(Throwable t) {
        net.dutymod.framework.DutyLog.error("Scheduled task failed", t);
    }

    @Override
    public LockToken[] lockTokens() {
        return EMPTY_TOKENS;
    }
}

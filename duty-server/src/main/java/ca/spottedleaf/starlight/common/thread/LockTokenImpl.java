package ca.spottedleaf.starlight.common.thread;

import net.dutymod.server.flowsched.executor.LockToken;

public record LockTokenImpl(int ownerTag, long pos) implements LockToken {
}

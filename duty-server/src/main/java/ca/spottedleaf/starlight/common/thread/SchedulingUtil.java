package ca.spottedleaf.starlight.common.thread;

import ca.spottedleaf.starlight.common.util.CoordinateUtils;
import net.dutymod.server.flowsched.executor.LockToken;

import java.util.ArrayList;

public class SchedulingUtil {

    public static void scheduleTask(int ownerTag, Runnable task, int x, int z, int radius) {
        final ArrayList<LockToken> lockTokens = new ArrayList<>((radius * 2 + 1) * (radius * 2 + 1));
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                lockTokens.add(new LockTokenImpl(ownerTag, CoordinateUtils.getChunkKey(x + i, z + j)));
            }
        }
        final SimpleTask simpleTask = new SimpleTask(task, lockTokens.toArray(LockToken[]::new));
        // The call that starts the scheduler's threads, and the only one. C2ME overwrites this
        // whole method, so on a setup with C2ME the threads are never created; see GlobalExecutors.
        GlobalExecutors.prioritizedScheduler().schedule(simpleTask, 60);
    }

    public static boolean isExternallyManaged() {
        return false;
    }

}

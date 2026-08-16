package net.dutymod.fixerupper.neoforge.load;

public class MinecraftServerReloadTracker {
    public static int ACTIVE_RELOADS = 0;

    public static boolean isReloadActive() {
        return ACTIVE_RELOADS > 0;
    }
}

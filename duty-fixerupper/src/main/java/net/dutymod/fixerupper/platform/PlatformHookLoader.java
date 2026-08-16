package net.dutymod.fixerupper.platform;

import java.lang.reflect.Constructor;

class PlatformHookLoader {
    static FixerUpperPlatformHooks findInstance() {
        String[] locations = new String[] { "neoforge", "fabric" };
        for(String location : locations) {
            try {
                Class<?> clz = Class.forName("net.dutymod.fixerupper.platform." + location + ".FixerUpperPlatformHooksImpl");
                Constructor<?> constructor = clz.getConstructor();
                constructor.setAccessible(true);
                return (FixerUpperPlatformHooks)constructor.newInstance();
            } catch(ClassNotFoundException ignored) {
            } catch(ReflectiveOperationException | ClassCastException e) {
                e.printStackTrace();
            }
        }
        System.err.println("Duty has failed to load platform hooks. It cannot function, the game will now close");
        Runtime.getRuntime().exit(1);
        throw new AssertionError("Somehow couldn't exit");
    }
}

package net.dutymod.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logging that is safe to call from a class transformer.
 *
 * <p>Log4j is available on the boot layer before mods load, so it is safe here; what is
 * <em>not</em> safe is holding the {@link Logger} in a static final field of a class the
 * transformer touches during its own initialization. Resolving it lazily keeps the transformer's
 * first log call from re-entering class loading at an awkward moment.
 */
public final class DutyLog {
    private static volatile Logger logger;

    private DutyLog() {}

    private static Logger logger() {
        Logger local = logger;
        if (local == null) {
            synchronized (DutyLog.class) {
                local = logger;
                if (local == null) {
                    local = LogManager.getLogger("Duty");
                    logger = local;
                }
            }
        }
        return local;
    }

    public static void info(String message) {
        logger().info(message);
    }

    public static void warn(String message) {
        logger().warn(message);
    }

    public static void error(String message, Throwable t) {
        logger().error(message, t);
    }

    public static void debug(String message) {
        logger().debug(message);
    }
}

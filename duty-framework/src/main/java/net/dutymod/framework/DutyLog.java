package net.dutymod.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Duty's logging.
 *
 * <p>Two things make this more than a wrapper around Log4j.
 *
 * <h2>It is safe to call from a class transformer</h2>
 *
 * <p>Log4j is on the boot layer before mods load, so logging early is fine; what is not fine is
 * holding a {@link Logger} in a {@code static final} field of a class the transformer touches
 * during its own initialization. Resolving lazily keeps the first log call from re-entering class
 * loading at an awkward moment. That is why {@link #logger} is resolved on demand rather than in a
 * field initializer, and why nothing here reads {@link DutyConfig} -- the config file is not
 * readable at that point, and a logger that needs config to decide whether to log is a logger that
 * cannot report a config failure.
 *
 * <h2>Log-once is built in</h2>
 *
 * <p>Duty had three separate hand-rolled versions of "say this the first time and never again": a
 * {@code volatile boolean} in the async saver, a {@code Set<Class<?>>} in the biome compat check,
 * and a per-thread flag in the structure watchdog. All three exist because the useful messages here
 * sit on paths that run thousands of times a second, where logging every occurrence would bury the
 * log. {@link #infoOnce} and its siblings make that one call instead of a field and a branch at
 * each site.
 *
 * <p><b>Why a module name matters.</b> Duty ships five jars that all log. "Duty" alone in a line
 * does not say which one, and the first question about any Duty log line is which module wrote it.
 * {@link #module(String)} gives a logger that answers that.
 */
public final class DutyLog {
    private static final String ROOT_NAME = "Duty";

    /** Keys already logged by a {@code *Once} call. */
    private static final Set<String> SAID = ConcurrentHashMap.newKeySet();

    /**
     * Whether {@link #debug} lines reach the log.
     *
     * <p>Set from {@link DutyConfig} once it is readable, rather than read from it on each call:
     * this class runs before the config file exists.
     */
    private static volatile boolean verbose;

    private static volatile Logger rootLogger;

    private final String name;
    private volatile Logger cached;

    private DutyLog(String name) {
        this.name = name;
    }

    /**
     * {@return a logger that names its module}
     *
     * <p>{@code module("Server")} logs under {@code Duty/Server}.
     */
    public static DutyLog module(String moduleName) {
        return new DutyLog(ROOT_NAME + "/" + moduleName);
    }

    /** Turns {@link #debug} output on or off. Called once the config has been read. */
    public static void setVerbose(boolean value) {
        verbose = value;
    }

    public static boolean verbose() {
        return verbose;
    }

    private static Logger root() {
        Logger local = rootLogger;
        if (local == null) {
            synchronized (DutyLog.class) {
                local = rootLogger;
                if (local == null) {
                    local = LogManager.getLogger(ROOT_NAME);
                    rootLogger = local;
                }
            }
        }
        return local;
    }

    private Logger logger() {
        Logger local = cached;
        if (local == null) {
            synchronized (this) {
                local = cached;
                if (local == null) {
                    local = LogManager.getLogger(name);
                    cached = local;
                }
            }
        }
        return local;
    }

    // -- Root logger, kept static so existing call sites read the same ---------------------------

    public static void info(String message) {
        root().info(message);
    }

    public static void warn(String message) {
        root().warn(message);
    }

    public static void error(String message, Throwable t) {
        root().error(message, t);
    }

    public static void error(String message) {
        root().error(message);
    }

    /** Only reaches the log when {@link #setVerbose} has been given {@code true}. */
    public static void debug(String message) {
        if (verbose) {
            root().debug(message);
        }
    }

    /**
     * Logs {@code message} the first time this {@code key} is used, and never again.
     *
     * <p>The key is what is remembered, not the message, so a caller can vary the text -- naming
     * the class or file that prompted it -- while still speaking once per distinct case.
     */
    public static void infoOnce(String key, String message) {
        if (SAID.add(key)) {
            root().info(message);
        }
    }

    public static void warnOnce(String key, String message) {
        if (SAID.add(key)) {
            root().warn(message);
        }
    }

    // -- Per-module ------------------------------------------------------------------------------

    public void moduleInfo(String message) {
        logger().info(message);
    }

    public void moduleWarn(String message) {
        logger().warn(message);
    }

    public void moduleError(String message, Throwable t) {
        logger().error(message, t);
    }

    public void moduleDebug(String message) {
        if (verbose) {
            logger().debug(message);
        }
    }

    public void moduleInfoOnce(String key, String message) {
        if (SAID.add(name + ":" + key)) {
            logger().info(message);
        }
    }

    public void moduleWarnOnce(String key, String message) {
        if (SAID.add(name + ":" + key)) {
            logger().warn(message);
        }
    }
}

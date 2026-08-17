package net.dutymod.framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Early, dependency-free configuration.
 *
 * <p>This class is deliberately built out of nothing but the JDK. Duty's bytecode transformer runs
 * during class loading, long before the mod list exists, so anything it consults cannot touch
 * NeoForge, Minecraft, Mixin or a JSON library without risking a classloading cycle. A flat
 * {@code .properties} file read through {@link Files} is the only thing safe at that point.
 *
 * <p>Options are registered by the module that owns them via {@link #register}, so the file is
 * self-documenting and unknown keys from a module the user has not installed are preserved rather
 * than silently dropped.
 */
public final class DutyConfig {
    private static final String FILE_NAME = "duty.properties";

    private static final Map<String, Option> OPTIONS = new LinkedHashMap<>();
    private static final Properties LOADED = new Properties();

    private static boolean initialized;
    private static boolean dirty;

    private DutyConfig() {}

    /**
     * A single option, with the comment written above it in the file.
     *
     * <p>{@code defaultValue} is stored as its string form so booleans and numbers can share one
     * registry and one file-writing path; the typed accessors parse on read.
     */
    public record Option(String key, String defaultValue, String comment) {}

    /**
     * Declares an option. Safe to call from a static initializer; the first {@link #get} call
     * triggers the actual file read.
     */
    public static synchronized void register(String key, boolean defaultValue, String comment) {
        register(key, Boolean.toString(defaultValue), comment);
    }

    /** Declares an integer option. */
    public static synchronized void register(String key, int defaultValue, String comment) {
        register(key, Integer.toString(defaultValue), comment);
    }

    /** Declares a free-text option. Used for enum-valued settings, which are stored by name. */
    public static synchronized void register(String key, String defaultValue, String comment) {
        OPTIONS.put(key, new Option(key, defaultValue, comment));
        if (initialized) {
            // A module registered late (e.g. a second Duty jar loaded after the first read).
            // Re-write so the new key lands in the file.
            dirty = true;
            writeIfDirty();
        }
    }

    /**
     * {@return the configured value of {@code key}, or its default if absent or unparseable}
     *
     * @throws IllegalArgumentException if the key was never {@link #register registered}
     */
    public static synchronized boolean get(String key) {
        Option option = requireOption(key);
        String raw = rawValue(option);
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        DutyLog.warn("Config key '" + key + "' has non-boolean value '" + raw + "'; using default "
                + option.defaultValue());
        return Boolean.parseBoolean(option.defaultValue());
    }

    /**
     * {@return the configured integer value of {@code key}, clamped to {@code [min, max]}}
     *
     * <p>Out-of-range values are clamped rather than rejected: a number someone typed by hand is
     * still a clear statement of intent, and refusing to start over it would be worse than
     * honouring the nearest legal value.
     */
    public static synchronized int getInt(String key, int min, int max) {
        Option option = requireOption(key);
        String raw = rawValue(option);
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            DutyLog.warn("Config key '" + key + "' has non-numeric value '" + raw + "'; using default "
                    + option.defaultValue());
            value = Integer.parseInt(option.defaultValue());
        }
        if (value < min || value > max) {
            DutyLog.warn("Config key '" + key + "' value " + value + " is outside [" + min + ", " + max
                    + "]; clamping.");
            value = Math.max(min, Math.min(max, value));
        }
        return value;
    }

    /**
     * {@return the configured text value of {@code key}, trimmed}
     *
     * <p>Enum settings are stored by name rather than ordinal so the file stays meaningful when
     * someone opens it, and so reordering an enum cannot silently change what is configured.
     */
    public static synchronized String getString(String key) {
        return rawValue(requireOption(key));
    }

    /**
     * Registers many options at once, writing the file only after the last one.
     *
     * <p>{@link #register} rewrites the file whenever a new key arrives after the first read, which
     * is fine for the handful of options a module declares by hand. FixerUpper registers roughly
     * two hundred, discovered by scanning its own mixin packages, and doing that one at a time
     * turns into two hundred rewrites during startup.
     */
    public static synchronized void registerAll(Collection<Option> newOptions) {
        boolean changed = false;
        for (Option option : newOptions) {
            Option previous = OPTIONS.put(option.key(), option);
            changed |= previous == null || !previous.equals(option);
        }
        if (changed && initialized) {
            dirty = true;
            writeIfDirty();
        }
    }

    /**
     * {@return every registered option, in registration order}
     *
     * <p>For building a settings screen. The order is the order modules registered their options,
     * which groups them by module without needing to encode that anywhere.
     *
     * <p>Note that this only lists options whose owning module is installed: registration happens
     * in each module's static initializer, so a screen built from this shows exactly the options
     * that actually do something in this instance.
     */
    public static synchronized List<Option> options() {
        ensureLoaded();
        return List.copyOf(OPTIONS.values());
    }

    /**
     * {@return the current value of {@code key} as it is stored, without parsing}
     *
     * <p>Used by a settings screen to populate a control. Returns the default when nothing has been
     * written, which is the same value {@link #get} would produce.
     */
    public static synchronized String rawOrDefault(String key) {
        return rawValue(requireOption(key));
    }

    /**
     * Stores {@code value} for {@code key} and rewrites the file.
     *
     * <p>The value is not validated here: the typed accessors already fall back or clamp on
     * anything they cannot use, and rejecting a value at this point would leave a settings screen
     * unable to report why. Callers that need validation should do it before calling.
     *
     * @throws IllegalArgumentException if the key was never {@link #register registered}
     */
    public static synchronized void set(String key, String value) {
        requireOption(key);
        ensureLoaded();
        String current = LOADED.getProperty(key);
        if (value.equals(current)) {
            return;
        }
        LOADED.setProperty(key, value);
        dirty = true;
        writeIfDirty();
    }

    private static Option requireOption(String key) {
        Option option = OPTIONS.get(key);
        if (option == null) {
            throw new IllegalArgumentException("Unregistered Duty config key: " + key);
        }
        return option;
    }

    private static String rawValue(Option option) {
        ensureLoaded();
        String raw = LOADED.getProperty(option.key());
        if (raw == null) {
            dirty = true;
            writeIfDirty();
            return option.defaultValue();
        }
        return raw.trim();
    }

    private static void ensureLoaded() {
        if (initialized) {
            return;
        }
        initialized = true;
        Path path = configPath();
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                LOADED.load(in);
            } catch (IOException e) {
                DutyLog.warn("Could not read " + path + "; falling back to defaults: " + e);
            }
        } else {
            dirty = true;
        }
        writeIfDirty();
    }

    /**
     * Rewrites the file so every registered option is present with its comment. Existing user
     * values win; keys we do not recognise are kept verbatim at the bottom so uninstalling one
     * Duty module does not wipe another module's settings.
     */
    private static void writeIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        Path path = configPath();
        StringBuilder out = new StringBuilder();
        out.append("# Duty configuration.\n");
        out.append("# Regenerated on startup: unknown keys are preserved, comments are not.\n");
        out.append('\n');

        for (Option option : OPTIONS.values()) {
            for (String line : option.comment().split("\n")) {
                out.append("# ").append(line).append('\n');
            }
            String value = LOADED.getProperty(option.key());
            if (value == null) {
                value = option.defaultValue();
                LOADED.setProperty(option.key(), value);
            }
            out.append("# default: ").append(option.defaultValue()).append('\n');
            out.append(option.key()).append('=').append(value.trim()).append('\n');
            out.append('\n');
        }

        boolean wroteHeader = false;
        for (String name : LOADED.stringPropertyNames()) {
            if (OPTIONS.containsKey(name)) {
                continue;
            }
            if (!wroteHeader) {
                out.append("# Options belonging to Duty modules that are not currently installed.\n");
                wroteHeader = true;
            }
            out.append(name).append('=').append(LOADED.getProperty(name)).append('\n');
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(path)) {
                os.write(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // Not fatal: we already hold every value in memory. A read-only config directory
            // should degrade to "defaults apply", never to "game fails to boot".
            DutyLog.warn("Could not write " + path + ": " + e);
        }
    }

    private static Path configPath() {
        // FMLPaths would be the natural answer, but it is not loaded yet when the transformer
        // asks for its config. The game's working directory is what FML itself resolves against.
        return Paths.get("config").resolve(FILE_NAME).toAbsolutePath();
    }
}

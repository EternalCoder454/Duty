package net.dutymod.framework.platform;

import net.dutymod.framework.DutyLog;

import java.util.ServiceLoader;

/**
 * Finds the {@link DutyPlatform} for the loader Duty is running on.
 *
 * <p>Resolved through {@link ServiceLoader}, so a target is added by shipping an implementation and
 * a {@code META-INF/services} entry rather than by editing this class. The lookup is lazy: the
 * holder class is not initialized until {@link #get()} is first called, which keeps a mixin config
 * plugin that never asks about the platform from paying for the lookup.
 *
 * <p><b>The failure here is deliberately loud.</b> A missing or misnamed service leaves Duty unable
 * to answer even "which loader is this", and every caller would have to handle a null. The
 * {@code ServiceLoader} file that crashed startup after a package rename went undetected precisely
 * because it was a silent string; the message below names what was looked for and what to check, so
 * the next occurrence reads as an answer rather than a puzzle. {@code tools/check-class-strings.py}
 * now verifies these names at build time.
 */
public final class Platform {
    private Platform() {}

    /** Lazy holder: initialized on first {@link #get()}, not on class load. */
    private static final class Holder {
        private static final DutyPlatform INSTANCE = resolve();

        private static DutyPlatform resolve() {
            DutyPlatform found = ServiceLoader.load(DutyPlatform.class, Platform.class.getClassLoader())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Duty Framework found no DutyPlatform implementation. One is expected from a"
                                    + " META-INF/services/net.dutymod.framework.platform.DutyPlatform entry"
                                    + " in the loader-specific source set. If a package was renamed, that"
                                    + " file names classes as text and will not have followed it."));
            DutyLog.debug("Duty Framework running on " + found.loader().displayName()
                    + " for Minecraft " + found.minecraftVersion());
            return found;
        }
    }

    /** {@return the platform Duty is running on} */
    public static DutyPlatform get() {
        return Holder.INSTANCE;
    }
}

package net.dutymod.server.lighting;

import net.dutymod.framework.DutyConfig;

/**
 * Settings for the Starlight-derived light engine.
 *
 * <p>Registered from the mixin plugin's {@code onLoad} so the keys exist in
 * {@code config/duty.properties} before anything reads them. Mixin plugins run before mods are
 * constructed, which is why this cannot wait for the mod entrypoint.
 */
public final class LightingOptions {
    /** Replace vanilla's light engine wholesale. */
    public static final String ENABLED = "server.starlight_engine";

    /**
     * How many threads the engine may use when it is scheduling its own work.
     *
     * <p>Ignored when a chunk-system mod has taken scheduling over -- see
     * {@code SchedulingUtil.isExternallyManaged}. Zero means "pick from the core count", which is
     * a third of the available processors, matching upstream.
     */
    public static final String PARALLELISM = "server.starlight_parallelism";

    private static boolean registered;

    private LightingOptions() {}

    public static synchronized void init() {
        if (registered) {
            return;
        }
        registered = true;

        DutyConfig.register(ENABLED, true,
                "Replace the light engine with the Starlight-derived one. Lighting is a large\n"
                        + "part of what chunk generation spends its time on, so this shows up as\n"
                        + "faster world generation and fewer dark chunks that fill in late.\n"
                        + "Turned off automatically if another light-engine mod is installed.");
        DutyConfig.register(PARALLELISM, 0,
                "Threads the light engine may use for its own scheduling. 0 picks a third of\n"
                        + "the available processors. Ignored when C2ME or another chunk-system mod\n"
                        + "is managing the scheduling instead, which it does automatically.");
    }

    public static boolean enabled() {
        init();
        return DutyConfig.get(ENABLED);
    }

    public static int parallelism() {
        init();
        int configured = DutyConfig.getInt(PARALLELISM, 0, 64);
        return configured > 0 ? configured : Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    }
}

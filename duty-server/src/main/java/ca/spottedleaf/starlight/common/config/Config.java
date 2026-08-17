package ca.spottedleaf.starlight.common.config;

import net.dutymod.server.lighting.LightingOptions;

/**
 * The engine's one tunable, read from Duty's config.
 *
 * <p>Upstream this parsed {@code config/scalablelux.properties} itself, and on NeoForge did so
 * through {@code FMLPaths.CONFIGDIR}. Duty already has a config file that every other module writes
 * into, and a second properties file for a single integer is a second place to look when lighting
 * misbehaves. The key is {@code server.starlight_parallelism}; see {@link LightingOptions}.
 *
 * <p>Still a constant read once into a {@code static final}, because the engine sizes its executor
 * from it at class-init and changing that later would mean rebuilding the thread pool underneath
 * running light updates.
 */
public final class Config {

    public static final int PARALLELISM = LightingOptions.parallelism();

    private Config() {}

    public static void init() {
        // Touching the class is what forces PARALLELISM to resolve; nothing else to do.
    }
}

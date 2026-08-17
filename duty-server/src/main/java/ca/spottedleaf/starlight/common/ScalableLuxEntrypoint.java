package ca.spottedleaf.starlight.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the engine's logger.
 *
 * <p>Upstream this is the mod entrypoint, carrying {@code @Mod("scalablelux")} on NeoForge and
 * calling {@code Config.init()} from its constructor. Here the engine is part of Duty: Server, which
 * already has an entrypoint, and a second {@code @Mod} would declare a second mod to the loader --
 * one with no metadata, whose id would then collide with a standalone ScalableLux install rather
 * than being caught by the plugin's stand-down check.
 *
 * <p>The class stays because the engine logs through it from several places, including
 * {@code BlockStateBaseMixin} during class transformation, where Duty's own logger is not
 * necessarily reachable yet.
 */
public final class ScalableLuxEntrypoint {
    public static final Logger LOGGER = LoggerFactory.getLogger("Duty/Lighting");

    private ScalableLuxEntrypoint() {}
}

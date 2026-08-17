package net.dutymod.client.hudcache.platform.neoforge;

import net.dutymod.client.hudcache.Gnetum;

/**
 * Starts the HUD cache.
 *
 * <p>Upstream this carries {@code @Mod(value = "gnetum", dist = Dist.CLIENT)} and is the mod's
 * entry point. Here the HUD cache is part of Duty: Client, which already has one, and a second
 * {@code @Mod} would declare a second mod to the loader -- with an id that would then collide with
 * a standalone Gnetum install rather than being caught by the incompatibility declaration.
 *
 * <p>It is also where the platform is chosen, because this is the only source set allowed to name
 * a loader. See {@link Gnetum#setPlatform}.
 */
public final class NeoforgeEntrypoint {

    private NeoforgeEntrypoint() {}

    /** Called from {@code DutyClient}, before anything can render a HUD. */
    public static void init() {
        Gnetum.setPlatform(new NeoforgePlatform());
        Gnetum.init();
    }
}

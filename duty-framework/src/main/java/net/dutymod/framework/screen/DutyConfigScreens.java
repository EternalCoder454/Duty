package net.dutymod.framework.screen;

import net.dutymod.framework.DutyLog;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers Duty's in-game settings screen, when Cloth Config is installed.
 *
 * <p>Cloth Config is an optional dependency: it is compile-only in the build and declared optional
 * in each module's {@code neoforge.mods.toml}. Without it, Duty behaves exactly as before and the
 * mod list simply shows no config button.
 *
 * <p>The guard matters more than it looks. {@link ClothConfigScreen} references
 * {@code me.shedaniel.clothconfig2} in its field and method signatures, so merely *resolving* that
 * class without the mod present throws {@link NoClassDefFoundError}. Keeping every mention of it
 * behind the {@link ModList} check, and inside a lambda that is only invoked when the player opens
 * the screen, is what makes the dependency genuinely optional rather than optional-until-someone-
 * clicks.
 */
public final class DutyConfigScreens {
    private DutyConfigScreens() {}

    private static final String CLOTH_CONFIG = "cloth_config";

    /**
     * Registers the config screen for {@code container} if Cloth Config is present.
     *
     * <p>Safe to call from any module's constructor, on either side; it does nothing on a dedicated
     * server. Each Duty module calls this for itself, so every installed module gets its own button
     * in the mod list, all opening the same screen.
     */
    public static void register(ModContainer container) {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        if (!ModList.get().isLoaded(CLOTH_CONFIG)) {
            DutyLog.debug("Cloth Config not installed; skipping the settings screen for "
                    + container.getModId() + ". Edit config/duty.properties instead.");
            return;
        }
        try {
            container.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    (c, parent) -> ClothConfigScreen.create(parent));
        } catch (Throwable t) {
            // A screen is a convenience. If Cloth changes its API under us, the mod must still
            // start -- the properties file remains the authoritative way to configure Duty.
            DutyLog.warn("Could not register the Duty settings screen: " + t);
        }
    }
}

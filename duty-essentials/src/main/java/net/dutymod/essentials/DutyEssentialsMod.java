package net.dutymod.essentials;

import net.dutymod.core.DutyLog;
import net.dutymod.core.screen.DutyConfigScreens;
import net.dutymod.essentials.config.CommandConfig;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.event.EssentialsEvents;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Duty: Essentials' entry point.
 *
 * <p>Both config classes are initialised here rather than lazily. {@link CommandConfig} has to be,
 * because commands are registered from an event that can fire before anything touches it and a
 * command whose switch has not been registered yet would read a default rather than the player's
 * setting. {@link EssentialsOptions} follows for the same reason the other modules do it: the keys
 * should exist in {@code duty.properties} and appear in the settings screen whether or not anything
 * has read them yet.
 */
@Mod(DutyEssentialsMod.MOD_ID)
public final class DutyEssentialsMod {
    public static final String MOD_ID = DutyEssentials.MOD_ID;

    public DutyEssentialsMod(ModContainer container) {
        EssentialsOptions.init();
        CommandConfig.init();
        EssentialsEvents.register();

        DutyConfigScreens.register(container);
        DutyLog.info("Duty: Essentials reporting for duty.");
    }
}

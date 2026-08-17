package net.dutymod.essentials.event;

import net.dutymod.essentials.command.CommandRegistry;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The two game events this module listens for.
 *
 * <p>Upstream routes both through its multiloader event bus. NeoForge has direct equivalents, so
 * these are two listener registrations rather than an abstraction layer.
 */
public final class EssentialsEvents {
    private EssentialsEvents() {}

    public static void register() {
        // Brigadier rebuilds its tree on every reload and on server start, so this fires more than
        // once; registering the same literals into the fresh dispatcher each time is correct.
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event ->
                CommandRegistry.COMMANDS.forEach(command -> command.register(event.getDispatcher())));

        // Recorded before the respawn so /back returns to where the player died rather than to the
        // bed they woke up in.
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, event -> {
            if (event.getEntity() instanceof DutyServerPlayer player
                    && EssentialsOptions.allowBackOnDeath.get()) {
                player.duty$setLastPosition();
            }
        });
    }
}

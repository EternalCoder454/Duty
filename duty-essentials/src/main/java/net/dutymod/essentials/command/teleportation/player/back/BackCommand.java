package net.dutymod.essentials.command.teleportation.player.back;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

public class BackCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "back", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.back", PermissionLevel.ALL))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        if (serverPlayer.duty$hasLastPosition()) {
                            Integer cooldown = EssentialsOptions.backCooldown.get();
                            Integer delay = EssentialsOptions.backTeleportDelay.get();

                            if (cooldown > 0) {
                                long cooldownTime = serverPlayer.duty$getTeleportCooldown("back");
                                if (System.currentTimeMillis() < cooldownTime) {
                                    long secondsLeft = (cooldownTime - System.currentTimeMillis()) / 1000;
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("teleport.cooldown", secondsLeft));
                                    return 0;
                                }
                            }

                            serverPlayer.duty$scheduleTeleport(serverPlayer.duty$getLastPosition(), delay, "back", cooldown, (player) -> {
                                player.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.back"), false);
                            });
                            return 1;
                        } else {
                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.back.no_last_position"));
                            return 0;
                        }
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                }));
    }
}

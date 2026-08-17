package net.dutymod.essentials.command.teleportation.level.spawn;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.model.Position;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

public class SpawnCommand implements Command {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "spawn", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.spawn", PermissionLevel.ALL))
                .executes(context -> {
                    if (context.getSource().getPlayer() != null) {
                        if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                            Position spawnPos = serverPlayer.duty$getLevelData().duty$getSpawnPosition();
                            Integer cooldown = EssentialsOptions.spawnCooldown.get();
                            Integer delay = EssentialsOptions.spawnTeleportDelay.get();

                            if (cooldown > 0) {
                                long cooldownTime = serverPlayer.duty$getTeleportCooldown("spawn");
                                if (System.currentTimeMillis() < cooldownTime) {
                                    long secondsLeft = (cooldownTime - System.currentTimeMillis()) / 1000;
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("teleport.cooldown", secondsLeft));
                                    return 0;
                                }
                            }

                            serverPlayer.duty$scheduleTeleport(spawnPos, delay, "spawn", cooldown, (player) -> {
                                player.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.spawn"), false);
                            });
                            return 1;
                        }
                    } else {
                        context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    }
                    return 0;
                }));
    }
}

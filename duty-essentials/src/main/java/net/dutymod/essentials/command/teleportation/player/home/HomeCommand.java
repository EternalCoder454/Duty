package net.dutymod.essentials.command.teleportation.player.home;

import java.util.ArrayList;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.permissions.PermissionLevel;

public class HomeCommand implements Command {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "home", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.home", PermissionLevel.ALL))
                .then(Commands.argument("home", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                return SharedSuggestionProvider.suggest(serverPlayer.duty$getHomes().stream().map(home -> home.name), builder);
                            }
                            return SharedSuggestionProvider.suggest(new ArrayList<>(), builder);
                        })
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                String homeName = StringArgumentType.getString(context, "home");
                                serverPlayer.duty$getHome(homeName).ifPresentOrElse(home -> {
                                    Integer cooldown = EssentialsOptions.homeCooldown.get();
                                    Integer delay = EssentialsOptions.homeTeleportDelay.get();

                                    if (cooldown > 0) {
                                        long cooldownTime = serverPlayer.duty$getTeleportCooldown("home");
                                        if (System.currentTimeMillis() < cooldownTime) {
                                            long secondsLeft = (cooldownTime - System.currentTimeMillis()) / 1000;
                                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("teleport.cooldown", secondsLeft));
                                            return;
                                        }
                                    }

                                    serverPlayer.duty$scheduleTeleport(home.position, delay, "home", cooldown, (player) -> {
                                        player.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home", DutyEssentials.colored(home.name)), false);
                                    });
                                }, () -> {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.not_found", DutyEssentials.coloredFailure(homeName)));
                                });
                                return 1;
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        }))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        if (serverPlayer.duty$getHomes().isEmpty()) {
                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.no_homes"));
                            return 0;
                        } else if (serverPlayer.duty$getHomes().size() == 1) {
                            Integer cooldown = EssentialsOptions.homeCooldown.get();
                            Integer delay = EssentialsOptions.homeTeleportDelay.get();

                            if (cooldown > 0) {
                                long cooldownTime = serverPlayer.duty$getTeleportCooldown("home");
                                if (System.currentTimeMillis() < cooldownTime) {
                                    long secondsLeft = (cooldownTime - System.currentTimeMillis()) / 1000;
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("teleport.cooldown", secondsLeft));
                                    return 0;
                                }
                            }

                            serverPlayer.duty$scheduleTeleport(serverPlayer.duty$getHomes().getFirst().position, delay, "home", cooldown, (player) -> {
                                player.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home", DutyEssentials.colored(serverPlayer.duty$getHomes().get(0).name)), false);
                            });
                            return 1;
                        } else {
                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.multiple_homes.get"));
                            return 0;
                        }
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                }));
    }
}

package net.dutymod.essentials.command.teleportation.player.home;

import java.util.ArrayList;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.model.Home;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.permissions.PermissionLevel;

public class DeleteHomeCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "delhome", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.delhome", PermissionLevel.ALL))
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
                                    serverPlayer.duty$removeHome(home.name);
                                    serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home.delete", DutyEssentials.colored(home.name)), false);
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
                            Home home = serverPlayer.duty$getHomes().getFirst();
                            serverPlayer.duty$removeHome(home.name);
                            serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home.delete", DutyEssentials.colored(home.name)), false);
                            return 1;
                        } else {
                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.multiple_homes.del"));
                            return 0;
                        }
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                }));
    }
}

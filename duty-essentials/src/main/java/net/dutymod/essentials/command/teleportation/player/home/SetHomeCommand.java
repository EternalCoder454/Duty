package net.dutymod.essentials.command.teleportation.player.home;

import java.util.List;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.exception.HomeLimitReachedException;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.model.Home;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionLevel;

public class SetHomeCommand implements Command {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "sethome", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.sethome", PermissionLevel.ALL))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                String name = StringArgumentType.getString(context, "name");
                                Home home = new Home(name, serverPlayer.duty$getPosition());
                                try {
                                    serverPlayer.duty$addHome(home);
                                } catch (HomeLimitReachedException e) {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.limit"));
                                    return 0;
                                }
                                serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home.set", DutyEssentials.colored(name)), false);
                                return 1;
                            } else {
                                context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                                return 0;
                            }
                        }))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        if (serverPlayer.duty$getHomes().isEmpty() || serverPlayer.duty$getHomes().size() == 1) {
                            Home home = new Home("home", serverPlayer.duty$getPosition());
                            try {
                                serverPlayer.duty$addHome(home);
                            } catch (HomeLimitReachedException e) {
                                serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.limit"));
                                return 0;
                            }
                            serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home.set", DutyEssentials.colored("home")), false);
                            return 1;
                        } else if (serverPlayer.duty$getHomes().size() == 1) {
                            Home home = new Home("home", serverPlayer.duty$getPosition());
                            serverPlayer.duty$setHomes(List.of(home));
                            serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.home.set", DutyEssentials.colored("home")), false);
                            return 1;
                        } else {
                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.home.multiple_homes.set"));
                            return 0;
                        }
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                }));
    }
}

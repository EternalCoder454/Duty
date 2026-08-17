package net.dutymod.essentials.command.player;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

public class DelNickCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "delnick", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.delnick", PermissionLevel.ALL))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        serverPlayer.duty$removeNick();
                        return 1;
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .requires(source -> DutyEssentials.API.hasPermission(source, "command.delnick.others"))
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            if (target instanceof DutyServerPlayer serverPlayer) {
                                serverPlayer.duty$removeNick();
                                return 1;
                            }
                            return 0;
                        })));
    }
}

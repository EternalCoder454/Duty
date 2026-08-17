package net.dutymod.essentials.command.teleportation.player.tpa;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

public class TPADenyCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "tpadeny", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.tpadeny", PermissionLevel.ALL))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        serverPlayer.duty$denyTPARequest();
                        return 1;
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                })
        );
    }
}

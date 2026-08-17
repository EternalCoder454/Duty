package net.dutymod.essentials.command.teleportation.level.warp;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.model.Warp;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class SetWarpCommand implements Command {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "setwarp", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.setwarp"))
                .then(Commands.argument("name", StringArgumentType.string())
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPLayer) {
                                String name = StringArgumentType.getString(context, "name");
                                Warp warp = new Warp(name, serverPLayer.duty$getPosition());
                                serverPLayer.duty$getLevelData().duty$addWarp(warp);
                                serverPLayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.warp.set", DutyEssentials.colored(name)), false);
                                return 1;
                            } else {
                                context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                                return 0;
                            }
                        })));
    }
}

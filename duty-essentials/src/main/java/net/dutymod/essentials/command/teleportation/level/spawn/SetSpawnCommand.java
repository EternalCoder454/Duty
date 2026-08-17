package net.dutymod.essentials.command.teleportation.level.spawn;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class SetSpawnCommand implements Command {

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "setspawn", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.setspawn"))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        serverPlayer.duty$getLevelData().duty$setSpawnPosition(serverPlayer.duty$getPosition());
                        context.getSource().getLevel().setRespawnData(serverPlayer.duty$getNewRespawnData());
                        serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.spawn.set"), false);
                        return 1;
                    } else {
                        context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                        return 0;
                    }
                }));
    }
}

package net.dutymod.essentials.command.player;

import java.util.Collection;
import java.util.Collections;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class VanishCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "vanish", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.vanish"))
                .executes(context -> vanish(context.getSource(), Collections.singleton(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> vanish(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
        }

    private int vanish(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int successCount = 0;
        for (ServerPlayer player : targets) {
            if (player instanceof DutyServerPlayer duty_essentialsPlayer) {
                boolean isVanished = !duty_essentialsPlayer.duty$isVanished();
                duty_essentialsPlayer.duty$setVanished(isVanished);
                successCount++;
            }
        }
        return successCount;
    }
}
package net.dutymod.essentials.command.player;

import java.util.Collection;
import java.util.Collections;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class FlyCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "fly", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.fly"))
                .executes(context -> toggleFlight(context.getSource(), Collections.singleton(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> toggleFlight(context.getSource(), EntityArgument.getPlayers(context, "targets"))))
        );
    }

    private int toggleFlight(CommandSourceStack source, Collection<ServerPlayer> targets) {
        if (!EssentialsOptions.flyAllow.get()) {
            if (source.getEntity() instanceof DutyServerPlayer serverPlayer) {
                serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.fly.disabled"));
            } else {
                source.sendFailure(DutyEssentials.prefixedFailureTranslatable("commands.fly.disabled"));
            }
            return 0;
        }
        for (ServerPlayer player : targets) {
            boolean canFly = !player.getAbilities().mayfly;
            player.getAbilities().mayfly = canFly;
            if (!canFly) {
                player.getAbilities().flying = false;
            }
            player.onUpdateAbilities();

            String status = canFly ? "enabled" : "disabled";
            if (source.getEntity() == player) {
                if (player instanceof DutyServerPlayer serverPlayer) {
                    serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.fly." + status), false);
                }
            } else {
                if (player instanceof DutyServerPlayer serverPlayer) {
                    serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.fly." + status), false);
                }
                source.sendSuccess(() -> DutyEssentials.prefixedTranslatable("commands.fly.other." + status, player.getDisplayName()), true);
            }
        }
        return targets.size();
    }
}
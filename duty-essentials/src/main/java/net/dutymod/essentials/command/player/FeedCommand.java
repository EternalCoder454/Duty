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

public class    FeedCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "feed", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.feed"))
                .executes(context -> feed(context.getSource(), Collections.singleton(context.getSource().getPlayerOrException())))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> feed(context.getSource(), EntityArgument.getPlayers(context, "targets")))));
    }

    private int feed(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);

            if (source.getEntity() == player) {
                if (player instanceof DutyServerPlayer serverPlayer) {
                    serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.feed"), false);
                }
            } else {
                if (player instanceof DutyServerPlayer serverPlayer) {
                    serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.feed"), false);
                }
                source.sendSuccess(() -> DutyEssentials.prefixedTranslatable("commands.feed.other", player.getDisplayName()), true);
            }
        }
        return targets.size();
    }
}
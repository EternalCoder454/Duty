package net.dutymod.essentials.command.player;

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
import net.minecraft.server.level.ServerPlayer;

public class GodCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "god", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.god"))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        context.getSource().getServer().getPlayerList().getPlayers().stream()
                                                .filter(player -> player != context.getSource().getPlayer())
                                                .map(player -> player.getGameProfile().name()), builder))
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                if (!EssentialsOptions.godModeAllow.get()) {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.god.disabled"));
                                    return 0;
                                }

                                String playerName = StringArgumentType.getString(context, "player");
                                ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayers().stream()
                                        .filter(player -> player != context.getSource().getPlayer())
                                        .filter(player -> player.getGameProfile().name().equals(playerName)).findFirst().orElse(null);
                                if (target instanceof DutyServerPlayer targetServerPlayer) {
                                    targetServerPlayer.duty$toggleGodMode();
                                    if (targetServerPlayer.duty$hasGodMode()) {
                                        serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.god.toggled.other.on", targetServerPlayer.duty$getName()), false);
                                    } else {
                                        serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.god.toggled.other.off", targetServerPlayer.duty$getName()), false);
                                    }
                                    return 1;
                                } else {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.god.player_not_found", DutyEssentials.coloredFailure(playerName)));
                                    return 0;
                                }
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        }))
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        if (!EssentialsOptions.godModeAllow.get()) {
                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.god.disabled"));
                            return 0;
                        }
                        serverPlayer.duty$toggleGodMode();
                        return 1;
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                }));
    }
}

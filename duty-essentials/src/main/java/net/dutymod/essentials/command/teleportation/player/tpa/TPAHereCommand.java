package net.dutymod.essentials.command.teleportation.player.tpa;

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
import net.minecraft.server.permissions.PermissionLevel;

public class TPAHereCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "tpahere", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.tpahere", PermissionLevel.ALL))
                .then(Commands.argument("player", StringArgumentType.string())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(
                                        context.getSource().getServer().getPlayerList().getPlayers().stream()
                                                .filter(player -> player != context.getSource().getPlayer())
                                                .map(player -> player.getGameProfile().name()), builder))
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                String playerName = StringArgumentType.getString(context, "player");
                                ServerPlayer target = context.getSource().getServer().getPlayerList().getPlayers().stream()
                                        .filter(player -> player != context.getSource().getPlayer())
                                        .filter(player -> player.getGameProfile().name().equals(playerName)).findFirst().orElse(null);
                                if (target instanceof DutyServerPlayer targetServerPlayer) {
                                    Integer cooldown = EssentialsOptions.tpaCooldown.get();
                                    if (cooldown > 0) {
                                        long cooldownTime = serverPlayer.duty$getTeleportCooldown("tpa");
                                        if (System.currentTimeMillis() < cooldownTime) {
                                            long secondsLeft = (cooldownTime - System.currentTimeMillis()) / 1000;
                                            serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("teleport.cooldown", secondsLeft));
                                            return 0;
                                        }
                                        serverPlayer.duty$setTeleportCooldown("tpa", cooldown);
                                    }
                                    serverPlayer.duty$sendTPARequest(targetServerPlayer, true);
                                    return 1;
                                } else {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.tpa.player_not_found", DutyEssentials.coloredFailure(playerName)));
                                    return 0;
                                }
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        }))
        );
    }
}

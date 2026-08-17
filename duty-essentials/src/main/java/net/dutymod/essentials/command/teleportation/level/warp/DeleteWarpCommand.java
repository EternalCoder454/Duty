package net.dutymod.essentials.command.teleportation.level.warp;

import java.util.ArrayList;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

public class DeleteWarpCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "delwarp", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.delwarp"))
                .then(Commands.argument("warp", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                return SharedSuggestionProvider.suggest(serverPlayer.duty$getLevelData().duty$getWarps().stream().map(warp -> warp.name), builder);
                            }
                            return SharedSuggestionProvider.suggest(new ArrayList<>(), builder);
                        })
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                String warpName = StringArgumentType.getString(context, "warp");
                                serverPlayer.duty$getLevelData().duty$getWarp(warpName).ifPresentOrElse(warp -> {
                                    serverPlayer.duty$getLevelData().duty$removeWarp(warp.name);
                                    serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.warp.delete", DutyEssentials.colored(warp.name)), false);
                                }, () -> {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.warp.not_found", DutyEssentials.coloredFailure(warpName)));
                                });
                                return 1;
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        })));
    }
}
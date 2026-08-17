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
import net.minecraft.server.permissions.PermissionLevel;

public class NickCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "nick", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.nick", PermissionLevel.ALL))
                .then(Commands.argument("nickname", StringArgumentType.string())
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                String nickname = StringArgumentType.getString(context, "nickname");
                                int maxLength = serverPlayer.duty$getMaxNickLength();
                                if (nickname.length() > maxLength) {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.nick.too_long", maxLength));
                                    return 0;
                                }
                                if (!EssentialsOptions.allowColorsInNick.get()) {
                                    if (nickname.contains("&") || nickname.contains("§")) {
                                        serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.nick.colors_disabled"));
                                        return 0;
                                    }
                                }
                                serverPlayer.duty$setNick(nickname);
                                return 1;
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        })
                )
                .executes(context -> {
                    if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                        serverPlayer.duty$removeNick();
                        return 1;
                    }
                    context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                    return 0;
                })
        );
    }
}

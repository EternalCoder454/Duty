package net.dutymod.essentials.command.player;

import java.util.Collection;
import java.util.Collections;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameModeArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;

public class GamemodeCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "gamemode", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.gamemode"))
                .then(Commands.argument("gamemode", GameModeArgument.gameMode())
                        .executes(context -> setMode(context, Collections.singleton(context.getSource().getPlayerOrException()), GameModeArgument.getGameMode(context, "gamemode")))
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(context -> setMode(context, EntityArgument.getPlayers(context, "target"), GameModeArgument.getGameMode(context, "gamemode"))))));
    }

    private static void logGamemodeChange(CommandSourceStack commandSourceStack, ServerPlayer serverPlayer, GameType gameType) {
        Component component = DutyEssentials.colored(Component.translatable("gameMode." + gameType.getName()));
        if (commandSourceStack.getEntity() == serverPlayer) {
            if (commandSourceStack.getPlayer() instanceof DutyServerPlayer duty_essentialsServerPlayer) {
                duty_essentialsServerPlayer.duty$sendSystemMessage(DutyEssentials.prefixedVanillaTranslatable("gameMode.changed", component), false);
            } else {
                commandSourceStack.sendSuccess(() -> DutyEssentials.prefixedVanillaTranslatable("commands.gamemode.success.self", component), true);
            }
        } else {
            if (commandSourceStack.getLevel().getGameRules().get(GameRules.SEND_COMMAND_FEEDBACK)) {
                if (serverPlayer instanceof DutyServerPlayer duty_essentialsServerPlayer) {
                    duty_essentialsServerPlayer.duty$sendSystemMessage(DutyEssentials.prefixedVanillaTranslatable("gameMode.changed", component), false);
                } else {
                    serverPlayer.sendSystemMessage(DutyEssentials.prefixedVanillaTranslatable("gameMode.changed", component));
                }
            }

            if (commandSourceStack.getPlayer() instanceof DutyServerPlayer duty_essentialsServerPlayer) {
                duty_essentialsServerPlayer.duty$sendSystemMessage(DutyEssentials.prefixedVanillaTranslatable("commands.gamemode.success.other", serverPlayer.getDisplayName(), component), false);
            } else {
                commandSourceStack.sendSuccess(() -> DutyEssentials.prefixedVanillaTranslatable("commands.gamemode.success.other", serverPlayer.getDisplayName(), component), true);
            }
        }
    }

    /** Package-visible so {@link GameModeShortcutCommand} can reuse it rather than copy it. */
    static int setMode(CommandContext<CommandSourceStack> commandContext, Collection<ServerPlayer> collection, GameType gameType) {
        int i = 0;

        for (ServerPlayer serverPlayer : collection) {
            if (serverPlayer.setGameMode(gameType)) {
                logGamemodeChange(commandContext.getSource(), serverPlayer, gameType);
                i++;
            }
        }

        return i;
    }
}

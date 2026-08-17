package net.dutymod.essentials.command.chat;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.dutymod.essentials.utils.ChatFormatter;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.server.level.ServerPlayer;

public class BroadcastCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "broadcast", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.broadcast"))
                .then(Commands.argument("message", MessageArgument.message()).executes(commandContext -> {
                    MessageArgument.resolveChatMessage(commandContext, "message", playerChatMessage -> {
                        for (ServerPlayer player : commandContext.getSource().getServer().getPlayerList().getPlayers()) {
                            if (player instanceof DutyServerPlayer serverPlayer) {
                                serverPlayer.duty$sendSystemMessage(DutyEssentials.getPrefix().append(ChatFormatter.format(playerChatMessage.signedContent())), false);
                            }
                        }
                    });
                    return 1;
                }))
        );
    }
}

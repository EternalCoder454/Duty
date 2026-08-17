package net.dutymod.essentials.command.chat;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.level.DutyServerPlayer;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.PlayerList;

public class ReplyCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, "reply", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.reply", PermissionLevel.ALL))
                .then(Commands.argument("message", MessageArgument.message())
                        .executes(context -> {
                            if (context.getSource().getPlayer() instanceof DutyServerPlayer serverPlayer) {
                                if (serverPlayer.duty$getLastMessageSender().isPresent()) {
                                    DutyServerPlayer recipient = serverPlayer.duty$getLastMessageSender().get();
                                    MessageArgument.resolveChatMessage(context, "message", playerChatMessage -> {
                                            sendMessage((ServerPlayer) serverPlayer, (ServerPlayer) recipient, playerChatMessage);
                                            recipient.duty$setLastMessageSender(serverPlayer.duty$getUUID());
                                    });
                                    return 1;
                                } else {
                                    serverPlayer.duty$sendFailedSystemMessage(DutyEssentials.prefixedFailureTranslatable("commands.reply.no_last_sender"));
                                    return 0;
                                }
                            }
                            context.getSource().sendFailure(NEEDS_PLAYER_ERROR);
                            return 0;
                        })));
    }

    private static void sendMessage(ServerPlayer sender, ServerPlayer recipient, PlayerChatMessage playerChatMessage) {
        ChatType.Bound bound = ChatType.bind(ChatType.MSG_COMMAND_INCOMING, sender);
        OutgoingChatMessage outgoingChatMessage = OutgoingChatMessage.create(playerChatMessage);
        boolean bl;

        ChatType.Bound bound2 = ChatType.bind(ChatType.MSG_COMMAND_OUTGOING, sender).withTargetName(recipient.getDisplayName());
        sender.sendChatMessage(outgoingChatMessage, false, bound2);
        boolean bl2 = sender.shouldFilterMessageTo(recipient);
        recipient.sendChatMessage(outgoingChatMessage, bl2, bound);
        bl = bl2 && playerChatMessage.isFullyFiltered();

        if (bl) {
            sender.sendSystemMessage(PlayerList.CHAT_FILTERED_FULL);
        }
    }
}

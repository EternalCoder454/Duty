package net.dutymod.essentials.command;

import net.dutymod.essentials.DutyEssentials;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public interface Command {

    Component NEEDS_PLAYER_ERROR = DutyEssentials.API.translatable("commands.error.needs_player");

    void register(CommandDispatcher<CommandSourceStack> dispatcher);
}

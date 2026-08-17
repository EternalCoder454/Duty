package net.dutymod.essentials.command.player;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.command.CommandManager;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.PermissionLevel;

public class AFKCommand implements Command {

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
         CommandManager.register(dispatcher, "afk", literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.afk", PermissionLevel.ALL)));
    }
}

package net.dutymod.essentials.command.time;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class NightCommand extends TimeCommand {

    public static final String TYPE = "night";

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, TYPE, literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.time.night"))
                .executes(context -> setTime(context.getSource(), TYPE, 13000)));
    }
}

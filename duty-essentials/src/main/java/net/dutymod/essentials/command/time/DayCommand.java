package net.dutymod.essentials.command.time;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class DayCommand extends TimeCommand {

    public static final String TYPE = "day";

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, TYPE, literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.time.day"))
                .executes(context -> setTime(context.getSource(), TYPE, 1000)));
    }
}

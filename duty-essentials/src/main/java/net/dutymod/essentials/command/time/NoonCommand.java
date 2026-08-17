package net.dutymod.essentials.command.time;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class NoonCommand extends TimeCommand {

    public static final String TYPE = "noon";

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, TYPE, literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.time.noon"))
                .executes(context -> setTime(context.getSource(), TYPE, 6000)));
    }
}

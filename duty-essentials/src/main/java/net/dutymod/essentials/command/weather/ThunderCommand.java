package net.dutymod.essentials.command.weather;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class ThunderCommand extends WeatherCommand {

    private static final String TYPE = "thunder";

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, TYPE, literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.weather.thunder"))
                .executes(context -> setWeather(context.getSource(), TYPE, 0, EssentialsOptions.thunderTime.get(), true, true)));
    }
}

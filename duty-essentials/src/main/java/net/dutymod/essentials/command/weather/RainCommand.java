package net.dutymod.essentials.command.weather;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.CommandManager;
import net.dutymod.essentials.config.EssentialsOptions;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;

public class RainCommand extends WeatherCommand {

    private static final String TYPE = "rain";

    @Override
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        CommandManager.register(dispatcher, TYPE, literal -> literal
                .requires(source -> DutyEssentials.API.hasPermission(source, "command.weather.rain"))
                .executes(context -> setWeather(context.getSource(), TYPE, 0, EssentialsOptions.rainyTime.get(), true, false)));
    }
}

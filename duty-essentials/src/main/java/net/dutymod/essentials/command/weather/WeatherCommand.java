package net.dutymod.essentials.command.weather;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.saveddata.WeatherData;

public abstract class WeatherCommand implements Command {

    protected static int setWeather(CommandSourceStack source, String type, int clearTime, int rainTime, boolean isRaining, boolean isThundering) {
        WeatherData weatherData = source.getLevel().getWeatherData();
        weatherData.setClearWeatherTime(clearTime);
        weatherData.setRainTime(rainTime);
        weatherData.setRaining(isRaining);
        weatherData.setThundering(isThundering);

        if (source.getPlayer() instanceof DutyServerPlayer serverPlayer) {
            serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.weather.set." + type), false);
        } else {
            source.sendSuccess(() -> DutyEssentials.prefixedTranslatable("commands.weather.set." + type), true);
        }
        return 1;
    }
}

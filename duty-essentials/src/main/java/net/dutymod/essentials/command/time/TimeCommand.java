package net.dutymod.essentials.command.time;

import net.dutymod.essentials.DutyEssentials;
import net.dutymod.essentials.command.Command;
import net.dutymod.essentials.level.DutyServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.dimension.DimensionType;

public abstract class TimeCommand implements Command {

    public static int setTime(CommandSourceStack source, String type, int time) {
        ServerClockManager clockManager = source.getServer().clockManager();
        Holder<DimensionType> dimensionType = source.getLevel().dimensionTypeRegistration();

        dimensionType.value().defaultClock().ifPresent(clock ->
                clockManager.setTotalTicks(clock, time));

        if (source.getPlayer() instanceof DutyServerPlayer serverPlayer) {
            serverPlayer.duty$sendSystemMessage(DutyEssentials.prefixedTranslatable("commands.time.set." + type), false);
        } else {
            source.sendSuccess(() -> DutyEssentials.prefixedTranslatable("commands.time.set." + type), true);
        }

        return 1;
    }
}
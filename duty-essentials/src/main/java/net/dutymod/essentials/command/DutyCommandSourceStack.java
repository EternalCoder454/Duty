package net.dutymod.essentials.command;

import net.dutymod.essentials.level.DutyServerLevel;
import net.dutymod.essentials.level.storage.EssentialsLevelData;
import net.dutymod.essentials.model.Position;

public interface DutyCommandSourceStack {

    DutyServerLevel duty$getLevel();
    Position duty$getPosition();

    EssentialsLevelData duty$getLevelData();

    DutyServerLevel duty$getOverworld();
}

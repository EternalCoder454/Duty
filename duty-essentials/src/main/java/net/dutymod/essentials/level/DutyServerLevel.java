package net.dutymod.essentials.level;

import net.dutymod.essentials.level.storage.EssentialsLevelData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public interface DutyServerLevel {

    EssentialsLevelData duty$getLevelData();

    Identifier duty$getDimension();
}

package net.dutymod.essentials.level.storage;

import net.dutymod.essentials.model.Position;
import net.dutymod.essentials.model.Warp;

import java.util.List;
import java.util.Optional;

public interface EssentialsLevelData {

    Position duty$getSpawnPosition();

    void duty$setSpawnPosition(Position position);

    List<Warp> duty$getWarps();

    void duty$setWarps(List<Warp> warps);

    Optional<Warp> duty$getWarp(String name);

    void duty$addWarp(Warp warp);

    void duty$removeWarp(String name);


}

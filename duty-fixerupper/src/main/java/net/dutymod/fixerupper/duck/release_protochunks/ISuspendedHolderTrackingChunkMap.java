package net.dutymod.fixerupper.duck.release_protochunks;

import net.minecraft.world.level.ChunkPos;

import java.util.concurrent.Executor;

public interface ISuspendedHolderTrackingChunkMap {
    void duty$markForSuspensionCheck(ChunkPos pos);

    Executor duty$getMainThreadExecutor();
}

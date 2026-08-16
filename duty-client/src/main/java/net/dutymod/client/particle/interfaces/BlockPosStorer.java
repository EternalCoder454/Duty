package net.dutymod.client.particle.interfaces;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public interface BlockPosStorer {

    void duty$tickCachedPos();
    BlockPos duty$getCachedPos();
    BlockState duty$getCachedState();
    boolean duty$getCachedEmpty();
}
package net.dutymod.client.particle.interfaces;

import net.minecraft.core.BlockPos;

import java.util.concurrent.ConcurrentHashMap;

public interface CachedLightProvider {
    ConcurrentHashMap<BlockPos, Integer> particle_core_getCache();
}
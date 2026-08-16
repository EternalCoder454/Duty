package net.dutymod.client.particle;

import net.minecraft.util.RandomSource;

/** Shared thread-local randomness for particle spawn decisions. */
public final class PcUtils {
    public static final RandomSource random = RandomSource.createThreadLocalInstance();

    private PcUtils() {}
}

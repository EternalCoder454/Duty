package org.codeberg.zenxarch.fastnoise.mixin;

import java.util.List;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.class)
public interface ChunkNoiseSamplerAccessor {
  @Invoker("createMultiNoiseSampler")
  public Sampler zenxarch$createMultiNoiseSampler(
      final NoiseRouter noiseRouter, final List<Climate.ParameterPoint> spawnTarget);
}

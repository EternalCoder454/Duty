package org.codeberg.zenxarch.fastnoise.mixin.perf.noise;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.codeberg.zenxarch.fastnoise.noise.FastNoiseGen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

  @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

  @Shadow
  protected abstract NoiseChunk createChunkNoiseSampler(
      ChunkAccess chunk, StructureManager world, Blender blender, RandomState noiseConfig);

  @Shadow
  protected abstract ChunkAccess populateNoise(
      Blender blender,
      StructureManager structureAccessor,
      RandomState noiseConfig,
      ChunkAccess chunk,
      int minimumCellY,
      int cellHeight);

  @WrapMethod(method = "method_38332")
  private ChunkAccess zenxarch$populateNoise(
      ChunkAccess chunk,
      int cellHeight,
      NoiseSettings generationShapeConfig,
      int minimumY,
      Blender blender,
      StructureManager structureAccessor,
      RandomState noiseConfig,
      int minimumCellY,
      Operation<ChunkAccess> op) {
    if (((Object) this.getClass()) != NoiseBasedChunkGenerator.class)
      return op.call(
          chunk,
          cellHeight,
          generationShapeConfig,
          minimumY,
          blender,
          structureAccessor,
          noiseConfig,
          minimumCellY);
    if (SharedConstants.debugVoidTerrain(chunk.getPos())) return chunk;

    var defaultBlock = settings.value().defaultBlock();

    if (SharedConstants.DEBUG_AQUIFERS
        || defaultBlock == FastNoiseGen.AIR
        || chunk.isUpgrading()
        || !FastNoiseGen.isEmpty(chunk))
      return this.populateNoise(
          blender, structureAccessor, noiseConfig, chunk, minimumCellY, cellHeight);

    var sampler =
        chunk.getOrCreateNoiseChunk(
            chunkx ->
                this.createChunkNoiseSampler(chunkx, structureAccessor, blender, noiseConfig));

    FastNoiseGen.doFill(sampler, defaultBlock, chunk, minimumCellY, cellHeight);

    return chunk;
  }
}

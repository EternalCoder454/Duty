package org.codeberg.zenxarch.fastnoise.mixin.perf.biome;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.codeberg.zenxarch.fastnoise.mixin.ChunkGeneratorAccessor;
import org.codeberg.zenxarch.fastnoise.noise.FastBiomeGen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

  @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

  @Shadow
  private NoiseChunk createChunkNoiseSampler(
      final ChunkAccess chunk,
      final StructureManager world,
      final Blender blender,
      final RandomState noiseConfig) {
    throw new IllegalStateException("Mixin method called");
  }

  @WrapMethod(
      method =
          "populateBiomes(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)V")
  private void zenxarch$populateBiomes(
      final Blender blender,
      final RandomState noiseConfig,
      final StructureManager structureAccessor,
      final ChunkAccess chunk,
      Operation<Void> op) {
    var sampler =
        chunk.getOrCreateNoiseChunk(
            chunkx ->
                this.createChunkNoiseSampler(chunkx, structureAccessor, blender, noiseConfig));

    var original = ((ChunkGeneratorAccessor) this).zenxarch$getBiomeSource();

    var supplier = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(original), chunk);

    if (supplier != original) {
      op.call(blender, noiseConfig, structureAccessor, chunk);
      return;
    }

    FastBiomeGen.populateBiomes(chunk, supplier, sampler, noiseConfig, this.settings);
  }
}

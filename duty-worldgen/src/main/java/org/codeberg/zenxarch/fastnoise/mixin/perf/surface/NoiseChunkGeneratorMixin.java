package org.codeberg.zenxarch.fastnoise.mixin.perf.surface;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Registry;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.codeberg.zenxarch.fastnoise.mixin.SurfaceBuilderAccessor;
import org.codeberg.zenxarch.fastnoise.surface.FastSurfaceGen;
import org.codeberg.zenxarch.fastnoise.surface.biome.FastBiomeAccumulator;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {

  @Shadow @Final private Holder<NoiseGeneratorSettings> settings;

  @Shadow
  private NoiseChunk createChunkNoiseSampler(
      final ChunkAccess chunk,
      final StructureManager world,
      final Blender blender,
      final RandomState noiseConfig) {
    throw new IllegalStateException("Mixin class code called");
  }

  @WrapOperation(
      method =
          "buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/gen/chunk/NoiseChunkGenerator;buildSurface(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/HeightContext;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/biome/source/BiomeAccess;Lnet/minecraft/registry/Registry;Lnet/minecraft/world/gen/chunk/Blender;)V"))
  private void zenxarch$buildSurface(
      final NoiseBasedChunkGenerator chunkGenerator,
      final ChunkAccess chunk,
      final WorldGenerationContext context,
      final RandomState noiseConfig,
      final StructureManager structureAccessor,
      final BiomeManager biomeAccess,
      final Registry<Biome> registry,
      final Blender blender,
      Operation<Void> op,
      @Local final WorldGenRegion chunkRegion) {

    var includedBiomes = FastBiomeAccumulator.accumulate(chunkRegion, chunk.getPos());

    if (!FastSurfaceGen.canUseSurfaceBuilder(chunk, includedBiomes, registry)) {
      op.call(
          chunkGenerator,
          chunk,
          context,
          noiseConfig,
          structureAccessor,
          biomeAccess,
          registry,
          blender);
      return;
    }

    final var chunks = FastSurfaceGen.addSectionsInFrustum(chunkRegion, chunk);

    if (chunks == null) {
      op.call(
          chunkGenerator,
          chunk,
          context,
          noiseConfig,
          structureAccessor,
          biomeAccess,
          registry,
          blender);
      return;
    }

    var sampler =
        chunk.getOrCreateNoiseChunk(
            chunkx ->
                this.createChunkNoiseSampler(chunkx, structureAccessor, blender, noiseConfig));

    var settings = this.settings.value();

    FastSurfaceGen.generateSurface(
        (SurfaceBuilderAccessor) noiseConfig.surfaceSystem(),
        noiseConfig,
        biomeAccess,
        settings.useLegacyRandomSource(),
        context,
        chunk,
        sampler,
        settings.surfaceRule(),
        includedBiomes,
        chunks,
        registry);
  }
}

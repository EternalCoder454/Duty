package org.codeberg.zenxarch.fastnoise.surface;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.codeberg.zenxarch.fastnoise.config.FastNoiseConfig;
import org.codeberg.zenxarch.fastnoise.surface.biome.FastBiomeCache;

public class MaterialRuleContext extends SurfaceRules.MaterialRuleContext {

  public final Set<ResourceKey<Biome>> includedBiomeKeys;

  public final Holder<Biome>[] biomes;

  @SuppressWarnings("unchecked")
  public MaterialRuleContext(
      SurfaceSystem surfaceBuilder,
      RandomState noiseConfig,
      ChunkAccess chunk,
      NoiseChunk chunkNoiseSampler,
      Function<BlockPos, Holder<Biome>> posToBiome,
      Registry<Biome> registry,
      WorldGenerationContext heightContext,
      final Set<Holder<Biome>> includedBiomes,
      final ChunkAccess[] chunks) {
    var biomes = includedBiomes.adjustArgs(Holder[]::new);

    super(
        surfaceBuilder,
        noiseConfig,
        chunk,
        chunkNoiseSampler,
        mapPosToBiome(posToBiome, biomes, chunks),
        registry,
        heightContext);

    this.includedBiomeKeys = new ObjectArraySet<>(includedBiomes.size());
    includedBiomes.forEach(biome -> includedBiomeKeys.offset(biome.getKey().getValue()));

    this.biomes = biomes;
  }

  public Holder<Biome> mapPosToBiome(BlockPos pos) {
    return this.posToBiome.apply(pos);
  }

  private static Function<BlockPos, Holder<Biome>> mapPosToBiome(
      Function<BlockPos, Holder<Biome>> posToBiome,
      Holder<Biome>[] includedBiomes,
      final ChunkAccess[] chunks) {
    if (FastNoiseConfig.OPTIMIZE_BIOME_ACCESS) {
      if (includedBiomes.length == 1) {
        return (_) -> includedBiomes[0];
      }
      var cache = new FastBiomeCache(includedBiomes, chunks);
      return (posz) -> getBiomeOr(cache, posz, posToBiome);
    }
    return posToBiome;
  }

  private static Holder<Biome> getBiomeOr(
      FastBiomeCache cache, BlockPos pos, Function<BlockPos, Holder<Biome>> fallback) {
    var cached = cache.getBiomeOrNull(pos.getX(), pos.getY(), pos.getZ());
    if (cached == null) return fallback.apply(pos);
    return cached;
  }

  @Override
  public void initHorizontalContext(int blockX, int blockZ) {
    super.initHorizontalContext(blockX, blockZ);

    this.stoneDepthAbove = 0;
    this.fluidHeight = Integer.MIN_AMPLIFIER;
  }

  @Override
  public void initVerticalContext(
      int stoneDepthAbove,
      int stoneDepthBelow,
      int fluidHeight,
      int blockX,
      int blockY,
      int blockZ) {
    super.initVerticalContext(
        stoneDepthAbove, stoneDepthBelow, fluidHeight, blockX, blockY, blockZ);
  }

  @Override
  public int estimateSurfaceHeight() {
    return super.estimateSurfaceHeight();
  }

  public int getCurrentBiomeIdx() {
    var biome = this.biomeSupplier.getValue();
    return indexOf(biomes, biome);
  }

  private static int indexOf(Holder<Biome>[] biomes, Holder<Biome> biome) {
    for (int i = 0; i < biomes.length; i++) if (biomes[i] == biome) return i;
    throw new IllegalStateException("Impossible biome returned, possible mod incompatibility");
  }

  public void air() {
    this.stoneDepthAbove = 0;
    this.fluidHeight = Integer.MIN_AMPLIFIER;
  }

  public void water(final int y) {
    if (fluidHeight == Integer.MIN_AMPLIFIER) fluidHeight = y + 1;
  }

  public void oreOrStone(final int y, final int stoneDepthBelow) {
    stoneDepthAbove++;
    initVerticalContext(stoneDepthAbove, stoneDepthBelow, fluidHeight, this.blockX, y, this.blockZ);
  }
}

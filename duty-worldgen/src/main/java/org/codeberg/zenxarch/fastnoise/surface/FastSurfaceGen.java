package org.codeberg.zenxarch.fastnoise.surface;

import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.SingleValuePalette;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.codeberg.zenxarch.fastnoise.config.FastNoiseConfig;
import org.codeberg.zenxarch.fastnoise.mixin.SurfaceBuilderAccessor;
import org.codeberg.zenxarch.fastnoise.surface.cache.FastColumnIterator;
import org.codeberg.zenxarch.fastnoise.surface.cache.FastIsFluidCache;
import org.codeberg.zenxarch.fastnoise.surface.cache.FastPaletteIndexCache;

public class FastSurfaceGen {

  public static boolean canUseSurfaceBuilder(
      ChunkAccess chunk, Set<Holder<Biome>> includedBiomes, Registry<Biome> registry) {
    if (chunk.isUpgrading()) return false;
    if (includedBiomes.size() > 64) return false;
    if (containsAny(
        registry,
        includedBiomes,
        Biomes.ERODED_BADLANDS,
        Biomes.FROZEN_OCEAN,
        Biomes.DEEP_FROZEN_OCEAN)) return false;
    return sanityCheck(chunk);
  }

  public static ChunkAccess[] collectChunks(WorldGenRegion chunkRegion, ChunkAccess center) {
    final var centerPos = center.getPos();
    final var cx = centerPos.x();
    final var cz = centerPos.z();
    final var chunks = new ChunkAccess[9];

    for (int z = -1; z <= 1; z++) {
      for (int x = -1; x <= 1; x++) {
        final var idx = ((z + 1) * 3) + (x + 1);
        if ((chunks[idx] = chunkRegion.getChunk(cx + x, cz + z))
            .getPersistedStatus()
            .isBefore(ChunkStatus.BIOMES)) return null;
      }
    }

    return chunks;
  }

  @SafeVarargs
  private static boolean containsAny(
      Registry<Biome> registry,
      Set<Holder<Biome>> includedBiomes,
      ResourceKey<Biome>... keys) {
    for (int i = 0; i < keys.length; i++)
      if (includedBiomes.contains(registry.getOrThrow(keys[i]))) return true;
    return false;
  }

  private static boolean sanityCheck(ChunkAccess chunk) {
    var sections = chunk.getSections();
    for (int i = 0; i < sections.length; i++) {
      var config = sections[i].states.data.configuration();
      var palette = sections[i].states.data.palette();
      if (palette instanceof SingleValuePalette) continue;
      if (palette instanceof LinearPalette && config.bitsInMemory() == 4) continue;
      return false;
    }
    return true;
  }

  public static void buildSurface(
      SurfaceBuilderAccessor builder,
      final RandomState noiseConfig,
      final BiomeManager biomeAccess,
      final boolean useLegacyRandom,
      final WorldGenerationContext heightContext,
      final ChunkAccess chunk,
      final NoiseChunk chunkNoiseSampler,
      final SurfaceRules.RuleSource materialRule,
      final Set<Holder<Biome>> includedBiomes,
      final ChunkAccess[] chunks,
      final Registry<Biome> registry) {

    final var defaultState = builder.zenxarch$defaultBlockState();

    if (canSkipSurfaceBuilder(materialRule, defaultState)) {
      return;
    }

    final ChunkPos chunkPos = chunk.getPos();
    int minBlockX = chunkPos.getMinBlockX();
    int minBlockZ = chunkPos.getMinBlockZ();
    var column = new FastBlockColumn(chunk);

    var context =
        new MaterialRuleContext(
            (SurfaceSystem) (Object) builder,
            noiseConfig,
            chunk,
            chunkNoiseSampler,
            biomeAccess::getBiome,
            registry,
            heightContext,
            includedBiomes,
            chunks);

    var rule = materialRule.apply(context);

    final int minY = chunk.getMinY();

    if (!sanityCheck(chunk)) {
      throw new IllegalStateException("Some mod has made unexpected changes to chunk gen");
    }

    var sections = chunk.getSections();
    final var indexCache = new FastPaletteIndexCache(sections);

    if (indexCache.size() == 0) return;

    final var iterator =
        FastColumnIterator.getIterator(sections, defaultState, minY, context, indexCache.size());
    final var isFluidCache = new FastIsFluidCache();

    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        int blockX = minBlockX + x;
        int blockZ = minBlockZ + z;

        context.updateXZ(blockX, blockZ);
        column.updateXZ(x, z);
        iterator.start(x, z);

        while (iterator.hasNext()) {
          final var y = iterator.nextY();
          final var state = rule.tryApply(blockX, y, blockZ);
          if (state == null || state == defaultState) continue;

          final var sy = (y - minY) >> 4;
          final var section = sections[sy];

          indexCache.updateBlock(sy, sections[sy].states, x, y & 0xF, z, state);

          updateExtras(section, x, y, z, state, column, chunk, sy, isFluidCache);
        }
      }
    }

    indexCache.finish(sections);
  }

  private static void updateExtras(
      LevelChunkSection section,
      int x,
      int iy,
      int z,
      BlockState state,
      FastBlockColumn column,
      ChunkAccess chunk,
      int sy,
      FastIsFluidCache isFluidCache) {
    column.fastUpdateHeightmap(x, z, iy, state);

    final var y = iy & 0xF;
    if (isFluidCache.isFluid(state))
      ChunkAccess.getOrCreateOffsetList(chunk.getPostProcessing(), sy).add((short) (x | y << 4 | z << 8));
  }

  private static boolean canSkipSurfaceBuilder(
      SurfaceRules.RuleSource rule, BlockState defaultState) {
    if (!FastNoiseConfig.SKIP_TRIVIAL_SURFACE_BUILDER) return false;
    if (rule instanceof SurfaceRules.BlockRuleSource block) {
      return block.resultState() == defaultState;
    }
    return false;
  }
}

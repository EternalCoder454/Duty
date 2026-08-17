package org.codeberg.zenxarch.fastnoise.noise;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.Climate.Sampler;
import net.minecraft.world.level.biome.Climate.RTree.Node;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import org.apache.commons.lang3.mutable.MutableObject;
import org.codeberg.zenxarch.fastnoise.config.FastNoiseConfig;
import org.codeberg.zenxarch.fastnoise.mixin.ChunkNoiseSamplerAccessor;
import org.codeberg.zenxarch.fastnoise.mixin.EntriesAccessor;
import org.codeberg.zenxarch.fastnoise.mixin.MultiNoiseBiomeSourceAccessor;

public final class FastBiomeGen {

  private static Sampler createSampler(
      NoiseChunk sampler,
      RandomState config,
      Holder<NoiseGeneratorSettings> settings) {
    return ((ChunkNoiseSamplerAccessor) sampler)
        .zenxarch$createMultiNoiseSampler(config.router(), settings.value().spawnTarget());
  }

  public static void populateBiomes(
      ChunkAccess chunk,
      BiomeResolver supplier,
      NoiseChunk sampler,
      RandomState config,
      Holder<NoiseGeneratorSettings> settings) {

    if (FastNoiseConfig.OPTIMIZE_FIXED_BIOMES && supplier instanceof FixedBiomeSource fixed) {
      packSingleBiome(chunk.getSections(), fixed.biomes);
      return;
    }

    if (FastNoiseConfig.OPTIMIZE_END_BIOMES && supplier instanceof TheEndBiomeSource theEnd) {
      final var chunkPos = chunk.asVec3();
      populateEndBiomes(
          theEnd,
          chunk,
          chunk.getSections(),
          chunkPos.x(),
          chunkPos.z(),
          createSampler(sampler, config, settings));
      return;
    }

    if (FastNoiseConfig.OPTIMIZE_BIOME_TREE
        && supplier instanceof MultiNoiseBiomeSource multiNoise) {
      populateMultiNoiseBiomes(multiNoise, chunk, createSampler(sampler, config, settings));
      return;
    }

    populateBiomes(chunk, supplier, createSampler(sampler, config, settings));
  }

  private static void populateBiomes(
      ChunkAccess chunk, BiomeResolver supplier, Sampler sampler) {

    var sections = chunk.getSections();

    final var chunkPos = chunk.asVec3();
    final int cx = chunkPos.x();
    final int cz = chunkPos.z();

    final int minY = chunk.getBottomY();
    final int x = cx << 2;
    int y = minY >> 2;
    final int z = cz << 2;

    @SuppressWarnings("unchecked")
    final Holder<Biome>[] biomes = new Holder[64];
    final var storage = new byte[64];

    for (int i = 0; i < sections.length; i++) {
      var section = sections[i];
      FastBiomeGen.fillBiomesFromNoise(section, supplier, sampler, x, y, z, biomes, storage);
      y += 4;
    }
  }

  private static void packSingleBiome(LevelChunkSection[] sections, Holder<Biome> biome) {
    for (int i = 0; i < sections.length; i++) {
      FastNoisePaletteHelper.packSingleElement(
          (PalettedContainer<Holder<Biome>>) sections[i].biomes, biome);
    }
  }

  private static void populateBiomes(
      LevelChunkSection section,
      BiomeResolver biomeSupplier,
      Climate.Sampler sampler,
      int x,
      int y,
      int z,
      Holder<Biome>[] biomes,
      byte[] storage) {

    int size = 0;

    for (int ix = 0; ix < 4; ix++) {
      for (int iy = 0; iy < 4; iy++) {
        for (int iz = 0; iz < 4; iz++) {

          var biome = biomeSupplier.getNoiseBiome(x + ix, y + iy, z + iz, sampler);

          int bidx = -1;
          for (int i = 0; i < size; i++)
            if (biomes[i] == biome) {
              bidx = i;
              break;
            }

          if (bidx == -1) biomes[(bidx = size++)] = biome;

          storage[(iy << 2 | iz) << 2 | ix] = (byte) bidx;
        }
      }
      var container = ((PalettedContainer<Holder<Biome>>) section.biomes);
      FastNoisePaletteHelper.pack(container, biomes, size, storage);
    }
  }

  private static class EndBiomeNoisePos implements DensityFunction.NoisePos {
    private final int x;
    private final int z;
    public int y;
    private final DensityFunction sampler;

    public EndBiomeNoisePos(int x, int y, int z, DensityFunction sampler) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.sampler = sampler;
    }

    @Override
    public int blockX() {
      return x;
    }

    @Override
    public int blockY() {
      return y;
    }

    @Override
    public int blockZ() {
      return z;
    }

    public double sampleAndStep() {
      var result = sampler.data(this);
      this.y += 4;
      return result;
    }
  }

  private static Holder<Biome> getEndBiomeFromHeight(
      TheEndBiomeSource source, double height) {
    if (height > 0.25) return source.highlands;
    if (height >= -0.0625) return source.midlands;
    if (height < -0.21875) return source.islands;
    return source.barrens;
  }

  private static void populateEndBiomes(
      TheEndBiomeSource source,
      ChunkAccess chunk,
      LevelChunkSection[] sections,
      int cx,
      int cz,
      Climate.Sampler sampler) {
    if ((Math.abs(cx) <= 64) && ((Math.abs(cz) <= 64)) && ((cx * cx + cz * cz) <= 4096)) {
      packSingleBiome(sections, source.end);
      return;
    }

    final int x = (cx << 4) + 8;
    final int z = (cz << 4) + 8;

    var noisePos = new EndBiomeNoisePos(x, chunk.getBottomY(), z, sampler.erosion());

    for (int i = 0; i < sections.length; i++) {
      var a = getEndBiomeFromHeight(source, noisePos.sampleAndStep());
      var b = getEndBiomeFromHeight(source, noisePos.sampleAndStep());
      var c = getEndBiomeFromHeight(source, noisePos.sampleAndStep());
      var d = getEndBiomeFromHeight(source, noisePos.sampleAndStep());

      FastNoisePaletteHelper.packFourEntries(
          (PalettedContainer<Holder<Biome>>) sections[i].biomes, a, b, c, d);
    }
  }

  private static void populateMultiNoiseBiomes(
      MultiNoiseBiomeSource source, ChunkAccess chunk, Sampler sampler) {
    @SuppressWarnings("unchecked")
    final var tree =
        ((EntriesAccessor<Holder<Biome>>)
                ((MultiNoiseBiomeSourceAccessor) source).zenxarch$getBiomeEntries())
            .zenxarch$tree();

    final var resultNode = new MutableObject<>(tree.lastResult.get());

    final long[] point = new long[] {0, 0, 0, 0, 0, 0, 0};

    BiomeResolver modifiedSupplier =
        (x, y, z, samplerx) -> {
          var sampled = sampler.data(x, y, z);

          point[0] = sampled.temperature();
          point[1] = sampled.humidity();
          point[2] = sampled.continentalness();
          point[3] = sampled.erosion();
          point[4] = sampled.depth();
          point[5] = sampled.weirdness();

          var leaf =
              tree.root.search(
                  point, resultNode.get(), TreeNode::getSquaredDistance);
          resultNode.setValue(leaf);
          return leaf.value;
        };

    populateBiomes(chunk, modifiedSupplier, sampler);

    tree.lastResult.set(resultNode.get());
  }
}

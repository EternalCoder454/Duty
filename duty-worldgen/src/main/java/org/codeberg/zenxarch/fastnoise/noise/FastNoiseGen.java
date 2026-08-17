package org.codeberg.zenxarch.fastnoise.noise;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.SingleValuePalette;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.codeberg.zenxarch.fastnoise.heightmap.HeightmapUtil;

public class FastNoiseGen {
  public static final BlockState AIR = Blocks.AIR.defaultBlockState();

  private static final Heightmap.Types[] heightmaps =
      HeightmapUtil.calculateHeightmaps(ChunkStatus.NOISE);

  public static boolean isEmpty(ChunkAccess chunk) {
    var sections = chunk.getSections();
    for (int i = 0; i < sections.length; i++) {
      if (sections[i].states.data.palette() instanceof SingleValuePalette) continue;
      return false;
    }
    return true;
  }

  public static void doFill(
      NoiseChunk chunkNoiseSampler,
      BlockState defaultBlockState,
      ChunkAccess chunk,
      int minimumCellY,
      int cellHeight) {

    var sections = chunk.getSections();

    var fastSections = new FastChunkSection[sections.length];

    ChunkPos chunkPos = chunk.getPos();
    int chunkStartX = chunkPos.getMinBlockX();
    int chunkStartZ = chunkPos.getMinBlockZ();
    Aquifer aquiferSampler = chunkNoiseSampler.aquifer();
    chunkNoiseSampler.initializeForFirstCellX();
    BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
    int horizontalCellBlockCount = chunkNoiseSampler.cellWidth();
    int verticalCellBlockCount = chunkNoiseSampler.cellHeight();
    int cellWidth = 16 / horizontalCellBlockCount;

    var minY = chunk.getMinY();

    for (int cellX = 0; cellX < cellWidth; cellX++) {
      chunkNoiseSampler.advanceCellX(cellX);

      for (int cellZ = 0; cellZ < cellWidth; cellZ++) {
        for (int cellY = cellHeight - 1; cellY >= 0; cellY--) {
          chunkNoiseSampler.selectCellYZ(cellY, cellZ);

          for (int verticalCellBlock = verticalCellBlockCount - 1;
              verticalCellBlock >= 0;
              verticalCellBlock--) {
            int blockY = (minimumCellY + cellY) * verticalCellBlockCount + verticalCellBlock;
            int blockYInSection = blockY & 15;

            double verticalCellProgress =
                (double) verticalCellBlock / (double) verticalCellBlockCount;
            chunkNoiseSampler.updateForY(blockY, verticalCellProgress);

            iterateCellXZ(
                chunk,
                chunkNoiseSampler,
                aquiferSampler,
                defaultBlockState,
                mutable,
                blockY,
                blockYInSection,
                minY,
                fastSections,
                sections,
                horizontalCellBlockCount,
                chunkStartX,
                chunkStartZ,
                cellX,
                cellZ);
          }
        }
      }

      chunkNoiseSampler.swapSlices();
    }

    chunkNoiseSampler.stopInterpolation();

    finalizeChunks(fastSections, chunk, defaultBlockState);
  }

  private static void finalizeChunks(
      FastChunkSection[] fastSections, ChunkAccess chunk, BlockState defaultBlockState) {
    for (int i = 0; i < fastSections.length; i++)
      if (fastSections[i] != null) fastSections[i].recalculateCounts();

    for (int i = 0; i < heightmaps.length; i++) {
      HeightmapUtil.populateHeightmapPostNoise(chunk, heightmaps[i], defaultBlockState, AIR);
    }
  }

  private static void iterateCellXZ(
      ChunkAccess chunk,
      NoiseChunk chunkNoiseSampler,
      Aquifer aquiferSampler,
      BlockState defaultBlockState,
      BlockPos.MutableBlockPos mutable,
      int blockY,
      int blockYInSection,
      int minY,
      FastChunkSection[] fastSections,
      LevelChunkSection[] sections,
      int horizontalCellBlockCount,
      int chunkStartX,
      int chunkStartZ,
      int cellX,
      int cellZ) {
    final var postProcessingLists = chunk.getPostProcessing();

    var cy = (blockY - minY) >> 4;
    var fastSection = fastSections[cy];

    for (int cellBlockX = 0; cellBlockX < horizontalCellBlockCount; cellBlockX++) {
      int blockX = chunkStartX + cellX * horizontalCellBlockCount + cellBlockX;
      int blockXInSection = blockX & 15;
      double cellXProgress = (double) cellBlockX / (double) horizontalCellBlockCount;
      chunkNoiseSampler.updateForX(blockX, cellXProgress);

      for (int cellBlockZ = 0; cellBlockZ < horizontalCellBlockCount; cellBlockZ++) {
        int blockZ = chunkStartZ + cellZ * horizontalCellBlockCount + cellBlockZ;
        int blockZInSection = blockZ & 15;
        double cellZProgress = (double) cellBlockZ / (double) horizontalCellBlockCount;

        chunkNoiseSampler.updateForZ(blockZ, cellZProgress);

        var state = chunkNoiseSampler.getInterpolatedState();

        if (state == AIR) continue;

        if (fastSection == null) {
          fastSection = (fastSections[cy] = new FastChunkSection(sections[cy]));
        }

        if (state == null) {
          fastSection.setDefaultBlockState(
              blockXInSection, blockYInSection, blockZInSection, defaultBlockState);
          continue;
        }

        fastSection.setBlockState(blockXInSection, blockYInSection, blockZInSection, state);

        if (aquiferSampler.shouldScheduleFluidUpdate() && !state.getFluidState().isEmpty()) {
          ChunkAccess.getOrCreateOffsetList(postProcessingLists, cy)
              .add((short) (blockXInSection | blockYInSection << 4 | blockZInSection << 8));
        }
      }
    }
  }
}

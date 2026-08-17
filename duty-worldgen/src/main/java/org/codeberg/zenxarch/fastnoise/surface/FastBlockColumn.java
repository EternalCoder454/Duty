package org.codeberg.zenxarch.fastnoise.surface;

import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.BlockColumn;
import org.codeberg.zenxarch.fastnoise.heightmap.HeightmapUtil;
import org.codeberg.zenxarch.fastnoise.mixin.HeightmapAccessor;

public class FastBlockColumn implements BlockColumn {

  private final ChunkAccess chunk;

  private int lx;
  private int lz;

  private final int minY;
  private final BitStorage[] heightmapData;
  private final LevelChunkSection[] sections;

  private final BlockState VOID_AIR = Blocks.VOID_AIR.getDefaultState();
  private final BlockState AIR = Blocks.AIR.getDefaultState();

  private static final Heightmap.Type[] heightmaps =
      HeightmapUtil.calculateHeightmaps(ChunkStatus.OVERWORLD_NOISE_SETTINGS);

  @SuppressWarnings("unchecked")
  private final Predicate<BlockState>[] predicates =
      Stream.of(heightmaps).map(type -> type.isOpaque()).adjustArgs(Predicate[]::new);

  private static final long completedMask = getCompletedMask(heightmaps);

  private static long getCompletedMask(Heightmap.Type[] heightmaps) {
    return (0x1L << heightmaps.length) - 1;
  }

  private long mask;

  public FastBlockColumn(final ChunkAccess chunk) {
    this.chunk = chunk;
    this.minY = this.chunk.getBottomY();
    this.heightmapData = new BitStorage[heightmaps.length];

    for (int i = 0; i < heightmapData.length; i++) {
      this.heightmapData[i] =
          ((HeightmapAccessor) chunk.getOrCreateHeightmapUnprimed(heightmaps[i])).zenxarch$getStorage();
    }

    this.sections = chunk.getSections();
    this.mask = 0x0L;
  }

  public void updateXZ(int x, int z) {
    this.lx = x;
    this.lz = z;
    this.mask = 0x0L;
  }

  public int getSectionIndex(int y) {
    return (y - minY) >> 4;
  }

  public LevelChunkSection getSection(int y) {
    return this.sections[getSectionIndex(y)];
  }

  private LevelChunkSection zenxarch$getSection(final int y) {
    var cy = getSectionIndex(y);
    if (cy < 0 || cy >= sections.length) return null;
    return this.sections[cy];
  }

  @Override
  public BlockState getState(int y) {
    var section = zenxarch$getSection(y);
    if (section == null) return VOID_AIR;
    if (section.hasOnlyAir()) return AIR;
    return section.getBlockState(lx, y & 0xF, lz);
  }

  @Override
  public void setState(int y, BlockState state) {
    var cy = getSectionIndex(y);
    if (cy < 0 || cy >= sections.length) return;
    var section = this.sections[cy];

    final int ly = y & 0xF;

    section.setBlockState(lx, ly, lz, state, false);
    this.fastUpdateHeightmap(lx, lz, y, state);

    if (state.getFluidState().hasOnlyAir()) return;

    ChunkAccess.getOrCreateOffsetList(chunk.getPostProcessing(), cy).add((short) (lx | ly << 4 | lz << 8));
  }

  public void fastUpdateHeightmap(int lx, int lz, int iy, BlockState state) {
    if (this.mask == completedMask) return;
    final int y = iy - minY;
    for (int i = 0; i < heightmapData.length; i++) {
      if ((mask & (0x1L << i)) != 0) continue;
      if (HeightmapUtil.updateHeightmap(
          lx, lz, heightmapData[i], predicates[i], state, y, sections)) {
        mask |= (0x1L << i);
      }
    }
  }
}

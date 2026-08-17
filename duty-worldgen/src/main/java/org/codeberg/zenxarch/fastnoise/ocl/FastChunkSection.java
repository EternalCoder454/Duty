package org.codeberg.zenxarch.fastnoise.ocl;

import java.util.Arrays;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.codeberg.zenxarch.fastnoise.noise.FastNoiseGen;
import org.codeberg.zenxarch.fastnoise.noise.container.BlockCountingPalettedContainer;

public final class FastChunkSection {

  private final int[] idToPaletteIdx;

  private final LevelChunkSection section;

  private final long[] storage;
  private final LinearPalette<BlockState> palette;
  private final BlockState[] states;
  private final BlockCountingPalettedContainer<BlockState> counter;

  private int defaultPacked = 0;

  public FastChunkSection(LevelChunkSection section, BlockState[] mappings) {
    this.idToPaletteIdx = new int[mappings.length];
    Arrays.fill(this.idToPaletteIdx, -1);

    this.section = section;

    this.states = new BlockState[16];
    this.storage = new long[256];
    this.palette = new LinearPalette<>(this.states, 4, 1);

    this.states[0] = FastNoiseGen.AIR;
    this.idToPaletteIdx[1] = 0;

    this.counter =
        new BlockCountingPalettedContainer<>(
            this.section.states.strategy,
            this.storage,
            this.states,
            this.palette);
    this.section.states = this.counter;
  }

  private int getIndex(BlockState[] mappings, int idx) {
    if (idToPaletteIdx[idx] == -1) {
      this.states[this.palette.size] = mappings[idx];
      this.idToPaletteIdx[idx] = this.palette.size;
      this.palette.size++;
    }
    return this.idToPaletteIdx[idx];
  }

  // x -> 0/1
  public void handleLong(int x, int y, int z, long value, BlockState[] mappings) {
    if (value == 0x0202020202020202L) {
      if (this.defaultPacked == 0) initDefaultPacked(mappings);

      this.storage[(y << 4) | z] |= ((long) defaultPacked) << (x << 2);
      this.counter.updateCount(getIndex(mappings, 2), 8);
      return;
    }

    var packed = 0;

    for (int i = 0; i < 32; i += 4) {
      int idx = getIndex(mappings, (int) (value & 0x7FL));

      counter.updateCount(idx);
      packed |= idx << i;

      value >>>= 8;
    }

    this.storage[(y << 4) | z] |= ((long) packed) << (x << 2);
  }

  private void initDefaultPacked(BlockState[] mappings) {
    var idx = getIndex(mappings, 2);
    idx = (idx << 4) | idx;
    idx = (idx << 8) | idx;
    this.defaultPacked = (idx << 16) | idx;
  }

  public void recalculateCounts() {
    this.section.recalcBlockCounts();
    this.section.states = this.counter.revert();
  }
}

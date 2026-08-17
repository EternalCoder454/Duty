package org.codeberg.zenxarch.fastnoise.noise.container;

import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.codeberg.zenxarch.fastnoise.noise.FastNoisePaletteHelper;

public class BlockCountingPalettedContainer<T> extends PalettedContainer<T> {
  private final short[] blockCounts = new short[16];
  private T[] states;
  private LinearPalette<T> palette;

  public BlockCountingPalettedContainer(
      Strategy<T> strategy, long[] storage, T[] states, LinearPalette<T> palette) {
    this.states = states;
    super(
        strategy,
        FastNoisePaletteHelper.ARRAY_4_TYPE,
        new SimpleBitStorage(4, 4096, storage),
        palette);
    this.palette = palette;
  }

  public void updateCount(int value) {
    this.blockCounts[value]++;
  }

  public void updateCount(int value, int amt) {
    this.blockCounts[value] += amt;
  }

  @Override
  public void count(CountConsumer<T> output) {
    short airCount = 4096;
    for (int i = 1; i < this.palette.size; i++) {
      airCount -= blockCounts[i];
    }

    output.accept(states[0], airCount);
    for (int i = 1; i < this.palette.size; i++) {
      output.accept(states[i], blockCounts[i]);
    }
  }

  public PalettedContainer<T> revert() {
    return new PalettedContainer<T>(
        this.strategy, this.data.configuration(), this.data.storage(), this.palette);
  }
}

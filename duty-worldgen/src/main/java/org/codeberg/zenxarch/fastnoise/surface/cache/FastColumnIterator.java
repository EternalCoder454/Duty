package org.codeberg.zenxarch.fastnoise.surface.cache;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.codeberg.zenxarch.fastnoise.surface.MaterialRuleContext;

public interface FastColumnIterator {

  public static final BlockState AIR = Blocks.AIR.getDefaultState();

  private static boolean isTrivial(
      LevelChunkSection[] sections, int numSections, BlockState defaultState) {
    for (int i = 0; i < numSections; i++) {
      var section = sections[i];
      if (section.hasFluid()) return false;
      if (section.hasOnlyAir()) return false;

      var palette = section.states.data.palette();
      if (palette.getSize() != 2) return false;

      if (palette.get(0) != AIR) return false;
      if (palette.get(1) != defaultState) return false;
    }
    return true;
  }

  public static FastColumnIterator getIterator(
      final LevelChunkSection[] sections,
      final BlockState defaultState,
      final int minY,
      final MaterialRuleContext context,
      final int numSections) {
    if (!isTrivial(sections, numSections, defaultState)) {
      return new GenericFastColumnIterator(sections, defaultState, minY, context, numSections);
    }

    return new TrivialFastColumnIterator(sections, defaultState, minY, context, numSections);
  }

  public void start(int x, int z);

  public boolean hasNext();

  public int nextY();
}

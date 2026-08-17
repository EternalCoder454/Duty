package org.codeberg.zenxarch.fastnoise.heightmap;

import java.util.EnumSet;
import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.SingleValuePalette;
import org.codeberg.zenxarch.fastnoise.mixin.HeightmapAccessor;

public final class HeightmapUtil {
  private HeightmapUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static Heightmap.Types[] calculateHeightmaps(ChunkStatus status) {
    var result = new Heightmap.Types[status.heightmapsAfter().size()];
    return status.heightmapsAfter().toArray(result);
  }

  public static boolean updateHeightmap(
      int lx,
      int lz,
      BitStorage storage,
      Predicate<BlockState> predicate,
      BlockState state,
      int y,
      LevelChunkSection[] sections) {
    int idx = lx + (lz << 4);
    int current = storage.get(idx);
    if ((y + 2) > current) {
      if (predicate.test(state)) {
        if (y >= current) storage.set(idx, y + 1);
      } else { // go down the whole chunk till we hit something
        storage.set(idx, getHeightmapY(idx, sections, y - 1, predicate));
        return false;
      }
    }
    return true;
  }

  private static int getHeightmapY(
      int hidx, LevelChunkSection[] sections, int startY, Predicate<BlockState> predicate) {
    int ly = startY & 15;
    int sy = startY >> 4;
    while (sy >= 0) {
      var section = sections[sy];
      var palette = section.states.data.palette();
      var storage = section.states.data.storage();
      while (ly >= 0) {
        if (predicate.test(palette.valueFor(storage.get((ly << 8) + hidx)))) {
          return (sy << 4) + ly + 1;
        }
        ly--;
      }
      ly = 15;
      sy--;
    }

    return 0;
  }

  /**
   * Use ArrayMap to cache predicate Cache defaultBlock and skip air
   *
   * @param chunk
   * @param heightmap
   */
  private static class StatePredicateCache {
    public BlockState[] states = new BlockState[16];
    public boolean[] values = new boolean[16];
    private int size = 0;

    public boolean get(BlockState state, Predicate<BlockState> predicate) {
      for (int i = 0; i < size; i++) if (states[i] == state) return values[i];
      states[size] = state;
      values[size] = predicate.test(state);
      return values[size++];
    }
  }

  private static int getLocalY(
      LevelChunkSection section, int hidx, StatePredicateCache cache, Predicate<BlockState> predicate) {
    var palette = section.states.data.palette();
    var storage = section.states.data.storage();
    if (palette instanceof SingleValuePalette) { // just assume air
      return -1;
    }
    var array = (LinearPalette<BlockState>) palette;

    for (int ly = 15; ly >= 0; ly--) {
      var val = storage.get((ly << 8) + hidx);
      if (val == 0) continue;
      if (cache.get(array.valueFor(val), predicate)) return ly;
    }

    return -1;
  }

  private static int findFirstNonEmptySection(
      LevelChunkSection[] sections, StatePredicateCache cache, Predicate<BlockState> predicate) {
    for (int sy = (sections.length - 1); sy >= 0; sy--) {
      var palette = sections[sy].states.data.palette();
      int psize = palette.getSize();
      if (psize == 1) continue;
      var array = ((LinearPalette<BlockState>) palette);
      for (int i = 1; i < array.size; i++) {
        if (cache.get(array.valueFor(i), predicate)) return sy;
      }
    }

    return -1; // Empty chunk
  }

  public static void populateHeightmapPostNoise(
      ChunkAccess chunk, Heightmap.Types typex, BlockState defaultBlockState, BlockState AIR) {
    var heightmap = chunk.getOrCreateHeightmapUnprimed(typex);
    var predicate = ((HeightmapAccessor) heightmap).zenxarch$getBlockPredicate();
    var storage = ((HeightmapAccessor) heightmap).zenxarch$getStorage();

    if (predicate.test(AIR)) { // We don't like air being counted in a heightmap
      Heightmap.primeHeightmaps(chunk, EnumSet.of(typex));
      return;
    }

    var sections = chunk.getSections();

    var cache = new StatePredicateCache();
    cache.get(defaultBlockState, predicate); // Cache default block first

    var nonEmptySection = findFirstNonEmptySection(sections, cache, predicate);
    if (nonEmptySection == -1) return; // Chunk is empty no-op

    for (int hidx = 0; hidx < 256; hidx++) {
      for (int sy = nonEmptySection; sy >= 0; sy--) {
        var ly = getLocalY(sections[sy], hidx, cache, predicate);
        if (ly >= 0) {
          storage.set(hidx, (sy << 4) + ly + 1);
          break;
        }
      }
    }
  }
}

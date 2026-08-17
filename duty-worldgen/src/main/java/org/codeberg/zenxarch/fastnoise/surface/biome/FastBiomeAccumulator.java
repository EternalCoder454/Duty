package org.codeberg.zenxarch.fastnoise.surface.biome;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.fastutil.objects.ReferenceSets;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class FastBiomeAccumulator {
  public static ReferenceSet<Holder<Biome>> accumulate(WorldGenRegion region, ChunkPos cpos) {
    var items = new ReferenceArraySet<Holder<Biome>>(8);

    var sx = cpos.x();
    var sz = cpos.z();

    accumulate(region.getChunk(sx - 1, sz - 1), items);
    accumulate(region.getChunk(sx, sz - 1), items);
    accumulate(region.getChunk(sx + 1, sz - 1), items);

    accumulate(region.getChunk(sx - 1, sz), items);
    accumulate(region.getChunk(sx, sz), items);
    accumulate(region.getChunk(sx + 1, sz), items);

    accumulate(region.getChunk(sx - 1, sz + 1), items);
    accumulate(region.getChunk(sx, sz + 1), items);
    accumulate(region.getChunk(sx + 1, sz + 1), items);

    if (items.size() == 1) {
      for (var item : items) return ReferenceSets.singleton(item);
    }

    if (items.size() > 4) {
      return new ReferenceOpenHashSet<>(items, Hash.VERY_FAST_LOAD_FACTOR);
    }

    return items;
  }

  public static void accumulate(ChunkAccess chunk, ReferenceSet<Holder<Biome>> biomeOut) {
    var sections = chunk.getSections();
    for (int i = 0; i < sections.length; i++) {
      var palette =
          ((PalettedContainer<Holder<Biome>>) sections[i].biomes).data.palette();
      for (int j = 0; j < palette.getSize(); j++) biomeOut.add(palette.valueFor(j));
    }
  }
}

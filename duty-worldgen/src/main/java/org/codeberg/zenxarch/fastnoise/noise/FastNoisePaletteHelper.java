package org.codeberg.zenxarch.fastnoise.noise;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.Configuration;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainer.Data;
import net.minecraft.world.level.chunk.SingleValuePalette;

public final class FastNoisePaletteHelper {
  private FastNoisePaletteHelper() {
    throw new IllegalStateException("Utility class");
  }

  private static final Palette.Factory ARRAY = LinearPalette::create;
  private static final Palette.Factory SINGULAR = SingleValuePalette::create;

  private static final Configuration SINGULAR_TYPE = new Configuration.Simple(SINGULAR, 0);
  private static final Configuration ARRAY_1_TYPE = new Configuration.Simple(ARRAY, 1);
  private static final Configuration ARRAY_2_TYPE = new Configuration.Simple(ARRAY, 2);
  private static final Configuration ARRAY_3_TYPE = new Configuration.Simple(ARRAY, 3);
  public static final Configuration ARRAY_4_TYPE = new Configuration.Simple(ARRAY, 4);
  private static final Configuration ARRAY_5_TYPE = new Configuration.Simple(ARRAY, 5);
  private static final Configuration ARRAY_6_TYPE = new Configuration.Simple(ARRAY, 6);

  private static final Configuration[] biomePaletteTypes =
      new Configuration[] {
        ARRAY_1_TYPE, ARRAY_2_TYPE, ARRAY_3_TYPE, ARRAY_4_TYPE, ARRAY_5_TYPE, ARRAY_6_TYPE
      };

  public static void pack(
      PalettedContainer<Holder<Biome>> container,
      Holder<Biome>[] biomes,
      int size,
      byte[] storage) {
    if (size == 1) {
      packSingleElement(container, biomes[0]);
      return;
    }
    int bits = Mth.ceillog2(size);
    @SuppressWarnings("unchecked")
    Holder<Biome>[] downSizedBiomes = new Holder[1 << bits];
    System.arraycopy(biomes, 0, downSizedBiomes, 0, size);
    container.data =
        new Data<Holder<Biome>>(
            biomePaletteTypes[bits - 1],
            repackBiomeStorage(biomes, bits, storage),
            new LinearPalette<>(downSizedBiomes, bits, size));
  }

  private static final int[] biomeStorageSizes = new int[] {-1, 1, 2, 4, 4, 6, 7};

  private static BitStorage repackBiomeStorage(
      Holder<Biome>[] palette, int bits, byte[] data) {
    var storage = new long[biomeStorageSizes[bits]];

    int idx = 0;
    for (int i = 0; i < bits; i++) {
      for (int j = 0; (j + bits) < 65; j += bits) {
        storage[i] |= ((long) data[idx++]) << j;
      }
    }
    int j = 0;
    while (idx < 64) {
      storage[bits] |= ((long) data[idx++]) << j;
      j += bits;
    }

    return new SimpleBitStorage(bits, 64, storage);
  }

  public static <T> void packSingleElement(PalettedContainer<T> container, T element) {
    if (container.data.palette() instanceof SingleValuePalette<T> palette) {
      palette.value = element;
    } else {
      container.data =
          new Data<>(
              SINGULAR_TYPE, new ZeroBitStorage(64), new SingleValuePalette<>(List.of(element)));
    }
  }

  private static int idFor(Holder<Biome>[] palette, int size, Holder<Biome> value) {
    for (int i = 0; i < size; i++) {
      if (palette[i] == value) return i;
    }
    return size;
  }

  @SuppressWarnings("unchecked")
  public static void packFourEntries(
      PalettedContainer<Holder<Biome>> container,
      Holder<Biome> a,
      Holder<Biome> b,
      Holder<Biome> c,
      Holder<Biome> d) {
    if (a == b && b == c && c == d) {
      packSingleElement(container, a);
      return;
    }
    var palette = (Holder<Biome>[]) new Holder[4];
    var idFor = new int[4];
    int size = 1;
    {
      palette[0] = a;
      idFor[0] = 0;
    }
    {
      var next = idFor(palette, size, b);
      idFor[1] = next;
      palette[next] = b;
      if (next == size) size++;
    }
    {
      var next = idFor(palette, size, c);
      idFor[2] = next;
      palette[next] = c;
      if (next == size) size++;
    }
    {
      var next = idFor(palette, size, d);
      idFor[3] = next;
      palette[next] = d;
      if (next == size) size++;
    }

    var storage = EndBiomeStorageCache.get(idFor);
    if (size == 2) {
      container.data =
          new Data<Holder<Biome>>(
              ARRAY_1_TYPE,
              storage.copy(),
              new LinearPalette<Holder<Biome>>(
                  (Holder<Biome>[]) new Holder[] {palette[0], palette[1]}, 1, size));
    } else {
      container.data =
          new Data<Holder<Biome>>(
              ARRAY_2_TYPE,
              storage.copy(),
              new LinearPalette<Holder<Biome>>(palette.clone(), 2, size));
    }
  }
}

package org.codeberg.zenxarch.fastnoise.surface;

import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.SurfaceRules.Condition;

public final class BiomeConditionModifier {
  private static final Condition ALWAYS_TRUE = () -> true;
  private static final Condition ALWAYS_FALSE = () -> false;

  public static Condition getSupplier(
      MaterialRuleContext context, Set<ResourceKey<Biome>> predicate) {
    if (containsAll(context.includedBiomeKeys, predicate)) return ALWAYS_TRUE;
    if (containsNone(predicate, context.includedBiomeKeys)) return ALWAYS_FALSE;
    return null;
  }

  private static boolean containsAll(
      Set<ResourceKey<Biome>> biomes, Set<ResourceKey<Biome>> predicate) {
    for (var biome : biomes) {
      if (!predicate.contains(biome)) return false;
    }
    return true;
  }

  private static boolean containsNone(
      Set<ResourceKey<Biome>> predicates, Set<ResourceKey<Biome>> biomes) {
    for (var predicate : predicates) {
      if (biomes.contains(predicate)) return false;
    }
    return true;
  }
}

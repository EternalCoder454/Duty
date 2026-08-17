package org.codeberg.zenxarch.fastnoise.mixin;

import net.minecraft.world.level.biome.Climate.ParameterList;
import net.minecraft.world.level.biome.Climate.RTree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ParameterList.class)
public interface EntriesAccessor<T> {
  @Accessor("tree")
  public RTree<T> zenxarch$tree();
}

package org.codeberg.zenxarch.fastnoise.mixin;

import java.util.function.Predicate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Heightmap.class)
public interface HeightmapAccessor {
  @Accessor("storage")
  public BitStorage zenxarch$getStorage();

  @Accessor("blockPredicate")
  public Predicate<BlockState> zenxarch$getBlockPredicate();
}

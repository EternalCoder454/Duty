package org.codeberg.zenxarch.fastnoise.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SurfaceSystem.class)
public interface SurfaceBuilderAccessor {

  @Accessor("defaultState")
  public BlockState zenxarch$getDefaultState();

  @Invoker("isDefaultBlock")
  public boolean zenxarch$isDefaultBlock(BlockState state);

  @Invoker("placeBadlandsPillar")
  public void zenxarch$placeBadlandsPillar(
      final BlockColumn column,
      final int x,
      final int z,
      final int surfaceY,
      final LevelHeightAccessor chunk);

  @Invoker("placeIceberg")
  public void zenxarch$placeIceberg(
      final int minY,
      final Biome biome,
      final BlockColumn column,
      final BlockPos.Mutable mutablePos,
      final int x,
      final int z,
      final int surfaceY);

  @Accessor("badlandsPillarNoise")
  public NormalNoise zenxarch$getBadlandsPillarNoise();

  @Accessor("badlandsPillarRoofNoise")
  public NormalNoise zenxarch$getBadlandsPillarRoofNoise();

  @Accessor("badlandsSurfaceNoise")
  public NormalNoise zenxarch$getBadlandsSurfaceNoise();
}

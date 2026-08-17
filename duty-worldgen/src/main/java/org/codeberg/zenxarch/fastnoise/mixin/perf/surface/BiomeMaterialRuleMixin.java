package org.codeberg.zenxarch.fastnoise.mixin.perf.surface;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceRules.BiomeConditionSource;
import net.minecraft.world.level.levelgen.SurfaceRules.Condition;
import org.codeberg.zenxarch.fastnoise.surface.BiomePredicate;
import org.codeberg.zenxarch.fastnoise.surface.MaterialRuleContext;
import org.spongepowered.asm.mixin.Debug;
import org.spongepowered.asm.mixin.Mixin;

@Debug(export = true)
@Mixin(BiomeConditionSource.class)
public abstract class BiomeMaterialRuleMixin {
  @WrapMethod(
      method =
          "apply(Lnet/minecraft/world/level/levelgen/SurfaceRules$Context;)Lnet/minecraft/world/level/levelgen/SurfaceRules$Condition;")
  public Condition zenxarch$apply(
      final SurfaceRules.Context ruleContext, Operation<Condition> op) {
    if (ruleContext instanceof MaterialRuleContext ctx) {
      return BiomePredicate.getSupplier(((BiomeConditionSource) (Object) this).biomes, ctx);
    }
    return op.call(ruleContext);
  }
}

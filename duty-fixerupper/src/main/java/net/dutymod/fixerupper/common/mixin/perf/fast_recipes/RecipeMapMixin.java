package net.dutymod.fixerupper.common.mixin.perf.fast_recipes;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.dutymod.fixerupper.fastrecipes.CachedRecipeList;
import net.dutymod.fixerupper.fastrecipes.FastRecipes;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Replaces {@link RecipeMap#getRecipesFor(RecipeType, RecipeInput, Level)} with an indexed variant (see {@link CachedRecipeList}) for the recipe types
 * whitelisted in {@link FastRecipes#indexedTypes} (crafting by default). All other recipe types fall through to vanilla.
 */
@Mixin(value = RecipeMap.class, remap = false)
public abstract class RecipeMapMixin {

    @Unique
    private Map<RecipeType<?>, CachedRecipeList<?, ?>> fastrecipes$cache = Collections.synchronizedMap(new HashMap<>());

    @Inject(method = "getRecipesFor", at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void fastrecipes$indexedGetRecipesFor(RecipeType<T> type, I container, Level level, CallbackInfoReturnable<Stream<RecipeHolder<T>>> cir) {
        if (!FastRecipes.indexedTypes.contains(type) || container.isEmpty()) {
            return; // only whitelisted recipe types are indexed; everything else (and empty inputs) falls through to vanilla
        }

        CachedRecipeList<I, T> cached = this.getCachedList(type);
        cir.setReturnValue(cached.getRecipesFor(container, level));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private <I extends RecipeInput, T extends Recipe<I>> CachedRecipeList<I, T> getCachedList(RecipeType<T> type) {
        synchronized (this.fastrecipes$cache) {
            CachedRecipeList<I, T> list = (CachedRecipeList<I, T>) this.fastrecipes$cache.get(type);
            if (list == null) {
                list = new CachedRecipeList<>(type, this.byType(type));
                this.fastrecipes$cache.put(type, list);
            }
            return list;
        }
    }

    @Shadow
    public abstract <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type);

}

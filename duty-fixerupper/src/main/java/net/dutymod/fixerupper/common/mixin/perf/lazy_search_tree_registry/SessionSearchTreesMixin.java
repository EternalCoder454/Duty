package net.dutymod.fixerupper.common.mixin.perf.lazy_search_tree_registry;

import com.google.common.base.Stopwatch;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.client.searchtree.SearchTree;
import net.dutymod.fixerupper.FixerUpper;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Mixin(SessionSearchTrees.class)
@ClientOnlyMixin
public class SessionSearchTreesMixin {
    @Shadow private CompletableFuture<SearchTree<RecipeCollection>> recipeSearch;
    private Supplier<SearchTree<RecipeCollection>> duty$deferredSearchTreeSupplier;

    @ModifyArg(method = { "lambda$updateRecipes$0" }, at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private Supplier<SearchTree<RecipeCollection>> duty$deferProcessing(Supplier<SearchTree<RecipeCollection>> supplier) {
        this.duty$deferredSearchTreeSupplier = supplier;
        return SearchTree::empty;
    }

    @WrapMethod(method = "recipes")
    private SearchTree<RecipeCollection> duty$processDeferredBuild(Operation<SearchTree<RecipeCollection>> original) {
        synchronized (this) {
            if (duty$deferredSearchTreeSupplier != null) {
                Stopwatch watch = Stopwatch.createStarted();
                this.recipeSearch = CompletableFuture.completedFuture(duty$deferredSearchTreeSupplier.get());
                watch.stop();
                FixerUpper.LOGGER.info("Building recipe book search tree took {}", watch);
                duty$deferredSearchTreeSupplier = null;
            }
            return original.call();
        }
    }
}

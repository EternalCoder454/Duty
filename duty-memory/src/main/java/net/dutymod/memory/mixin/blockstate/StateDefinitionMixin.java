package net.dutymod.memory.mixin.blockstate;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dutymod.memory.blockstate.BlockStateTables;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Builds the shared table instead of vanilla's per-state neighbour arrays.
 *
 * <p>Vanilla has two construction paths -- one for blocks with a single property, one for blocks
 * with several -- and both are hooked, because a block that misses out would keep null neighbour
 * arrays and fail on the first {@code setValue} call.
 *
 * <p>Both {@code createSinglePropertyStates} overloads exist in 26.1, so the descriptor is spelled
 * out rather than matching on the name.
 */
@Mixin(StateDefinition.class)
public class StateDefinitionMixin {

    /**
     * Replaces the loop that fills in neighbours for multi-property blocks.
     *
     * <p>The redirect swallows the {@code forEach} that would have populated the arrays and builds
     * the shared table from the same collection instead.
     */
    @Redirect(
            method = "createMultiPropertyStates",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"))
    private static <O, S extends StateHolder<O, S>> void duty$buildMultiPropertyTable(
            Map<List<Comparable<?>>, S> states, BiConsumer<?, ?> unused) {
        BlockStateTables.initialize(states.values());
    }

    /** Drops the per-state neighbour initialization for single-property blocks. */
    @Redirect(
            method = "createSinglePropertyStates(Ljava/lang/Object;"
                    + "Lnet/minecraft/world/level/block/state/StateDefinition$Factory;"
                    + "Lnet/minecraft/world/level/block/state/properties/Property;)"
                    + "Lcom/google/common/collect/ImmutableList;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/StateHolder;"
                            + "initializeNeighbors([[Ljava/lang/Object;)V"))
    private static <S> void duty$skipNeighborInit(StateHolder<?, ?> instance, S[][] neighbors) {
        // Deliberately empty: the shared table replaces these arrays.
    }

    /**
     * Builds the table for single-property blocks once their state list is complete.
     *
     * <p>Wrapping the {@code build()} call is what gives access to the finished list; the states
     * do not all exist before it returns.
     */
    @WrapOperation(
            method = "createSinglePropertyStates(Ljava/lang/Object;"
                    + "Lnet/minecraft/world/level/block/state/StateDefinition$Factory;"
                    + "Lnet/minecraft/world/level/block/state/properties/Property;)"
                    + "Lcom/google/common/collect/ImmutableList;",
            at = @At(value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableList$Builder;"
                            + "build()Lcom/google/common/collect/ImmutableList;"))
    private static <O, S extends StateHolder<O, S>, T extends Comparable<T>>
    ImmutableList<S> duty$buildSinglePropertyTable(
            ImmutableList.Builder<S> builder, Operation<ImmutableList<S>> original,
            O owner, StateDefinition.Factory<O, S> factory, Property<T> property) {
        ImmutableList<S> states = original.call(builder);
        BlockStateTables.initialize(states);
        return states;
    }
}

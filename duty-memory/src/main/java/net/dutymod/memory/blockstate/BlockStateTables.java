package net.dutymod.memory.blockstate;

import net.dutymod.framework.DutyConfig;
import net.dutymod.memory.MemoryOptions;
import net.dutymod.memory.mixin.blockstate.StateHolderAccessor;
import net.minecraft.world.level.block.state.StateHolder;

import java.util.Collection;

/** Builds the one shared neighbour table a block's states will share. */
public final class BlockStateTables {
    private BlockStateTables() {}

    /**
     * Creates the table for one block and hands every state its index into it.
     *
     * <p>Called once per block during registry construction, from the two places vanilla would
     * otherwise have filled in per-state neighbour arrays.
     */
    public static <S extends StateHolder<?, S>> void initialize(Collection<S> states) {
        if (states.isEmpty()) {
            return;
        }
        S first = states.iterator().next();
        FastMap<S> table = new FastMap<>(
                ((StateHolderAccessor) first).duty$getPropertyKeys(),
                DutyConfig.get(MemoryOptions.COMPACT_STATE_ENCODING));
        for (S state : states) {
            int index = table.insertAtIndex(state, S::getValue);
            @SuppressWarnings("unchecked")
            FastMapStateHolder<S> holder = (FastMapStateHolder<S>) state;
            holder.duty$setStateMap(table, index);
        }
    }
}

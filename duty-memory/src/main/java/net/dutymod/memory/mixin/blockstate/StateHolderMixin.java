package net.dutymod.memory.mixin.blockstate;

import net.dutymod.memory.blockstate.FastMap;
import net.dutymod.memory.blockstate.FastMapStateHolder;
import net.dutymod.memory.MemoryOptions;
import net.dutymod.framework.DutyConfig;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replaces vanilla's per-state neighbour arrays with an index into a shared table.
 *
 * <p>Applied at priority 900 -- earlier than the default 1000 -- so that any mod injecting into
 * these methods sees the replaced versions rather than having its injections silently discarded by
 * the overwrites below.
 *
 * <p>The two {@link Overwrite}s are unavoidable rather than lazy. {@code setValueInternal} needs
 * its final branch swapped for a table lookup, and mixin has no way to replace a multidimensional
 * array access in place. {@code initializeNeighbors} has to become a no-op because the arrays it
 * would fill no longer exist.
 */
@Mixin(value = StateHolder.class, priority = 900)
public abstract class StateHolderMixin<O, S> implements FastMapStateHolder<S> {

    @Shadow
    @Final
    @Mutable
    private Comparable<?>[] propertyValues;

    @Shadow
    @Final
    protected O owner;

    @Shadow
    public abstract boolean isSingletonState();

    @Unique
    private FastMap<S> duty$table;

    @Unique
    private int duty$tableIndex;

    /**
     * @author Duty (from FerriteCore by malte0811)
     * @reason The neighbour table is replaced wholesale; only the final lookup differs, but mixin
     *         cannot redirect a two-dimensional array access without producing unverifiable
     *         bytecode.
     */
    @Overwrite
    private <T extends Comparable<T>, V extends T> S setValueInternal(
            Property<T> property, int propertyIndex, V value) {
        int valueIndex = property.getInternalIndex(value);
        if (valueIndex < 0) {
            throw new IllegalArgumentException(
                    "Cannot set property " + property + " to " + value + " on " + this.owner
                            + ", it is not an allowed value");
        }
        return duty$table.with(this.duty$tableIndex, propertyIndex, valueIndex);
    }

    /**
     * @author Duty (from FerriteCore by malte0811)
     * @reason The arrays this would populate no longer exist. Singleton states never had them in
     *         the first place, so reaching here with one is a genuine error worth reporting.
     */
    @Overwrite
    void initializeNeighbors(S[][] neighbors) {
        if (!this.isSingletonState()) {
            throw new UnsupportedOperationException(
                    "Neighbour arrays are replaced by Duty; this should only run for singleton states.");
        }
    }

    /**
     * Serves property reads from the shared table once the per-state array has been released.
     *
     * <p>Covers both the direct getter and the lambda behind {@code getValues()}, which are the
     * only two places vanilla touches the array.
     */
    @Redirect(
            method = { "getNullableValue", "lambda$getValues$0" },
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/block/state/StateHolder;"
                            + "propertyValues:[Ljava/lang/Comparable;",
                    opcode = Opcodes.GETFIELD,
                    args = "array=get"))
    private Comparable<?> duty$readProperty(Comparable<?>[] values, int index) {
        // Non-null means either the table is not built yet, or the user kept the vanilla array.
        if (values != null) {
            return values[index];
        }
        return duty$table.getValue(duty$tableIndex, index);
    }

    @Override
    public void duty$setStateMap(FastMap<S> stateMap, int tableIndex) {
        this.duty$table = stateMap;
        this.duty$tableIndex = tableIndex;
        if (DutyConfig.get(MemoryOptions.PROPERTY_MAP_COMPACTION)) {
            // Release the per-state array; from here on the values come from the index.
            this.propertyValues = null;
        }
    }
}

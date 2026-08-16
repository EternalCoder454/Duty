package net.dutymod.memory.blockstate;

import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One shared neighbour table for an entire block, replacing vanilla's per-state arrays.
 *
 * <p>This is where most of the block state memory goes, and why. Vanilla gives every block state
 * its own two-dimensional array of neighbours -- for each property, every value that property could
 * take. Those arrays are almost entirely duplicates of each other: all the states of one block
 * describe the same grid of possibilities, just entered at different points. On a large modpack
 * with hundreds of thousands of states, that redundancy runs to hundreds of megabytes.
 *
 * <p>The replacement holds a single flat array per block, plus one integer per state saying where
 * in it that state sits. Asking for a neighbour becomes arithmetic on that integer rather than an
 * array dereference, which is what {@link FastMapKey} implements.
 *
 * <p>A second saving follows for free: once a state knows its index, its property values can be
 * derived from that index, so vanilla's per-state {@code propertyValues} array can be dropped
 * entirely. That is what {@link #getValue} is for.
 */
public class FastMap<Value> {
    private final Property<?>[] properties;
    private final List<FastMapKey> keys;
    private final List<Value> valueMatrix;

    public FastMap(Property<?>[] properties, boolean compact) {
        this.properties = properties;

        List<FastMapKey> keys = new ArrayList<>(properties.length);
        int factorUpTo = 1;
        for (Property<?> property : properties) {
            int numValues = property.getPossibleValues().size();
            FastMapKey key;
            if (compact) {
                key = new CompactFastMapKey(factorUpTo, numValues);
            } else {
                try {
                    key = BinaryFastMapKey.create(factorUpTo, numValues);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    // Binary encoding cannot represent this block; fall back rather than fail.
                    // Mixing encodings within one map is fine: each key only describes its own slice.
                    key = new CompactFastMapKey(factorUpTo, numValues);
                }
            }
            keys.add(key);
            factorUpTo *= key.getFactorToNext();
        }
        this.keys = List.copyOf(keys);

        List<Value> values = new ArrayList<>(factorUpTo);
        for (int i = 0; i < factorUpTo; i++) {
            values.add(null);
        }
        this.valueMatrix = values;
    }

    /**
     * {@return the state reached from {@code oldIndex} by setting one property to a new value}
     *
     * @throws IllegalStateException if the combination does not exist, which would mean the index
     *         arithmetic and the property list have gone out of step
     */
    public Value with(int oldIndex, int propertyIndex, int valueIndex) {
        int newIndex = keys.get(propertyIndex).replaceIn(oldIndex, valueIndex);
        if (newIndex < 0 || valueMatrix.get(newIndex) == null) {
            throw new IllegalStateException("Invalid block state lookup: replacing property "
                    + propertyIndex + " with value " + valueIndex + " in state " + oldIndex
                    + ", properties " + Arrays.toString(properties));
        }
        return valueMatrix.get(newIndex);
    }

    /** {@return the index {@code state} occupies, computed from its property values} */
    public int getIndexOf(Value state, PropertyValueGetter<Value> getValue) {
        int index = 0;
        for (int i = 0; i < properties.length; i++) {
            int valueIndex = internalIndex(properties[i], getValue.getValue(state, properties[i]));
            index += keys.get(i).toPartialMapIndex(valueIndex);
        }
        return index;
    }

    /** Places {@code state} into the matrix and returns where it went. */
    public int insertAtIndex(Value state, PropertyValueGetter<Value> getValue) {
        int index = getIndexOf(state, getValue);
        if (valueMatrix.get(index) != null) {
            throw new IllegalStateException("Two block states resolved to index " + index);
        }
        valueMatrix.set(index, state);
        return index;
    }

    /** {@return the value {@code propertyIndex} takes in the state at {@code stateIndex}} */
    public int getValueIndex(int stateIndex, int propertyIndex) {
        return keys.get(propertyIndex).getIndexIn(stateIndex);
    }

    public Property<?>[] getProperties() {
        return properties;
    }

    /**
     * {@return the value of one property, recovered from a state's index}
     *
     * <p>This is what makes dropping the per-state {@code propertyValues} array possible.
     */
    public Comparable<?> getValue(int stateIndex, int propertyIndex) {
        int valueIndex = getValueIndex(stateIndex, propertyIndex);
        return properties[propertyIndex].getPossibleValues().get(valueIndex);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> int internalIndex(Property<T> property, Comparable<?> value) {
        return property.getInternalIndex((T) value);
    }

    /** Reads a property off a state. Vanilla's own getter, passed in as a method reference. */
    public interface PropertyValueGetter<Owner> {
        Comparable<?> getValue(Owner state, Property<?> property);
    }
}

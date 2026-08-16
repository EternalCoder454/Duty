package net.dutymod.memory.blockstate;

/**
 * How a single property is encoded into a state's index in the shared value matrix.
 *
 * <p>Every block state is identified by one integer. Each property owns a slice of that integer,
 * and this interface is the arithmetic for reading and replacing its slice. Two implementations
 * exist because the trade-off is real: {@link BinaryFastMapKey} uses bit ranges, which makes
 * lookups shifts and masks but leaves gaps when a property's value count is not a power of two;
 * {@link CompactFastMapKey} uses multiplication and leaves no gaps but needs integer division.
 */
public interface FastMapKey {
    /**
     * {@return the index of the state identical to {@code mapIndex} except this property is set to
     * {@code valueIndex}, or -1 if that value is out of range}
     */
    int replaceIn(int mapIndex, int valueIndex);

    /** {@return this property's contribution to a state index; the contributions sum to the index} */
    int toPartialMapIndex(int valueIndex);

    /** {@return the stride of the next property along, used when building the map} */
    int getFactorToNext();

    /** {@return the value index this property has in {@code mapIndex}} */
    int getIndexIn(int mapIndex);
}

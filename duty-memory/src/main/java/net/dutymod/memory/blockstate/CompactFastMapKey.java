package net.dutymod.memory.blockstate;

/**
 * Mixed-radix encoding: each property's values are spaced {@code mapFactor} apart.
 *
 * <p>Leaves no gaps in the value matrix, so it is the smaller of the two encodings, but reading a
 * property back needs an integer division. Used when the binary encoding would overflow 31 bits,
 * and available as an opt-in for anyone who would rather trade a little speed for the memory.
 */
public record CompactFastMapKey(int mapFactor, int numValues) implements FastMapKey {
    @Override
    public int replaceIn(int mapIndex, int valueIndex) {
        if (valueIndex >= numValues) {
            return -1;
        }
        int lowerData = mapIndex % mapFactor;
        int upperFactor = mapFactor * numValues;
        int upperData = mapIndex - mapIndex % upperFactor;
        return lowerData + toPartialMapIndex(valueIndex) + upperData;
    }

    @Override
    public int toPartialMapIndex(int valueIndex) {
        return mapFactor * valueIndex;
    }

    @Override
    public int getFactorToNext() {
        return numValues;
    }

    @Override
    public int getIndexIn(int mapIndex) {
        return (mapIndex / mapFactor) % numValues;
    }
}

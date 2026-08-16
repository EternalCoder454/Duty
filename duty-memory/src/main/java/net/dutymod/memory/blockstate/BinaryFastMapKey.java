package net.dutymod.memory.blockstate;

import net.minecraft.util.Mth;

/**
 * Bit-range encoding: each property occupies a contiguous run of bits in the state index.
 *
 * <p>Lookups become a shift and a mask, which is considerably cheaper than the division {@link
 * CompactFastMapKey} needs. The cost is wasted slots -- a property with 3 values is given 4 bits'
 * worth of room -- so the value matrix is larger. This is the default because block states are
 * looked up far more often than they are created, and the matrix holds references, not objects.
 */
public record BinaryFastMapKey(int numValues, byte firstBitInValue, byte firstBitAfterValue)
        implements FastMapKey {

    public static BinaryFastMapKey create(int mapFactor, int numValues) {
        if (!Mth.isPowerOfTwo(mapFactor)) {
            throw new IllegalArgumentException("Map factor must be a power of two, got " + mapFactor);
        }
        int addedFactor = Mth.smallestEncompassingPowerOfTwo(numValues);
        int firstBit = Mth.log2(mapFactor);
        int afterBit = firstBit + Mth.log2(addedFactor);
        if (afterBit > 31) {
            // Would overflow the index. The caller falls back to the compact encoding.
            throw new IllegalStateException("Block state index would exceed 31 bits");
        }
        return new BinaryFastMapKey(numValues, (byte) firstBit, (byte) afterBit);
    }

    @Override
    public int replaceIn(int mapIndex, int valueIndex) {
        if (valueIndex >= numValues) {
            return -1;
        }
        int keepMask = ~lowestNBits(firstBitAfterValue) | lowestNBits(firstBitInValue);
        return (keepMask & mapIndex) | toPartialMapIndex(valueIndex);
    }

    @Override
    public int toPartialMapIndex(int valueIndex) {
        return valueIndex << firstBitInValue;
    }

    @Override
    public int getFactorToNext() {
        return 1 << (firstBitAfterValue - firstBitInValue);
    }

    @Override
    public int getIndexIn(int mapIndex) {
        return (mapIndex >> firstBitInValue) & lowestNBits((byte) (firstBitAfterValue - firstBitInValue));
    }

    private static int lowestNBits(byte n) {
        return n >= Integer.SIZE ? -1 : (1 << n) - 1;
    }
}

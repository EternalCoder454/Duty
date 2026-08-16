package net.dutymod.memory.enums;

import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.tree.analysis.Value;

/**
 * A {@link SourceValue} carrying one extra bit: whether this stack slot holds the array returned by
 * the {@code values()} call we are currently considering.
 *
 * <p>The dataflow analysis exists to answer one question -- "can the caller observe that this array
 * is shared?" -- so the only thing worth tracking is where that particular array flows.
 */
final class TrackedValue implements Value {
    final SourceValue sourceValue;
    final boolean tracking;

    private TrackedValue(SourceValue sourceValue, boolean tracking) {
        this.sourceValue = sourceValue;
        this.tracking = tracking;
    }

    static TrackedValue of(SourceValue sourceValue) {
        return sourceValue == null ? null : new TrackedValue(sourceValue, false);
    }

    /** {@return this value, marked as tracking if {@code condition} holds} */
    TrackedValue trackIf(boolean condition) {
        if (!condition || this.tracking) {
            return this;
        }
        return new TrackedValue(this.sourceValue, true);
    }

    @Override
    public int getSize() {
        return sourceValue.getSize();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TrackedValue that
                && this.tracking == that.tracking
                && this.sourceValue.equals(that.sourceValue);
    }

    @Override
    public int hashCode() {
        return sourceValue.hashCode() * 31 + (tracking ? 1 : 0);
    }
}

package net.dutymod.client.particle.util;

/**
 * Three-valued flag: true, false, or "not worked out yet".
 *
 * <p>Replaces fzzy-config's TriState so Duty does not need that library at runtime. Used to cache
 * an answer that is expensive to compute, where the absence of an answer has to be distinguishable
 * from a cached {@code false}.
 */
public enum TriState {
    TRUE,
    FALSE,
    DEFAULT;

    public static TriState of(boolean value) {
        return value ? TRUE : FALSE;
    }

    /** Named to match the accessor the ported call sites already use. */
    public boolean getAsBoolean() {
        return this == TRUE;
    }
}

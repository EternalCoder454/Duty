package net.dutymod.server.wire;

import net.dutymod.core.DutyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Duty's copy of Alternate Current's redstone dust implementation.
 *
 * <p>Vanilla recalculates a wire's power by repeatedly asking its neighbours, which for a long dust
 * line is quadratic and emits far more block and shape updates than the result needs. Alternate
 * Current walks the network once and emits updates in a deterministic order. Upstream measures dust
 * MSPT up to twenty times lower.
 *
 * <p><b>Off by default, and not because of stability.</b> It cannot coexist with Lithium's redstone:
 * both patch {@code RedStoneWireBlock.affectNeighborsAfterRemoval}, and both replace the same
 * evaluation. Only one can own redstone. Lithium is installed and owns it today.
 *
 * <p>To hand redstone to this instead, set {@code server.alternate_current} true <em>and</em> put
 * this in {@code config/lithium.properties}:
 *
 * <pre>mixin.block.redstone_wire=false</pre>
 *
 * <p>Enabling one without the other is the failure case: with both on, the two implementations fight
 * over the same method; with neither, dust is vanilla.
 *
 * <p>The other thing to know before flipping it is that this is not a pure optimisation. Alternate
 * Current makes dust update order deterministic and non-locational -- it fixes MC-11193 -- so a
 * contraption built against vanilla's locational ordering can behave differently. That is why the
 * per-world {@link Config} lives in the level directory rather than in Duty's config: update order
 * is a property of the world you built in, not of the installation.
 */
public final class AlternateCurrent {
    public static final Logger LOGGER = LogManager.getLogger("Duty/AlternateCurrent");

    /** Duty's master switch. Read once; the mixins do not apply at all when this is false. */
    public static final String ENABLED = "server.alternate_current";

    /**
     * Whether the wire handler is live.
     *
     * <p>Two gates, deliberately. This one is the per-world {@link Config} toggle, flipped at
     * runtime by that config; the Duty option above decides whether the mixins are applied in the
     * first place. A disabled world therefore falls back to vanilla dust, while a disabled
     * installation leaves the class untouched for Lithium.
     */
    public static boolean on = true;

    private AlternateCurrent() {}

    static {
        DutyConfig.register(ENABLED, false,
                "Replace vanilla redstone dust with Alternate Current's implementation.\n"
                        + "\n"
                        + "OFF by default because it cannot coexist with Lithium's redstone: both\n"
                        + "patch RedStoneWireBlock.affectNeighborsAfterRemoval. Turning this on also\n"
                        + "requires mixin.block.redstone_wire=false in config/lithium.properties, or\n"
                        + "the two will fight over the same method.\n"
                        + "\n"
                        + "Note this is not a pure speed-up: dust update order becomes deterministic\n"
                        + "and non-locational (it fixes MC-11193), so a contraption built against\n"
                        + "vanilla's locational ordering can behave differently.");
    }

    /** Forces the registration above to run. */
    public static void init() {}

    /** {@return whether Duty is configured to own redstone dust} */
    public static boolean enabled() {
        init();
        return DutyConfig.get(ENABLED);
    }
}

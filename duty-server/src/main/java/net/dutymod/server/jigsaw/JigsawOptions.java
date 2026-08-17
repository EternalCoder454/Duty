package net.dutymod.server.jigsaw;

import net.dutymod.framework.DutyConfig;

/**
 * Options for the jigsaw structure work.
 *
 * <p>The main optimization has no switch of its own and does not need one: vanilla tracks the space
 * a jigsaw structure has already claimed as a {@link net.minecraft.world.phys.shapes.VoxelShape}
 * and calls {@code Shapes.join} once per placed piece, so the cost of placing the next piece grows
 * with the number already placed. Duty replaces that shape with an octree of boxes, which answers
 * the same questions in log time. It generates identical structures -- it is the same test, asked
 * of a better index -- so there is nothing to opt out of.
 *
 * <p>The one option below is different, and is off because of it.
 */
public final class JigsawOptions {
    /** See the comment registered with it; this one changes what generates. */
    public static final String DEDUPLICATE_POOL_ELEMENTS = "server.jigsaw_deduplicate_pool_elements";

    /**
     * Read once and cached.
     *
     * <p>Structure assembly reads this per piece considered, deep inside worldgen and on C2ME's
     * worker threads. Going through the config map there would be the most-read option in Duty.
     */
    public static boolean deduplicateShuffledTemplatePoolElementList;

    static {
        DutyConfig.register(DEDUPLICATE_POOL_ELEMENTS, false,
                "Collapse duplicate entries in a structure's template pool before choosing pieces.\n"
                        + "\n"
                        + "OFF by default, and not because it is slower. It changes the layout that\n"
                        + "generates: pools express weighting by repeating an entry, so removing the\n"
                        + "repeats changes which piece is picked. Structures stay valid and complete,\n"
                        + "but they are laid out differently from vanilla for the same seed, and a\n"
                        + "world generated with this on does not match one generated with it off.\n"
                        + "\n"
                        + "Worth turning on only for a pack with structure mods that use very high\n"
                        + "pool weights, where the shuffled list is long enough for the saving to\n"
                        + "matter more than seed parity does.");
    }

    private JigsawOptions() {}

    /** Forces the registration above to run, and caches the value. */
    public static void init() {
        deduplicateShuffledTemplatePoolElementList = DutyConfig.get(DEDUPLICATE_POOL_ELEMENTS);
    }
}

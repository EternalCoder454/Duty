package net.dutymod.server.spawn;

import net.dutymod.framework.DutyConfig;

/**
 * How much of the world a joining player waits for.
 *
 * <p>Vanilla waits for a radius of 3 -- 49 chunks -- around the spawn position before letting the
 * player in. Duty defaults to 1, which is 9 chunks: enough that the ground is under their feet,
 * without holding the join on chunks they will not look at before the view distance has caught up.
 */
public final class SpawnJoinOptions {
    public static final String JOIN_CHUNK_RADIUS = "server.join_chunk_radius";

    /**
     * Cached at startup.
     *
     * <p>Read from a mixin on the join path. It is not hot enough to matter, but reading the config
     * map from inside worldgen is a habit worth not forming.
     */
    private static int joinChunkRadius = 1;

    static {
        DutyConfig.register(JOIN_CHUNK_RADIUS, 1,
                "How many chunks out from the spawn position a joining player waits for.\n"
                        + "\n"
                        + "Vanilla is 3, which is 49 chunks, and on a heavy pack that is most of\n"
                        + "the time spent joining a world. The chunks a player can actually see\n"
                        + "arrive through the normal view-distance path regardless, so waiting for\n"
                        + "them up front buys nothing.\n"
                        + "\n"
                        + "1 loads the chunk under the player plus its neighbours. 0 loads only the\n"
                        + "chunk they stand in, which is faster still but can drop them into open\n"
                        + "air for a moment on a slow disk. 3 restores vanilla.");
    }

    private SpawnJoinOptions() {}

    /** Forces the registration above to run, and caches the value. */
    public static void init() {
        joinChunkRadius = DutyConfig.getInt(JOIN_CHUNK_RADIUS, 0, 32);
    }

    public static int joinChunkRadius() {
        return joinChunkRadius;
    }
}

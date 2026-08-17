package net.dutymod.memory;

import net.dutymod.framework.DutyConfig;

/**
 * Every toggle Duty: Memory owns, registered in one place.
 *
 * <p>Registration happens in a static initializer so the keys exist in {@code duty.properties}
 * before either the mixin plugin or the class transformer asks for them.
 */
public final class MemoryOptions {
    /** FerriteCore's neighbour-table and property-map deduplication. */
    public static final String BLOCK_STATE_DEDUPLICATION = "memory.block_state_deduplication";

    /** Drop each state's own property array and derive values from its table index instead. */
    public static final String PROPERTY_MAP_COMPACTION = "memory.property_map_compaction";

    /** Use the gap-free but slower state index encoding. */
    public static final String COMPACT_STATE_ENCODING = "memory.compact_state_encoding";

    /** Jasione's removal of the defensive array copy in Enum.values(). */
    public static final String ENUM_VALUES_CACHING = "memory.enum_values_caching";

    /** Intern TagKey and ResourceKey instances instead of allocating duplicates. */
    public static final String TAG_KEY_INTERNING = "memory.tag_key_interning";

    /** Log every Enum.values() call site that was rewritten. Very noisy; for debugging only. */
    public static final String ENUM_VALUES_LOG_REWRITES = "memory.enum_values_log_rewrites";

    /** Skip the retained Set that small tag/holder sets would otherwise build. */
    public static final String SMALL_HOLDER_SET_SCAN = "memory.small_holder_set_scan";

    /** Share the collision shapes and face-sturdy tables held by the block state cache. */
    public static final String BLOCK_STATE_CACHE_DEDUPLICATION = "memory.block_state_cache_deduplication";

    /** Replace empty data component patch maps with a shared immutable empty map. */
    public static final String DATA_COMPONENT_DEDUPLICATION = "memory.data_component_deduplication";

    static {
        DutyConfig.register(BLOCK_STATE_DEDUPLICATION, true,
                "Share the neighbour-lookup tables and property maps between identical block\n"
                        + "states. This is where most of the block state heap goes; on a large\n"
                        + "modpack it is worth hundreds of megabytes.");
        DutyConfig.register(PROPERTY_MAP_COMPACTION, true,
                "Drop each block state's own array of property values and read them back from\n"
                        + "its position in the shared table instead. Requires the option above.");
        DutyConfig.register(COMPACT_STATE_ENCODING, true,
                "Pack block state indices with no wasted slots. Uses less memory than the default\n"
                        + "bit-range encoding, at the cost of an integer division per property\n"
                        + "lookup instead of a shift and a mask.\n"
                        + "\n"
                        + "Upstream FerriteCore leaves this off because it trades CPU for memory.\n"
                        + "Duty turns it on: that is the trade this module exists to make, and the\n"
                        + "division lands on block state property reads rather than on a render or\n"
                        + "tick path. Turn it off if you would rather have the cycles back.");
        DutyConfig.register(ENUM_VALUES_CACHING, true,
                "Rewrite Enum.values() calls to return a shared array where it is provably safe\n"
                        + "to do so. Enum.values() clones its array on every call; almost every\n"
                        + "caller only reads it. Disable this if you suspect a mod is being\n"
                        + "miscompiled.");
        DutyConfig.register(TAG_KEY_INTERNING, true,
                "Share one instance of each TagKey and ResourceKey rather than allocating a\n"
                        + "new equal object every time one is parsed. Modpacks parse a great many\n"
                        + "of these during datapack loading.");
        DutyConfig.register(ENUM_VALUES_LOG_REWRITES, false,
                "Log every rewritten Enum.values() call site. Extremely noisy. Debugging only.");
        DutyConfig.register(SMALL_HOLDER_SET_SCAN, true,
                "Scan small tag and holder sets instead of building a hash set for them. Vanilla\n"
                        + "materialises a Set on first membership test and keeps it forever; for a\n"
                        + "handful of entries the retained set costs more than the lookup saves, and\n"
                        + "a modded registry has thousands of them. The answer is identical either\n"
                        + "way; larger sets keep the cache.");
        DutyConfig.register(BLOCK_STATE_CACHE_DEDUPLICATION, true,
                "Share the collision shape and face-sturdy table between block states whose cached\n"
                        + "values are identical. Every block state builds its own cache at startup,\n"
                        + "and across a large modpack the overwhelming majority of them are equal --\n"
                        + "every full cube shares one shape, every fence variant shares a handful.\n"
                        + "Also rewrites the internals of the discarded shapes to point at the kept\n"
                        + "one, because mods commonly hold their own references to shapes outside\n"
                        + "the block state cache.");
        DutyConfig.register(DATA_COMPONENT_DEDUPLICATION, true,
                "Replace a data component patch map with a shared immutable empty map once it\n"
                        + "becomes empty. Most item stacks carry no component changes at all, and an\n"
                        + "empty mutable map per stack is pure overhead.");
    }

    private MemoryOptions() {}

    /** Forces the static initializer above to run. */
    public static void init() {}
}

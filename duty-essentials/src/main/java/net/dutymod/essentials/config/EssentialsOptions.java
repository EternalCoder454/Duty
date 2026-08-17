package net.dutymod.essentials.config;

import net.dutymod.framework.DutyConfig;

/**
 * Every option Duty: Essentials owns.
 *
 * <p>Upstream keeps these in a YAML file of its own, through its author's config library. Duty has
 * one config system, so they are registered with {@link DutyConfig} instead and appear in the same
 * settings screen as every other module's options. Nothing else in Duty needs a second config
 * format, and a module that wrote its own would be the only one you could not change in game.
 *
 * <p>The {@link Option} wrapper exists so the forty command classes can keep reading their settings
 * as {@code homesLimit.get()}. That reads the live value on each call rather than a cached one,
 * which is what lets these options take effect without a restart -- unusual in Duty, but correct
 * here, because a command reads its setting when it runs rather than when it is registered.
 */
public final class EssentialsOptions {
    /** A single option, read through on every call so edits take effect immediately. */
    public static final class Option<T> {
        private final String key;
        private final int min;
        private final int max;
        private final Class<T> type;

        private Option(String key, Class<T> type, int min, int max) {
            this.key = key;
            this.type = type;
            this.min = min;
            this.max = max;
        }

        @SuppressWarnings("unchecked")
        public T get() {
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(DutyConfig.get(key));
            }
            if (type == Integer.class) {
                return (T) Integer.valueOf(DutyConfig.getInt(key, min, max));
            }
            return (T) DutyConfig.getString(key);
        }
    }

    /** Wraps an already-registered boolean key. Used by {@link CommandConfig}. */
    static Option<Boolean> boolOption(String fullKey) {
        return new Option<>(fullKey, Boolean.class, 0, 0);
    }

    private static Option<Boolean> bool(String name, boolean defaultValue, String comment) {
        DutyConfig.register("essentials." + name, defaultValue, comment);
        return new Option<>("essentials." + name, Boolean.class, 0, 0);
    }

    private static Option<Integer> integer(String name, int defaultValue, int min, int max, String comment) {
        DutyConfig.register("essentials." + name, defaultValue, comment);
        return new Option<>("essentials." + name, Integer.class, min, max);
    }

    private static Option<String> string(String name, String defaultValue, String comment) {
        DutyConfig.register("essentials." + name, defaultValue, comment);
        return new Option<>("essentials." + name, String.class, 0, 0);
    }

    // -- Presentation ---------------------------------------------------------------------------

    public static final Option<String> prefix = string("prefix", "",
            "Text shown in brackets before every message this module sends. Empty uses \"Duty\".");
    public static final Option<Integer> primaryColor = integer("primary_color", 0x29A3F0, 0x000000, 0xFFFFFF,
            "Colour of that prefix, as a decimal RGB value. 2728944 is Duty's blue.");

    // -- Homes, warps and spawn -----------------------------------------------------------------

    public static final Option<Integer> homesLimit = integer("homes_limit", 5, 1, 1000,
            "How many homes one player may set.");
    public static final Option<Integer> homeTeleportDelay = integer("home_teleport_delay", 0, 0, 600,
            "Seconds a player must stand still before /home completes. 0 teleports at once.");
    public static final Option<Integer> homeCooldown = integer("home_cooldown", 0, 0, 86400,
            "Seconds before /home can be used again. 0 removes the cooldown.");
    public static final Option<Integer> warpTeleportDelay = integer("warp_teleport_delay", 0, 0, 600,
            "Seconds a player must stand still before /warp completes.");
    public static final Option<Integer> warpCooldown = integer("warp_cooldown", 0, 0, 86400,
            "Seconds before /warp can be used again.");
    public static final Option<Integer> spawnTeleportDelay = integer("spawn_teleport_delay", 0, 0, 600,
            "Seconds a player must stand still before /spawn completes.");
    public static final Option<Integer> spawnCooldown = integer("spawn_cooldown", 0, 0, 86400,
            "Seconds before /spawn can be used again.");

    // -- Back -----------------------------------------------------------------------------------

    public static final Option<Boolean> allowBackOnDeath = bool("allow_back_on_death", true,
            "Record where a player died so /back returns them there.");
    public static final Option<Integer> backTeleportDelay = integer("back_teleport_delay", 0, 0, 600,
            "Seconds a player must stand still before /back completes.");
    public static final Option<Integer> backCooldown = integer("back_cooldown", 0, 0, 86400,
            "Seconds before /back can be used again.");

    // -- Random teleport ------------------------------------------------------------------------

    public static final Option<Integer> rtpMinRadius = integer("rtp_min_radius", 1000, 0, 10_000_000,
            "Closest /rtp will drop a player, in blocks from the world spawn.");
    public static final Option<Integer> rtpMaxRadius = integer("rtp_max_radius", 10000, 1, 10_000_000,
            "Furthest /rtp will drop a player. Large values load a lot of new chunks.");
    public static final Option<Integer> rtpCooldown = integer("rtp_cooldown", 300, 0, 86400,
            "Seconds before /rtp can be used again. Generating new terrain is expensive, so\n"
                    + "this one is not zero by default.");
    public static final Option<Integer> rtpDelay = integer("rtp_delay", 0, 0, 600,
            "Seconds a player must stand still before /rtp completes.");

    // -- Teleport requests ----------------------------------------------------------------------

    public static final Option<Integer> tpaTimeout = integer("tpa_timeout", 60, 5, 3600,
            "Seconds a /tpa request stays open before it lapses.");
    public static final Option<Integer> tpaTeleportDelay = integer("tpa_teleport_delay", 0, 0, 600,
            "Seconds a player must stand still before an accepted /tpa completes.");
    public static final Option<Integer> tpaCooldown = integer("tpa_cooldown", 0, 0, 86400,
            "Seconds before /tpa can be used again.");

    // -- Time and weather -----------------------------------------------------------------------

    public static final Option<Integer> sunnyTime = integer("sunny_time", 6000, 20, 1_000_000,
            "Ticks of clear weather /sun sets.");
    public static final Option<Integer> rainyTime = integer("rainy_time", 6000, 20, 1_000_000,
            "Ticks of rain /rain sets.");
    public static final Option<Integer> thunderTime = integer("thunder_time", 6000, 20, 1_000_000,
            "Ticks of thunder /thunder sets.");

    // -- Player commands ------------------------------------------------------------------------

    public static final Option<Integer> maxNickLength = integer("max_nick_length", 16, 1, 32,
            "Longest nickname /nick accepts.");
    public static final Option<Boolean> allowColorsInNick = bool("allow_colors_in_nick", true,
            "Let nicknames contain colour codes.");
    public static final Option<Boolean> godModeAllow = bool("god_mode_allow", true,
            "Allow /god at all. Turning this off hides the command rather than failing it.");
    public static final Option<Boolean> flyAllow = bool("fly_allow", true,
            "Allow /fly at all.");

    private EssentialsOptions() {}

    /** Forces the registrations above to run. */
    public static void init() {}
}

package net.dutymod.client.quiet.config;

import net.dutymod.client.ClientOptions;
import net.dutymod.framework.DutyConfig;

/**
 * Quiet's settings, rewritten on top of Duty's config.
 *
 * <p>Upstream this was backed by YACL (yet-another-config-lib) with a generated options screen,
 * which made the jar depend on YACL and ModMenu at runtime. Duty already has a config file, so
 * the dependency is dropped and the fields are read from that instead.
 *
 * <p>The shape is kept deliberately: the thirty-odd ported mixins all read {@code Config.get().x},
 * so preserving public fields on a singleton meant none of them had to be touched.
 *
 * <p>Values are read once and cached. These are consulted from render and tick paths where a map
 * lookup per frame would cost more than the setting saves.
 */
public final class Config {
    private static final Config INSTANCE = new Config();

    // -- chat -------------------------------------------------------------------------------
    public final int maxChatHistory;
    public final CompactChat compactChat;
    public final AdminChat adminChat;

    // -- toasts and announcements -----------------------------------------------------------
    public final boolean announceAdvancements;
    public final boolean advancementToasts;
    public final boolean recipeToasts;

    // -- screens and overlays ---------------------------------------------------------------
    public final boolean disableWidgetFade;
    public final boolean disableFade;
    public final boolean disableSplash;
    public final boolean disableLoadingTerrain;
    public final boolean disableWorldAdvice;

    // -- rendering --------------------------------------------------------------------------
    public final boolean nightVisionFlicker;
    public final boolean disableParticles;
    public final boolean animateTextures;
    public final boolean renderWeather;

    // -- misc -------------------------------------------------------------------------------
    public final boolean deleteToTrash;
    public final float unfocusedVolume;
    public final int renderThreadPriority;
    public final int serverThreadPriority;
    public final int ioThreadPriority;

    private Config() {
        ClientOptions.init();
        maxChatHistory = DutyConfig.getInt(ClientOptions.MAX_CHAT_HISTORY, 100, 32768);
        compactChat = parse(CompactChat.class, ClientOptions.COMPACT_CHAT, CompactChat.ONLY_CONSECUTIVE);
        adminChat = parse(AdminChat.class, ClientOptions.ADMIN_CHAT, AdminChat.ENABLED);

        announceAdvancements = DutyConfig.get(ClientOptions.ANNOUNCE_ADVANCEMENTS);
        advancementToasts = DutyConfig.get(ClientOptions.ADVANCEMENT_TOASTS);
        recipeToasts = DutyConfig.get(ClientOptions.RECIPE_TOASTS);

        disableWidgetFade = DutyConfig.get(ClientOptions.DISABLE_WIDGET_FADE);
        disableFade = DutyConfig.get(ClientOptions.DISABLE_FADE);
        disableSplash = DutyConfig.get(ClientOptions.DISABLE_SPLASH);
        disableLoadingTerrain = DutyConfig.get(ClientOptions.DISABLE_LOADING_TERRAIN);
        disableWorldAdvice = DutyConfig.get(ClientOptions.DISABLE_WORLD_ADVICE);

        nightVisionFlicker = DutyConfig.get(ClientOptions.NIGHT_VISION_FLICKER);
        disableParticles = DutyConfig.get(ClientOptions.STFU_DISABLE_PARTICLES);
        animateTextures = DutyConfig.get(ClientOptions.ANIMATE_TEXTURES);
        renderWeather = DutyConfig.get(ClientOptions.RENDER_WEATHER);

        deleteToTrash = DutyConfig.get(ClientOptions.DELETE_TO_TRASH);
        // Stored as a percentage because the config file is text and whole numbers survive
        // hand-editing better than floats.
        unfocusedVolume = DutyConfig.getInt(ClientOptions.UNFOCUSED_VOLUME_PERCENT, 0, 100) / 100.0F;

        int defaultPriority = Runtime.getRuntime().availableProcessors() > 4 ? 8 : 5;
        renderThreadPriority = clampPriority(ClientOptions.RENDER_THREAD_PRIORITY, defaultPriority);
        serverThreadPriority = clampPriority(ClientOptions.SERVER_THREAD_PRIORITY, defaultPriority);
        ioThreadPriority = clampPriority(ClientOptions.IO_THREAD_PRIORITY, 1);
    }

    /** Thread priorities outside 1..10 throw from {@link Thread#setPriority}, so clamp hard. */
    private static int clampPriority(String key, int fallback) {
        int value = DutyConfig.getInt(key, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
        return value == 0 ? fallback : value;
    }

    /** Reads an enum setting by name, falling back rather than failing on a typo. */
    private static <E extends Enum<E>> E parse(Class<E> type, String key, E fallback) {
        String raw = DutyConfig.getString(key);
        for (E value : type.getEnumConstants()) {
            if (value.name().equalsIgnoreCase(raw)) {
                return value;
            }
        }
        net.dutymod.framework.DutyLog.warn("Config key '" + key + "' has unknown value '" + raw
                + "'; using " + fallback.name());
        return fallback;
    }

    /** How much command feedback reaches chat. */
    public enum AdminChat { ENABLED, ONLY_PLAYERS, DISABLED }

    /** Which repeated chat messages get collapsed into one line. */
    public enum CompactChat { ALL, ONLY_CONSECUTIVE, NEVER }

    public static Config get() {
        return INSTANCE;
    }
}

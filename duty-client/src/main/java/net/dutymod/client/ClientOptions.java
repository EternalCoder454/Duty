package net.dutymod.client;

import net.dutymod.core.DutyConfig;

/**
 * Every toggle Duty: Client owns.
 *
 * <p>EntityCulling's own config had a large surface because it shipped across many Minecraft
 * versions and both loaders. Duty targets one version, so the options that existed to paper over
 * version differences are gone; what remains is what a player would actually want to change.
 */
public final class ClientOptions {
    // -- Occlusion culling (from EntityCulling) ------------------------------------------------

    /** Master switch for the async occlusion culling thread. */
    public static final String CULLING_ENABLED = "client.culling_enabled";

    /** Skip the entity half of culling, keeping block entity culling. */
    public static final String SKIP_ENTITY_CULLING = "client.skip_entity_culling";

    /** Skip the block entity half of culling, keeping entity culling. */
    public static final String SKIP_BLOCK_ENTITY_CULLING = "client.skip_block_entity_culling";

    /** Also skip the client-side tick of entities that are hidden. */
    public static final String TICK_CULLING = "client.tick_culling";

    /** Treat leaves as opaque when tracing. Big win in forests, slight over-culling. */
    public static final String SOLID_LEAVES = "client.solid_leaves";

    /** Keep drawing name tags of culled entities so players stay findable behind walls. */
    public static final String NAMETAGS_THROUGH_WALLS = "client.nametags_through_walls";

    /** Frustum-cull block entities as well as occlusion-culling them. */
    public static final String BLOCK_ENTITY_FRUSTUM_CULLING = "client.block_entity_frustum_culling";

    // -- Particles (from Particle Core) --------------------------------------------------------

    /** Master switch for all particle work. */
    public static final String PARTICLE_OPTIMIZATIONS = "client.particle_optimizations";

    // -- Baked block entities (from OptimisedBlockEntities) ------------------------------------

    /** Master switch for baking block entities into the chunk mesh. */
    public static final String BAKED_BLOCK_ENTITIES = "client.baked_block_entities";

    /** Tick particles on a worker thread instead of the render thread. */
    public static final String ASYNC_PARTICLE_TICKING = "client.async_particle_ticking";

    /** Upper bound on particles buffered per texture sheet. */
    public static final String MAX_PARTICLES_PER_SHEET = "client.max_particles_per_sheet";

    /** Stop spawning particles entirely. */
    public static final String DISABLE_ALL_PARTICLES = "client.disable_all_particles";

    /** Force the "minimal" particle setting regardless of the video option. */
    public static final String FORCE_MINIMAL_PARTICLES = "client.force_minimal_particles";

    /** Master switch for the potion particle filters below. */
    public static final String POTION_PARTICLE_FILTERING = "client.potion_particle_filtering";

    public static final String HIDE_OWN_POTION_PARTICLES = "client.hide_own_potion_particles";
    public static final String HIDE_OTHER_PLAYER_POTION_PARTICLES = "client.hide_other_player_potion_particles";
    public static final String HIDE_MOB_POTION_PARTICLES = "client.hide_mob_potion_particles";


    // -- Stfu: annoyance fixes and quality-of-life ---------------------------------------------

    public static final String MAX_CHAT_HISTORY = "client.max_chat_history";
    public static final String COMPACT_CHAT = "client.compact_chat";
    public static final String ADMIN_CHAT = "client.admin_chat";
    public static final String ANNOUNCE_ADVANCEMENTS = "client.announce_advancements";
    public static final String ADVANCEMENT_TOASTS = "client.advancement_toasts";
    public static final String RECIPE_TOASTS = "client.recipe_toasts";
    public static final String DISABLE_WIDGET_FADE = "client.disable_widget_fade";
    public static final String DISABLE_FADE = "client.disable_fade";
    public static final String DISABLE_SPLASH = "client.disable_splash";
    public static final String DISABLE_LOADING_TERRAIN = "client.disable_loading_terrain";
    public static final String DISABLE_WORLD_ADVICE = "client.disable_world_advice";
    public static final String NIGHT_VISION_FLICKER = "client.night_vision_flicker";
    public static final String STFU_DISABLE_PARTICLES = "client.stfu_disable_particles";
    public static final String ANIMATE_TEXTURES = "client.animate_textures";
    public static final String RENDER_WEATHER = "client.render_weather";
    public static final String DELETE_TO_TRASH = "client.delete_to_trash";
    public static final String UNFOCUSED_VOLUME_PERCENT = "client.unfocused_volume_percent";
    public static final String RENDER_THREAD_PRIORITY = "client.render_thread_priority";
    public static final String SERVER_THREAD_PRIORITY = "client.server_thread_priority";
    public static final String IO_THREAD_PRIORITY = "client.io_thread_priority";

    static {
        DutyConfig.register(CULLING_ENABLED, true,
                "Hide entities and block entities that are behind solid blocks, decided on a\n"
                        + "background thread by tracing from the camera. This is the single largest\n"
                        + "frame time win available in a busy world.");
        DutyConfig.register(SKIP_ENTITY_CULLING, false,
                "Do not cull entities. Leaves block entity culling running.");
        DutyConfig.register(SKIP_BLOCK_ENTITY_CULLING, false,
                "Do not cull block entities. Leaves entity culling running.");
        DutyConfig.register(TICK_CULLING, true,
                "Also skip the client-side tick of hidden entities, not just their rendering.\n"
                        + "Hidden entities still get position and animation updates so they do not\n"
                        + "snap when they come back into view.");
        DutyConfig.register(SOLID_LEAVES, true,
                "Treat leaf blocks as opaque when tracing. Large win in forests. Can very\n"
                        + "occasionally hide something visible through a gap in foliage.");
        DutyConfig.register(NAMETAGS_THROUGH_WALLS, true,
                "Keep drawing name tags for culled entities, so players do not vanish from view\n"
                        + "behind walls. Matches vanilla behaviour more closely than hiding them.");
        DutyConfig.register(BLOCK_ENTITY_FRUSTUM_CULLING, true,
                "Frustum-cull block entities as well, the same way entities already are.");
        DutyConfig.register(PARTICLE_OPTIMIZATIONS, true,
                "Batch particle geometry, cache their light and position lookups, and skip the\n"
                        + "ones that cannot be seen. Turn this off to take Duty entirely out of\n"
                        + "the particle rendering path.");
        DutyConfig.register(BAKED_BLOCK_ENTITIES, true,
                "Bake chests, signs, banners, beds and similar into the chunk mesh instead of\n"
                        + "redrawing them every frame as block entities. This is the largest win\n"
                        + "available in a built-up world, where block entities outnumber everything\n"
                        + "else. Per-block-entity settings live in config/duty-blockentities.json.");
        DutyConfig.register(ASYNC_PARTICLE_TICKING, true,
                "Tick particles on a worker thread. Duty switches this off by itself if it\n"
                        + "detects a concurrency problem, so leaving it on is low risk.");
        DutyConfig.register(MAX_PARTICLES_PER_SHEET, 16384,
                "Most particles buffered per texture sheet. Lower values cap the worst-case\n"
                        + "cost of an explosion of particles at the price of dropping some.");
        DutyConfig.register(DISABLE_ALL_PARTICLES, false,
                "Stop spawning particles altogether. Blunt, but the largest single saving\n"
                        + "available if particles are what is hurting.");
        DutyConfig.register(FORCE_MINIMAL_PARTICLES, false,
                "Behave as though the video setting were Minimal, whatever it is set to.");
        DutyConfig.register(POTION_PARTICLE_FILTERING, false,
                "Enable the three potion particle filters below. Off by default because\n"
                        + "hiding potion particles changes what you can see about other players.");
        DutyConfig.register(HIDE_OWN_POTION_PARTICLES, false,
                "Hide the swirls from your own active effects.");
        DutyConfig.register(HIDE_OTHER_PLAYER_POTION_PARTICLES, false,
                "Hide potion swirls on other players.");
        DutyConfig.register(HIDE_MOB_POTION_PARTICLES, false,
                "Hide potion swirls on mobs.");
        int defaultPriority = Runtime.getRuntime().availableProcessors() > 4 ? 8 : 5;
        DutyConfig.register(MAX_CHAT_HISTORY, 100,
                "How many chat lines to keep. Vanilla keeps 100.");
        DutyConfig.register(COMPACT_CHAT, "ONLY_CONSECUTIVE",
                "Collapse repeated chat messages. ALL, ONLY_CONSECUTIVE or NEVER.");
        DutyConfig.register(ADMIN_CHAT, "ENABLED",
                "How much command feedback reaches chat. ENABLED, ONLY_PLAYERS or DISABLED.");
        DutyConfig.register(ANNOUNCE_ADVANCEMENTS, true,
                "Announce advancements in chat.");
        DutyConfig.register(ADVANCEMENT_TOASTS, true,
                "Show a toast when an advancement is earned. Turning this off drops the whole\n"
                        + "advancement update packet, not just the popup, so the advancement screen\n"
                        + "stops tracking progress while it is off.");
        DutyConfig.register(RECIPE_TOASTS, false, "Show a toast when a recipe is unlocked.");
        DutyConfig.register(DISABLE_WIDGET_FADE, true, "Remove the fade on widgets.");
        DutyConfig.register(DISABLE_FADE, false, "Remove screen fade transitions.");
        DutyConfig.register(DISABLE_SPLASH, true, "Remove the splash text on the title screen.");
        DutyConfig.register(DISABLE_LOADING_TERRAIN, true,
                "Skip the \"Loading terrain\" screen and drop straight into the world.");
        DutyConfig.register(DISABLE_WORLD_ADVICE, true,
                "Skip the experimental-settings warning when opening a world.");
        DutyConfig.register(NIGHT_VISION_FLICKER, false,
                "Let night vision flicker as it runs out. Off means it stays steady.");
        DutyConfig.register(STFU_DISABLE_PARTICLES, false,
                "Stop particles entirely. Separate from the particle work above, which\n"
                        + "optimizes them rather than removing them.");
        DutyConfig.register(ANIMATE_TEXTURES, true,
                "Animate textures. Turning this off is a real frame time saving on packs with\n"
                        + "many animated blocks.");
        DutyConfig.register(RENDER_WEATHER, true, "Render rain and snow.");
        DutyConfig.register(DELETE_TO_TRASH, true,
                "Deleting a world moves it to the recycle bin instead of erasing it.");
        DutyConfig.register(UNFOCUSED_VOLUME_PERCENT, 100,
                "Volume percentage while the window is not focused. 100 leaves it alone.");
        DutyConfig.register(RENDER_THREAD_PRIORITY, defaultPriority,
                "Thread priority for the render thread, 1 to 10.");
        DutyConfig.register(SERVER_THREAD_PRIORITY, defaultPriority,
                "Thread priority for the integrated server thread, 1 to 10.");
        DutyConfig.register(IO_THREAD_PRIORITY, 1,
                "Thread priority for background IO, 1 to 10. Low on purpose.");
    }

    private ClientOptions() {}

    /** Forces the static initializer above to run. */
    public static void init() {}
}

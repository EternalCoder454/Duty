package net.dutymod.client.particle;

import net.dutymod.client.ClientOptions;
import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Particle Core's configuration, rewritten in Java on top of Duty's config.
 *
 * <p>Upstream this was four Kotlin files backed by fzzy-config, which meant the jar needed both
 * KotlinForForge and fzzy-config at runtime. Neither earns its place for what is, in the end, a
 * handful of booleans and two numbers, so both dependencies are gone.
 *
 * <p>The odd shape here -- a singleton called {@code INSTANCE}, getters returning small holder
 * objects -- mirrors what Kotlin's {@code object} declarations compiled to, so the ported mixins
 * did not have to be rewritten alongside it.
 */
public final class PcConfig {
    public static final PcConfig INSTANCE = new PcConfig();

    private final Impl impl = new Impl();

    /**
     * Squared render distance for particles, refreshed from the video settings.
     *
     * <p>Squared because the check is done against {@code distanceToSqr}; taking a square root per
     * particle per frame is exactly the sort of thing this mod exists to remove.
     */
    private volatile double renderDistanceSq = 0.0;

    /**
     * The potion filters, read once rather than per particle.
     *
     * <p>{@link #shouldDisablePotionParticle} is reached from {@code LivingEntity.tickEffects},
     * which is every living entity with an effect on it, every tick. {@link DutyConfig#get} is
     * {@code synchronized} and reads through a {@link java.util.Properties}, itself a
     * {@code Hashtable}, so asking it there cost two monitors and a map lookup per potion particle
     * -- inside the module whose job is to make particles cheaper.
     *
     * <p>Volatile rather than final because {@code /duty reload} refreshes them below.
     */
    private volatile boolean potionFiltering;
    private volatile boolean hideOwn;
    private volatile boolean hideOtherPlayers;
    private volatile boolean hideMobs;

    /**
     * The particle buffer cap, read once rather than per tick.
     *
     * <p>The async ticking mixin asks for this every client tick, and {@link DutyConfig#getInt}
     * parses the value out of its string form on every call.
     */
    private volatile int maxParticlesPerSheet;

    /**
     * The forced-minimal override, read once rather than per particle.
     *
     * <p>{@code ClientLevel.calculateParticleLevel} decides whether each spawn attempt is allowed,
     * so this sat on the same per-particle path as the potion filters above.
     */
    private volatile boolean forceMinimalParticles;

    private PcConfig() {
        readConfig();
        // DEV.md asks any module that caches an option to register here, so the file and the
        // behaviour cannot disagree after a reload.
        DutyConfig.onReload(this::readConfig);
    }

    private void readConfig() {
        ClientOptions.init();
        potionFiltering = DutyConfig.get(ClientOptions.POTION_PARTICLE_FILTERING);
        hideOwn = DutyConfig.get(ClientOptions.HIDE_OWN_POTION_PARTICLES);
        hideOtherPlayers = DutyConfig.get(ClientOptions.HIDE_OTHER_PLAYER_POTION_PARTICLES);
        hideMobs = DutyConfig.get(ClientOptions.HIDE_MOB_POTION_PARTICLES);
        maxParticlesPerSheet =
                DutyConfig.getInt(ClientOptions.MAX_PARTICLES_PER_SHEET, 256, Integer.MAX_VALUE);
        forceMinimalParticles = DutyConfig.get(ClientOptions.FORCE_MINIMAL_PARTICLES);
    }

    public Impl getImpl() {
        return impl;
    }

    public DutyLogAdapter getLogger() {
        return DutyLogAdapter.INSTANCE;
    }

    /** {@return whether a particle at the given point is near enough to draw} */
    public boolean shouldRenderParticle(double x, double y, double z, Vec3 cameraPos) {
        return cameraPos.distanceToSqr(x, y, z) <= renderDistanceSq;
    }

    /** {@return whether potion particles of this category are switched off} */
    public boolean shouldDisablePotionParticle(PotionDisableType type) {
        if (!potionFiltering) {
            return type == PotionDisableType.NONE;
        }
        return switch (type) {
            case NONE -> false;
            case SELF -> hideOwn;
            case OTHER_PLAYER -> hideOtherPlayers;
            case MOBS -> hideMobs;
            case ALL -> hideOwn && hideOtherPlayers && hideMobs;
        };
    }

    /** Which set of potion particles a check is asking about. */
    public enum PotionDisableType {
        NONE,
        OTHER_PLAYER,
        SELF,
        MOBS,
        ALL
    }

    /** The settings the ported mixins read. */
    public final class Impl {
        // Mutable at runtime: the async ticking mixin switches this off if it detects trouble,
        // and that decision must stick for the session without rewriting the config file.
        private final Flag asynchronousTicking =
                new Flag(DutyConfig.get(ClientOptions.ASYNC_PARTICLE_TICKING));
        private final Flag disableParticles =
                new Flag(DutyConfig.get(ClientOptions.DISABLE_ALL_PARTICLES));

        private Impl() {}

        public Flag getAsynchronousTicking() {
            return asynchronousTicking;
        }

        public Flag getDisableParticles() {
            return disableParticles;
        }

        /** {@return the particle buffer cap, in particles per texture sheet} */
        public IntValue getMaxParticlesPerSheet() {
            return new IntValue(maxParticlesPerSheet);
        }

        /** Recomputes the particle render distance from the current video settings. */
        public void setupParticleViewDistance() {
            Minecraft client = Minecraft.getInstance();
            if (client.options == null) {
                return;
            }
            double chunks = client.options.renderDistance().get();
            double blocks = chunks * 16.0;
            renderDistanceSq = blocks * blocks;
        }

        /**
         * {@return whether a particle of this type should spawn at all}
         *
         * <p>The per-type spawn-chance map upstream supported is not carried over; it needed
         * fzzy-config's validated map types to be editable, and the blunt on/off switches below
         * cover what people actually reach for.
         */
        public boolean shouldSpawnParticle(ParticleType<?> type) {
            return !disableParticles.get();
        }

        /**
         * {@return the particle status to actually use}
         *
         * <p>Lets the "minimal" setting be forced regardless of the video option, which is the
         * cheapest single lever available on a struggling machine.
         */
        public ParticleStatus getReducedParticleSpawnType(ParticleStatus requested) {
            if (disableParticles.get()) {
                return ParticleStatus.MINIMAL;
            }
            if (forceMinimalParticles) {
                return ParticleStatus.MINIMAL;
            }
            return requested;
        }
    }

    /** A boolean that code can turn off at runtime, mirroring fzzy-config's ValidatedBoolean. */
    public static final class Flag {
        private final AtomicBoolean value;

        Flag(boolean initial) {
            this.value = new AtomicBoolean(initial);
        }

        public boolean get() {
            return value.get();
        }

        /** Sets the value for this session only; the config file is not rewritten. */
        public void validateAndSet(boolean newValue) {
            value.set(newValue);
        }
    }

    /** Read-only integer holder, mirroring fzzy-config's ValidatedInt. */
    public record IntValue(int value) {
        public int get() {
            return value;
        }
    }

    /** Adapts Duty's logger to the {@code error(String, Throwable)} shape the mixins call. */
    public static final class DutyLogAdapter {
        static final DutyLogAdapter INSTANCE = new DutyLogAdapter();

        private DutyLogAdapter() {}

        public void error(String message) {
            DutyLog.warn(message);
        }

        public void error(String message, Throwable throwable) {
            DutyLog.error(message, throwable);
        }
    }
}

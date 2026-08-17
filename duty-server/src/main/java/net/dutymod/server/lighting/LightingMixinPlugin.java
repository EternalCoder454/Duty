package net.dutymod.server.lighting;

import net.dutymod.framework.DutyLog;
import net.dutymod.framework.DutyMixinPlugin;
import net.dutymod.framework.platform.Platform;

/**
 * Decides whether the Starlight-derived light engine applies at all.
 *
 * <h2>Why this refuses rather than idles</h2>
 *
 * <p>These mixins do not tune vanilla's light engine, they replace it: {@code LevelLightEngineMixin}
 * swaps the implementation behind every lighting call. There is no meaningful "applied but
 * inactive" state, so the decision has to be made here, before anything is transformed. A second
 * light engine that applied and then tried to stand down would leave two implementations disagreeing
 * about the same nibble arrays, which shows up as corrupted lighting rather than as a conflict
 * message.
 *
 * <h2>C2ME is not in this list, deliberately</h2>
 *
 * <p>C2ME is the obvious candidate to stand down for, and it is the wrong answer. Its
 * {@code threading-lighting} module ships
 * {@code com.ishland.c2me.threading.lighting.mixin.scalablelux.MixinSchedulingUtil}, which targets
 * {@code ca.spottedleaf.starlight.common.thread.SchedulingUtil} by name: it overwrites
 * {@code isExternallyManaged} to return true and reroutes {@code scheduleTask} through C2ME's own
 * prioritised scheduler. In other words C2ME already knows about this engine and takes its
 * scheduling over. That is the integration working, not a conflict.
 *
 * <p>It composes at the mixin level too. C2ME's {@code MixinServerLightingProvider} is an
 * {@code @Inject} on {@code ThreadedLevelLightEngine}; this engine's mixin on the same class is two
 * {@code @WrapOperation}s around different call sites. Neither overwrites the other.
 *
 * <p>That integration is also the reason the engine keeps the package
 * {@code ca.spottedleaf.starlight} instead of being renamed into Duty's namespace like everything
 * else here. Mixin resolves targets by class name, so the rename would silently cost the C2ME
 * integration -- Duty would schedule lighting on its own threads while C2ME scheduled it on its
 * own, with nothing to say so.
 */
public final class LightingMixinPlugin extends DutyMixinPlugin {

    /**
     * Mods that bring their own light engine.
     *
     * <p>Standalone ScalableLux and Starlight are the same code this ships, so running both means
     * two copies competing for the same targets. Nothing else here replaces the engine.
     */
    private static final String[] COMPETING_ENGINES = {"scalablelux", "starlight", "phosphor"};

    private boolean applyMixins = true;

    @Override
    public void onLoad(String mixinPackage) {
        // Force registration now so the keys are in duty.properties before the first read.
        LightingOptions.init();

        if (!LightingOptions.enabled()) {
            applyMixins = false;
            DutyLog.info("Starlight light engine off by config (" + LightingOptions.ENABLED + ")");
            return;
        }

        for (String modId : COMPETING_ENGINES) {
            if (Platform.get().isModLoadedAtStartup(modId)) {
                applyMixins = false;
                DutyLog.warn("Standing down from the light engine: '" + modId + "' is installed and"
                        + " replaces it too. Remove one of the two.");
                return;
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return applyMixins;
    }
}

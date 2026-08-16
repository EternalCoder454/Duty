package net.dutymod.client;

import net.dutymod.client.ifast.injection.ImmediatelyFastMixinPlugin;
import net.dutymod.core.DutyConfig;
import net.dutymod.core.DutyLog;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gates Duty: Client's mixins on the config.
 *
 * <p>Replaces the ConditionalMixin library Particle Core used upstream. The behaviour is the same
 * -- a disabled feature is never patched in rather than patched and then skipped at runtime -- but
 * it reuses the plugin hook mixin already provides instead of adding a dependency for it.
 *
 * <p>That distinction matters for the culling mixins in particular: with culling switched off,
 * Duty should leave the render path completely untouched, so that turning it off is a real test of
 * whether Duty is responsible for a problem.
 */
public class DutyClientMixinPlugin implements IMixinConfigPlugin {

    /**
     * ImmediatelyFast's own config plugin, delegated to for its mixins.
     *
     * <p>Merging its mixins into Duty's shared config meant losing its plugin, and with it two
     * things: {@code onLoad} is where it loads its config, and {@code shouldApplyMixin} is where
     * each feature is gated by sub-package. Without the first, {@code ImmediatelyFast.config} is
     * null when {@code RenderSystem.initRenderer} fires and the game dies during startup.
     */
    private final ImmediatelyFastMixinPlugin ifast = new ImmediatelyFastMixinPlugin();

    /**
     * Stfu's own config plugin, delegated to for its mixins.
     *
     * <p>Its {@code onLoad} is empty, so nothing is lost there, but {@code shouldApplyMixin}
     * honours the {@code @DisableIf} annotations and the duty-stfu-disable.txt list. Dropping it
     * would silently re-enable mixins that are meant to stand down for a particular mod.
     */
    private final net.dutymod.client.stfu.MixinPlugin stfu = new net.dutymod.client.stfu.MixinPlugin();

    private static final String STFU_PACKAGE = "net.dutymod.client.mixin.stfu";

    /** The package ImmediatelyFast's mixins live under, which is what its plugin expects. */
    private static final String IFAST_PACKAGE = "net.dutymod.client.mixin.ifast";

    @Override
    public void onLoad(String mixinPackage) {
        ClientOptions.init();
        ifast.onLoad(IFAST_PACKAGE);
    }

    /**
     * Compat mixins keyed by the mod whose classes they touch.
     *
     * <p>These reference types that only exist when that mod is installed. Applying one without it
     * fails at class load with an error that points at Duty rather than at the missing mod, so the
     * presence check happens here instead.
     */
    private static final Map<String, String> COMPAT_MIXINS = Map.of(
            ".mixin.obe.renderer.compat.sodium.", "sodium",   // Embeddium handled below
            ".mixin.obe.blockentity.compat.lootr.", "lootr");

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        for (Map.Entry<String, String> compat : COMPAT_MIXINS.entrySet()) {
            boolean present = isModLoaded(compat.getValue())
                    // OBE's own plugin treated Embeddium as a Sodium equivalent here.
                    || ("sodium".equals(compat.getValue()) && isModLoaded("embeddium"));
            if (mixinClassName.contains(compat.getKey()) && !present) {
                DutyLog.debug("Skipping " + mixinClassName + ": " + compat.getValue() + " is not installed.");
                return false;
            }
        }
        if (mixinClassName.startsWith(IFAST_PACKAGE + ".")) {
            return ifast.shouldApplyMixin(targetClassName, mixinClassName);
        }
        if (mixinClassName.startsWith(STFU_PACKAGE + ".")
                && !stfu.shouldApplyMixin(targetClassName, mixinClassName)) {
            return false;
        }
        String key = keyFor(mixinClassName);
        if (key == null) {
            return true;
        }
        boolean enabled = DutyConfig.get(key);
        if (!enabled) {
            DutyLog.debug("Skipping " + mixinClassName + ": " + key + " is disabled.");
        }
        return enabled;
    }

    /**
     * {@return whether {@code modId} is in the mod list}
     *
     * <p>Read from the loading mod list rather than {@code ModList}, which does not exist yet when
     * mixin configs are evaluated.
     */
    private static boolean isModLoaded(String modId) {
        try {
            return net.neoforged.fml.loading.FMLLoader.getCurrent()
                    .getLoadingModList().getModFileById(modId) != null;
        } catch (Throwable t) {
            // If the mod list cannot be read, assume absent: skipping an optimization is
            // always recoverable, applying a mixin against missing classes is not.
            return false;
        }
    }

    /** {@return the config key gating this mixin, or null if it is always applied} */
    private static String keyFor(String mixinClassName) {
        if (mixinClassName.contains(".mixin.cull.")) {
            return ClientOptions.CULLING_ENABLED;
        }
        if (mixinClassName.contains(".mixin.particle.")) {
            // The accessors carry no behaviour of their own, but the mixins that do read through
            // them, so they stand or fall together.
            return ClientOptions.PARTICLE_OPTIMIZATIONS;
        }
        if (mixinClassName.contains(".mixin.rendercache.")) {
            // Gated as one unit on purpose: EntityRendererDispatcher casts to the duck interfaces
            // the type and entity mixins provide, so a partly applied group is a ClassCastException.
            return ClientOptions.RENDERER_CACHING;
        }
        if (mixinClassName.contains(".mixin.obe.")) {
            return ClientOptions.BAKED_BLOCK_ENTITIES;
        }
        return null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {}
}

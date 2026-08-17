package net.dutymod.client.hudcache;

import net.dutymod.client.ClientOptions;
import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;
import net.dutymod.framework.DutyMixinPlugin;
import net.dutymod.framework.platform.Platform;

/**
 * Decides whether the HUD cache applies at all.
 *
 * <h2>Why it refuses rather than idles</h2>
 *
 * <p>These mixins do not tune HUD drawing, they redirect it: the HUD is rendered into a framebuffer
 * and that framebuffer is blitted, with only part of the HUD refreshed each frame. There is no
 * useful "applied but inactive" state -- a half-installed indirection is how you get a black screen
 * rather than a slow one. So the decision is made here, before anything is transformed.
 *
 * <h2>Mods that do the same thing</h2>
 *
 * <p>Exordium is the same technique and the same framebuffer. Two mods caching the HUD into two
 * framebuffers and each blitting theirs does not produce a conflict message; it produces flicker,
 * or one of them winning at random per frame.
 */
public final class HudCacheMixinPlugin extends DutyMixinPlugin {

    /** Mods that also cache the HUD into a framebuffer. */
    private static final String[] COMPETING = {"exordium", "gnetum"};

    private boolean apply;

    @Override
    public void onLoad(String mixinPackage) {
        ClientOptions.init();
        apply = DutyConfig.get(ClientOptions.HUD_CACHE);

        if (!apply) {
            // Not a warning. Off is this option's default and the common case.
            DutyLog.debug("HUD cache off (" + ClientOptions.HUD_CACHE + ")");
            return;
        }

        for (String modId : COMPETING) {
            if (Platform.get().isModLoadedAtStartup(modId)) {
                apply = false;
                DutyLog.warn("Standing down from the HUD cache: '" + modId + "' is installed and "
                        + "caches the HUD too. Remove one of the two.");
                return;
            }
        }
    }

    /**
     * Compat mixins and the mod each one exists for.
     *
     * <p>These target classes that only exist when their mod does. Mixin already skips a mixin
     * whose target is absent, with a warning -- but a warning per absent mod, every launch, in a
     * pack that deliberately has none of them, is noise that trains people to ignore the log.
     * Asking first is quieter and says what happened.
     */
    private static final String[][] COMPAT = {
            {"compat.jade.", "jade"},
            {"compat.journeymap.", "journeymap"},
            {"compat.xaerominimap.", "xaerominimap"},
            {"compat.sodium.", "sodium"},
    };

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!apply) {
            return false;
        }
        for (String[] entry : COMPAT) {
            if (mixinClassName.contains(entry[0])) {
                return Platform.get().isModLoadedAtStartup(entry[1]);
            }
        }
        return true;
    }
}

package net.dutymod.client.quiet;

import net.dutymod.framework.DutyMixinPlugin;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;
import net.dutymod.client.quiet.config.QuietMixinConfig;


public class QuietMixinPlugin extends DutyMixinPlugin {
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (QuietMixinConfig.get(mixinClassName)) return false;

        try {
            DisableIf annotation = Annotations.getValue(Annotations.getVisible(MixinService.getService().getBytecodeProvider().getClassNode(mixinClassName), DisableIf.class));
            if (annotation != null) for (String mod : annotation.value()) if (isModLoaded(mod)) return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    /**
     * {@return whether {@code modId} is loaded}
     *
     * <p>Read from the loading mod list rather than ModList, which does not exist yet while
     * mixin configs are being evaluated.
     */
    private static boolean isModLoaded(String modId) {
        try {
            return net.neoforged.fml.loading.FMLLoader.getCurrent()
                    .getLoadingModList().getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;   // absent is the safe assumption: skip the mixin rather than misapply it
        }
    }

}

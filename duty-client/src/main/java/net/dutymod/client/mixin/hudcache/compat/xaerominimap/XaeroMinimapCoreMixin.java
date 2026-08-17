package net.dutymod.client.mixin.hudcache.compat.xaerominimap;

import net.dutymod.client.hudcache.Gnetum;
import net.dutymod.client.hudcache.compat.xaerominimap.XaeroMinimapCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xaero.common.core.XaeroMinimapCore", remap = false)
public class XaeroMinimapCoreMixin {
    //? if xaerominimap {
    /*@Inject(method = "beforeIngameGuiRender", at = @At("HEAD"))
    private static void gnetum$beforeIngameGuiRender(CallbackInfo ci) {
        //? fabric {
        /^if (Gnetum.config.isEnabled()) {
            XaeroMinimapCompat.update();
        }
        ^///? }
    }
    *///? }
}

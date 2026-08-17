package net.dutymod.client.mixin.hudcache.compat.xaerominimap;

import net.dutymod.client.hudcache.Gnetum;
import net.dutymod.client.hudcache.compat.xaerominimap.XaeroMinimapCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "xaero.common.events.ClientEvents", remap = false)
public class ClientEventsMixin {
    //? if xaerominimap {
    /*@Inject(method = "handleRenderGameOverlayEventPre", at = @At("HEAD"), cancellable = true)
    private static void gnetum$beforeIngameGuiRender(CallbackInfo ci) {
        if (Gnetum.rendering && !XaeroMinimapCompat.error && !XaeroMinimapCompat.shouldRenderWaypoint) {
            ci.cancel();
        }
    }
    *///? }
}

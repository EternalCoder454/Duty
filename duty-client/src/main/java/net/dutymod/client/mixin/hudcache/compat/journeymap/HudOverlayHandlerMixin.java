package net.dutymod.client.mixin.hudcache.compat.journeymap;

import net.dutymod.client.hudcache.Gnetum;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? journeymap && >=1.21.1 {
/*import journeymap.client.event.handlers.HudOverlayHandler;

@Pseudo
@Mixin(value = HudOverlayHandler.class, remap = false)
public class HudOverlayHandlerMixin {
	@Inject(method = "renderWaypointDecos", at = @At("HEAD"), cancellable = true)
	private void gnetum$cancelRenderWaypointDecos(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		if (Gnetum.rendering) {
			ci.cancel();
		}
	}
}
*///? } else {
@Pseudo
@Mixin(targets = {}, remap = false)
public class HudOverlayHandlerMixin {}
//? }
package net.dutymod.client.mixin.hudcache;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.mojang.blaze3d.systems.RenderSystem;
import net.dutymod.client.hudcache.CachedElement;
import net.dutymod.client.hudcache.Gnetum;
import net.dutymod.client.hudcache.VersionCompatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//$ import_delta_tracker
import net.minecraft.client.DeltaTracker;

//? >=26.2 {
/*import net.minecraft.client.gui.Hud;
*///? } else {
import net.minecraft.client.gui.Gui;
//? }
//? >=1.21.10 {
import net.dutymod.client.hudcache.versioned.StatefulHudHandler;
//?} else {
/*import static net.dutymod.client.hudcache.hud.SharedValues.deltaTracker;
import static net.dutymod.client.hudcache.hud.SharedValues.guiGraphics;
import net.dutymod.client.hudcache.versioned.HudHandler;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//? >=1.21.1
import net.minecraft.client.gui.LayeredDraw;
import java.util.function.Predicate;
*///?}
//? <=1.20.4 {
/*import net.dutymod.client.hudcache.compat.immediatelyfast.ImmediatelyFastCompat;
import net.dutymod.client.hudcache.hud.HudManager;
import net.dutymod.client.hudcache.hud.SharedValues;
*///? }
//? xaerominimap {
/*import net.dutymod.client.hudcache.compat.xaerominimap.XaeroMinimapCompat;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///? }

//? >=26.2 {
/*@Mixin(value = Hud.class, priority = 5000)
*///? } else {
@Mixin(value = Gui.class, priority = 5000)
//? }
public class HudMixin {
    //? if >=1.21.10 || <=1.20.4 {
	//? if >26 {
	@WrapMethod(method = "extractRenderState")
	//? } else {
	/*@WrapMethod(method = "render")
	*///? }
	private void gnetum$wrapGuiRender(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, Operation<Void> original) {
		if (!Gnetum.config.isEnabled()) {
			original.call(guiGraphics, deltaTracker);
			return;
		}

		//? >=26.2 {
		/*Gnetum.checkForPoseCatchUp(guiGraphics);
		 *///? }
		//? <=1.20.4 {
		/*SharedValues.guiGraphics = guiGraphics;
		SharedValues.deltaTracker = deltaTracker;
		gnetum$renderVanillaHuds(CachedElement::shouldRenderAsUncached);
		gnetum$renderFabricHuds(guiGraphics, deltaTracker);
		VersionCompatUtil.flush(guiGraphics);
		*///? }
		if (Gnetum.pass == 0) {
			VersionCompatUtil.profilerPush("sleep");
		} else {
			VersionCompatUtil.profilerPush("pass" + Gnetum.pass);
			Gnetum.framebuffers().resize();
			Gnetum.framebuffers().bind();
		}

		Gnetum.rendering = true;

		//? >=1.21.10
		original.call(guiGraphics, deltaTracker);
		//? <=1.20.4 {
		/*gnetum$renderVanillaHuds(CachedElement::shouldRenderAsCached);
		gnetum$renderFabricHuds(guiGraphics, deltaTracker);
		*///? }


		if (Gnetum.pass > 0) {
			VersionCompatUtil.flush(guiGraphics);
		}
		Gnetum.nextPass();
		Gnetum.framebuffers().unbind();

		Gnetum.rendering = false;

		//? xaerominimap {
		/*VersionCompatUtil.profilerPopPush("uncached");
		XaeroMinimapCompat.tryRenderWaypoint(guiGraphics, deltaTracker);
		*///? }
		if (Gnetum.framebuffers().needsCatchUp()) {
			//? >=1.21.10
			StatefulHudHandler.dropDeferredSubmission();
			original.call(guiGraphics, deltaTracker);
		} else {
			VersionCompatUtil.profilerPopPush("uncached");
			//? >=1.21.10
			StatefulHudHandler.performDeferredSubmission(guiGraphics);
			Gnetum.framebuffers().blit(guiGraphics);
		}
		VersionCompatUtil.profilerPop();

		//? <=1.20.4 {
		/*SharedValues.guiGraphics = null;
		*///? }
	}
	//? } else >=1.21.1 {
    /*@SuppressWarnings({"MixinAnnotationTarget", "InvalidInjectorMethodSignature"})
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback;onHudRender(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", remap = false), require = 0, expect = 0)
    private boolean gnetum$renderInjection$fabric(HudRenderCallback instance, GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        return !Gnetum.renderingGuiInjection;
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LayeredDraw;render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
    private boolean gnetum$renderInjection(LayeredDraw instance, GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
        return !Gnetum.renderingGuiInjection;
    }

    //? 1.21.1 {
    /^@Inject(method = "<init>", at = @At("TAIL"))
    private void gnetum$init(Minecraft minecraft, CallbackInfo ci, @Local(ordinal = 0) LayeredDraw layeredDraw) {
        // Some mods (e.g. InventoryHUD) adds their HUDs to the LayeredDraw, detect them for compat
        for (LayeredDraw.Layer layer : ((LayeredDrawAccessor)layeredDraw).getLayers()) {
            var clazz = layer.getClass();
            if (!clazz.getName().startsWith("net.minecraft")) {
                HudHandler.unknownElements.add(() -> layer.render(guiGraphics(), deltaTracker()));
            }
        }
    }
    ^///? }
    *///? }

	//? >=1.21.10 {
	//? >26 {
	@Inject(method = "extractRenderState", at = @At("RETURN"), order = 200, cancellable = true)
	//? } else {
	/*@Inject(method = "render", at = @At("RETURN"), order = 200, cancellable = true)
	*///? }
	private void gnetum$render$tail(CallbackInfo ci) {
		// If we cache these unknown elements, make sure they only render once through all passes
		if (!Gnetum.rendering || Gnetum.pass == Gnetum.config.getNumberOfPasses()) {
			return;
		}
		if (!Gnetum.getUnknownElement().isUncached()) {
			ci.cancel();
		}
	}
	//? }

	//? <=1.20.4 {
	/*@Unique
	private void gnetum$renderVanillaHuds(Predicate<CachedElement> check) {
		for (int i = 0; i < HudManager.huds.size(); i++) {
			var hud = HudManager.huds.get(i);
			var element = Gnetum.getElement(hud.id());
			if (hud.isDummy() || check.test(element)) {
				if (Gnetum.rendering) {
					element.begin();
					hud.render();
					ImmediatelyFastCompat.flushIfInstalledAndUsingHudBatching(guiGraphics); // Duplicates ImmediatelyFast behavior: https://github.com/RaphiMC/ImmediatelyFast/blob/e05390bbc2c2bdc3d19cad458d894dc4f605d3fb/common/src/main/java/net/raphimc/immediatelyfast/injection/mixins/hud_batching/MixinLayeredDrawer.java#L32-L37
					element.end();
				}
				else {
					hud.render();
				}
				RenderSystem.enableDepthTest(); // Fixes GL state leak by some mods
				guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
			}
		}
	}

	@Unique
	private void gnetum$renderFabricHuds(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker) {
		HudRenderCallback.EVENT.invoker().onHudRender(guiGraphics, deltaTracker);
	}
	*///? }

    //? >=1.21.11 {
	//? >=26 {
	@Inject(method = "extractSubtitleOverlay", at = @At("HEAD"))
	//? } else {
	/*@Inject(method = "renderSubtitleOverlay", at = @At("HEAD"))
	*///? }
	private void gnetum$doNotDeferSubtitles(GuiGraphicsExtractor guiGraphics, boolean bl, CallbackInfo ci, @Local(argsOnly = true)LocalBooleanRef defer) {
		defer.set(false);
	}
    //? }
}

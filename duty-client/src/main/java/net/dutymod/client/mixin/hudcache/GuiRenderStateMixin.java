package net.dutymod.client.mixin.hudcache;

//? >=1.21.10 {
import com.mojang.blaze3d.pipeline.BlendFunction;
//$ import_blend_factors
import com.mojang.blaze3d.platform.DestFactor; import com.mojang.blaze3d.platform.SourceFactor;
import net.dutymod.client.hudcache.Gnetum;
import net.dutymod.client.hudcache.versioned.StatefulHudHandler;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
//? >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///? }
//? >26 {
import com.mojang.blaze3d.pipeline.ColorTargetState;
//? }

@Mixin(GuiRenderState.class)
public class GuiRenderStateMixin {
	@Unique
	private void gnetum$submit(ScreenArea state, CallbackInfo ci) {
		if (!Gnetum.rendering) {
			return;
		}
		if (Gnetum.isCurrentElementUncached()) {
			gnetum$submitForUncached(state);
			ci.cancel();
		}
	}

	@Inject(method = "addText", at = @At("HEAD"), cancellable = true)
	private void gnetum$addText(GuiTextRenderState state, CallbackInfo ci) {
		gnetum$submit(state, ci);
	}

	@Inject(method = "addPicturesInPictureState", at = @At("HEAD"), cancellable = true)
	private void gnetum$submitPIP(PictureInPictureRenderState state, CallbackInfo ci) {
		gnetum$submit(state, ci);
	}

	@Inject(method = "addItem", at = @At("HEAD"), cancellable = true)
	private void gnetum$addItem(GuiItemRenderState state, CallbackInfo ci) {
		gnetum$submit(state, ci);
	}

	@Inject(method = "addGuiElement", at = @At("HEAD"), cancellable = true)
	private void gnetum$addGuiElement(GuiElementRenderState state, CallbackInfo ci) {
		if (!Gnetum.rendering) {
			return;
		}
		if (Gnetum.isCurrentElementUncached()) {
			gnetum$submitForUncached(state);
			ci.cancel();
			return;
		}
		var pipeline = state.pipeline();
		//? >26 {
		var colorTargetState = pipeline.getColorTargetState();
		if (colorTargetState == null || colorTargetState.blendFunction().isEmpty()) {
			return;
		}
		var blend = colorTargetState.blendFunction().get();
		//? } else {
		/*var optionalBlend = pipeline.getBlendFunction();
		if (optionalBlend.isEmpty()) {
			return;
		}
		var blend = optionalBlend.get();
		*///? }
		var pipelineAccessor = (RenderPipelineAccessor) pipeline;
		if (blend.sourceAlpha() != /*$src_factor ONE*/ SourceFactor.ONE
				|| blend.destAlpha() != /*$dest_factor ONE_MINUS_SRC_ALPHA*/ DestFactor.ONE_MINUS_SRC_ALPHA
		) {
			// TODO: optimize alloc
			blend = new BlendFunction(blend.sourceColor(), blend.destColor(),
					/*$src_factor ONE*/ SourceFactor.ONE
					,/*$dest_factor ONE_MINUS_SRC_ALPHA*/ DestFactor.ONE_MINUS_SRC_ALPHA
			);
			//? >=26.2 {
			/*pipelineAccessor.setColorTargetStates(new ColorTargetState[] { new ColorTargetState(Optional.of(blend), GpuFormat.RGBA8_UNORM, pipeline.getColorTargetState().writeMask()) } );
			*///? } else >26 {
			pipelineAccessor.setColorTargetState(new ColorTargetState(Optional.of(blend), pipeline.getColorTargetState().writeMask()));
			//? } else {
			/*pipelineAccessor.setBlendFunction(Optional.of(blend));
			*///? }
		}
		if (gnetum$isBlendIncompatible(blend) && !Gnetum.isCurrentElementForceCached()) {
			Gnetum.disableCachingForCurrentElement("Blend Func (%s, %s, %s, %s)".formatted(blend.sourceColor(), blend.destColor(), blend.sourceAlpha(), blend.destAlpha()));
		}
	}

	@Unique
	private void gnetum$submitForUncached(ScreenArea state) {
		StatefulHudHandler.submitLater(state);
	}

	@Unique
	private boolean gnetum$isBlendIncompatible(BlendFunction blend) {
		if (gnetum$isUsingDestColor(blend.sourceColor()) || gnetum$isUsingDestColor(blend.destColor())) return true;
		if (blend.destColor() ==
				/*$dest_factor SRC_COLOR*/ DestFactor.SRC_COLOR
				|| blend.destColor() ==
				/*$dest_factor ONE_MINUS_SRC_COLOR*/ DestFactor.ONE_MINUS_SRC_COLOR
		) {
			return true;
		}
		if (blend.sourceColor() ==
				/*$src_factor ONE*/ SourceFactor.ONE
				&& blend.destColor() ==
				/*$dest_factor ONE*/ DestFactor.ONE
		) {
			return true;
		}
		return false;
	}

	//? >=26.2 {
	/*@Unique
	private static boolean gnetum$isUsingDestColor(BlendFactor factor) {
		return factor == BlendFactor.DST_COLOR || factor == BlendFactor.ONE_MINUS_DST_COLOR;
	}
	*///? } else {
	@Unique
	private static boolean gnetum$isUsingDestColor(SourceFactor factor) {
		return factor == SourceFactor.DST_COLOR || factor == SourceFactor.ONE_MINUS_DST_COLOR;
	}

	@Unique
	private static boolean gnetum$isUsingDestColor(DestFactor factor) {
		return factor == DestFactor.DST_COLOR || factor == DestFactor.ONE_MINUS_DST_COLOR;
	}
	//? }
}
//?} else {

/*import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = {})
public class GuiRenderStateMixin { }
*///?}

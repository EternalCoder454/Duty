package net.dutymod.client.mixin.stfu.RemoveOverlay;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.dutymod.client.stfu.config.Config;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Mojang splash screen: skip it, and stop it holding the game paused while it fades.
 *
 * <p>This backs {@code client.disable_splash} and {@code client.disable_fade}. Both options were
 * registered and read into {@link Config} during the Stfu port, but this mixin was never brought
 * across, so neither did anything -- and {@code disable_splash} defaults to on, so the config file
 * advertised a feature that was switched on and inert.
 *
 * <p>Three differences from upstream, all forced by 26.1.2:
 *
 * <ul>
 *   <li>{@code LoadingOverlay.render} no longer exists; only {@code extractRenderState} does, so
 *       the constant modifier and both operation wrappers target that alone. Upstream lists both
 *       names, which resolves on older versions and silently half-applies here.
 *   <li>Upstream's {@code isReadyToFadeOut} injection returns {@code true} unconditionally, with no
 *       config check at all. Duty gates it on {@code disableSplash}: an ungated behaviour change
 *       cannot be turned off, and this one shortens the window the overlay stays up.
 *   <li>The {@code blit} overload taken here is the eleven-argument one that
 *       {@code extractRenderState} actually calls -- {@code GuiGraphicsExtractor} has several, and
 *       picking by name alone lands on the wrong one.
 * </ul>
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    @Final
    private boolean fadeIn;

    /** The fade curve divides progress by 2; flattening it to 1 removes the transition. */
    @ModifyConstant(method = "extractRenderState", constant = @Constant(floatValue = 2.0F))
    private float duty$disableFade(float progress) {
        return Config.get().disableFade ? 1F : progress;
    }

    @Inject(method = "isPauseScreen", at = @At("HEAD"), cancellable = true)
    private void duty$doNotPause(CallbackInfoReturnable<Boolean> cir) {
        if (Config.get().disableSplash && fadeIn) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Vanilla keeps the overlay up for a grace period before it is allowed to fade. When the splash
     * is being skipped there is nothing to wait for.
     */
    @Inject(method = "isReadyToFadeOut", at = @At("HEAD"), cancellable = true)
    private void duty$skipGracePeriod(CallbackInfoReturnable<Boolean> cir) {
        if (Config.get().disableSplash) {
            cir.setReturnValue(true);
        }
    }

    @WrapOperation(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V")
    )
    private void duty$skipBackground(GuiGraphicsExtractor extractor, int x0, int y0, int x1, int y1,
                                     int colour, Operation<Void> original) {
        if (!Config.get().disableSplash || !fadeIn) {
            original.call(extractor, x0, y0, x1, y1, colour);
        }
    }

    @WrapOperation(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"
            )
    )
    private void duty$skipLogo(GuiGraphicsExtractor extractor, RenderPipeline pipeline, Identifier texture,
                               int x, int y, float u, float v, int width, int height,
                               int srcWidth, int srcHeight, int textureWidth, int textureHeight,
                               int colour, Operation<Void> original) {
        if (!Config.get().disableSplash || !fadeIn) {
            original.call(extractor, pipeline, texture, x, y, u, v, width, height,
                    srcWidth, srcHeight, textureWidth, textureHeight, colour);
        }
    }
}

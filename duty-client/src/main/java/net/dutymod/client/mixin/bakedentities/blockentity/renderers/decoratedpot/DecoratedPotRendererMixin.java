package net.dutymod.client.mixin.bakedentities.blockentity.renderers.decoratedpot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.dutymod.client.bakedentities.config.SettingsManager;
import net.dutymod.client.bakedentities.renderer.blockentity.ext.BlockEntityRenderStateExt;
import net.dutymod.client.bakedentities.renderer.blockentity.misc.RenderModeManager;
import net.minecraft.client.renderer.blockentity.DecoratedPotRenderer;
import net.minecraft.client.renderer.blockentity.state.DecoratedPotRenderState;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;

@Mixin(DecoratedPotRenderer.class)
public abstract class DecoratedPotRendererMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    public void obe$cancelSubmit(CallbackInfo ci, @Local DecoratedPotRenderState state){
        if(!RenderModeManager.shouldRenderEntity(state) && SettingsManager.OPTIMISED_DECORATED_POTS.getValue()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    public void obe$cancelExtract(CallbackInfo ci, @Local DecoratedPotRenderState state, @Local DecoratedPotBlockEntity be){
        ((BlockEntityRenderStateExt)state).blockEntity(be);
        if(!RenderModeManager.shouldRenderEntity(be) && SettingsManager.OPTIMISED_DECORATED_POTS.getValue()) ci.cancel();
    }
}

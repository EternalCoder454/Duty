package net.dutymod.client.mixin.bakedentities.renderer.compat.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.sugar.Local;

import net.dutymod.client.bakedentities.renderer.blockentity.ext.BlockEntityExt;
import net.dutymod.client.bakedentities.renderer.blockentity.misc.RenderModeManager;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

@Pseudo
@Mixin(value = SodiumWorldRenderer.class, remap = false)
public class SodiumWorldRendererMixin {

    @Inject(method = "extractBlockEntity", at = @At("HEAD"), cancellable = true)
    public void obe$preventUselessExtraction(CallbackInfo ci, @Local BlockEntity be){
        if (be instanceof BlockEntityExt ext) {
            if(ext.isEnabled() && (!RenderModeManager.shouldRenderEntityFast(ext) || ext.shouldSkipBeRendering())){
                ci.cancel();
            }
            else{
                RenderModeManager.updateOnRender(ext);
            }
        }
    }
}

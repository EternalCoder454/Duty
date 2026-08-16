package net.dutymod.client.mixin.obe.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dutymod.client.obe.util.ResourceUtil;

import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.resources.model.ModelManager;

@Mixin(ModelManager.class)
public class ModelManagerMixin {

    @Inject(
        method = "apply",
        at = @At("TAIL")
    )
    private void obe$onTextureReloadComplete(CallbackInfo ci) {
        ResourceUtil.clearCache();
    }
}

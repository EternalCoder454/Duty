package net.dutymod.fixerupper.common.mixin.feature.measure_time;

import net.minecraft.client.Minecraft;
import net.dutymod.fixerupper.ModernFixClient;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin_Forge {
    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void recordWorldLoadStart(CallbackInfo ci) {
        ModernFixClient.worldLoadStartTime = System.nanoTime();
    }
}


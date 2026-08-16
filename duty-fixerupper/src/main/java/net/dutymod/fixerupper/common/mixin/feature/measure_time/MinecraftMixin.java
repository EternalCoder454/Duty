package net.dutymod.fixerupper.common.mixin.feature.measure_time;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.dutymod.fixerupper.FixerUpperClient;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Minecraft.class)
@ClientOnlyMixin
public class MinecraftMixin {
    // TODO re-add datapack reload time measurement
    @Shadow @Nullable public Overlay overlay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onClientTick(CallbackInfo ci) {
        if(this.overlay == null && FixerUpperClient.INSTANCE != null) {
            FixerUpperClient.INSTANCE.onGameLaunchFinish();
        }
    }

    @Inject(method = "doWorldLoad", at = @At("HEAD"))
    private void recordWorldLoadStart(CallbackInfo ci) {
        FixerUpperClient.worldLoadStartTime = System.nanoTime();
    }
}

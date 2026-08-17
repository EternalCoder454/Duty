package net.dutymod.client.mixin.quiet.UnfocusedVolumeReducer;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.dutymod.client.quiet.Quiet.client;

@Mixin(Window.class)
public class WindowActiveListenerMixin {
    @Inject(method = "onFocus", at = @At("TAIL"))
    private void onFocus(long handle, boolean focused, CallbackInfo ci) {
        client().getSoundManager().refreshCategoryVolume(SoundSource.MASTER);
    }
}

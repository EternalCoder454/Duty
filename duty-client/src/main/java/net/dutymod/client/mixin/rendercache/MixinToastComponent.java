package net.dutymod.client.mixin.rendercache;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.ToastManager.ToastInstance;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Deque;
import java.util.List;
import java.util.Set;

// 26.1.2 renamed ToastManager.render to extractRenderState, part of the render/extract
// split across the 26.x GUI. check-descriptors.py caught the stale name; the hideGui read
// the injection point needs was verified to live in the new method.
@Mixin(ToastManager.class)
public abstract class MixinToastComponent {
	@Shadow @Final private List<Object> visibleToasts;
	@Shadow @Final private Deque<Toast> queued;
	@Shadow @Final private Set<SoundEvent> playedToastSounds;
	@Shadow private ToastInstance<NowPlayingToast> nowPlayingToast;

	// Don't do anything if we don't need to
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z"))
	private boolean shouldSkipDraw(boolean hudHidden) {
		if(hudHidden) return true;

		return visibleToasts.isEmpty()
				&& queued.isEmpty()
				&& playedToastSounds.isEmpty()
				&& nowPlayingToast == null;
	}
}
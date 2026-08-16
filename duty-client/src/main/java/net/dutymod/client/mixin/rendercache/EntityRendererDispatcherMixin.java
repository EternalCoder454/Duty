package net.dutymod.client.mixin.rendercache;

import net.dutymod.client.rendercache.EntityMethods;
import net.dutymod.client.rendercache.EntityTypeMethods;
import net.dutymod.client.rendercache.PlayerModelRendererHolder;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Map.Entry;

@Mixin(value = EntityRenderDispatcher.class, priority = 700)
public abstract class EntityRendererDispatcherMixin {
	@Shadow private Map<EntityType<?>, EntityRenderer<?, ?>> renderers;
	@Shadow private Map<PlayerModelType, EntityRenderer<? extends Player, ?>> mannequinRenderers;

	@Overwrite
	public <T extends Entity & EntityMethods> EntityRenderer<? super T, ?> getRenderer(T entity) {
		var renderer = entity.duty$getRenderer();
		if(renderer != null) {
			return renderer;
		} else {
			return duty$getOtherRenderer(entity);
		}
	}

	private <T extends Entity & EntityMethods> EntityRenderer<? super T, ?> duty$getOtherRenderer(T entity) {
		// some mods inject renderers late, or add custom unsupported player models
		if(entity instanceof ClientAvatarEntity player) {
			var renderer = mannequinRenderers.get(player.getSkin().model());
			if(renderer != null) {
				return (EntityRenderer<? super T, ?>) renderer;
			} else {
				return (EntityRenderer<? super T, ?>) this.mannequinRenderers.get(PlayerModelType.WIDE);
			}
		} else {
			return (EntityRenderer<? super T, ?>) this.renderers.get(entity.getType());
		}
	}

	@Inject(method = "onResourceManagerReload", at = @At("RETURN"))
	private void afterReload(ResourceManager manager, CallbackInfo ci) {
		for(Entry<EntityType<?>, EntityRenderer<?, ?>> entry : renderers.entrySet()) {
			((EntityTypeMethods) entry.getKey()).duty$setRenderer(entry.getValue());
		}

		// Used by ClientPlayerMixin
		PlayerModelRendererHolder.WIDE_RENDERER = mannequinRenderers.get(PlayerModelType.WIDE);
		PlayerModelRendererHolder.SLIM_RENDERER = mannequinRenderers.get(PlayerModelType.SLIM);
	}
}
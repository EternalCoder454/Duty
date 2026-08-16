package net.dutymod.client.mixin.rendercache;

import net.dutymod.client.rendercache.EntityMethods;
import net.dutymod.client.rendercache.EntityTypeMethods;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity implements EntityMethods {
	private EntityTypeMethods duty$typeMethods;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void afterInit(EntityType<?> type, Level world, CallbackInfo ci) {
		this.duty$typeMethods = (EntityTypeMethods) type;
	}

	@Override
	@SuppressWarnings("unchecked")
	public EntityRenderer<?, ?> duty$getRenderer() {
		return duty$typeMethods.duty$getRenderer();
	}
}
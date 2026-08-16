package net.dutymod.client.mixin.rendercache;

import net.dutymod.client.rendercache.EntityTypeMethods;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityType.class)
public class EntityTypeMixin implements EntityTypeMethods {
	private EntityRenderer<?, ?> duty$renderer;

	@Override
	public EntityRenderer<?, ?> duty$getRenderer() {
		return duty$renderer;
	}

	@Override
	public void duty$setRenderer(EntityRenderer<?, ?> renderer) {
		this.duty$renderer = renderer;
	}
}
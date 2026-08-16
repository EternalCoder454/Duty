package net.dutymod.client.rendercache;

import net.minecraft.client.renderer.entity.EntityRenderer;

public interface EntityTypeMethods {
	EntityRenderer<?, ?> duty$getRenderer();
	void duty$setRenderer(EntityRenderer<?, ?> renderer);
}
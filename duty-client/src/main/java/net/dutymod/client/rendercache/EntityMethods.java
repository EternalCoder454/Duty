package net.dutymod.client.rendercache;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

public interface EntityMethods {
	void duty$refreshEntityData(int data);

	<T extends Entity> EntityRenderer<T, ?> duty$getRenderer();
}
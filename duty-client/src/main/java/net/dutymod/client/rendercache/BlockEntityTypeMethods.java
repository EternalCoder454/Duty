package net.dutymod.client.rendercache;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface BlockEntityTypeMethods {
	<T extends BlockEntity> BlockEntityRenderer<T, ?> duty$getRenderer();
	void duty$setRenderer(BlockEntityRenderer<?, ?> renderer);
}
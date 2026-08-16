package net.dutymod.client.mixin.rendercache;

import net.dutymod.client.rendercache.BlockEntityTypeMethods;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityType.class)
public class MixinBlockEntityType implements BlockEntityTypeMethods {
	private BlockEntityRenderer<?, ?> duty$renderer;

	@Override
	@SuppressWarnings("unchecked")
	public BlockEntityRenderer<?, ?> duty$getRenderer() {
		return duty$renderer;
	}

	@Override
	public void duty$setRenderer(BlockEntityRenderer<?, ?> renderer) {
		this.duty$renderer = renderer;
	}
}
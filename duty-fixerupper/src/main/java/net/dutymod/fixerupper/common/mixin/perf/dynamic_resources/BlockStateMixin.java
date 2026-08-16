package net.dutymod.fixerupper.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import net.dutymod.fixerupper.duck.IModelHoldingBlockState;
import org.spongepowered.asm.mixin.Mixin;

import java.lang.ref.SoftReference;

@Mixin(BlockBehaviour.BlockStateBase.class)
@ClientOnlyMixin
public class BlockStateMixin implements IModelHoldingBlockState {
    private volatile SoftReference<BlockStateModel> duty$model;

    @Override
    public BlockStateModel duty$getModel() {
        var ref = duty$model;
        return ref != null ? ref.get() : null;
    }

    @Override
    public void duty$setModel(BlockStateModel model) {
        duty$model = model != null ? new SoftReference<>(model) : null;
    }
}

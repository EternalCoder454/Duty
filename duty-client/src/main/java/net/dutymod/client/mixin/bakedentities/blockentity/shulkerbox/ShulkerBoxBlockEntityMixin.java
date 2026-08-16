package net.dutymod.client.mixin.bakedentities.blockentity.shulkerbox;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dutymod.client.bakedentities.registry.Registry;
import net.dutymod.client.bakedentities.renderer.blockentity.ext.BlockEntityExt;
import net.dutymod.client.bakedentities.renderer.blockentity.misc.RenderModeManager;
import net.dutymod.client.bakedentities.renderer.blockentity.misc.RenderModeManager.RenderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(ShulkerBoxBlockEntity.class)
public class ShulkerBoxBlockEntityMixin{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void obe$init(CallbackInfo ci) {
        
        BlockEntity be = (BlockEntity)(Object)this;
        BlockEntityExt ext = (BlockEntityExt)be;
        
        ext.isSupportedBlockEntity(Registry.isSupported("shulker_box", be.getType()));
    }

    @Inject(method = "updateAnimation", at = @At("HEAD"))
    public void obe$updateAnimation(final Level level, final BlockPos pos, final BlockState blockState, CallbackInfo ci){
        ShulkerBoxBlockEntity be = (ShulkerBoxBlockEntity) (Object) this;
        if(be.getAnimationStatus() != AnimationStatus.CLOSED){
            RenderModeManager.setRenderModeDelayed(be, RenderMode.ENTITY, be.getBlockPos());
        }
        else{
            RenderModeManager.setRenderModeDelayed(be, RenderMode.TERRAIN, be.getBlockPos());
        }
    }
}

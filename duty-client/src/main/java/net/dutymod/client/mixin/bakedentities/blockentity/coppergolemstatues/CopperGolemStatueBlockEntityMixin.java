package net.dutymod.client.mixin.bakedentities.blockentity.coppergolemstatues;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dutymod.client.bakedentities.registry.Registry;
import net.dutymod.client.bakedentities.renderer.blockentity.ext.BlockEntityExt;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CopperGolemStatueBlockEntity;

@Mixin(CopperGolemStatueBlockEntity.class)
public abstract class CopperGolemStatueBlockEntityMixin{
    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(CallbackInfo ci) {
        
        BlockEntity be = (BlockEntity)(Object)this;
        BlockEntityExt ext = (BlockEntityExt)be;
        
        ext.isSupportedBlockEntity(Registry.isSupported("copper_golem_statue", be.getType()));
    }
}

package net.dutymod.client.mixin.obe.blockentity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dutymod.client.obe.config.SettingsManager;
import net.dutymod.client.obe.renderer.blockentity.ext.BlockEntityExt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

@Mixin(BlockEntity.class)
public class BlockEntityMixin {

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void obe$onSetBlockState(BlockState newBlockState, CallbackInfo ci) {
        if (!Minecraft.getInstance().isSameThread()) return;
        if (((BlockEntity)(Object)this) instanceof LecternBlockEntity be) {
            BlockEntityExt ext = (BlockEntityExt) be;
            
            boolean hasBook = newBlockState != null && newBlockState.getValueOrElse(LecternBlock.HAS_BOOK, true);

            ext.shouldSkipBeRendering(!hasBook && SettingsManager.OPTIMISED_LECTERNS.getValue() &&  SettingsManager.MOD_TOGGLE.getValue());
        }
    }
}

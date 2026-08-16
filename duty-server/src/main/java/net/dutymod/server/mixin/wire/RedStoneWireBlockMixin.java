package net.dutymod.server.mixin.wire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.dutymod.server.wire.AlternateCurrent;
import net.dutymod.server.wire.interfaces.IServerLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

@Mixin(RedStoneWireBlock.class)
public class RedStoneWireBlockMixin {

	@Inject(
		method = "updatePowerStrength",
		cancellable = true,
		at = @At(
			value = "HEAD"
		)
	)
	private void alternate_current$onUpdate(Level level, BlockPos pos, BlockState state, Orientation orientation, boolean added, CallbackInfo ci) {
		if (AlternateCurrent.on) {
			// Using redirects for calls to this method makes conflicts with
			// other mods more likely, so we inject-cancel instead.
			ci.cancel();
		}
	}

	@Inject(
		method = "onPlace",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;updatePowerStrength(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/redstone/Orientation;Z)V"
		)
	)
	private void alternate_current$onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston, CallbackInfo ci) {
		if (AlternateCurrent.on) {
			((IServerLevel)level).alternate_current$getWireHandler().onWireAdded(pos, state);
		}
	}

	@Inject(
		method = "affectNeighborsAfterRemoval",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/block/RedStoneWireBlock;updatePowerStrength(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/redstone/Orientation;Z)V"
		)
	)
	private void alternate_current$onRemove(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston, CallbackInfo ci) {
		if (AlternateCurrent.on) {
			((IServerLevel)level).alternate_current$getWireHandler().onWireRemoved(pos, state);
		}
	}

	@Inject(
		method = "neighborChanged",
		cancellable = true,
		at = @At(
			value = "HEAD"
		)
	)
	private void alternate_current$onNeighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, Orientation orientation, boolean movedByPiston, CallbackInfo ci) {
		if (AlternateCurrent.on) {
			if (((IServerLevel)level).alternate_current$getWireHandler().onWireUpdated(pos, state, orientation)) {
				ci.cancel(); // needed to fix duplication bugs
			}
		}
	}
}

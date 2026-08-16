package net.dutymod.server.mixin.wire;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.dutymod.server.wire.AlternateCurrent;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;

@Mixin(ExperimentalRedstoneUtils.class)
public class ExperimentalRedstoneUtilsMixin {

	@Inject(
		method = "initialOrientation",
		cancellable = true,
		at = @At(
			value = "RETURN",
			ordinal = 0
		)
	)
	private static void noRandomOrientation(Level level, Direction front, Direction up, CallbackInfoReturnable<Orientation> cir) {
		// if the given front is null, a random front is chosen - use default value instead
		if (AlternateCurrent.on && front == null) {
			cir.setReturnValue(cir.getReturnValue().withFront(Direction.WEST));
		}
	}
}

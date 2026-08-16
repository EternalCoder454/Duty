package net.dutymod.client.mixin.particle;

import net.dutymod.client.particle.PcConfig;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = ParticleGroup.class, priority = 100000)
public class ParticleGroupCountMixin {

	@ModifyConstant(method = "<init>", constant = @Constant(intValue = 16384))
	private int duty$modifyParticleMaxCount(int original) {
		return PcConfig.INSTANCE.getImpl().getMaxParticlesPerSheet().get();
	}
}
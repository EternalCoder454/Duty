package net.dutymod.client.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dutymod.client.particle.interfaces.CachedLightPreparer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ParticleGroup.class)
public class ParticleGroupBrightnessTickMixin {

	@WrapOperation(method = "tickParticle", at = @At(value = "INVOKE", target = "net/minecraft/client/particle/Particle.tick ()V"))
	private void duty$tickParticleLightUpdates(Particle instance, Operation<Void> original) {
		((CachedLightPreparer) instance).duty$tickLightUpdate();
		original.call(instance);
	}

}
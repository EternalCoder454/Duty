package net.dutymod.client.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dutymod.client.particle.interfaces.ParticleCreator;
import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireworkParticles.Starter.class)
public class FireworksSparkParticleMixin {

	@WrapOperation(method = "createParticle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"))
	private Particle duty$handleParticleNullability(ParticleEngine instance, ParticleOptions parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Operation<Particle> original) {
		Particle particle = ((ParticleCreator)instance).duty$createSafe(parameters, x, y, z, velocityX, velocityY, velocityZ);
		instance.add(particle);
		return particle;
	}
}
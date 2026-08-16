package net.dutymod.client.mixin.particle;

import net.dutymod.client.particle.interfaces.ParticleCreator;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineCreatorMixin implements ParticleCreator {

	@Invoker("makeParticle")
	public abstract <T extends ParticleOptions> Particle duty$makeParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);

	@Override
	public <T extends ParticleOptions> Particle duty$createSafe(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
		return this.duty$makeParticle(parameters, x, y, z, velocityX, velocityY, velocityZ);
	}
}
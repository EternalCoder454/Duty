package net.dutymod.client.particle.interfaces;

import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;

public interface ParticleCreator {
	<T extends ParticleOptions> Particle duty$createSafe(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ);
}
package net.dutymod.client.mixin.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ParticleGroup.class)
public interface ParticleGroupAccessor {
	@Invoker
	void callTickParticle(Particle particle);
}
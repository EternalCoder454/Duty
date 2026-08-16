package net.dutymod.client.mixin.particle;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.dutymod.client.particle.PcConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.QuadParticleGroup;
import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(QuadParticleGroup.class)
public abstract class ParticleGroupRenderDistanceMixin {

	@WrapWithCondition(method = "extractRenderState", at = @At(value = "INVOKE", target = "net/minecraft/client/particle/SingleQuadParticle.extract (Lnet/minecraft/client/renderer/state/level/QuadParticleRenderState;Lnet/minecraft/client/Camera;F)V"))
	private boolean duty$buildGeoIfWithinRenderDistance(SingleQuadParticle instance, net.minecraft.client.renderer.state.level.QuadParticleRenderState particleTypeRenderState, Camera camera, float partialTickTime) {
		return PcConfig.INSTANCE.shouldRenderParticle(
				((ParticleAccessor)instance).getX(),
				((ParticleAccessor)instance).getY(),
				((ParticleAccessor)instance).getZ(),
				camera.position()
		);
	}

}
package net.dutymod.client.mixin.rendercache;

import net.dutymod.client.mixin.rendercache.GameRendererAccessor;
import net.dutymod.client.mixin.rendercache.PlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EndFlashState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapExtractorMixin {
	@Shadow @Final private Minecraft minecraft;

	private EnvironmentAttributeProbe duty$probe;
	private GameRendererAccessor duty$gameRendererAccessor;

	private int duty$lastSkyColor;
	private float duty$lastSkyFactor;

	private float duty$lastEndFactor = 0f;
	private double duty$lastGamma;
	private DimensionType duty$lastDimension;
	private boolean duty$lastNightVision;
	private boolean duty$lastConduitPower;

	private float duty$previousSkyDarkness;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void onInit(GameRenderer renderer, Minecraft client, CallbackInfo ci) {
		this.duty$gameRendererAccessor = (GameRendererAccessor) renderer;
		this.duty$probe = renderer.getMainCamera().attributeProbe();
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void onTick(CallbackInfo ci) {
		if(minecraft.player == null) return;

		if(!this.duty$isDirty()) {
			ci.cancel();
		}
	}

	private boolean duty$isDirty() {
		int skyColor = duty$probe.getValue(EnvironmentAttributes.SKY_LIGHT_COLOR, 1.0f);
		float skyFactor = duty$probe.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, 1.0f);
		if(duty$lastSkyColor != skyColor || duty$lastSkyFactor != skyFactor) {
			this.duty$lastSkyColor = skyColor;
			this.duty$lastSkyFactor = skyFactor;
			return true;
		}

		if(minecraft.player.isUnderWater() && ((PlayerAccessor) minecraft.player).duty$underwaterVisibilityTicks() < 600)
			return true; // water light fading

		if(!minecraft.options.hideLightningFlash().get()) {
			EndFlashState flash = minecraft.level.endFlashState();
			if(flash != null) {
				float factor = flash.getIntensity(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
				if(this.duty$lastEndFactor != factor) {
					this.duty$lastEndFactor = factor;
					return true;
				}
			}
		}

		MobEffectInstance nightVision = minecraft.player.getEffect(MobEffects.NIGHT_VISION);
		boolean hasNightVision = nightVision != null;
		if(duty$lastNightVision != hasNightVision) {
			duty$lastNightVision = hasNightVision;
			return true;
		} else if(nightVision != null && nightVision.endsWithin(200))
			return true; // flicker effect
		else if(minecraft.player.hasEffect(MobEffects.DARKNESS))
			return true; // flicker effect

		// Stuff that doesn't change as often

		boolean conduitPower = minecraft.player.hasEffect(MobEffects.CONDUIT_POWER);
		if(duty$lastConduitPower != conduitPower) {
			duty$lastConduitPower = conduitPower;
			return true;
		}
		DimensionType dimension = minecraft.level.dimensionType();
		if(duty$lastDimension != dimension) {
			duty$lastDimension = dimension;
			return true;
		}
		float skyDarkness = duty$gameRendererAccessor.duty$getSkyDarkness();
		if(duty$previousSkyDarkness != skyDarkness) {
			duty$previousSkyDarkness = skyDarkness;
			return true;
		}
		double gamma = minecraft.options.gamma().get();
		if(duty$lastGamma != gamma) { // jamma celestial??
			duty$lastGamma = gamma;
			return true;
		}
		// Upstream consults CacheHooks here, an API letting other mods declare extra reasons the
		// lightmap must be recomputed. Nothing outside that mod implements it, and carrying it
		// would mean carrying its config and platform layers too, so Duty drops it. If a mod ever
		// needs to force a lightmap update, this is the place to add the check back.
		return false;
	}
}
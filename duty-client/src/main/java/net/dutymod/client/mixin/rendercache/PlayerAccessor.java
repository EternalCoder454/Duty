package net.dutymod.client.mixin.rendercache;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface PlayerAccessor {
	@Accessor("waterVisionTime")
	int duty$underwaterVisibilityTicks();
}
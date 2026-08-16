package net.dutymod.client.mixin.audio;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Sets a sound's position without allocating an array for it.
 *
 * <p>Vanilla builds a {@code float[3]} and hands it to {@code alSourcefv}; confirmed against the
 * 26.1.2 bytecode, which contains exactly one {@code newarray} in this method. OpenAL has taken
 * three loose floats since forever, and LWJGL exposes that as {@code alSource3f}, so the array is
 * pure overhead. This runs for every playing sound whose position moves, every tick.
 *
 * <p>Nothing else installed touches {@code com.mojang.blaze3d.audio.Channel}. Adapted from Lomka
 * (MIT); the constant is spelled out rather than left as the literal {@code 4100} upstream uses.
 */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow
    @Final
    private int source;

    /**
     * @author Duty (from Lomka by Starlevka, MIT)
     * @reason Pass the position as three floats instead of building an array per update.
     */
    @Overwrite
    public void setSelfPosition(Vec3 position) {
        AL10.alSource3f(this.source, AL10.AL_POSITION,
                (float) position.x, (float) position.y, (float) position.z);
    }
}

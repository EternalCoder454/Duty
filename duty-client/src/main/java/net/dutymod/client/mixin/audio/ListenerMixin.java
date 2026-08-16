package net.dutymod.client.mixin.audio;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Updates the listener transform without allocating an array per frame.
 *
 * <p>This runs once per frame, always, whether or not anything is playing -- the listener follows
 * the camera. Vanilla allocates a {@code float[6]} for the orientation each time; confirmed against
 * the 26.1.2 bytecode, one {@code newarray} per call. OpenAL needs an array for
 * {@code alListenerfv} because orientation is six floats, so the array cannot be avoided the way
 * {@link ChannelMixin}'s can -- but it can be allocated once and refilled.
 *
 * <p>The buffer is per-instance and there is one {@code Listener}, so the reuse is not shared state
 * between sounds. {@code alListenerfv} copies out of it synchronously before returning, so nothing
 * retains a reference past the call.
 *
 * <p>Position still goes through {@code alListener3f}, which takes loose floats and needs no array
 * at all. Nothing else installed touches {@code com.mojang.blaze3d.audio.Listener}. Adapted from
 * Lomka (MIT), with the OpenAL constants named rather than left as literals.
 */
@Mixin(Listener.class)
public abstract class ListenerMixin {
    @Shadow
    private ListenerTransform transform;

    @Unique
    private final float[] duty$orientation = new float[6];

    /**
     * @author Duty (from Lomka by Starlevka, MIT)
     * @reason Reuse one orientation array instead of allocating one per frame.
     */
    @Overwrite
    public void setTransform(ListenerTransform listenerTransform) {
        this.transform = listenerTransform;

        Vec3 position = listenerTransform.position();
        AL10.alListener3f(AL10.AL_POSITION,
                (float) position.x, (float) position.y, (float) position.z);

        Vec3 forward = listenerTransform.forward();
        Vec3 up = listenerTransform.up();
        float[] orientation = this.duty$orientation;
        orientation[0] = (float) forward.x;
        orientation[1] = (float) forward.y;
        orientation[2] = (float) forward.z;
        orientation[3] = (float) up.x;
        orientation[4] = (float) up.y;
        orientation[5] = (float) up.z;
        AL10.alListenerfv(AL10.AL_ORIENTATION, orientation);
    }
}

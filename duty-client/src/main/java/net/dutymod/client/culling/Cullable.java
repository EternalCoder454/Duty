package net.dutymod.client.culling;

/**
 * Culling state attached to every {@code Entity} and {@code BlockEntity} by mixin.
 *
 * <p>Written by the culling thread and read by the render thread without synchronization. That is
 * intentional and safe: every field is a plain boolean or long, a stale read costs at most one
 * frame of a wrongly hidden or wrongly drawn object, and the alternative -- locking on every entity
 * every frame -- would cost more than the culling saves.
 */
public interface Cullable {

    /**
     * Marks this object visible for the next second regardless of what the culling thread decides.
     *
     * <p>Without this, an object that becomes visible flickers: the render thread would hide it
     * again before the culling thread has re-traced it. The grace period covers that gap.
     */
    void duty$setTimeout();

    /** {@return whether the grace period from {@link #duty$setTimeout()} is still running} */
    boolean duty$isForcedVisible();

    void duty$setCulled(boolean value);

    /** {@return whether the culling thread decided this object is hidden} */
    boolean duty$isCulled();

    void duty$setOutOfCamera(boolean value);

    /** {@return whether this object was outside the camera frustum last frame} */
    boolean duty$isOutOfCamera();
}

package net.dutymod.client.culling;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Exposes the two package-private {@code EntityRenderer} methods the culler needs.
 *
 * <p>Since 1.21.2 the "should this entity be culled" and "what is its culling box" decisions live
 * on the renderer rather than the entity, and both are package-private. Implemented by mixin on
 * {@code EntityRenderer}.
 */
public interface EntityRendererCullingAccess<T extends Entity> {

    /** {@return whether this entity opts out of culling entirely} */
    boolean duty$ignoresCulling(T entity);

    /** {@return the box to trace against, or null if the renderer cannot supply one} */
    AABB duty$getCullingBox(T entity);
}

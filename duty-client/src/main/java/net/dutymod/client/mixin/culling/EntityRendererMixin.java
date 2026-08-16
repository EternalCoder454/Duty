package net.dutymod.client.mixin.culling;

import net.dutymod.client.culling.EntityRendererCullingAccess;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Exposes the renderer's package-private culling decisions to the culling thread.
 *
 * <p>Both methods moved from Entity onto EntityRenderer in 1.21.2 and are not public, so there is
 * no way to ask for them from outside the package without this.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> implements EntityRendererCullingAccess<T> {

    @Shadow
    protected abstract boolean affectedByCulling(T entity);

    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity);

    @Override
    public boolean duty$ignoresCulling(T entity) {
        return !affectedByCulling(entity);
    }

    @Override
    public AABB duty$getCullingBox(T entity) {
        return getBoundingBoxForCulling(entity);
    }
}

package net.dutymod.client.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

/** Bridges to the renderer-side culling information that vanilla keeps package-private. */
public final class CullingHelper {
    /**
     * Resolved on each call rather than captured in a static field.
     *
     * <p>A {@code static final} snapshot is null if the class happens to load during mod
     * construction, which runs inside {@code Minecraft.<init>} before the singleton is assigned.
     * Nothing loads this class that early today, but the failure mode is silent and permanent.
     */
    private static Minecraft client() {
        return Minecraft.getInstance();
    }

    private CullingHelper() {}

    /**
     * {@return whether {@code entity} must never be culled}
     *
     * <p>Defaults to {@code true} when no renderer exists: an entity we cannot ask about is one we
     * must not hide.
     */
    @SuppressWarnings("unchecked")
    public static boolean ignoresCulling(Entity entity) {
        var renderer = client().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) {
            return true;
        }
        return ((EntityRendererCullingAccess<Entity>) renderer).duty$ignoresCulling(entity);
    }

    /**
     * {@return the box to trace against for {@code entity}, or null if none is available}
     */
    @SuppressWarnings("unchecked")
    public static AABB getCullingBox(Entity entity) {
        // Marker armor stands have a zero-size bounding box, which would trace as invisible and
        // hide whatever they carry. Substitute the type's default box instead.
        if (entity instanceof ArmorStand armorStand && armorStand.isMarker()) {
            return EntityType.ARMOR_STAND.getDimensions().makeBoundingBox(entity.position());
        }
        var renderer = client().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) {
            return null;
        }
        return ((EntityRendererCullingAccess<Entity>) renderer).duty$getCullingBox(entity);
    }
}

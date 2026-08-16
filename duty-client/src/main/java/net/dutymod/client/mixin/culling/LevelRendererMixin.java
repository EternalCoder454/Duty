package net.dutymod.client.mixin.culling;

import net.dutymod.client.culling.Cullable;
import net.dutymod.client.culling.CullingHelper;
import net.dutymod.client.culling.EntityCulling;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Where hidden entities actually stop costing anything.
 *
 * <p>Since 1.21.9 rendering is split into an extract phase that builds a render state and a draw
 * phase that consumes it. Culling at extraction is strictly better than cancelling the draw: the
 * render state is never built, so the saving covers the state object and everything the renderer
 * would have computed to fill it.
 *
 * <p>A culled entity returns a minimal placeholder state rather than null, because the caller does
 * not expect null. The placeholder is marked invisible and carries only a position, plus the name
 * tag when the player asked to keep those visible through walls.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private void duty$cullEntity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityCulling culling = EntityCulling.get();
        if (!culling.isEnabled() || culling.isSkipEntityCulling()) {
            return;
        }
        if (!(entity instanceof Cullable cullable)) {
            return;
        }
        if (cullable.duty$isForcedVisible() || !cullable.duty$isCulled() || CullingHelper.ignoresCulling(entity)) {
            // Visible this frame. Record that so tick culling knows it is on screen.
            cullable.duty$setOutOfCamera(false);
            return;
        }

        EntityRenderState state = new EntityRenderState();
        state.entityType = EntityType.INTERACTION;
        state.x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        state.y = Mth.lerp(partialTick, entity.yOld, entity.getY());
        state.z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        state.isInvisible = true;

        if (culling.isNametagsThroughWalls() && entity.shouldShowName()) {
            state.nameTag = entity.getDisplayName();
            state.nameTagAttachment = entity.getAttachments()
                    .getNullable(EntityAttachment.NAME_TAG, 0, entity.getYRot(partialTick));
        }

        cir.setReturnValue(state);
    }

    /**
     * Captures the frustum for block entity culling.
     *
     * <p>Block entities are extracted through a different path that is not handed a frustum, so it
     * is borrowed from here. Both run on the render thread within the same frame, so the value is
     * always current by the time it is read.
     */
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void duty$captureFrustum(Camera camera, Frustum frustum, DeltaTracker deltaTracker,
                                     LevelRenderState levelRenderState, CallbackInfo ci) {
        EntityCulling.get().setFrustum(frustum);
    }
}

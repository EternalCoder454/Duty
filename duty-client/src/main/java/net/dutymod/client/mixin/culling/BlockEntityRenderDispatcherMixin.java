package net.dutymod.client.mixin.culling;

import net.dutymod.client.culling.Cullable;
import net.dutymod.client.culling.EntityCulling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips render state extraction for hidden block entities.
 *
 * <p>Two filters run here, cheapest first. Frustum culling rejects anything outside the camera's
 * field of view, which is a handful of plane tests. Only what survives is checked against the
 * occlusion result the culling thread produced.
 *
 * <p>Renderers that declare {@code shouldRenderOffScreen} are exempt from both. Beacons are the
 * obvious case: the beam is drawn far outside the block's own bounds, so hiding the block entity
 * would remove a beam that is genuinely visible.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Shadow
    public abstract <E extends BlockEntity, S extends BlockEntityRenderState>
    BlockEntityRenderer<E, S> getRenderer(E blockEntity);

    /**
     * Targets the four-argument overload by full descriptor.
     *
     * <p>{@code tryExtractRenderState} is overloaded -- there is a three-argument form and this
     * frustum-taking one that NeoForge calls. Matching on the bare name is ambiguous, so the
     * descriptor is spelled out.
     */
    @Inject(
            method = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;"
                    + "tryExtractRenderState(Lnet/minecraft/world/level/block/entity/BlockEntity;F"
                    + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;"
                    + "Lnet/minecraft/client/renderer/culling/Frustum;)"
                    + "Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;",
            at = @At("HEAD"),
            cancellable = true)
    private void duty$cullBlockEntity(BlockEntity blockEntity, float partialTicks,
                                      ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
                                      Frustum passedFrustum,
                                      CallbackInfoReturnable<BlockEntityRenderState> cir) {
        EntityCulling culling = EntityCulling.get();
        if (!culling.isEnabled() || culling.isSkipBlockEntityCulling()) {
            return;
        }
        BlockEntityRenderer<?, ?> renderer = getRenderer(blockEntity);
        if (renderer == null) {
            return; // Nothing draws it; there is nothing to skip.
        }
        if (renderer.shouldRenderOffScreen()) {
            return; // Draws outside its own bounds, so neither test applies.
        }

        // Prefer the frustum NeoForge handed us; fall back to the one captured during entity
        // extraction for the paths that pass null.
        Frustum frustum = passedFrustum != null ? passedFrustum : culling.getFrustum();
        if (culling.isBlockEntityFrustumCulling() && frustum != null
                && !frustum.isVisible(EntityCulling.boundingBoxFor(blockEntity, blockEntity.getBlockPos()))) {
            cir.setReturnValue(null);
            return;
        }

        if (blockEntity instanceof Cullable cullable) {
            if (!cullable.duty$isForcedVisible() && cullable.duty$isCulled()) {
                cir.setReturnValue(null);
                return;
            }
            cullable.duty$setOutOfCamera(false);
        }
    }
}

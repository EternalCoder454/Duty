package net.dutymod.client.culling;

import net.dutymod.client.occlusion.OcclusionCullingInstance;
import net.dutymod.client.occlusion.util.Vec3d;
import net.dutymod.framework.DutyLog;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The culling thread.
 *
 * <p>Tracing a ray from the camera to every entity is far too expensive to do while the frame is
 * waiting, so it happens here instead, continuously and one step behind. The render thread only
 * ever reads a boolean that this thread wrote. Being one frame stale is invisible in practice; the
 * grace period in {@link Cullable#duty$setTimeout()} covers the case that would otherwise flicker.
 *
 * <p>The main thread hands over defensive copies of what it wants considered rather than letting
 * this thread walk live game state. Even so, the block entity map can be mutated underneath us, so
 * the iteration below tolerates that explicitly.
 */
public final class CullTask implements Runnable {
    /** Set by the main thread each tick to request a fresh pass. */
    public volatile boolean requestCull = false;

    public volatile boolean disableEntityCulling = false;
    public volatile boolean disableBlockEntityCulling = false;

    /** Milliseconds the last pass took. Read by the debug overlay. */
    public volatile double lastPassMillis = 0;

    private final OcclusionCullingInstance culling;
    private final Minecraft client = Minecraft.getInstance();
    private final Set<BlockEntityType<?>> blockEntityWhitelist;
    private final Set<EntityType<?>> entityWhitelist;
    private final int sleepDelay;
    private final int hitboxLimit;
    private final double tracingDistance;

    // Reused across passes; this thread runs constantly and must not itself become a source of
    // garbage, which would defeat the point of the mod it sits next to.
    private final Vec3d lastPos = new Vec3d(0, 0, 0);
    private final Vec3d aabbMin = new Vec3d(0, 0, 0);
    private final Vec3d aabbMax = new Vec3d(0, 0, 0);

    private volatile boolean ingame = false;
    private volatile List<Entity> entitiesForRendering = new ArrayList<>();
    private volatile Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private volatile Vec3 camera = new Vec3(0, 0, 0);

    public CullTask(OcclusionCullingInstance culling, Set<BlockEntityType<?>> blockEntityWhitelist,
                    Set<EntityType<?>> entityWhitelist, int sleepDelay, int hitboxLimit, double tracingDistance) {
        this.culling = culling;
        this.blockEntityWhitelist = blockEntityWhitelist;
        this.entityWhitelist = entityWhitelist;
        this.sleepDelay = sleepDelay;
        this.hitboxLimit = hitboxLimit;
        this.tracingDistance = tracingDistance;
    }

    public void setIngame(boolean ingame) {
        this.ingame = ingame;
    }

    public void setEntitiesForRendering(List<Entity> entities) {
        this.entitiesForRendering = entities;
    }

    public void setBlockEntities(Map<BlockPos, BlockEntity> blockEntities) {
        this.blockEntities = blockEntities;
    }

    public void setCamera(Vec3 camera) {
        this.camera = camera;
    }

    @Override
    public void run() {
        while (client.isRunning()) {
            try {
                Thread.sleep(sleepDelay);
                if (!ingame) {
                    lastPassMillis = 0;
                    continue;
                }
                Vec3 cameraNow = camera;
                // Nothing moved and nothing asked for a pass, so the previous answer still holds.
                if (!requestCull
                        && cameraNow.x == lastPos.x && cameraNow.y == lastPos.y && cameraNow.z == lastPos.z) {
                    continue;
                }
                long start = System.nanoTime();
                requestCull = false;
                lastPos.set(cameraNow.x, cameraNow.y, cameraNow.z);
                culling.resetCache();
                cullBlockEntities(cameraNow);
                cullEntities(cameraNow);
                lastPassMillis = (System.nanoTime() - start) / 1_000_000.0;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // A failure here must never take the game down: the worst outcome of giving up on
                // this pass is that everything stays visible for a moment.
                DutyLog.error("Culling pass failed", e);
            }
        }
        DutyLog.info("Culling thread shutting down.");
    }

    private void cullEntities(Vec3 cameraPos) {
        if (disableEntityCulling) {
            return;
        }
        Iterator<Entity> iterator = entitiesForRendering.iterator();
        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            if (entity == null) {
                // The iterator is being mutated under us; abandon this pass rather than guess.
                break;
            }
            if (!(entity instanceof Cullable cullable)) {
                continue;
            }
            if (entityWhitelist.contains(entity.getType()) || cullable.duty$isForcedVisible()) {
                continue;
            }
            // A glowing entity is deliberately meant to be visible through walls.
            if (client.shouldEntityAppearGlowing(entity)) {
                cullable.duty$setCulled(false);
                continue;
            }
            if (!entity.position().closerThan(cameraPos, tracingDistance)) {
                // Further away than we trace. Leave it to the vanilla view distance.
                cullable.duty$setCulled(false);
                continue;
            }
            AABB box = CullingHelper.getCullingBox(entity);
            if (box == null || box.getXsize() > hitboxLimit || box.getYsize() > hitboxLimit
                    || box.getZsize() > hitboxLimit) {
                // Something huge; tracing it is expensive and it is unlikely to be fully hidden.
                cullable.duty$setCulled(false);
                continue;
            }
            aabbMin.set(box.minX, box.minY, box.minZ);
            aabbMax.set(box.maxX, box.maxY, box.maxZ);
            cullable.duty$setCulled(!culling.isAABBVisible(aabbMin, aabbMax, lastPos));
        }
    }

    private void cullBlockEntities(Vec3 cameraPos) {
        if (disableBlockEntityCulling) {
            return;
        }
        Iterator<Map.Entry<BlockPos, BlockEntity>> iterator = blockEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockEntity> entry;
            try {
                entry = iterator.next();
            } catch (NullPointerException | ConcurrentModificationException e) {
                // Chunks unload while we walk this map. Synchronizing against the main thread to
                // avoid it would cost more than simply stopping and picking it up next pass.
                break;
            }
            if (entry == null) {
                break;
            }
            BlockEntity blockEntity = entry.getValue();
            if (blockEntityWhitelist.contains(blockEntity.getType())) {
                continue;
            }
            if (client.getBlockEntityRenderDispatcher().getRenderer(blockEntity) == null) {
                continue; // Nothing draws it, so there is nothing to skip.
            }
            if (!(blockEntity instanceof Cullable cullable) || cullable.duty$isForcedVisible()) {
                continue;
            }
            BlockPos pos = entry.getKey();
            // 64 blocks is the fixed vanilla block entity render distance.
            if (!closerThan(pos, cameraPos, 64)) {
                continue;
            }
            AABB box = EntityCulling.boundingBoxFor(blockEntity, pos);
            if (box.getXsize() > hitboxLimit || box.getYsize() > hitboxLimit || box.getZsize() > hitboxLimit) {
                cullable.duty$setCulled(false);
                continue;
            }
            aabbMin.set(box.minX, box.minY, box.minZ);
            aabbMax.set(box.maxX, box.maxY, box.maxZ);
            cullable.duty$setCulled(!culling.isAABBVisible(aabbMin, aabbMax, lastPos));
        }
    }

    private static boolean closerThan(BlockPos pos, Vec3 position, double distance) {
        // Measured from the block's centre, hence the half-block offset.
        double dx = pos.getX() + 0.5D - position.x();
        double dy = pos.getY() + 0.5D - position.y();
        double dz = pos.getZ() + 0.5D - position.z();
        return dx * dx + dy * dy + dz * dz < distance * distance;
    }
}

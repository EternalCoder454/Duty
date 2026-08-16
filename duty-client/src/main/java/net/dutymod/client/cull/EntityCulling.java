package net.dutymod.client.cull;

import net.dutymod.client.ClientOptions;
import net.dutymod.client.occlusion.OcclusionCullingInstance;
import net.dutymod.core.DutyConfig;
import net.dutymod.core.DutyLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * Owns the culling thread and feeds it a snapshot of the world each tick.
 *
 * <p>Split of responsibility: this class runs on the main thread and only ever reads live game
 * state, {@link CullTask} runs on its own thread and only ever reads the snapshots handed to it.
 * Nothing crosses in the other direction except the per-object booleans on {@link Cullable}.
 */
public final class EntityCulling {
    /** How long the culling thread sleeps between passes, in milliseconds. */
    private static final int SLEEP_DELAY = 10;

    /** Objects with a bounding box larger than this on any axis are never culled. */
    private static final int HITBOX_LIMIT = 128;

    /** How far the tracer looks. Beyond this, vanilla view distance takes over. */
    private static final int TRACING_DISTANCE = 128;

    /** Ticks between rebuilding the snapshot handed to the culling thread. */
    private static final int CAPTURE_RATE = 2;

    /** Radius in chunks around the player whose block entities are considered. */
    private static final int BLOCK_ENTITY_CHUNK_RADIUS = 8;

    private static EntityCulling instance;

    private final Set<BlockEntityType<?>> blockEntityWhitelist = new HashSet<>();
    private final Set<EntityType<?>> entityWhitelist = new HashSet<>();

    private final boolean enabled;
    private final boolean skipEntityCulling;
    private final boolean skipBlockEntityCulling;
    private final boolean tickCulling;
    private final boolean blockEntityFrustumCulling;
    private final boolean nametagsThroughWalls;

    private CullTask cullTask;
    private Thread cullThread;
    private boolean started;
    private int tickCounter;

    /**
     * The frustum captured during entity extraction, reused for block entity frustum culling.
     * Written and read on the render thread only.
     */
    private Frustum frustum;

    private EntityCulling() {
        ClientOptions.init();
        this.enabled = DutyConfig.get(ClientOptions.CULLING_ENABLED);
        this.skipEntityCulling = DutyConfig.get(ClientOptions.SKIP_ENTITY_CULLING);
        this.skipBlockEntityCulling = DutyConfig.get(ClientOptions.SKIP_BLOCK_ENTITY_CULLING);
        this.tickCulling = DutyConfig.get(ClientOptions.TICK_CULLING);
        this.blockEntityFrustumCulling = DutyConfig.get(ClientOptions.BLOCK_ENTITY_FRUSTUM_CULLING);
        this.nametagsThroughWalls = DutyConfig.get(ClientOptions.NAMETAGS_THROUGH_WALLS);
    }

    public static EntityCulling get() {
        EntityCulling local = instance;
        if (local == null) {
            synchronized (EntityCulling.class) {
                local = instance;
                if (local == null) {
                    local = new EntityCulling();
                    instance = local;
                }
            }
        }
        return local;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSkipEntityCulling() {
        return skipEntityCulling;
    }

    public boolean isSkipBlockEntityCulling() {
        return skipBlockEntityCulling;
    }

    public boolean isTickCulling() {
        return tickCulling;
    }

    public boolean isBlockEntityFrustumCulling() {
        return blockEntityFrustumCulling;
    }

    public boolean isNametagsThroughWalls() {
        return nametagsThroughWalls;
    }

    public Frustum getFrustum() {
        return frustum;
    }

    public void setFrustum(Frustum frustum) {
        this.frustum = frustum;
    }

    public Set<EntityType<?>> getEntityWhitelist() {
        return entityWhitelist;
    }

    /** Called once the client is far enough along that registries can be read. */
    private void start() {
        started = true;
        OcclusionCullingInstance culling = new OcclusionCullingInstance(
                TRACING_DISTANCE, new LevelDataProvider(DutyConfig.get(ClientOptions.SOLID_LEAVES)));
        cullTask = new CullTask(culling, blockEntityWhitelist, entityWhitelist,
                SLEEP_DELAY, HITBOX_LIMIT, TRACING_DISTANCE);
        cullTask.disableEntityCulling = skipEntityCulling;
        cullTask.disableBlockEntityCulling = skipBlockEntityCulling;

        cullThread = new Thread(cullTask, "Duty Culling");
        cullThread.setDaemon(true);
        cullThread.setUncaughtExceptionHandler(
                (thread, ex) -> DutyLog.error("The culling thread crashed; culling is now inactive.", ex));
        cullThread.start();
        DutyLog.info("Culling thread started.");
    }

    /** Called every client tick. */
    public void clientTick() {
        if (!enabled) {
            return;
        }
        if (!started) {
            start();
        }

        Minecraft client = Minecraft.getInstance();
        // tickCount > 10 avoids culling during the first moments of world join, when positions
        // and chunks are still settling and everything would trace as hidden.
        boolean ingame = client.level != null && client.player != null && client.player.tickCount > 10;
        if (!ingame) {
            cullTask.setIngame(false);
            cullTask.setEntitiesForRendering(Collections.emptyList());
            cullTask.setBlockEntities(Collections.emptyMap());
            return;
        }

        // Rebuilding the snapshot is the expensive part, so it runs at a fraction of tick rate.
        // The camera position is refreshed every tick regardless, since that is what decides
        // whether a new pass is worth running at all.
        if (tickCounter++ % CAPTURE_RATE == 0) {
            if (!skipEntityCulling) {
                List<Entity> entities = StreamSupport
                        .stream(client.level.entitiesForRendering().spliterator(), false)
                        .toList();
                cullTask.setEntitiesForRendering(entities);
            }
            if (!skipBlockEntityCulling) {
                Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
                int centerX = client.player.chunkPosition().x();
                int centerZ = client.player.chunkPosition().z();
                for (int x = -BLOCK_ENTITY_CHUNK_RADIUS; x <= BLOCK_ENTITY_CHUNK_RADIUS; x++) {
                    for (int z = -BLOCK_ENTITY_CHUNK_RADIUS; z <= BLOCK_ENTITY_CHUNK_RADIUS; z++) {
                        LevelChunk chunk = client.level.getChunk(centerX + x, centerZ + z);
                        blockEntities.putAll(chunk.getBlockEntities());
                    }
                }
                cullTask.setBlockEntities(blockEntities);
            }
        }

        cullTask.setIngame(true);
        cullTask.setCamera(client.gameRenderer.getMainCamera().position());
        cullTask.requestCull = true;
    }

    /** Called when the world ticks, to force a pass even if the camera has not moved. */
    public void worldTick() {
        if (enabled && started) {
            cullTask.requestCull = true;
        }
    }

    /**
     * {@return the box to trace against for a block entity}
     *
     * <p>Uses the renderer's declared render bounding box, which is what multiblock-style block
     * entities extend to cover the geometry they actually draw. Falls back to the block's own cube
     * when there is no renderer to ask.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static AABB boundingBoxFor(BlockEntity blockEntity, BlockPos pos) {
        var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);
        if (renderer == null) {
            return new AABB(pos);
        }
        // Raw cast: the renderer's own type parameter is captured here and cannot be named, but
        // the dispatcher only ever hands back a renderer that accepts this block entity.
        return ((net.neoforged.neoforge.client.extensions.IBlockEntityRendererExtension) renderer)
                .getRenderBoundingBox(blockEntity);
    }
}

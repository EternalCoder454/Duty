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

    /**
     * How many objects the last pass actually traced, and how many it hid.
     *
     * <p>{@link #lastPassMillis} was already recorded and never read by anything, which made the
     * cost of culling unmeasurable -- and an optimization whose cost nobody can see is one nobody
     * can reason about. These feed {@link net.dutymod.client.culling.DebugEntryCulling}, so the
     * question "is this worth what it costs" has an answer on screen instead of an opinion.
     *
     * <p>Written from the culling thread and read from the render thread, hence volatile. They are
     * counters rather than a snapshot, so a torn read shows a slightly stale number, never a wrong
     * one.
     */
    /**
     * The same three numbers, accumulated rather than last-only.
     *
     * <p>Static finals so the registry lookup happens once at class-init rather than per pass; see
     * {@link net.dutymod.framework.DutyMetrics}.
     */
    private static final net.dutymod.framework.DutyMetrics.Timer PASS_TIME =
            net.dutymod.framework.DutyMetrics.timer("client.culling.pass");
    private static final net.dutymod.framework.DutyMetrics.Counter TRACED =
            net.dutymod.framework.DutyMetrics.counter("client.culling.traced");
    private static final net.dutymod.framework.DutyMetrics.Counter HIDDEN =
            net.dutymod.framework.DutyMetrics.counter("client.culling.hidden");

    /**
     * How many candidates a pass was handed, before any filter.
     *
     * <p>Against {@code traced} this says how much of the working set the cheap filters throw away,
     * which is what distinguishes "a pass was slow because it had a lot to do" from "a pass was
     * slow for some other reason". A measured 38ms outlier against a 0.8ms mean is what prompted
     * this; a cumulative average alone could not tell those apart.
     */
    private static final net.dutymod.framework.DutyMetrics.Counter CANDIDATES =
            net.dutymod.framework.DutyMetrics.counter("client.culling.candidates");

    static {
        // What the generic rules cannot know: that these three counters are a funnel, and that the
        // ratios between them are the answer to "is this feature worth its thread".
        net.dutymod.framework.DutyReport.contributor(findings -> {
            long candidates = CANDIDATES.value();
            long traced = TRACED.value();
            long hidden = HIDDEN.value();
            if (traced == 0) {
                return;
            }
            long passes = Math.max(1L, PASS_TIME.count());
            double hitRate = 100.0 * hidden / traced;

            findings.add(new net.dutymod.framework.DutyReport.Finding(
                    net.dutymod.framework.DutyReport.Severity.INFO,
                    "Culling hid " + String.format(java.util.Locale.ROOT, "%.1f%%", hitRate)
                            + " of what it traced",
                    String.format(java.util.Locale.ROOT,
                            "%d of %d traced, about %d skipped renders per pass. Each of those is "
                                    + "a draw that did not happen, against %.3fms of tracing per "
                                    + "pass on a background thread.",
                            hidden, traced, hidden / passes,
                            PASS_TIME.totalNanos() / 1.0e6 / passes)));

            if (candidates > 0) {
                double survived = 100.0 * traced / candidates;
                if (survived < 10.0) {
                    findings.add(new net.dutymod.framework.DutyReport.Finding(
                            net.dutymod.framework.DutyReport.Severity.INFO,
                            "Most culling candidates are rejected before tracing",
                            String.format(java.util.Locale.ROOT,
                                    "%d of %d candidates reached a ray trace (%.1f%%). The cheap "
                                            + "filters are carrying the work, which is what they "
                                            + "are for -- but it also means their order matters "
                                            + "more than the tracing does.",
                                    traced, candidates, survived)));
                }
            }
        });
    }

    /**
     * A pass slower than this is worth a line in the log, with the context to explain it.
     *
     * <p>Was 20ms, chosen before there was any data. A real session then produced a 12.6ms worst
     * against a 0.655ms median -- a nineteen-fold outlier that the report flagged and this
     * diagnostic sat silently through, which is the wrong way round: the report noticed and the
     * thing that could have explained it did not fire. 10ms is still an order of magnitude above
     * the median, so it stays quiet in normal play.
     */
    private static final long SLOW_PASS_NANOS = 10_000_000L;

    /** Rate limit, so a genuinely bad situation reports once a minute rather than every pass. */
    private static final long SLOW_PASS_REPORT_INTERVAL_NANOS = 60_000_000_000L;

    private long lastSlowReportNanos = Long.MIN_VALUE;

    /**
     * Logs a slow pass, at most once a minute, with what it was given to work with.
     *
     * <p>Unconditional rather than gated on metrics: a pass forty times the mean is worth knowing
     * about whether or not somebody thought to switch measuring on first, and the check is a
     * comparison against a constant on a path that has just spent milliseconds.
     */
    private void reportIfSlow(long elapsedNanos, int candidates) {
        if (elapsedNanos < SLOW_PASS_NANOS) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastSlowReportNanos < SLOW_PASS_REPORT_INTERVAL_NANOS) {
            return;
        }
        lastSlowReportNanos = now;
        DutyLog.warn(String.format(java.util.Locale.ROOT,
                "Culling pass took %.1fms (%d candidates, %d traced, %d hidden). This runs on its "
                        + "own thread, so it does not drop a frame, but visibility data was that "
                        + "stale for a moment.",
                elapsedNanos / 1.0e6, candidates, passTraced, passHidden));
    }

    public volatile int lastPassTraced = 0;

    public volatile int lastPassHidden = 0;

    /** Accumulated during a pass on the culling thread only, then published to the two above. */
    private int passTraced;

    private int passHidden;

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
                passTraced = 0;
                passHidden = 0;
                // Sampled before the pass, because both collections are swapped wholesale from the
                // render thread and could be replaced while this pass walks them.
                int candidates = blockEntities.size() + entitiesForRendering.size();
                cullBlockEntities(cameraNow);
                cullEntities(cameraNow);
                long elapsed = System.nanoTime() - start;
                CANDIDATES.add(candidates);
                reportIfSlow(elapsed, candidates);
                lastPassMillis = elapsed / 1_000_000.0;
                lastPassTraced = passTraced;
                lastPassHidden = passHidden;

                // The fields above stay because the F3 line wants the last pass exactly, and it
                // has to work whether or not measurement is switched on. What these add is the
                // shape over time -- how many passes, the worst one, the mean -- which is what
                // answers "does this earn its thread" and which a single last-value cannot.
                // No-ops when framework.metrics is off, which is the default.
                PASS_TIME.record(elapsed);
                TRACED.add(passTraced);
                HIDDEN.add(passHidden);
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
            // Distance before the glow test, for the same reason as in cullBlockEntities: both
            // arms end in setCulled(false) and a continue, so which runs first cannot change the
            // outcome -- but shouldEntityAppearGlowing walks team and spectator state, and there is
            // no reason to ask it about an entity that is beyond the tracing distance anyway.
            if (!entity.position().closerThan(cameraPos, tracingDistance)) {
                // Further away than we trace. Leave it to the vanilla view distance.
                cullable.duty$setCulled(false);
                continue;
            }
            // A glowing entity is deliberately meant to be visible through walls.
            if (client.shouldEntityAppearGlowing(entity)) {
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
            boolean hidden = !culling.isAABBVisible(aabbMin, aabbMax, lastPos);
            cullable.duty$setCulled(hidden);
            passTraced++;
            if (hidden) {
                passHidden++;
            }
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
            // Distance first, and the order matters more than it looks.
            //
            // This map is every block entity in a 17x17 chunk area (BLOCK_ENTITY_CHUNK_RADIUS = 8),
            // which in a storage-heavy base is thousands of entries. The 64-block cutoff below is
            // four chunks, so the large majority of that map is rejected right here -- and every
            // check that used to run before this one was paid for entries that were about to be
            // thrown away. The dispatcher lookup was the expensive one.
            //
            // All four filters here are pure `continue` with no side effects, so reordering them
            // cannot change what ends up culled; it only changes what gets asked.
            BlockPos pos = entry.getKey();
            // 64 blocks is the fixed vanilla block entity render distance.
            if (!closerThan(pos, cameraPos, 64)) {
                continue;
            }
            BlockEntity blockEntity = entry.getValue();
            if (blockEntityWhitelist.contains(blockEntity.getType())) {
                continue;
            }
            if (!(blockEntity instanceof Cullable cullable) || cullable.duty$isForcedVisible()) {
                continue;
            }
            if (client.getBlockEntityRenderDispatcher().getRenderer(blockEntity) == null) {
                continue; // Nothing draws it, so there is nothing to skip.
            }
            AABB box = EntityCulling.boundingBoxFor(blockEntity, pos);
            if (box.getXsize() > hitboxLimit || box.getYsize() > hitboxLimit || box.getZsize() > hitboxLimit) {
                cullable.duty$setCulled(false);
                continue;
            }
            aabbMin.set(box.minX, box.minY, box.minZ);
            aabbMax.set(box.maxX, box.maxY, box.maxZ);
            boolean hidden = !culling.isAABBVisible(aabbMin, aabbMax, lastPos);
            cullable.duty$setCulled(hidden);
            passTraced++;
            if (hidden) {
                passHidden++;
            }
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

package net.dutymod.client.culling;

import net.dutymod.client.occlusion.DataProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LeavesBlock;

/**
 * Feeds world geometry to the occlusion tracer.
 *
 * <p>Called from the culling thread, so it reads the client level off the main thread. That is
 * tolerated for the same reason the rest of this package tolerates it: a torn read produces a
 * momentarily wrong visibility decision, never a corrupted world.
 */
public final class LevelDataProvider implements DataProvider {
    private final Minecraft client = Minecraft.getInstance();
    private final boolean solidLeaves;
    private ClientLevel level;

    /**
     * Reused for every block query instead of allocating a position per lookup.
     *
     * <p>{@link #isOpaqueFullCube} is the innermost call in the whole culling system: once per
     * block, per ray, per entity, per pass. A {@code BlockPos} per call is short-lived garbage in
     * the hottest loop Duty has.
     *
     * <p><b>Safe because this object is confined to one thread.</b> Exactly one
     * {@code LevelDataProvider} is built, in {@code EntityCulling.start}, handed to one
     * {@code OcclusionCullingInstance}, used by one {@code CullTask}, which runs on the single
     * "Duty Culling" thread. Nothing else holds a reference. If a second culling thread is ever
     * added, each needs its own provider -- which it would get anyway, since the provider is
     * constructed alongside the instance.
     */
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    public LevelDataProvider(boolean solidLeaves) {
        this.solidLeaves = solidLeaves;
    }

    @Override
    public boolean prepareChunk(int chunkX, int chunkZ) {
        level = client.level;
        return level != null;
    }

    @Override
    public boolean isOpaqueFullCube(int x, int y, int z) {
        var state = level.getBlockState(scratch.set(x, y, z));
        // Leaves are technically see-through, but a tree canopy occludes in practice. Treating
        // them as solid is what makes culling worthwhile in a forest.
        if (solidLeaves && state.getBlock() instanceof LeavesBlock) {
            return true;
        }
        return state.isSolidRender();
    }

    @Override
    public void cleanup() {
        level = null;
    }
}

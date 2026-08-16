package net.dutymod.client.cull;

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
        var state = level.getBlockState(new BlockPos(x, y, z));
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

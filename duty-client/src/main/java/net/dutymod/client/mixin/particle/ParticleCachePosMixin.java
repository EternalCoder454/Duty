package net.dutymod.client.mixin.particle;

import net.dutymod.client.particle.util.TriState;
import net.dutymod.client.particle.interfaces.BlockPosStorer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import org.jetbrains.annotations.Nullable;

@Mixin(Particle.class)
public class ParticleCachePosMixin implements BlockPosStorer {

    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow @Final protected ClientLevel level;

    @Unique
    private volatile BlockPos cachedPos = BlockPos.ZERO;
    @Unique
    @Nullable
    private volatile BlockState cachedState = null;
    @Unique
    private volatile TriState isEmpty = TriState.DEFAULT;

    /**
     * Runs once per particle per tick.
     *
     * <p>Upstream allocates a {@link BlockPos} unconditionally. A particle usually spends many
     * ticks inside one block -- smoke drifting, flames idling, redstone dust sitting still -- so
     * most of those allocations produce a position equal to the one already held. Comparing the
     * three integers first keeps the object when it has not moved, which also avoids a volatile
     * write and the memory barrier that comes with it.
     *
     * <p>The block state and collision flag are still invalidated every tick regardless, exactly as
     * before: the particle staying in one block does not mean the block is still there.
     */
    @Override
    public void duty$tickCachedPos() {
        int blockX = Mth.floor(this.x);
        int blockY = Mth.floor(this.y);
        int blockZ = Mth.floor(this.z);
        BlockPos current = cachedPos;
        if (current.getX() != blockX || current.getY() != blockY || current.getZ() != blockZ) {
            cachedPos = new BlockPos(blockX, blockY, blockZ);
        }
        cachedState = null;
        isEmpty = TriState.DEFAULT;
    }

    @Override
    public BlockPos duty$getCachedPos() {
        return cachedPos;
    }

    @Override
    public BlockState duty$getCachedState() {
        if (cachedState == null) {
            cachedState = this.level.getBlockState(cachedPos);
        }
        return cachedState;
    }

    @Override
    public boolean duty$getCachedEmpty() {
        if (isEmpty == TriState.DEFAULT) {
            isEmpty = TriState.of(duty$getCachedState().getCollisionShape(this.level, cachedPos).isEmpty());
        }
        return isEmpty.getAsBoolean();
    }

}

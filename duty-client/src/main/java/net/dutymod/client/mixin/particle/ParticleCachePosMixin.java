package net.dutymod.client.mixin.particle;

import net.dutymod.client.particle.util.TriState;
import net.dutymod.client.particle.interfaces.BlockPosStorer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
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

    @Override
    public void particle_core_tickCachedPos() {
        cachedPos = BlockPos.containing(this.x, this.y, this.z);
        cachedState = null;
        isEmpty = TriState.DEFAULT;
    }

    @Override
    public BlockPos particle_core_getCachedPos() {
        return cachedPos;
    }

    @Override
    public BlockState particle_core_getCachedState() {
        if (cachedState == null) {
            cachedState = this.level.getBlockState(cachedPos);
        }
        return cachedState;
    }

    @Override
    public boolean particle_core_getCachedEmpty() {
        if (isEmpty == TriState.DEFAULT) {
            isEmpty = TriState.of(particle_core_getCachedState().getCollisionShape(this.level, cachedPos).isEmpty());
        }
        return isEmpty.getAsBoolean();
    }

}
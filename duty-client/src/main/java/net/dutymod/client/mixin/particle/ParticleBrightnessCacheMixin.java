package net.dutymod.client.mixin.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.dutymod.client.particle.interfaces.BlockPosStorer;
import net.dutymod.client.particle.interfaces.CachedLightPreparer;
import net.dutymod.client.particle.interfaces.CachedLightProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.ConcurrentHashMap;

@Mixin(Particle.class)
public class ParticleBrightnessCacheMixin implements CachedLightPreparer {

    @Shadow protected double x;
    @Shadow protected double y;
    @Shadow protected double z;
    @Shadow @Final protected ClientLevel level;

    @Unique
    private int particle_core_cachedLight = -1;

    @WrapOperation(method = "getLightCoords", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I"), require = 0)
    private int particle_core_getCachedBrightness(BlockAndLightGetter world, BlockPos pos, Operation<Integer> original) {
        if (particle_core_cachedLight == -1) {
            particle_core_cachedLight = LevelRenderer.getLightCoords(world, pos);
        }
        return particle_core_cachedLight;
    }

    /**
     * Runs once per particle per tick, so the allocation behaviour here matters more than the
     * lookup does.
     *
     * <p>Upstream calls {@code computeIfAbsent} with a lambda capturing {@code this}, {@code state}
     * and {@code blockPos}. That lambda is an argument, so it is allocated on every call --
     * including every cache hit, which is the case this cache exists to make cheap. Probing with
     * {@code get} first keeps the hit path allocation-free and only builds the value on a miss.
     *
     * <p>{@code get} then {@code putIfAbsent} is not atomic the way {@code computeIfAbsent} is, but
     * the value is a pure function of the level, block state and position, so two threads racing on
     * the same key compute the same number. The loser's result is discarded rather than published,
     * which is why {@code putIfAbsent}'s return value is preferred over the local.
     */
    @Override
    public void particle_core_tickLightUpdate() {
        BlockPos blockPos = ((BlockPosStorer) this).particle_core_getCachedPos();
        BlockState state = ((BlockPosStorer) this).particle_core_getCachedState();
        ConcurrentHashMap<BlockPos, Integer> cache =
                ((CachedLightProvider) Minecraft.getInstance().particleEngine).particle_core_getCache();

        Integer cached = cache.get(blockPos);
        if (cached == null) {
            Integer computed = getLightmap(this.level, state, blockPos);
            Integer existing = cache.putIfAbsent(blockPos, computed);
            cached = existing != null ? existing : computed;
        }
        particle_core_cachedLight = cached;
    }

    @Unique
    private int getLightmap(BlockAndLightGetter world, BlockState state, BlockPos blockPos) {
        return LevelRenderer.getLightCoords(LevelRenderer.BrightnessGetter.DEFAULT, world, state, blockPos);
    }
}

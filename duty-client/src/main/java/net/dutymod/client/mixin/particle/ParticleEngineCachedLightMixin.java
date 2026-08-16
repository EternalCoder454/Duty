package net.dutymod.client.mixin.particle;

import net.dutymod.client.particle.interfaces.CachedLightProvider;
import net.minecraft.client.particle.TrackingEmitter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ParticleEngine.class)
public class ParticleEngineCachedLightMixin implements CachedLightProvider {

    @Unique
    private volatile ConcurrentHashMap<BlockPos, Integer> cachedLightMap = new ConcurrentHashMap<>(64, 0.75f);

    @Override
    public ConcurrentHashMap<BlockPos, Integer> duty$getCache() {
        return cachedLightMap;
    }

    /**
     * Drops last tick's light values.
     *
     * <p>The map is replaced rather than cleared because particles tick on worker threads: swapping
     * the reference hands readers a whole consistent map, where {@code clear()} would empty the one
     * they are already walking. That part is upstream's design and is kept.
     *
     * <p>What is not kept is doing it unconditionally. Replacing an already-empty map allocates a
     * {@link ConcurrentHashMap} and its backing table twenty times a second to discard nothing, and
     * a player standing somewhere without particles is the common case, not the rare one.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void duty$clearCache(CallbackInfo ci) {
        int size = cachedLightMap.size();
        if (size == 0) {
            return;
        }
        cachedLightMap = new ConcurrentHashMap<>(size, 0.75f);
    }
}
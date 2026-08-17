package net.dutymod.fixerupper.common.mixin.bugfix.offthread_biome_lookup;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops a zombie villager being built on a worldgen thread from deadlocking the server.
 *
 * <h2>The deadlock</h2>
 *
 * <p>Every {@code ZombieVillager} picks its type from the biome it spawned in, and it does so from
 * {@code defineSynchedData} -- during the constructor. When a zombie villager is created during
 * world generation (an igloo basement, a zombie village) that constructor runs on a worldgen
 * worker, and the biome read becomes a blocking cross-chunk load:
 *
 * <pre>
 * Worker-Main-N   ZombieVillager.&lt;init&gt; -&gt; initializeVillagerData -&gt; Level.getBiome
 *                 -&gt; ServerChunkCache.getChunkOffThread -&gt; join()   [waits for the server thread]
 * Server thread   runs that supplier -&gt; getChunkBlocking -&gt; parks   [waits for the chunk]
 * </pre>
 *
 * <p>Neither side can finish. A thread dump taken while it was stuck showed 110 threads and not a
 * single {@code BLOCKED} one, because nothing here is a lock -- it is two futures waiting on each
 * other. The visible symptom is the whole server stopping, and a watchdog eventually reporting a
 * tick that has taken forty seconds.
 *
 * <p>Anything that generates chunks in bulk makes this near-certain rather than rare, simply by
 * running the spawn step often enough.
 *
 * <h2>What this changes</h2>
 *
 * <p>Only the case that would otherwise hang. If the read is happening off the server thread
 * <em>and</em> the chunk it wants is not already in memory, the villager type falls back to the
 * plains default instead of demanding a chunk load. On the server thread, or when the chunk is
 * already there, vanilla runs untouched -- so this cannot change a villager that vanilla could have
 * typed correctly without blocking.
 *
 * <p>The cost is a zombie villager occasionally wearing plains clothing where it would have worn
 * snowy or desert. That is a texture, weighed against the server stopping dead.
 *
 * <p>Note this is a different bug from {@code bugfix.chunk_deadlock}, which patches chunk
 * <em>promotion</em>. This one is on the spawn path and that fix does not reach it.
 */
@Mixin(net.minecraft.world.entity.monster.zombie.ZombieVillager.class)
public abstract class ZombieVillagerMixin {

    @WrapOperation(
            method = "initializeVillagerData",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBiome"
                            + "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private Holder<Biome> duty$biomeWithoutBlockingChunkLoad(
            Level level, BlockPos pos, Operation<Holder<Biome>> original) {
        if (duty$wouldBlock(level, pos)) {
            Holder<Biome> fallback = duty$fallbackBiome(level);
            if (fallback != null) {
                return fallback;
            }
        }
        return original.call(level, pos);
    }

    /**
     * {@return whether reading this biome would have to load a chunk from another thread}
     *
     * <p>Both halves matter. Off the server thread, the read is routed through the server thread
     * and waited on, which is the half that deadlocks; and if the chunk is already in memory there
     * is nothing to wait for, so vanilla is left alone.
     */
    @Unique
    private static boolean duty$wouldBlock(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (serverLevel.getServer().isSameThread()) {
            return false;
        }
        return !serverLevel.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** {@return the plains biome, or null if the registry cannot answer without work} */
    @Unique
    private static Holder<Biome> duty$fallbackBiome(Level level) {
        try {
            return level.registryAccess().lookupOrThrow(Registries.BIOME).get(Biomes.PLAINS)
                    .orElse(null);
        } catch (Throwable t) {
            // Falling through to vanilla is the safe direction: a hang is bad, but a hang is
            // better than an exception thrown out of an entity constructor during worldgen.
            return null;
        }
    }
}

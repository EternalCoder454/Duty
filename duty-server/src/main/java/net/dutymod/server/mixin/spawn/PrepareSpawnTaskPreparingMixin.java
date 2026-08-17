package net.dutymod.server.mixin.spawn;

import net.dutymod.server.spawn.SpawnJoinOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Stops a joining player waiting on a 7x7 block of chunks before entering the world.
 *
 * <p>When a player joins, the server registers a {@code PLAYER_SPAWN} ticket around their spawn
 * position with a radius of 3 -- 49 chunks -- and does not let them in until all of them are
 * loaded. On a heavy modpack those 49 chunks are the difference between joining immediately and
 * staring at a loading screen, and none of it is necessary: the chunks the player can actually see
 * are streamed by the normal view-distance path either way.
 *
 * <p>Duty lowers the radius to 1, so the chunk the player stands in is ready and the rest arrives
 * as it normally would.
 *
 * <h2>What this is not</h2>
 *
 * <p>This is <b>not</b> the 441 permanently-loaded spawn chunks that mods like Ksyxis are known
 * for removing. Those are gone from 26.1.2 already: {@code MinecraftServer.prepareLevels} no longer
 * registers a ticket at all, it only tracks loading through {@code ChunkLoadCounter}, and the 441
 * constant is not in the class. The only spawn-chunk load left in vanilla is this one, and it is
 * per join rather than permanent.
 *
 * <p>Uncontested: no installed mod mixes into {@code PrepareSpawnTask}, and the method contains
 * exactly one {@code 3}, so the constant is unambiguous.
 */
@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Preparing")
public abstract class PrepareSpawnTaskPreparingMixin {
    /**
     * @param radius vanilla's 3
     * @return the configured radius
     */
    @ModifyConstant(
            method = "lambda$tick$0(Lnet/minecraft/world/level/ChunkPos;)V",
            constant = @Constant(intValue = 3)
    )
    private int duty$shrinkSpawnLoadRadius(int radius) {
        return SpawnJoinOptions.joinChunkRadius();
    }
}

package net.dutymod.server.mixin.save;

import net.dutymod.server.save.AsyncWorldSave;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.nio.file.Path;

/**
 * Writes player data on the save worker instead of the server thread.
 *
 * <p>Redirects rather than overwrites, so the surrounding method -- which decides the paths and
 * handles the failure logging -- stays vanilla's. Only the two blocking calls move.
 *
 * <p>Both redirects have to defer or neither can: the write produces the temp file that the
 * replace consumes, and running one on the worker while the other runs on the server thread would
 * race on that file. They go to the same single worker, which keeps them ordered.
 */
@Mixin(PlayerDataStorage.class)
public class PlayerDataStorageMixin {
    @Redirect(
            method = "save",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/nio/file/Path;)V")
    )
    private void duty$writeAsync(CompoundTag data, Path path, Player player) {
        if (!AsyncWorldSave.enabled()) {
            duty$write(data, path, player);
            return;
        }
        AsyncWorldSave.submit("player data", () -> duty$write(data, path, player));
    }

    @Redirect(
            method = "save",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/util/Util;safeReplaceFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;)V")
    )
    private void duty$replaceAsync(Path target, Path temp, Path backup, Player player) {
        if (!AsyncWorldSave.enabled()) {
            Util.safeReplaceFile(target, temp, backup);
            return;
        }
        // Queued behind the write above, so the temp file exists by the time this runs.
        AsyncWorldSave.submit("player data", () -> Util.safeReplaceFile(target, temp, backup));
    }

    @org.spongepowered.asm.mixin.Unique
    private void duty$write(CompoundTag data, Path path, Player player) {
        try {
            NbtIo.writeCompressed(data, path);
        } catch (Exception e) {
            net.dutymod.core.DutyLog.warn("Could not save player data for "
                    + player.getName().getString() + ": " + e);
        }
    }
}

package net.dutymod.server.mixin.save;

import net.dutymod.server.save.AsyncWorldSave;
import net.minecraft.util.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes {@code level.dat} on the save worker instead of the server thread.
 *
 * <p>Four other installed mods patch {@code LevelStorageAccess} -- drop_26_3, lithostitched and
 * both Xaero maps -- so this was checked against their compiled mixins before being written: none
 * of them reference {@code saveLevelData}, which is the only method touched here.
 *
 * <p>The temp-file-then-{@code safeReplaceFile} sequence is vanilla's own and is kept exactly. It
 * is what makes moving this off-thread safe: the swap is atomic, so a write that never completes
 * leaves the previous {@code level.dat} in place.
 */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public class LevelStorageAccessMixin {
    @Shadow
    @Final
    private LevelStorageSource.LevelDirectory levelDirectory;

    /**
     * @author Duty (idea from FastAsyncWorldSave by someaddons)
     * @reason Move the write off the server thread; the body is vanilla's, relocated.
     */
    @Overwrite
    private void saveLevelData(CompoundTag levelData) {
        if (!AsyncWorldSave.enabled()) {
            duty$write(levelData);
            return;
        }
        AsyncWorldSave.submit("level.dat", () -> duty$write(levelData));
    }

    @org.spongepowered.asm.mixin.Unique
    private void duty$write(CompoundTag levelData) {
        Path directory = this.levelDirectory.path();
        try {
            Path temp = Files.createTempFile(directory, "level", ".dat");
            NbtIo.writeCompressed(levelData, temp);
            Util.safeReplaceFile(this.levelDirectory.dataFile(), temp, this.levelDirectory.oldDataFile());
        } catch (Exception e) {
            net.dutymod.core.DutyLog.warn("Could not write level.dat: " + e);
        }
    }
}

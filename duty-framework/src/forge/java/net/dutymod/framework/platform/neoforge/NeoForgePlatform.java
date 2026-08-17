package net.dutymod.framework.platform.neoforge;

import net.dutymod.framework.platform.DutyPlatform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@link DutyPlatform} for NeoForge.
 *
 * <p>Covers both NeoForge targets. The 1.21.1 and 26.1.2 FML APIs used here are the same calls, so
 * one implementation serves both; if they diverge, this file is what gets a second copy rather than
 * anything in {@code src/main}.
 *
 * <p>Nothing outside this source set may import {@code net.neoforged}. That is the whole point of
 * the split: {@code src/main} has to compile against a loader that is not present, so a stray
 * import there is a build failure on Fabric rather than something discovered at runtime.
 */
public final class NeoForgePlatform implements DutyPlatform {
    @Override
    public Loader loader() {
        return Loader.NEOFORGE;
    }

    @Override
    public String minecraftVersion() {
        return FMLLoader.getCurrent().getVersionInfo().mcVersion();
    }

    @Override
    public boolean isModLoadedAtStartup(String modId) {
        return FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public Optional<String> modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
    }

    @Override
    public String modName(String modId) {
        var file = FMLLoader.getCurrent().getLoadingModList().getModFileById(modId);
        if (file == null || file.getMods().isEmpty()) {
            return modId;
        }
        return file.getMods().get(0).getDisplayName();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLLoader.getCurrent().isProduction();
    }
}

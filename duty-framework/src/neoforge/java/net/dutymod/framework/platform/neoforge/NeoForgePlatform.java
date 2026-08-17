package net.dutymod.framework.platform.neoforge;

import net.dutymod.framework.platform.DutyPlatform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

/**
 * {@link DutyPlatform} for NeoForge on Minecraft 1.21.1.
 *
 * <p>The API here differs from the 26.1 branch's copy of this file, which is why the file exists
 * per branch rather than being shared. FML 4.x exposes the loader through static methods --
 * {@code FMLLoader.getLoadingModList()}, {@code FMLLoader.isProduction()} -- where 26.1 routes
 * everything through an {@code FMLLoader.getCurrent()} instance. Nothing outside this class had to
 * change for that.
 *
 * <p>Nothing outside this source set may import {@code net.neoforged};
 * {@code checkMainIsLoaderNeutral} fails the build if anything does.
 */
public final class NeoForgePlatform implements DutyPlatform {
    @Override
    public Loader loader() {
        return Loader.NEOFORGE;
    }

    @Override
    public String minecraftVersion() {
        return FMLLoader.versionInfo().mcVersion();
    }

    @Override
    public boolean isModLoadedAtStartup(String modId) {
        return FMLLoader.getLoadingModList().getModFileById(modId) != null;
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
        var file = FMLLoader.getLoadingModList().getModFileById(modId);
        if (file == null || file.getMods().isEmpty()) {
            return modId;
        }
        return file.getMods().get(0).getDisplayName();
    }

    @Override
    public boolean isDevelopment() {
        return !FMLLoader.isProduction();
    }
}

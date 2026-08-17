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
        ModList loaded = ModList.get();
        if (loaded == null) {
            // Not an NPE. Thrown from a mixin config plugin, an NPE gets swallowed into "Failed to
            // select mixin config" and every mixin in that file stops applying without anything
            // saying why. This at least names the mistake and the method that fixes it.
            throw new IllegalStateException(
                    "isModLoaded(\"" + modId + "\") was called before the mod list exists. Use "
                            + "isModLoadedAtStartup from a mixin config plugin or any other code "
                            + "that runs during class loading.");
        }
        return loaded.isLoaded(modId);
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Answers before mods are constructed as well as after, which is not decoration: a mixin
     * config plugin asks this from {@code onLoad}, and {@link ModList#get()} returns {@code null}
     * that early. Reading it through {@code ModList} alone threw an NPE out of
     * {@code MixinConfig.onSelect}, and mixin's response to a plugin that throws is to drop the
     * whole config -- so every mixin in {@code duty_client.mixins.json} silently stopped applying,
     * with one warning buried among hundreds to say so.
     *
     * <p>The loading list is checked first because it is the one that exists at both times.
     */
    @Override
    public Optional<String> modVersion(String modId) {
        var file = FMLLoader.getCurrent().getLoadingModList().getModFileById(modId);
        if (file != null && !file.getMods().isEmpty()) {
            return Optional.of(file.getMods().get(0).getVersion().toString());
        }
        ModList loaded = ModList.get();
        if (loaded == null) {
            return Optional.empty();
        }
        return loaded.getModContainerById(modId)
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

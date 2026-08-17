package net.dutymod.framework;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The handful of loader questions Duty's modules ask.
 *
 * <p>Four separate versions of this arrived with the mods Duty merged: a static helper in
 * duty-client's baked block entities, a {@code ServiceLoader} interface in its batching code,
 * another {@code ServiceLoader} interface in duty-server's biome search, and a
 * {@code Class.forName} lookup in duty-fixerupper. Each existed to let one mod run on both Fabric
 * and NeoForge. Duty runs on NeoForge, so all four resolved to the same two or three calls behind
 * three different indirections.
 *
 * <p>Collapsing them is not only tidiness. One of those {@code ServiceLoader} files is what took
 * the game down after the {@code ifast} package was renamed: the service name is a string, nothing
 * checks it, and the module descriptor failed to build. An abstraction with one implementation
 * costs a crash class for nothing.
 *
 * <h2>Two ways to ask whether a mod is present, and they are not interchangeable</h2>
 *
 * <p>{@link #isModLoadedAtStartup} reads the <em>loading</em> mod list, which exists before mods are
 * constructed. That is the only one a mixin config plugin may use, because plugins run while the
 * game is still deciding what to load.
 *
 * <p>{@link #isModLoaded} reads the loaded mod list and is correct everywhere else. Calling it too
 * early does not return false -- it throws, because the list is not built yet.
 *
 * <p>The names each say when they apply. The version this replaced had {@code isModLoaded} and
 * {@code hasLoadingMod} side by side on one interface, which said nothing about which was safe
 * where.
 */
public final class DutyPlatform {
    private DutyPlatform() {}

    /**
     * Whether {@code modId} is in the list of mods about to load.
     *
     * <p>Safe from a mixin config plugin. Answers for mods that will load, including any that later
     * fail to construct.
     */
    public static boolean isModLoadedAtStartup(String modId) {
        return FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null;
    }

    /**
     * Whether {@code modId} finished loading.
     *
     * <p>Not usable from a mixin config plugin -- see the class note. Use it from mod constructors,
     * events and gameplay code.
     */
    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** {@return the {@code config} directory} */
    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /** {@return {@code modId}'s version, or empty if it is not present} */
    public static Optional<String> modVersion(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getVersion().toString());
    }

    /** {@return {@code modId}'s display name, or the id itself if it is not present} */
    public static String modName(String modId) {
        var file = FMLLoader.getCurrent().getLoadingModList().getModFileById(modId);
        if (file == null || file.getMods().isEmpty()) {
            return modId;
        }
        return file.getMods().get(0).getDisplayName();
    }
}

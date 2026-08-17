package net.dutymod.framework.platform;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The loader questions Duty asks, with one answer per loader.
 *
 * <p>Duty targets four combinations and only ever these four:
 *
 * <table border="1">
 *   <caption>Supported targets</caption>
 *   <tr><th>Loader</th><th>Minecraft</th><th>Java</th></tr>
 *   <tr><td>Forge</td><td>1.20.1</td><td>17</td></tr>
 *   <tr><td>NeoForge</td><td>1.21.1</td><td>21</td></tr>
 *   <tr><td>NeoForge</td><td>26.1.2+</td><td>25</td></tr>
 *   <tr><td>Fabric</td><td>26.1.2+</td><td>25</td></tr>
 * </table>
 *
 * <p>Four separate versions of this interface arrived with the mods Duty merged -- a static helper
 * in duty-client's baked block entities, a {@code ServiceLoader} interface in its batching code,
 * another in duty-server's biome search, and a {@code Class.forName} lookup in duty-fixerupper.
 * Each was one mod's answer to the same question. This is the single one they collapse into.
 *
 * <h2>Why the interface stayed rather than being inlined</h2>
 *
 * <p>It was going to be deleted. On a NeoForge-only Duty an interface with one implementation is
 * pure indirection, and one of the {@code ServiceLoader} files it replaces is what crashed startup
 * after a package rename -- the service name is a string and nothing checked it. With four targets
 * the indirection is the point, and the string is now covered by
 * {@code tools/check-class-strings.py}, which exists because of that crash.
 *
 * <h2>Two ways to ask whether a mod is present, and they are not interchangeable</h2>
 *
 * <p>{@link #isModLoadedAtStartup} reads the loading mod list, which exists before mods are
 * constructed. It is the only one a mixin config plugin may call, because plugins run while the
 * game is still deciding what to load.
 *
 * <p>{@link #isModLoaded} reads the loaded list and is right everywhere else. Calling it too early
 * does not return false -- on some loaders it throws, because the list is not built yet.
 *
 * <p>The names say when each applies. The interface this replaces had {@code isModLoaded} and
 * {@code hasLoadingMod} side by side, which said nothing about which was safe where.
 */
public interface DutyPlatform {
    /** {@return the running loader} */
    Loader loader();

    /** {@return the Minecraft version, as the loader reports it, e.g. {@code "26.1.2"}} */
    String minecraftVersion();

    /** {@return whether {@code modId} is in the list of mods about to load} */
    boolean isModLoadedAtStartup(String modId);

    /** {@return whether {@code modId} finished loading} */
    boolean isModLoaded(String modId);

    /** {@return the {@code config} directory} */
    Path configDir();

    /** {@return {@code modId}'s version, or empty if it is absent} */
    Optional<String> modVersion(String modId);

    /** {@return {@code modId}'s display name, or the id itself if it is absent} */
    String modName(String modId);

    /**
     * {@return every installed mod, id to version, in no particular order}
     *
     * <p>For the report, and it is the single most useful thing in it. A crash caused by a mod
     * being the wrong version looks exactly like a crash caused by a bug until somebody lists what
     * is actually loaded -- which is how an hour went into a stock Iris sitting next to the fork
     * built to replace it.
     *
     * <p>Implementations return an unmodifiable map and never null; an empty map means the loader
     * was asked too early, not that nothing is installed.
     */
    java.util.Map<String, String> installedMods();

    /** {@return whether this is a development environment} */
    boolean isDevelopment();

    /** The loaders Duty runs on. */
    enum Loader {
        FORGE("Forge"),
        NEOFORGE("NeoForge"),
        FABRIC("Fabric");

        private final String displayName;

        Loader(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }
}

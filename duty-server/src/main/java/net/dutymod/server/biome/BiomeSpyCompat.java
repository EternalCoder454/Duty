package net.dutymod.server.biome;

import net.dutymod.framework.DutyLog;
import net.minecraft.world.level.biome.BiomeSource;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reports, once per biome source type, that BiomeSpy has stood aside.
 *
 * <p>The accelerated structure search only works against a plain {@code MultiNoiseBiomeSource},
 * because it decides where a structure can be by testing that source's climate envelope. Mods that
 * layer biomes on top -- Lithostitched's {@code InjectorBiomeSource}, Biolith's equivalent -- wrap
 * the source rather than subclass it, and they change which biomes generate. Reading the envelope
 * through the wrapper would answer questions about a world that is no longer the one being played.
 *
 * <p>So BiomeSpy declines, and vanilla's search runs. That is the safe outcome and always was. The
 * problem was that it happened without a word: the only sign was {@code /locate} being slow again,
 * which is not something anyone would trace back to a biome mod.
 *
 * <p>Once per source type, not per call. A single {@code /locate} reaches the search thousands of
 * times, and a message per call would bury the log it is meant to help with.
 */
public final class BiomeSpyCompat {
    private static final Set<Class<?>> REPORTED = ConcurrentHashMap.newKeySet();

    private BiomeSpyCompat() {}

    public static void reportUnsupported(BiomeSource biomeSource) {
        Class<?> type = biomeSource.getClass();
        if (!REPORTED.add(type)) {
            return;
        }
        DutyLog.info("Accelerated structure search is standing aside for " + type.getName()
                + ", which is not a plain MultiNoiseBiomeSource. A mod is layering biomes on top of"
                + " the world's own, so the fast search cannot tell where a structure may generate"
                + " and vanilla's search runs instead. /locate stays correct; it is only slower.");
    }
}

package net.dutymod.server.biome.biome;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// BiomeSpy's TerraBlender compatibility is not carried over: it compiles against
// TerraBlender's API, which is only published as a CurseForge artifact pinned by numeric
// file id, and the pack does not use TerraBlender. The branches that needed it are gone;
// vanilla biome lookup is what remains. To restore it, take the compat package from
// BiomeSpy upstream and add TerraBlender as a compileOnly dependency.
public class BiomeEnvelopeSelector {
    private final Map<Integer, BiomeEnvelope> envelopeMap;
    private final Map<Class<?>, Object> platformData = new HashMap<>();

    public BiomeEnvelopeSelector(Collection<Holder<Biome>> biomes, Climate.ParameterList<Holder<Biome>> parameters, MultiNoiseBiomeSource biomeSource) {
        this.envelopeMap = new HashMap<>();
        {
            BiomeEnvelope combinedEnvelope = new BiomeEnvelope();
            combinedEnvelope.impossible = true;
            for (var pair : parameters.values()) {
                if (biomes.contains(pair.getSecond())) {
                    combinedEnvelope.impossible = false;
                    combinedEnvelope.add(pair.getFirst());
                }
            }
            this.envelopeMap.put(0, combinedEnvelope);
        }
        for (Integer i : this.envelopeMap.keySet()) {
            BiomeEnvelope env = this.envelopeMap.get(i);
            if (!env.isValid())
                this.envelopeMap.put(i, new BiomeEnvelope()); // Full range
        }
        //Services.PLATFORM.initPlatformSpecificBiomeEnvelope(this, biomes, parameters, biomeSource);
    }

    public BiomeEnvelope getEnvelope(Climate.ParameterList<Holder<Biome>> parameters, int qx, int qy, int qz) {
//        BiomeEnvelope platformEnvelope = Services.PLATFORM.getPlatformSpecificBiomeEnvelope(this, parameters, qx, qy, qz);
//        if (platformEnvelope != null) {
//            return platformEnvelope;
//        }
        return envelopeMap.get(0);
    }

    public <T> void setPlatformData(Class<?> key, T data) {
        platformData.put(key, data);
    }

    public Object getPlatformData(Class<?> key) {
        return platformData.get(key);
    }
}

package net.dutymod.memory.mixin.tags;

import com.mojang.serialization.Codec;
import net.dutymod.memory.tags.FastTag;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ResourceKey.class)
@SuppressWarnings("rawtypes")
public class ResourceKeyMixin {

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> Codec codec(ResourceKey<? extends Registry<T>> registryName) {
        var cache = FastTag.getResourceKeyCache(registryName);
        return ResourceLocation.CODEC.xmap(cache::getCache, ResourceKey::identifier);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> StreamCodec streamCodec(ResourceKey<? extends Registry<T>> registryName) {
        var cache = FastTag.getResourceKeyCache(registryName);
        return ResourceLocation.STREAM_CODEC.map(cache::getCache, ResourceKey::identifier);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> ResourceKey create(ResourceKey<? extends Registry<T>> registryName, ResourceLocation location) {
        return FastTag.getResourceKeyCache(registryName).getCache(location);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static ResourceKey createRegistryKey(ResourceLocation identifier) {
        return FastTag.getRootResourceKey(identifier);
    }

    /**
     * @author nutant233
     * @reason deprecated
     */
    @Overwrite
    private static <T> ResourceKey<T> create(ResourceLocation registryName, ResourceLocation identifier) {
        throw new UnsupportedOperationException("[FastTag] This method is deprecated");
    }
}

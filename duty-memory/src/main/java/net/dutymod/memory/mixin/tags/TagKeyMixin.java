package net.dutymod.memory.mixin.tags;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.dutymod.memory.tags.FastTag;
import net.minecraft.core.Registry;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(TagKey.class)
@SuppressWarnings("rawtypes")
public class TagKeyMixin {

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> Codec codec(ResourceKey<? extends Registry<T>> registryName) {
        var cache = FastTag.getTagCache(registryName);
        return Identifier.CODEC.xmap(cache::getCache, TagKey::location);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> Codec hashedCodec(ResourceKey<? extends Registry<T>> registryName) {
        var cache = FastTag.getTagCache(registryName);
        return Codec.STRING.comapFlatMap(name -> name.startsWith("#") ? Identifier.read(name.substring(1)).map(cache::getCache) : DataResult.error(() -> "Not a tag id"), e -> "#" + e.location());
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> StreamCodec streamCodec(ResourceKey<? extends Registry<T>> registryName) {
        var cache = FastTag.getTagCache(registryName);
        return Identifier.STREAM_CODEC.map(cache::getCache, TagKey::location);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite
    public static <T> TagKey create(ResourceKey<? extends Registry<T>> registry, Identifier location) {
        return FastTag.getTagCache(registry).getCache(location);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite(remap = false)
    public final int hashCode() {
        return System.identityHashCode(this);
    }

    /**
     * @author nutant233
     * @reason optimize
     */
    @Overwrite(remap = false)
    public final boolean equals(Object o) {
        return o == this;
    }
}

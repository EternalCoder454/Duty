package net.dutymod.fixerupper.common.mixin.perf.dynamic_resources;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.client.resources.model.ModelDiscovery;
import net.minecraft.resources.ResourceLocation;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelDiscovery.class)
@ClientOnlyMixin
public interface ModelDiscoveryAccessor {
    @Accessor("modelWrappers")
    Object2ObjectMap<ResourceLocation, ModelDiscovery.ModelWrapper> duty$getModelWrappers();
}

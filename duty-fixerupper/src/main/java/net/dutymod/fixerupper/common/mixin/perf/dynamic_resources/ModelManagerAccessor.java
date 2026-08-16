package net.dutymod.fixerupper.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(ModelManager.class)
@ClientOnlyMixin
public interface ModelManagerAccessor {
    @Accessor("bakedItemStackModels")
    Map<Identifier, ItemModel> duty$getBakedItemModels();
}

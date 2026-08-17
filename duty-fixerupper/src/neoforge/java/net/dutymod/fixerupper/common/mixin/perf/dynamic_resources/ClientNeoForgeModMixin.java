package net.dutymod.fixerupper.common.mixin.perf.dynamic_resources;

import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.ClientNeoForgeMod;
import net.dutymod.fixerupper.FixerUpper;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientNeoForgeMod.class)
@ClientOnlyMixin
public class ClientNeoForgeModMixin {
    /**
     * @author embeddedt
     * @reason avoid triggering eager load of every item model
     */
    @Redirect(method = "lambda$new$7", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/ModelManager;getItemModel(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/item/ItemModel;"))
    private static ItemModel checkExistenceWithoutLoadingModel(ModelManager instance, Identifier id) {
        if (!((ModelManagerAccessor)instance).duty$getBakedItemModels().containsKey(id)) {
            FixerUpper.LOGGER.warn("Missing item model '{}'", id);
        }
        return null;
    }
}

package net.dutymod.client.mixin.crafting;

import net.dutymod.client.crafting.ClientCrafting;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookComponent.class)
public class RecipeBookComponentMixin<T extends RecipeBookMenu>
{
    @Shadow
    @Final
    protected T menu;

    @Inject(method = "tryPlaceRecipe", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V"))
    private void placeNow(final RecipeCollection recipeCollection, final RecipeDisplayId displayId, final boolean p_446681_, final CallbackInfoReturnable<Boolean> cir)
    {
        ClientCrafting.tryPlaceRecipe(recipeCollection, displayId, menu);
    }
}

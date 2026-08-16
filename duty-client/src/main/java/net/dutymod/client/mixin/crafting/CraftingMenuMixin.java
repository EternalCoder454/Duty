package net.dutymod.client.mixin.crafting;

import net.dutymod.client.crafting.ClientCrafting;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuMixin extends AbstractCraftingMenu
{
    @Shadow
    @Final
    @Mutable
    private ContainerLevelAccess access;

    @Shadow
    @Final
    private Player player;

    public CraftingMenuMixin(final MenuType<?> p_362493_, final int p_360673_, final int p_364200_, final int p_363034_)
    {
        super(p_362493_, p_360673_, p_364200_, p_363034_);
    }

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void onInitContainerAccess(final int p_39356_, final Inventory p_39357_, final ContainerLevelAccess containerLevelAccess, final CallbackInfo ci)
    {
        if (containerLevelAccess == ContainerLevelAccess.NULL)
        {
            this.access = ContainerLevelAccess.create(player.level(), player.blockPosition());
        }
    }

    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void showClientRecipe(
        final Container p_39366_, final CallbackInfo ci)
    {
        this.access.execute((level, p_379188_) -> {
            if (level.isClientSide())
            {
                ClientCrafting.oncraft(level, (CraftingMenu) (Object) this);
            }
        });
    }
}

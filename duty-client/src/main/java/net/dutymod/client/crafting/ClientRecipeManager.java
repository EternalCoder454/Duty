package net.dutymod.client.crafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeManager;

public class ClientRecipeManager extends RecipeManager
{
    public ClientRecipeManager(final HolderLookup.Provider provider)
    {
        super(provider);
    }
}

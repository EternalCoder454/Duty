package net.dutymod.client.crafting;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ClientCrafting
{
    private static List<ItemStack> lastItems = new ArrayList<>();
    private static long               lastTickCount = 0;
    private static RecipeDisplayEntry lastRecipe    = null;
    private static ItemStack          lastSet       = ItemStack.EMPTY;

    /**
     * Do clientside crafting
     *
     * @param level
     * @param craftingMenu
     */
    public static void oncraft(final Level level, final CraftingMenu craftingMenu)
    {
        if (craftingMenu.getResultSlot().getItem().equals(lastSet))
        {
            lastSet = ItemStack.EMPTY;
            craftingMenu.getResultSlot().set(lastSet);
            craftingMenu.setRemoteSlot(0, lastSet);
        }
        else if (craftingMenu.getResultSlot().hasItem())
        {
            return;
        }

        final ClientRecipeBook recipeBook = Minecraft.getInstance().player.getRecipeBook();
        boolean matches = true;
        if (lastTickCount == level.getGameTime())
        {
            List<Slot> slotList = craftingMenu.getInputGridSlots();
            if (slotList.size() == lastItems.size())
            {
                for (int i = 0; i < slotList.size(); i++)
                {
                    if (!ItemStack.isSameItemSameComponents(slotList.get(i).getItem(), lastItems.get(i)))
                    {
                        matches = false;
                    }
                }
            }
            else
            {
                matches = false;
            }
        }

        if (lastTickCount != level.getGameTime() || !matches)
        {
            lastTickCount = level.getGameTime();

            List<Slot> slotList = craftingMenu.getInputGridSlots();
            lastItems = new ArrayList<>();
            for (final Slot slot : slotList)
            {
                lastItems.add(slot.getItem().copy());
            }

            StackedItemContents contents = new StackedItemContents();
            craftingMenu.fillCraftSlotsStackedContents(contents);
            List<RecipeCollection> allCraftingCollections = new ArrayList<>();
            allCraftingCollections.addAll(recipeBook.getCollection(RecipeBookCategories.CRAFTING_BUILDING_BLOCKS));
            allCraftingCollections.addAll(recipeBook.getCollection(RecipeBookCategories.CRAFTING_EQUIPMENT));
            allCraftingCollections.addAll(recipeBook.getCollection(RecipeBookCategories.CRAFTING_MISC));
            allCraftingCollections.addAll(recipeBook.getCollection(RecipeBookCategories.CRAFTING_REDSTONE));

            lastRecipe = null;

            final List<Slot> trimmedSlots = new ArrayList<>(craftingMenu.getInputGridSlots());

            if (trimmedSlots.isEmpty())
            {
                return;
            }

            int minRow = Integer.MAX_VALUE;
            int maxRow = Integer.MIN_VALUE;
            int minColumn = Integer.MAX_VALUE;
            int maxColumn = Integer.MIN_VALUE;

            for (final Slot slot : trimmedSlots)
            {
                if (slot.getItem().isEmpty())
                {
                    continue;
                }

                final int row = getRow(slot.index);
                final int column = getColumn(slot.index);
                minRow = Math.min(minRow, row);
                maxRow = Math.max(maxRow, row);
                minColumn = Math.min(minColumn, column);
                maxColumn = Math.max(maxColumn, column);
            }

            for (Iterator<Slot> iterator = trimmedSlots.iterator(); iterator.hasNext(); )
            {
                final Slot slot = iterator.next();
                final int row = getRow(slot.index);
                final int column = getColumn(slot.index);
                if (!(row >= minRow && row <= maxRow && column >= minColumn && column <= maxColumn))
                {
                    iterator.remove();
                }
            }

            if (trimmedSlots.isEmpty())
            {
                return;
            }

            int width = (maxColumn - minColumn) + 1;
            int height = (maxRow - minRow) + 1;
            final ContextMap contextMap = SlotDisplayContext.fromLevel(level);
            search:
            for (final RecipeCollection collection : allCraftingCollections)
            {
                for (final RecipeDisplayEntry recipeDisplayEntry : collection.getRecipes())
                {
                    if (recipeDisplayEntry.canCraft(contents) && canDisplay(recipeDisplayEntry.display(), craftingMenu))
                    {
                        final List<Ingredient> ingredientList = recipeDisplayEntry.craftingRequirements().get();
                        boolean allMatch = true;
                        if (recipeDisplayEntry.display() instanceof ShapedCraftingRecipeDisplay shapedRecipeDisplay)
                        {
                            if (shapedRecipeDisplay.width() != width || shapedRecipeDisplay.height() != height)
                            {
                                continue;
                            }

                            //.getFirst().resolveForFirstStack()
                            final List<SlotDisplay> ingredients = shapedRecipeDisplay.ingredients();

                            int requirementsIndex = 0;
                            for (int i = 0; i < ingredients.size(); i++)
                            {
                                final ItemStack ingredientStack = ingredients.get(i).resolveForFirstStack(contextMap);
                                final ItemStack slotStack = trimmedSlots.get(i).getItem();

                                if (!ingredientStack.isEmpty())
                                {
                                    requirementsIndex++;
                                    if (!ingredientList.get(requirementsIndex - 1).test(slotStack))
                                    {
                                        allMatch = false;
                                        break;
                                    }
                                }
                            }
                        }
                        else
                        {
                            // TODO: Check this for shapeless only?
                            if (ingredientList.size() != trimmedSlots.size())
                            {
                                continue;
                            }

                            for (int i = 0; i < ingredientList.size(); i++)
                            {
                                final Ingredient ingredient = ingredientList.get(i);
                                final Slot slot = trimmedSlots.get(i);
                                if (!ingredient.test(slot.getItem()))
                                {
                                    allMatch = false;
                                    break;
                                }
                            }
                        }

                        if (allMatch && lastRecipe == null)
                        {
                            lastRecipe = recipeDisplayEntry;
                        }
                        else if (allMatch)
                        {
                            lastRecipe = null;
                            break search;
                        }
                    }
                }
            }
        }

        List<ItemStack> itemStacks = new ArrayList<>();
        if (lastRecipe != null)
        {
            StackedItemContents contents = new StackedItemContents();
            craftingMenu.fillCraftSlotsStackedContents(contents);

            if (lastRecipe.canCraft(contents))
            {
                itemStacks = lastRecipe.resultItems(SlotDisplayContext.fromLevel(level));
            }
        }

        if (itemStacks.size() == 1)
        {
            lastSet = itemStacks.get(0).copy();
            craftingMenu.getResultSlot().set(lastSet);
            craftingMenu.setRemoteSlot(0, lastSet);
        }
    }

    public static <T extends RecipeBookMenu> void tryPlaceRecipe(final RecipeCollection recipeCollection, final RecipeDisplayId displayId, final T menu)
    {
        RecipeDisplayEntry displayEntry = null;
        for (final RecipeDisplayEntry entry : recipeCollection.getRecipes())
        {
            if (entry.id().equals(displayId))
            {
                displayEntry = entry;
                break;
            }
        }

        if (!(menu instanceof CraftingMenu craftingMenu) || displayEntry == null)
        {
            return;
        }

        int height = 3;
        int width = 3;

        List<SlotDisplay> ingredientList = new ArrayList<>();
        final ContextMap contextMap = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
        if (displayEntry.display() instanceof ShapedCraftingRecipeDisplay shapedCraftingRecipeDisplay)
        {
            ingredientList = shapedCraftingRecipeDisplay.ingredients();
            height = shapedCraftingRecipeDisplay.height();
            width = shapedCraftingRecipeDisplay.width();
        }

        if (displayEntry.display() instanceof ShapelessCraftingRecipeDisplay shapelessCraftingRecipeDisplay)
        {
            ingredientList = shapelessCraftingRecipeDisplay.ingredients();
        }

        if (ingredientList.isEmpty())
        {
            return;
        }

        List<ItemStack> stacksToUse = new ArrayList<>();
        for (final Ingredient req : displayEntry.craftingRequirements().get())
        {
            for (final ItemStack stack : Minecraft.getInstance().player.getInventory())
            {
                if (req.test(stack))
                {
                    final ItemStack contained = stack.copy();
                    contained.setCount(1);
                    stacksToUse.add(contained);
                    stack.setCount(stack.getCount() - 1);
                    break;
                }
            }
        }

        if (Minecraft.getInstance().hasShiftDown())
        {
            boolean hasAll = true;
            while (hasAll)
            {
                List<ItemStack> stacksToAdd = new ArrayList<>();
                for (final Ingredient req : displayEntry.craftingRequirements().get())
                {
                    boolean hasReq = false;
                    for (final ItemStack stack : Minecraft.getInstance().player.getInventory())
                    {
                        if (req.test(stack))
                        {
                            final ItemStack contained = stack.copy();
                            contained.setCount(1);
                            stacksToAdd.add(contained);
                            stack.setCount(stack.getCount() - 1);
                            hasReq = true;
                            break;
                        }
                    }

                    if (!hasReq)
                    {
                        stacksToAdd.forEach(stack -> Minecraft.getInstance().player.getInventory().add(stack));
                        hasAll = false;
                        break;
                    }
                }

                if (hasAll)
                {
                    stacksToUse.forEach(stack -> stack.setCount(stack.getCount() + 1));
                }
            }
        }

        if (stacksToUse.size() != ingredientList.size())
        {
            return;
        }

        int startRow = 1;
        int startColumn = 1;
        if (height == 1)
        {
            startRow = 2;
            height = 2;
        }

        if (width == 1)
        {
            startColumn = 2;
            width = 2;
        }

        int ingredientIndex = 0;
        int stackUseIndex = 0;
        for (int i = 0; i < craftingMenu.getInputGridSlots().size(); i++)
        {
            final Slot slot = craftingMenu.getInputGridSlots().get(i);
            if (ClientCrafting.getRow(slot.index) >= startRow && ClientCrafting.getRow(slot.index) <= height
                && ClientCrafting.getColumn(slot.index) >= startColumn && ClientCrafting.getColumn(slot.index) <= width)
            {
                if (ingredientList.size() > ingredientIndex && !ingredientList.get(ingredientIndex).resolveForFirstStack(contextMap).isEmpty())
                {
                    slot.set(stacksToUse.get(stackUseIndex));
                    stackUseIndex++;
                }
                ingredientIndex++;
            }
        }
    }

    public static int getRow(final int slotIndex)
    {
        if (slotIndex < 4)
        {
            return 1;
        }
        else if (slotIndex < 7)
        {
            return 2;
        }
        else
        {
            return 3;
        }
    }

    public static int getColumn(final int slotIndex)
    {
        final int columnIndex = (slotIndex - 1) % 3;
        return columnIndex + 1;
    }

    private static boolean canDisplay(RecipeDisplay p_379470_, final CraftingMenu menu)
    {
        int i = menu.getGridWidth();
        int j = menu.getGridHeight();
        Objects.requireNonNull(p_379470_);

        return switch (p_379470_)
        {
            case ShapedCraftingRecipeDisplay shapedcraftingrecipedisplay -> i >= shapedcraftingrecipedisplay.width()
                && j >= shapedcraftingrecipedisplay.height();
            case ShapelessCraftingRecipeDisplay shapelesscraftingrecipedisplay -> i * j >= shapelesscraftingrecipedisplay.ingredients().size();
            default -> false;
        };
    }
}

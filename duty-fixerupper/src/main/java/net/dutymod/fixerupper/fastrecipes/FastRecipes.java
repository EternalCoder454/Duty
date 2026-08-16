package net.dutymod.fixerupper.fastrecipes;

import net.dutymod.core.DutyConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Which recipe types get an item index, and the hooks other mods use to opt their own in.
 *
 * <p>Vanilla answers "what can I craft from this?" by testing the input against every recipe of the
 * type. On a pack with sixteen thousand crafting recipes that is sixteen thousand match calls per
 * change to the crafting grid, and mods that ask for *all* matches rather than the first -- recipe
 * conflict resolvers especially -- pay it in full every time.
 *
 * <p>{@link CachedRecipeList} indexes each recipe under the items its most selective ingredient
 * accepts, so a lookup only tests recipes that could plausibly match. Recipes that cannot be indexed
 * statically still get checked every time, which is why the index is a filter rather than a
 * replacement.
 *
 * <p>Only whitelisted types are indexed. The index assumes a recipe's ingredients are fixed once
 * loaded; a recipe type that decides its ingredients at match time would be indexed under the wrong
 * items and would silently stop matching. Crafting and the three furnace types are safe and are the
 * default. Adapted from FastSuite by Shadows-of-Fire (MIT).
 */
public final class FastRecipes {
    public static final Logger LOGGER = LogManager.getLogger("Duty/FastRecipes");

    /** Set from the environment; prints why each recipe was or was not indexed. Very noisy. */
    public static final boolean DEBUG_MATCHING = "on".equalsIgnoreCase(System.getenv("DUTY_DEBUG_RECIPE_MATCHING"));

    public static final String INDEXED_TYPES = "fixerupper.indexed_recipe_types";

    /** Recipe types that get an index. Populated once the recipe type registry is available. */
    public static final Set<RecipeType<?>> indexedTypes = new HashSet<>();

    /**
     * Recipe classes known to keep their ingredients fixed after load.
     *
     * <p>Identity-keyed: these are {@link Class} objects, so identity comparison is both correct and
     * cheaper than {@code equals}. Consulted per recipe during indexing only, not per lookup.
     */
    static final Map<Class<?>, Boolean> parallelRecipeClassCache = Collections.synchronizedMap(new IdentityHashMap<>());

    static final Map<Class<?>, Boolean> ingredientClassCache = Collections.synchronizedMap(new IdentityHashMap<>());

    private FastRecipes() {}

    static {
        DutyConfig.register(INDEXED_TYPES,
                "minecraft:crafting,minecraft:smelting,minecraft:blasting,minecraft:smoking",
                "Recipe types to build an item index for, comma separated. Indexing assumes a\n"
                        + "recipe's ingredients are fixed once loaded; a type that chooses its\n"
                        + "ingredients at match time would be filed under the wrong items and would\n"
                        + "stop matching. Only add a type you know is safe. An unknown name here is\n"
                        + "logged and skipped rather than failing the load.");
    }

    /** Resolves the configured type names. Call once the recipe type registry is populated. */
    public static void resolveIndexedTypes() {
        indexedTypes.clear();
        for (String name : DutyConfig.getString(INDEXED_TYPES).split(",")) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                RecipeType<?> type = BuiltInRegistries.RECIPE_TYPE.getValue(Identifier.parse(trimmed));
                if (type == null) {
                    LOGGER.warn("Unknown recipe type '{}' in {}; skipping it.", trimmed, INDEXED_TYPES);
                } else {
                    indexedTypes.add(type);
                }
            } catch (RuntimeException e) {
                LOGGER.warn("Malformed recipe type '{}' in {}; skipping it.", trimmed, INDEXED_TYPES);
            }
        }
        LOGGER.info("Indexing {} recipe type(s).", indexedTypes.size());
    }

    /** Declares that {@code cls} keeps its ingredients fixed after load, so it can be indexed. */
    public static void registerSafeRecipeClass(Class<? extends Recipe<?>> cls) {
        parallelRecipeClassCache.put(cls, true);
    }

    /** Declares that {@code cls} reports a fixed item list, so it can be used as a pivot. */
    public static void registerSafeIngredientClass(Class<? extends Ingredient> cls) {
        ingredientClassCache.put(cls, true);
    }
}

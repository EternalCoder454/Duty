package net.dutymod.core.screen;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.dutymod.core.DutyConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds Duty's settings screen from whatever is registered in {@link DutyConfig}.
 *
 * <p>Nothing here is hand-written per option. {@code DutyConfig} already holds every option's key,
 * default and help text, so the screen is generated from that registry: adding an option to a
 * module puts it on the screen with its comment as the tooltip, and no second place to update.
 *
 * <p><b>This class must only be loaded when Cloth Config is present.</b> It refers to
 * {@code me.shedaniel.clothconfig2} directly, so touching it without the mod installed throws
 * {@link NoClassDefFoundError}. {@link DutyConfigScreens} is the gate; do not reference this class
 * from anywhere else.
 *
 * <p>Options are grouped into a category per module, taken from the prefix of their key
 * ({@code memory.}, {@code client.}, {@code server.}, {@code fixerupper.}), and into sub-categories
 * within that -- see {@link #SUBCATEGORY_RULES}. FixerUpper alone contributes around two hundred
 * options, so a flat list per module would not be usable. Only modules that are actually installed
 * have registered anything, so the screen shows exactly the options that do something here.
 *
 * <p><b>Every entry is marked {@code requireRestart}</b>, and that is accurate rather than lazy.
 * Duty's options are read from three places that all run once: mixin config plugins, which decide
 * at class-load time whether a mixin applies at all; the enum transformer, which runs before the
 * mod list even exists; and cached fields on per-packet and per-frame paths. Almost nothing
 * re-reads its option, so presenting any of them as live would mean the value changes in the file
 * and nothing happens until the next launch.
 *
 */
public final class ClothConfigScreen {
    private ClothConfigScreen() {}

    /** Human-readable names for the key prefixes each module registers under. */
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "memory", "Duty: Memory",
            "client", "Duty: Client",
            "server", "Duty: Server",
            "fixerupper", "Duty: FixerUpper");

    /**
     * Ordered rules mapping an option name to the sub-category it appears under.
     *
     * <p>Matched by substring, first rule wins, so the order is the priority order -- {@code
     * hide_own_potion_particles} has to reach "Particles" before anything matching {@code hide_}
     * could claim it.
     *
     * <p>These are derived from the key rather than declared alongside the option because the
     * screen is generated: a rule that matches on {@code particle} keeps working when a module adds
     * another particle option, where a hand-written list would silently leave it ungrouped. Options
     * matching nothing fall into "Other", which is the signal that a rule is missing.
     */
    private static final List<Map.Entry<String, String>> SUBCATEGORY_RULES = List.of(
            // FixerUpper: its keys are FixerUpper's, already structured as mixin.<kind>.<feature>
            Map.entry("mixin.perf.", "Performance"),
            Map.entry("mixin.bugfix.", "Bug fixes"),
            Map.entry("mixin.feature.", "Features"),
            Map.entry("mixin.safety.", "Safety"),
            Map.entry("mixin.devenv", "Development"),
            Map.entry("stability_level", "Stability"),

            // Client
            Map.entry("particle", "Particles"),
            Map.entry("cull", "Culling"),
            Map.entry("nametags_through_walls", "Culling"),
            Map.entry("solid_leaves", "Culling"),
            Map.entry("chat", "Chat"),
            Map.entry("announce_advancements", "Chat"),
            Map.entry("toast", "Toasts and overlays"),
            Map.entry("splash", "Toasts and overlays"),
            Map.entry("fade", "Toasts and overlays"),
            Map.entry("loading_terrain", "Toasts and overlays"),
            Map.entry("world_advice", "Toasts and overlays"),
            Map.entry("thread_priority", "Thread priorities"),
            Map.entry("block_entities", "Block entities"),
            Map.entry("animate_textures", "Rendering"),
            Map.entry("render_weather", "Rendering"),
            Map.entry("night_vision_flicker", "Rendering"),
            Map.entry("delete_to_trash", "Miscellaneous"),
            Map.entry("unfocused_volume", "Miscellaneous"),

            // Memory
            Map.entry("block_state", "Block states"),
            Map.entry("property_map", "Block states"),
            Map.entry("compact_state", "Block states"),
            Map.entry("enum_values", "Enums"),
            Map.entry("tag_key_interning", "Tags and components"),
            Map.entry("data_component", "Tags and components"),

            // Server
            Map.entry("compression", "Compression"),
            Map.entry("compress", "Compression"),
            Map.entry("encryption", "Encryption"),
            Map.entry("varint", "Packet codec"),
            Map.entry("varlong", "Packet codec"),
            Map.entry("packet", "Packet codec"),
            Map.entry("structure_search", "World generation"),
            Map.entry("alternate_current", "Redstone"),
            Map.entry("async_world_save", "Saving"));

    /** {@return the sub-category {@code name} belongs to} */
    private static String subCategoryOf(String name) {
        for (Map.Entry<String, String> rule : SUBCATEGORY_RULES) {
            if (name.contains(rule.getKey())) {
                return rule.getValue();
            }
        }
        return "Other";
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Duty"))
                .setSavingRunnable(() -> {});
        ConfigEntryBuilder entries = builder.entryBuilder();

        // Category order follows first appearance, which is module registration order; within a
        // category, sub-categories and their entries keep the order their options were registered.
        Map<String, ConfigCategory> categories = new LinkedHashMap<>();
        Map<String, SubCategoryBuilder> subCategories = new LinkedHashMap<>();

        for (DutyConfig.Option option : DutyConfig.options()) {
            String key = option.key();
            int dot = key.indexOf('.');
            String prefix = dot > 0 ? key.substring(0, dot) : "other";
            String name = key.substring(dot + 1);

            categories.computeIfAbsent(prefix, p -> builder.getOrCreateCategory(
                    Component.literal(CATEGORY_NAMES.getOrDefault(p, "Duty: " + capitalise(p)))));
            String groupName = subCategoryOf(name);
            SubCategoryBuilder group = subCategories.computeIfAbsent(prefix + '/' + groupName,
                    k -> entries.startSubCategory(Component.literal(groupName)));

            String current = DutyConfig.rawOrDefault(key);
            Component label = Component.literal(prettify(name));
            Component[] tooltip = tooltip(option);

            if (isBoolean(option.defaultValue())) {
                boolean value = Boolean.parseBoolean(current);
                group.add(entries
                        .startBooleanToggle(label, value)
                        .setDefaultValue(Boolean.parseBoolean(option.defaultValue()))
                        .setTooltip(tooltip)
                        .requireRestart()
                        .setSaveConsumer(v -> DutyConfig.set(key, Boolean.toString(v)))
                        .build());
            } else if (isInteger(option.defaultValue())) {
                group.add(entries
                        .startIntField(label, parseIntOr(current, option.defaultValue()))
                        .setDefaultValue(Integer.parseInt(option.defaultValue()))
                        .setTooltip(tooltip)
                        .requireRestart()
                        .setSaveConsumer(v -> DutyConfig.set(key, Integer.toString(v)))
                        .build());
            } else {
                group.add(entries
                        .startStrField(label, current)
                        .setDefaultValue(option.defaultValue())
                        .setTooltip(tooltip)
                        .requireRestart()
                        .setSaveConsumer(v -> DutyConfig.set(key, v))
                        .build());
            }
        }

        // Attach each sub-category to its category, once every entry is in it.
        for (Map.Entry<String, SubCategoryBuilder> entry : subCategories.entrySet()) {
            String prefix = entry.getKey().substring(0, entry.getKey().indexOf('/'));
            categories.get(prefix).addEntry(entry.getValue().build());
        }

        return builder.build();
    }

    /**
     * The option's own comment, one line per tooltip line, with the config key last so it can be
     * matched up against {@code config/duty.properties} by hand.
     */
    private static Component[] tooltip(DutyConfig.Option option) {
        String[] lines = option.comment().split("\n");
        Component[] out = new Component[lines.length + 1];
        for (int i = 0; i < lines.length; i++) {
            out[i] = Component.literal(lines[i]);
        }
        out[lines.length] = Component.literal("Key: " + option.key());
        return out;
    }

    private static boolean isBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static int parseIntOr(String value, String fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(fallback);
        }
    }

    /** {@code block_state_deduplication} to {@code Block state deduplication}. */
    private static String prettify(String key) {
        String spaced = key.replace('_', ' ').replace('.', ' ');
        return capitalise(spaced);
    }

    private static String capitalise(String text) {
        if (text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}

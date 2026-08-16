package net.dutymod.core.screen;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.dutymod.core.DutyConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
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
 * <p>Options are grouped into categories by the prefix of their key ({@code memory.}, {@code
 * client.}, {@code server.}). Only modules that are actually installed have registered anything, so
 * the screen shows exactly the options that do something in this instance.
 *
 * <p><b>Every entry is marked {@code requireRestart}</b>, and that is accurate rather than lazy.
 * Duty's options are read from three places that all run once: mixin config plugins, which decide
 * at class-load time whether a mixin applies at all; the enum transformer, which runs before the
 * mod list even exists; and cached fields on per-packet and per-frame paths. Almost nothing
 * re-reads its option, so presenting any of them as live would mean the value changes in the file
 * and nothing happens until the next launch.
 *
 * <p>{@code duty-fixerupper} does not appear here. It kept ModernFix's own config system and file
 * rather than being rewritten onto {@link DutyConfig}, so its options are not in this registry.
 */
public final class ClothConfigScreen {
    private ClothConfigScreen() {}

    /** Human-readable names for the key prefixes each module registers under. */
    private static final Map<String, String> CATEGORY_NAMES = Map.of(
            "memory", "Duty: Memory",
            "client", "Duty: Client",
            "server", "Duty: Server");

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Duty"))
                .setSavingRunnable(() -> {});
        ConfigEntryBuilder entries = builder.entryBuilder();

        // Preserve registration order within a category, and category order by first appearance.
        Map<String, ConfigCategory> categories = new LinkedHashMap<>();

        for (DutyConfig.Option option : DutyConfig.options()) {
            String key = option.key();
            int dot = key.indexOf('.');
            String prefix = dot > 0 ? key.substring(0, dot) : "other";
            String name = key.substring(dot + 1);

            ConfigCategory category = categories.computeIfAbsent(prefix, p -> builder.getOrCreateCategory(
                    Component.literal(CATEGORY_NAMES.getOrDefault(p, "Duty: " + capitalise(p)))));

            String current = DutyConfig.rawOrDefault(key);
            Component label = Component.literal(prettify(name));
            Component[] tooltip = tooltip(option);

            if (isBoolean(option.defaultValue())) {
                boolean value = Boolean.parseBoolean(current);
                category.addEntry(entries
                        .startBooleanToggle(label, value)
                        .setDefaultValue(Boolean.parseBoolean(option.defaultValue()))
                        .setTooltip(tooltip)
                        .requireRestart()
                        .setSaveConsumer(v -> DutyConfig.set(key, Boolean.toString(v)))
                        .build());
            } else if (isInteger(option.defaultValue())) {
                category.addEntry(entries
                        .startIntField(label, parseIntOr(current, option.defaultValue()))
                        .setDefaultValue(Integer.parseInt(option.defaultValue()))
                        .setTooltip(tooltip)
                        .requireRestart()
                        .setSaveConsumer(v -> DutyConfig.set(key, Integer.toString(v)))
                        .build());
            } else {
                category.addEntry(entries
                        .startStrField(label, current)
                        .setDefaultValue(option.defaultValue())
                        .setTooltip(tooltip)
                        .requireRestart()
                        .setSaveConsumer(v -> DutyConfig.set(key, v))
                        .build());
            }
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

package net.dutymod.essentials.config;

import net.dutymod.core.DutyConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A per-command on/off switch and alias list.
 *
 * <p>Every command is registered here rather than unconditionally, so a command that clashes with
 * another mod's -- {@code /home} and {@code /back} are common -- can be turned off without removing
 * the module. {@link net.dutymod.essentials.command.CommandManager} consults this at registration
 * time, which is why these particular options need a restart: Brigadier's tree is built once.
 *
 * <p>Aliases are one comma-separated string rather than a list because {@link DutyConfig} stores
 * flat values. That is a fair trade for keeping one config file across the whole of Duty, and
 * {@code bc,broadcast2} is not meaningfully worse to type than a YAML sequence.
 */
public final class CommandConfig {
    /** Reading order is registration order, which keeps the generated config readable. */
    public static final Map<String, CommandEntry> COMMANDS = new LinkedHashMap<>();

    /** One command's settings. Mirrors upstream's shape so the call sites port unchanged. */
    public record CommandEntry(EssentialsOptions.Option<Boolean> enabled, Aliases aliases) {}

    /** Splits the stored comma-separated aliases on read. */
    public record Aliases(String key) {
        public List<String> get() {
            String raw = DutyConfig.getString(key);
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String part : raw.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    out.add(trimmed);
                }
            }
            return out;
        }
    }

    private static void register(String command, String... defaultAliases) {
        String enabledKey = "essentials.command." + command;
        String aliasKey = "essentials.command." + command + "_aliases";
        String joined = String.join(",", Arrays.asList(defaultAliases));

        DutyConfig.register(enabledKey, true, "Register the /" + command + " command.");
        DutyConfig.register(aliasKey, joined,
                "Comma-separated aliases for /" + command + ". Empty for none.");

        COMMANDS.put(command, new CommandEntry(
                EssentialsOptions.boolOption(enabledKey), new Aliases(aliasKey)));
    }

    static {
        register("broadcast", "bc");
        register("reply");
        register("enderchest", "ec");
        register("invsee");
        register("afk");
        register("delnick");
        register("feed");
        register("fly");
        register("gamemode", "gm");
        register("gmc");
        register("gms");
        register("gma");
        register("gmsp");
        register("flyspeed");
        register("walkspeed");
        register("god");
        register("heal");
        register("nick");
        register("vanish", "v");
        register("rtp");
        register("setspawn");
        register("spawn");
        register("delwarp");
        register("setwarp");
        register("warp");
        register("back");
        register("delhome");
        register("home");
        register("sethome");
        register("tpa");
        register("tpadeny");
        register("tpahere");
        register("tpatoggle");
        register("tpaccept");
        register("day");
        register("midnight");
        register("night");
        register("noon");
        register("rain");
        register("sun");
        register("thunder");
        // No "time" or "weather" entry: TimeCommand and WeatherCommand are the abstract bases the
        // seven above extend, not commands of their own. A switch for them would toggle nothing.
    }

    private CommandConfig() {}

    /** Forces the registrations above to run. */
    public static void init() {}
}

package net.dutymod.client.quiet.config;

import com.google.common.io.Files;
import net.dutymod.framework.DutyLog;
import net.dutymod.framework.platform.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class QuietMixinConfig {
    private static boolean loaded = false;
    public static final ArrayList<String> DISABLED = new ArrayList<>();

    private static void load() {
        File file = Platform.get().configDir().resolve("duty-stfu-disable.txt").toFile();
        if (file.exists()) {
            try(BufferedReader reader = Files.newReader(file, StandardCharsets.UTF_8)) {
                reader.lines().forEach(line -> {
                    if (!line.startsWith("#")) DISABLED.add(line);
                });
            } catch (Exception e) {
                // Read from a mixin config plugin, so throwing here fails mixin application
                // rather than the read, and takes the module down over an opt-out list. An
                // unreadable list means nothing is opted out, which is what having no file
                // means -- and matches DutyConfig, where a config it cannot read degrades to
                // defaults rather than to a game that will not boot.
                DutyLog.warn("Could not read " + file + ", so no Stfu mixins are disabled: " + e);
                DISABLED.clear();
            }
        }
        loaded = true;
    }

    public static boolean get(String name) {
        if (!loaded) load();
        return DISABLED.stream().anyMatch(name::contains);
    }
}

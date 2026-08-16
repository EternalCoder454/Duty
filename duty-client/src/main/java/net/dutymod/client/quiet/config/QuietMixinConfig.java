package net.dutymod.client.quiet.config;

import com.google.common.io.Files;
import net.neoforged.fml.loading.FMLPaths;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class QuietMixinConfig {
    private static boolean loaded = false;
    public static final ArrayList<String> DISABLED = new ArrayList<>();

    private static void load() {
        File file = FMLPaths.CONFIGDIR.get().resolve("duty-stfu-disable.txt").toFile();
        if (file.exists()) {
            try(BufferedReader reader = Files.newReader(file, StandardCharsets.UTF_8)) {
                reader.lines().forEach(line -> {
                    if (!line.startsWith("#")) DISABLED.add(line);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        loaded = true;
    }

    public static boolean get(String name) {
        if (!loaded) load();
        return DISABLED.stream().anyMatch(name::contains);
    }
}

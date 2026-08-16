/*
 * This file is part of Batching - https://github.com/RaphiMC/Batching
 * Copyright (C) 2023-2026 RK_01/RaphiMC and contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.dutymod.client.batching;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.dutymod.client.batching.feature.core.BatchingConfig;
import net.dutymod.client.batching.feature.core.BatchingRuntimeConfig;
import net.dutymod.client.batching.feature.sign_text_buffering.SignTextCache;
import net.dutymod.client.batching.service.PlatformService;
import net.dutymod.client.batching.util.IrisCompat;
import org.lwjgl.system.MathUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.util.Objects;

public class Batching {

    public static final Logger LOGGER = LoggerFactory.getLogger("Duty/Batching");
    public static String VERSION;
    public static BatchingConfig config;
    public static BatchingRuntimeConfig runtimeConfig;

    public static SignTextCache signTextCache;

    public static void earlyInit() {
        if (Batching.config != null) {
            return;
        }
        Batching.loadConfig();
        Batching.createRuntimeConfig();
        VERSION = PlatformService.INSTANCE.getModVersion("duty_client").orElse("unknown");

        //System.load("C:\\Program Files\\RenderDoc\\renderdoc.dll");
    }

    public static void onRenderSystemInit() {
        final String gpuVendor = RenderSystem.getDevice().getVendor();
        final String gpuModel = RenderSystem.getDevice().getRenderer();
        final String backendName = RenderSystem.getDevice().getBackendName();
        final String backendVersion = RenderSystem.getDevice().getVersion();
        LOGGER.info("Initializing Batching " + VERSION + " on " + gpuModel + " (" + gpuVendor + ") with " + backendName + " " + backendVersion);

        final String gpuVendorLower = gpuVendor.toLowerCase();
        final boolean isNvidia = gpuVendorLower.startsWith("nvidia");
        final boolean isAmd = gpuVendorLower.startsWith("ati") || gpuVendorLower.startsWith("amd");
        final boolean isIntel = gpuVendorLower.startsWith("intel");
        final boolean isApple = gpuVendorLower.startsWith("apple");

        Objects.requireNonNull(Batching.config, "Config not loaded yet");
        Objects.requireNonNull(Batching.runtimeConfig, "Runtime config not created yet");

        if (Batching.config.fix_slow_buffer_upload_on_apple_gpu && isApple && backendName.equals("OpenGL") && !(RenderSystem.getDevice().getEnabledExtensions().contains("GL_ARB_direct_state_access") || RenderSystem.getDevice().getEnabledExtensions().contains("GL_ARB_buffer_storage"))) {
            Batching.runtimeConfig.disable_fast_buffer_upload = true;
        }

        if (!Batching.config.debug_only_and_not_recommended_disable_mod_conflict_handling) {
            PlatformService.INSTANCE.getModVersion("iris").ifPresent(version -> {
                Batching.LOGGER.info("Found Iris " + version + ". Enabling compatibility.");
                IrisCompat.init();
            });
        }
    }

    public static void lateInit() {
        if (Batching.config.experimental_sign_text_buffering) {
            Batching.signTextCache = new SignTextCache();
            if (PlatformService.INSTANCE.getModVersion("neoforge").isEmpty()) { // NeoForge uses an event. Handled in BatchingNeoForge
                ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).registerReloadListener(Batching.signTextCache);
            }
        }
    }

    public static void onLevelChange() {
        if (Batching.signTextCache != null) {
            Batching.signTextCache.clearCache();
        }
    }

    public static void loadConfig() {
        final File configFile = PlatformService.INSTANCE.getConfigDirectory().resolve("duty-immediatelyfast.json").toFile();
        if (configFile.exists()) {
            try {
                Batching.config = new Gson().fromJson(new FileReader(configFile), BatchingConfig.class);
            } catch (Throwable e) {
                LOGGER.error("Failed to load Batching config. Resetting it.", e);
            }
        }
        if (Batching.config == null) {
            Batching.config = new BatchingConfig();
        }

        if (!MathUtil.mathIsPoT(Batching.config.font_atlas_size)) {
            LOGGER.warn("Font atlas size " + Batching.config.font_atlas_size + " is not a power of two! Rounding up to the next power of two.");
            Batching.config.font_atlas_size = MathUtil.mathRoundPoT(Batching.config.font_atlas_size);
        }
        if (!MathUtil.mathIsPoT(Batching.config.map_atlas_size)) {
            LOGGER.warn("Map atlas size " + Batching.config.map_atlas_size + " is not a power of two! Rounding up to the next power of two.");
            Batching.config.map_atlas_size = MathUtil.mathRoundPoT(Batching.config.map_atlas_size);
        }
        if (!MathUtil.mathIsPoT(Batching.config.experimental_sign_atlas_size)) {
            LOGGER.warn("Sign atlas size " + Batching.config.experimental_sign_atlas_size + " is not a power of two! Rounding up to the next power of two.");
            Batching.config.experimental_sign_atlas_size = MathUtil.mathRoundPoT(Batching.config.experimental_sign_atlas_size);
        }

        try {
            Files.writeString(configFile.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(Batching.config));
        } catch (Throwable e) {
            LOGGER.error("Failed to save Batching config.", e);
        }
    }

    public static void createRuntimeConfig() {
        Batching.runtimeConfig = new BatchingRuntimeConfig(Batching.config);
    }

}

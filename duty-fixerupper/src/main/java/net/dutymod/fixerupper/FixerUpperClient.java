package net.dutymod.fixerupper;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.MemoryReserve;
import net.dutymod.fixerupper.annotation.FeatureLevel;
import net.dutymod.fixerupper.api.constants.IntegrationConstants;
import net.dutymod.fixerupper.api.entrypoint.FixerUpperClientIntegration;
import net.dutymod.fixerupper.core.FixerUpperMixinPlugin;
import net.dutymod.fixerupper.platform.FixerUpperPlatformHooks;
import net.dutymod.fixerupper.spark.SparkLaunchProfiler;
import net.dutymod.fixerupper.util.ClassInfoManager;
import net.dutymod.fixerupper.world.IntegratedWatchdog;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FixerUpperClient {
    public static FixerUpperClient INSTANCE;
    public static long worldLoadStartTime = -1;
    private static int numRenderTicks;

    public static float gameStartTimeSeconds = -1;

    public static boolean recipesUpdated, tagsUpdated = false;

    public String brandingString = null;

    /**
     * The list of loaded client integrations.
     */
    public static List<FixerUpperClientIntegration> CLIENT_INTEGRATIONS = new CopyOnWriteArrayList<>();

    public FixerUpperClient() {
        INSTANCE = this;
        // clear reserve as it's not needed
        MemoryReserve.release();
        if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.branding.F3Screen")) {
            brandingString = FixerUpper.NAME + " " + FixerUpperPlatformHooks.INSTANCE.getVersionString();
            if (FixerUpperMixinPlugin.activeFeatureLevel() != FeatureLevel.GA) {
                brandingString = brandingString + "[" + FixerUpperMixinPlugin.activeFeatureLevel().name() + "]";
            }
        }
        for(String className : FixerUpperPlatformHooks.INSTANCE.getCustomModOptions().get(IntegrationConstants.CLIENT_INTEGRATION_CLASS)) {
            try {
                CLIENT_INTEGRATIONS.add((FixerUpperClientIntegration)Class.forName(className).getDeclaredConstructor().newInstance());
            } catch(ReflectiveOperationException | ClassCastException e) {
                FixerUpper.LOGGER.error("Could not instantiate integration {}", className, e);
            }
        }

        if(FixerUpperMixinPlugin.instance.isOptionEnabled("perf.dynamic_resources.FireIntegrationHook")) {
            for(FixerUpperClientIntegration integration : FixerUpperClient.CLIENT_INTEGRATIONS) {
                integration.onDynamicResourcesStatusChange(true);
            }
        }
    }

    public void resetWorldLoadStateMachine() {
        numRenderTicks = 0;
        worldLoadStartTime = -1;
        recipesUpdated = false;
        tagsUpdated = false;
    }

    public void onGameLaunchFinish() {
        if(gameStartTimeSeconds >= 0)
            return;
        gameStartTimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
        if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.measure_time.GameLoad"))
            FixerUpper.LOGGER.warn("Game took " + gameStartTimeSeconds + " seconds to start");
        FixerUpperPlatformHooks.INSTANCE.onLaunchComplete();
        ClassInfoManager.clear();
    }

    public void onRecipesUpdated() {
        recipesUpdated = true;
    }

    public void onTagsUpdated() {
        tagsUpdated = true;
    }

    public void onRenderTickEnd() {
        if(recipesUpdated
                && tagsUpdated
                && worldLoadStartTime != -1
                && Minecraft.getInstance().player != null
                && numRenderTicks++ >= 10) {
            float timeSpentLoading = ((float)(System.nanoTime() - worldLoadStartTime) / 1000000000f);
            if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.measure_time.WorldLoad")) {
                FixerUpper.LOGGER.warn("Time from main menu to in-game was " + timeSpentLoading + " seconds");
                FixerUpper.LOGGER.warn("Total time to load game and open world was " + (timeSpentLoading + gameStartTimeSeconds) + " seconds");
            }
            if (FixerUpperPlatformHooks.INSTANCE.modPresent("spark") && FixerUpperMixinPlugin.instance.isOptionEnabled("feature.spark_profile_world_join.WorldJoin")) {
                SparkLaunchProfiler.stop("world_join");
            }
            resetWorldLoadStateMachine();
        }
    }

    public void onServerStarted(MinecraftServer server) {
        if(!FixerUpperMixinPlugin.instance.isOptionEnabled("feature.integrated_server_watchdog.IntegratedWatchdog"))
            return;
        IntegratedWatchdog watchdog = new IntegratedWatchdog(server);
        watchdog.start();
    }
}

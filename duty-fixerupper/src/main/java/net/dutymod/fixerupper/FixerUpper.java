package net.dutymod.fixerupper;

import net.minecraft.SharedConstants;
import net.minecraft.TracingExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.dutymod.fixerupper.command.FixerUpperCommands;
import net.dutymod.fixerupper.core.FixerUpperMixinPlugin;
import net.dutymod.fixerupper.platform.FixerUpperPlatformHooks;
import net.dutymod.fixerupper.resources.ReloadExecutor;
import net.dutymod.fixerupper.util.ClassInfoManager;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.management.ManagementFactory;

// The value here should match an entry in the META-INF/mods.toml file
public class FixerUpper {

    // Directly reference a log4j logger.
    public static final Logger LOGGER = LogManager.getLogger("Duty");

    public static final String MODID = "duty_fixerupper";

    public static String NAME = "Duty";

    public static FixerUpper INSTANCE;

    // Used to skip computing the blockstate caches twice
    public static boolean runningFirstInjection = false;

    private static TracingExecutor resourceReloadService = null;

    static {
        if(FixerUpperMixinPlugin.instance.isOptionEnabled("perf.dedicated_reload_executor.ReloadExecutor")) {
            resourceReloadService = new TracingExecutor(ReloadExecutor.createCustomResourceReloadExecutor());
        } else {
            resourceReloadService = Util.backgroundExecutor();
        }
    }

    public static TracingExecutor resourceReloadExecutor() {
        return resourceReloadService;
    }

    public static void runAuditIfRequested() {
        boolean auditAndExit = Boolean.getBoolean("duty.auditAndExit");
        if (auditAndExit || Boolean.getBoolean("duty.auditMixinsAtStart")) {
            MixinEnvironment.getCurrentEnvironment().audit();
            if (auditAndExit) {
                // Prevents Crash Assistant from treating mixin audit as a crash
                Minecraft.getInstance().stop();
                System.exit(0);
            }
        }
    }

    public FixerUpper() {
        INSTANCE = this;
        if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.snapshot_easter_egg.NameChange") && !SharedConstants.getCurrentVersion().stable())
            NAME = "PreemptiveFix";
        FixerUpperPlatformHooks.INSTANCE.onServerCommandRegister(FixerUpperCommands::register);
    }

    public void onServerStarted() {
        if(FixerUpperPlatformHooks.INSTANCE.isDedicatedServer()) {
            float gameStartTime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000f;
            if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.measure_time.ServerLoad"))
                FixerUpper.LOGGER.warn("Dedicated server took " + gameStartTime + " seconds to load");
            FixerUpperPlatformHooks.INSTANCE.onLaunchComplete();
        }
        ClassInfoManager.clear();
    }

    @SuppressWarnings("ConstantValue")
    public void onServerDead(MinecraftServer server) {
        /* Clear as much data from the integrated server as possible, in case a mod holds on to it */
        try {
            for(ServerLevel level : server.getAllLevels()) {
                ChunkMap chunkMap = level.getChunkSource().chunkMap;
                // Null check for mods that replace chunk system
                if(chunkMap.updatingChunkMap != null)
                    chunkMap.updatingChunkMap.clear();
                if(chunkMap.visibleChunkMap != null)
                    chunkMap.visibleChunkMap.clear();
                if(chunkMap.pendingUnloads != null)
                    chunkMap.pendingUnloads.clear();
            }
        } catch(RuntimeException e) {
            FixerUpper.LOGGER.error("Couldn't clear chunk data", e);
        }
    }
}

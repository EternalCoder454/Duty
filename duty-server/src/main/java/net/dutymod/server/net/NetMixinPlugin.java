package net.dutymod.server.net;

import net.dutymod.framework.DutyConfig;
import net.dutymod.framework.DutyLog;
import net.dutymod.server.biome.platform.Services;
import net.dutymod.framework.DutyMixinPlugin;


/**
 * Decides which of the network pipeline mixins apply.
 *
 * <p>Two separate jobs, and they fail in different ways if conflated. The config gates let a
 * feature be switched off without a different jar. The mod gates defer to another mod that is
 * already rewriting the same part of the pipeline -- installing two implementations of the same
 * Netty handler does not produce a conflict message, it produces a connection that hangs at
 * "Encrypting..." or drops frames, which is far harder to trace back.
 *
 * <p>Gating is done here rather than with a runtime branch inside each mixin because these sit on
 * per-packet paths, and because a mixin that applied and then returned early is indistinguishable
 * in a log from one that never applied.
 */
public final class NetMixinPlugin extends DutyMixinPlugin {
    private static final String PACKAGE = "net.dutymod.server.mixin.net.";

    @Override
    public void onLoad(String mixinPackage) {
        // Force the option registrations to run now, so config.duty.properties is complete
        // before anything reads it. Mixin plugins load before the mod itself is constructed.
        NetOptions.init();
    }

    private static boolean isModLoaded(String modId) {
        return Services.PLATFORM.hasLoadingMod(modId);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String name = mixinClassName.startsWith(PACKAGE)
                ? mixinClassName.substring(PACKAGE.length())
                : mixinClassName;

        boolean enabled = switch (name) {
            // -- Compression ---------------------------------------------------------------
            case "pipeline.CompressionConnectionMixin" ->
                    option(NetOptions.NATIVE_COMPRESSION);

            // -- Encryption ----------------------------------------------------------------
            //
            // e4mc tunnels the connection and installs its own handlers around the cipher;
            // upstream disables all three encryption mixins when it is present, and there is
            // no reason to be braver about it than the people who found the problem.
            case "pipeline.EncryptionConnectionMixin",
                 "pipeline.ServerLoginPacketListenerImplMixin" ->
                    option(NetOptions.NATIVE_ENCRYPTION) && !isModLoaded("e4mc");
            case "pipeline.ClientLoginMixin" ->
                    option(NetOptions.NATIVE_ENCRYPTION)
                            && option(NetOptions.CLIENT_ENCRYPTION)
                            && !isModLoaded("e4mc");

            // -- Codec ---------------------------------------------------------------------
            case "microopt.VarLongMixin" -> option(NetOptions.FAST_VARLONG);
            case "microopt.PacketProcessorMixin" -> option(NetOptions.PACKET_PROCESSOR_OPT);

            default -> true;
        };

        if (!enabled) {
            DutyLog.info("Not applying " + name + " (disabled by config or by another mod)");
        }
        return enabled;
    }

    /**
     * Reads a boolean option. {@link DutyConfig#get} falls back to the registered default for a
     * missing or unparseable value, so a damaged config file leaves the defaults in force rather
     * than silently disabling the pipeline.
     */
    private static boolean option(String key) {
        NetOptions.init();
        return DutyConfig.get(key);
    }

}

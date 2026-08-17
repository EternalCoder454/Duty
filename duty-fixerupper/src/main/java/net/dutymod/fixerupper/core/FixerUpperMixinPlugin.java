package net.dutymod.fixerupper.core;

import com.google.common.collect.ImmutableSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.dutymod.fixerupper.annotation.FeatureLevel;
import net.dutymod.fixerupper.core.config.BuiltInOptions;
import net.dutymod.fixerupper.core.config.FixerUpperEarlyConfig;
import net.dutymod.fixerupper.core.config.Option;
import net.dutymod.fixerupper.core.config.OptionType;
import net.dutymod.fixerupper.platform.FixerUpperPlatformHooks;
import net.dutymod.fixerupper.world.ThreadDumper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import net.dutymod.framework.DutyMixinPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.io.File;
import java.util.*;

public class FixerUpperMixinPlugin extends DutyMixinPlugin {
    private static final String MIXIN_PACKAGE_ROOT = "net.dutymod.fixerupper.mixin.";

    public final Logger logger = LogManager.getLogger("Duty");
    public FixerUpperEarlyConfig config = null;
    public static FixerUpperMixinPlugin instance;

    public FixerUpperMixinPlugin() {
        /* invoke early to ensure it gets read on one thread */
        FixerUpperPlatformHooks.INSTANCE.getCustomModOptions();
        boolean firstConfig = instance == null;
        if(firstConfig) {
            instance = this;
            try {
                config = FixerUpperEarlyConfig.load(new File("./config/duty_fixerupper-mixins.properties"));
            } catch (Exception e) {
                throw new RuntimeException("Could not load configuration file for Duty", e);
            }

            this.logger.info("Loaded configuration file for Duty {}: {} options available, {} override(s) found",
                    FixerUpperPlatformHooks.INSTANCE.getVersionString(), config.getOptionCount(), config.getOptionOverrideCount());

            if(activeFeatureLevel() != FeatureLevel.GA) {
                this.logger.warn("Duty stability level is set to {}. Features at this level may be unstable or cause crashes.",
                        activeFeatureLevel());
            }

            config.getOptionMap().values().forEach(option -> {
                if (option.isOverridden()) {
                    String source = "[unknown]";

                    if (option.isUserDefined()) {
                        source = "user configuration";
                    } else if (!FixerUpperPlatformHooks.INSTANCE.isEarlyLoadingNormally()) {
                        source = "load error";
                    } else if (option.isModDefined()) {
                        source = "mods [" + String.join(", ", option.getDefiningMods()) + "]";
                    }
                    this.logger.warn("Option '{}' overriden (by {}) to '{}'", option.getName(),
                           source, option.getValue());
                }
            });


            if(FixerUpperEarlyConfig.OPTIFINE_PRESENT)
                this.logger.fatal("OptiFine detected. Use of Duty with OptiFine is not supported due to its impact on launch time and breakage of Forge features.");

            // The Nashorn fix used to sit here. It probed for
            // sun.misc.Unsafe.defineAnonymousClass and, when that was missing, told Nashorn not to
            // use anonymous classes. Both halves are gone on Java 25: defineAnonymousClass was
            // removed in Java 17 and Nashorn itself left the JDK in Java 15. So the probe always
            // threw, the catch always ran, and every launch logged "Applying Nashorn fix" while
            // setting a system property for a script engine that is not there.

            /* We abuse the constructor of a mixin plugin as a safe location to start modifying the classloader */
            FixerUpperPlatformHooks.INSTANCE.injectPlatformSpecificHacks();

            if(FixerUpperMixinPlugin.instance.isOptionEnabled("feature.spam_thread_dump.ThreadDumper")) {
                // run once to trigger classloading
                ThreadDumper.obtainThreadDump();
                Thread t = new Thread() {
                    public void run() {
                        while(true) {
                            try {
                                Thread.sleep(60000);
                                logger.info("------ DEBUG THREAD DUMP (occurs every 60 seconds) ------");
                                logger.info(ThreadDumper.obtainThreadDump());
                            } catch(InterruptedException | RuntimeException e) {}
                        }
                    }
                };
                t.setDaemon(true);
                t.start();
            }

            if (FixerUpperPlatformHooks.INSTANCE.isClient() && FixerUpperMixinPlugin.instance.isOptionEnabled("perf.thread_priorities.AdjustThreadCount")) {
                computeBetterThreadCount();
            }
        }
    }

    private void computeBetterThreadCount() {
        // Allow user-provided thread count to take precedence
        if (System.getProperty("max.bg.threads") != null) {
            return;
        }
        // Server thread + client thread + GC thread
        int reservedCores = 3;
        int availableBackgroundCores = Math.max(1, Runtime.getRuntime().availableProcessors() - reservedCores);
        logger.info("Configuring Minecraft's max.bg.threads option with {} threads", availableBackgroundCores);
        System.setProperty("max.bg.threads", String.valueOf(availableBackgroundCores));
    }


    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        mixinClassName = FixerUpperEarlyConfig.sanitize(mixinClassName);
        if (!mixinClassName.startsWith(MIXIN_PACKAGE_ROOT)) {
            this.logger.error("Expected mixin '{}' to start with package root '{}', treating as foreign and " +
                    "disabling!", mixinClassName, MIXIN_PACKAGE_ROOT);

            return false;
        }

        String mixin = mixinClassName.substring(MIXIN_PACKAGE_ROOT.length());
        if(!instance.isOptionEnabled(mixin)) {
            this.logger.debug("Skipping mixin {}: disabled by configuration", mixin);
            return false;
        }
        String disabledBecauseMod = instance.config.getPermanentlyDisabledMixins().get(mixin);
        if(disabledBecauseMod != null) {
            this.logger.debug("Skipping mixin {}: disabled for mod compat ({})", mixin, disabledBecauseMod);
            return false;
        }
        this.logger.debug("Applying mixin {}", mixin);
        return true;
    }

    public boolean isOptionEnabled(String mixin) {
        Option<?> option = instance.config.getEffectiveOptionForMixin(mixin);

        if (option == null) {
            String msg = "No rules matched mixin '{}', treating as foreign and disabling!";
            if(FixerUpperPlatformHooks.INSTANCE.isDevEnv())
                this.logger.error(msg, mixin);
            else
                this.logger.debug(msg, mixin);

            return false;
        }

        return option.getType() == OptionType.BOOLEAN && option.asBoolean().getValue();
    }

    public <T> T getOptionValue(String optionName, Class<T> type) {
        return this.config.getOptionValue(optionName, type);
    }

    public static FeatureLevel activeFeatureLevel() {
        return instance.getOptionValue(BuiltInOptions.STABILITY_LEVEL, FeatureLevel.class);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // Upstream ran an ASM scan here for a reduce_blockstate_cache_rebuilds mixin. That feature
        // was never ported into Duty, so the name matched nothing and the scan never ran. It is not
        // worth restoring: it existed because old Forge rebuilt the blockstate cache at many points
        // during startup, and on 26.1.2 the cache is rebuilt from three call sites in total --
        // Blocks' bootstrap plus NeoForge's DataMapHooks and BlockCallbacks -- all at startup or
        // reload. See FEATURES.md.
        FixerUpperPlatformHooks.INSTANCE.applyASMTransformers(mixinClassName, targetClass);
    }


}
package net.dutymod.fixerupper.core.config;

import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.google.gson.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.dutymod.framework.DutyConfig;
import net.dutymod.fixerupper.annotation.ClientOnlyMixin;
import net.dutymod.fixerupper.annotation.FeatureLevel;
import net.dutymod.fixerupper.annotation.IgnoreOutsideDev;
import net.dutymod.fixerupper.annotation.RequiresFeatureLevel;
import net.dutymod.fixerupper.annotation.RequiresMod;
import net.dutymod.fixerupper.core.FixerUpperMixinPlugin;
import net.dutymod.fixerupper.platform.FixerUpperPlatformHooks;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixin;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FixerUpperEarlyConfig {
    private static final Logger LOGGER = LogManager.getLogger("DutyConfig");

    private final Map<String, Option<?>> options = new HashMap<>();
    private final Multimap<String, Option<?>> optionsByCategory = HashMultimap.create();

    private static final boolean ALLOW_OVERRIDE_OVERRIDES = Boolean.getBoolean("duty.unsupported.allowOverriding");

    public static final boolean OPTIFINE_PRESENT;


    static {
        boolean hasOfClass = false;
        try {
            Class.forName("optifine.OptiFineTransformationService");
            hasOfClass = true;
        } catch(Throwable e) {
        }
        OPTIFINE_PRESENT = hasOfClass;
    }

    private static boolean modPresent(String modId) {
        if(modId.equals("optifine"))
            return OPTIFINE_PRESENT;
        else
            return FixerUpperPlatformHooks.INSTANCE.modPresent(modId);
    }

    private static final String MIXIN_DESC = Type.getDescriptor(Mixin.class);
    private static final String MIXIN_CLIENT_ONLY_DESC = Type.getDescriptor(ClientOnlyMixin.class);
    private static final String MIXIN_REQUIRES_MOD_DESC = Type.getDescriptor(RequiresMod.class);
    private static final String MIXIN_DEV_ONLY_DESC = Type.getDescriptor(IgnoreOutsideDev.class);
    private static final String FEATURE_LEVEL_ANNOTATION_DESC = Type.getDescriptor(RequiresFeatureLevel.class);

    private static final Pattern PLATFORM_PREFIX = Pattern.compile("(neoforge|fabric|common)\\.");

    public static String sanitize(String mixinClassName) {
        return PLATFORM_PREFIX.matcher(mixinClassName).replaceFirst("");
    }

    private final Set<String> mixinOptions = new ObjectOpenHashSet<>();
    private final Map<String, String> mixinsMissingMods = new Object2ObjectOpenHashMap<>();

    private final Map<String, FeatureLevel> mixinsRequiringLowerStability = new Object2ObjectOpenHashMap<>();

    private static class PackageMetadata {
        String requiredModId;
        FeatureLevel requiredLevel;
    }

    private final Map<String, PackageMetadata> packageMetadataCache = new HashMap<>();

    public static boolean isFabric = FixerUpperEarlyConfig.class.getClassLoader().getResourceAsStream("duty_fixerupper-fabric.mixins.json") != null;

    public Map<String, String> getPermanentlyDisabledMixins() {
        return mixinsMissingMods;
    }

    @SuppressWarnings("unchecked")
    private static <T> T getAnnotationValue(AnnotationNode ann, String key) {
        if (ann.values == null) return null;
        for (int i = 0; i < ann.values.size(); i += 2) {
            if (ann.values.get(i).equals(key)) return (T) ann.values.get(i + 1);
        }
        return null;
    }

    private PackageMetadata loadPackageMetadata(String packageResourcePath) {
        String classPath = packageResourcePath + "/package-info.class";
        try (InputStream stream = FixerUpperEarlyConfig.class.getClassLoader().getResourceAsStream(classPath)) {
            if (stream == null) return new PackageMetadata();
            ClassReader reader = new ClassReader(stream);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            PackageMetadata meta = new PackageMetadata();
            List<AnnotationNode> annotations = new ArrayList<>();
            if (node.invisibleAnnotations != null) annotations.addAll(node.invisibleAnnotations);
            if (node.visibleAnnotations != null) annotations.addAll(node.visibleAnnotations);
            for (AnnotationNode annotation : annotations) {
                if (Objects.equals(annotation.desc, MIXIN_REQUIRES_MOD_DESC)) {
                    meta.requiredModId = getAnnotationValue(annotation, "value");
                } else if (Objects.equals(annotation.desc, FEATURE_LEVEL_ANNOTATION_DESC)) {
                    String[] enumVal = getAnnotationValue(annotation, "value");
                    meta.requiredLevel = FeatureLevel.valueOf(enumVal[1]);
                }
            }
            return meta;
        } catch (IOException e) {
            LOGGER.error("Error scanning package-info " + classPath, e);
            return new PackageMetadata();
        }
    }

    private PackageMetadata getOrLoadPackageMetadata(String packageResourcePath) {
        return packageMetadataCache.computeIfAbsent(packageResourcePath, this::loadPackageMetadata);
    }

    private void scanForAndBuildMixinOptions() {
        List<String> configFiles = ImmutableList.of("duty_fixerupper.mixins.json");
        List<String> mixinPaths = new ArrayList<>();
        for(String configFile : configFiles) {
            InputStream stream = FixerUpperEarlyConfig.class.getClassLoader().getResourceAsStream(configFile);
            if(stream == null)
                continue;
            try(Reader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                JsonObject configObject = (JsonObject)new JsonParser().parse(reader);
                List<JsonElement> mixinList = Stream.of("mixins", "client")
                        .map(key -> Optional.ofNullable(configObject.getAsJsonArray(key)))
                        .flatMap(arr -> arr.map(jsonElements -> StreamSupport.stream(jsonElements.spliterator(), false)).orElseGet(Stream::of))
                        .collect(Collectors.toList());
                String packageName = configObject.get("package").getAsString().replace('.', '/');
                for(JsonElement mixin : mixinList) {
                    mixinPaths.add(packageName + "/" + mixin.getAsString().replace('.', '/') + ".class");
                }
            } catch(IOException | JsonParseException e) {
                LOGGER.error("Error loading config " + configFile, e);
            }
        }
        Splitter dotSplitter = Splitter.on('.');
        for(String mixinPath : mixinPaths) {
            try(InputStream stream = FixerUpperEarlyConfig.class.getClassLoader().getResourceAsStream(mixinPath)) {
                ClassReader reader = new ClassReader(stream);
                ClassNode node = new ClassNode();
                reader.accept(node,  ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
                if(node.invisibleAnnotations == null)
                    return;
                boolean isMixin = false, isClientOnly = false, requiredModPresent = true, isDevOnly = false;
                String requiredModId = "";
                FeatureLevel requiredLevel = FeatureLevel.GA;
                for(AnnotationNode annotation : node.invisibleAnnotations) {
                    if(Objects.equals(annotation.desc, MIXIN_DESC)) {
                        isMixin = true;
                    } else if(Objects.equals(annotation.desc, MIXIN_CLIENT_ONLY_DESC)) {
                        isClientOnly = true;
                    } else if(Objects.equals(annotation.desc, MIXIN_REQUIRES_MOD_DESC)) {
                        String modId = getAnnotationValue(annotation, "value");
                        if(modId != null) {
                            requiredModPresent = modId.startsWith("!") ? !modPresent(modId.substring(1)) : modPresent(modId);
                            requiredModId = modId;
                        }
                    } else if(Objects.equals(annotation.desc, MIXIN_DEV_ONLY_DESC)) {
                        isDevOnly = true;
                    } else if(Objects.equals(annotation.desc, FEATURE_LEVEL_ANNOTATION_DESC)) {
                        // ASM stores enum annotation values as String[]{typeDescriptor, constantName}
                        String[] enumVal = getAnnotationValue(annotation, "value");
                        requiredLevel = FeatureLevel.valueOf(enumVal[1]);
                    }
                }
                // Merge constraints from ancestor package-info files (up to the mixin root)
                String classPackagePath = mixinPath.substring(0, mixinPath.lastIndexOf('/'));
                int mixinRootEnd = classPackagePath.indexOf("/mixin");
                if (mixinRootEnd >= 0) {
                    String mixinRoot = classPackagePath.substring(0, mixinRootEnd + "/mixin".length());
                    String walkPkg = mixinRoot;
                    while (walkPkg.length() < classPackagePath.length()) {
                        int nextSlash = classPackagePath.indexOf('/', walkPkg.length() + 1);
                        walkPkg = (nextSlash == -1) ? classPackagePath : classPackagePath.substring(0, nextSlash);
                        PackageMetadata pkgMeta = getOrLoadPackageMetadata(walkPkg);
                        if (requiredModPresent && pkgMeta.requiredModId != null) {
                            boolean present = pkgMeta.requiredModId.startsWith("!")
                                    ? !modPresent(pkgMeta.requiredModId.substring(1))
                                    : modPresent(pkgMeta.requiredModId);
                            if (!present) {
                                requiredModPresent = false;
                                requiredModId = pkgMeta.requiredModId;
                            }
                        }
                        if (pkgMeta.requiredLevel != null && pkgMeta.requiredLevel.ordinal() > requiredLevel.ordinal()) {
                            requiredLevel = pkgMeta.requiredLevel;
                        }
                    }
                }
                if(isMixin && (!isDevOnly || FixerUpperPlatformHooks.INSTANCE.isDevEnv())) {
                    String mixinClassName = sanitize(node.name.replace('/', '.')).replace("net.dutymod.fixerupper.mixin.", "");
                    if(!requiredModPresent)
                        mixinsMissingMods.put(mixinClassName, requiredModId);
                    else if(isClientOnly && !FixerUpperPlatformHooks.INSTANCE.isClient())
                        mixinsMissingMods.put(mixinClassName, "[not client]");

                    // Store the required stability level so it can be checked later
                    if (requiredLevel != FeatureLevel.GA) {
                        mixinsRequiringLowerStability.put(mixinClassName, requiredLevel);
                    }

                    String mixinCategoryName = "mixin." + mixinClassName.substring(0, mixinClassName.lastIndexOf('.'));
                    mixinOptions.add(mixinCategoryName);
                }
            } catch(IOException e) {
                LOGGER.error("Error scanning file " + mixinPath, e);
            }
        }
    }

    private static final boolean isDevEnv = FixerUpperPlatformHooks.INSTANCE.isDevEnv();

    private static class DefaultSettingMapBuilder extends ImmutableMap.Builder<String, Boolean> {
        public DefaultSettingMapBuilder putConditionally(BooleanSupplier condition, String k, Boolean v) {
            if(condition.getAsBoolean())
                put(k, v);
            return this;
        }

        @Override
        public DefaultSettingMapBuilder put(String key, Boolean value) {
            super.put(key, value);
            return this;
        }
    }

    /**
     * Options whose default differs from "on".
     *
     * <p>Every key here becomes an option whether or not a mixin package backs it, because the
     * constructor adds this key set to the ones it found by scanning. Seventeen keys inherited from
     * upstream named mixins this port never took, so they wrote seventeen settings into
     * duty.properties that nothing reads -- including four that read as memory and startup wins
     * (clear_mixin_classinfo, deduplicate_location, deduplicate_climate_parameters,
     * dynamic_entity_renderers) and would have done nothing whatever they were set to.
     *
     * <p>So a key belongs here only if {@code common/mixin/<category>/<name>} exists. Adding one
     * for a mixin that does not ship puts the setting back in the file and nothing behind it.
     */
    private static final ImmutableMap<String, Boolean> DEFAULT_SETTING_OVERRIDES = new DefaultSettingMapBuilder()
            // Changes the wire format of identifiers, so both ends must run Duty. Safe in a
            // pack you control on both sides, broken when joining a server without it.
            .put("mixin.perf.compact_identifier_encoding", false)
            // Upstream ships this off because it is the most invasive thing FixerUpper does. It is
            // also the single largest memory and startup win available: models and their block
            // state definitions are baked on demand instead of all of them at load, which on a
            // pack this size is hundreds of megabytes of heap and a large slice of the load time.
            // Duty turns it on -- that trade is the entire point of the module.
            //
            // The thing to watch is connected textures (Athena is installed here). Upstream's own
            // connectedness warning is dead-coded to if(false) in FixerUpperClientForge, so that
            // conflict is stale rather than current, but missing or wrong block textures is the
            // symptom to look for, and this one line is the revert.
            .put("mixin.perf.dynamic_resources", true)
            .put("mixin.bugfix.restore_old_dragon_movement", false)
            .put("mixin.feature.cause_lag_by_disabling_threads", false)
            .put("mixin.bugfix.missing_block_entities", false)
            .put("mixin.feature.blockentity_incorrect_thread", false)
            .put("mixin.feature.remove_chat_signing", false)
            .put("mixin.devenv", isDevEnv)
            .putConditionally(() -> !isFabric, "mixin.feature.registry_event_progress", true)
            .build();

    private FixerUpperEarlyConfig(File file) {
        // file is not kept. It is only used by the one-time migration in load(), and the field
        // that used to hold it was never read once FixerUpper's options moved into duty.properties.
        OptionCategories.load();
        this.scanForAndBuildMixinOptions();
        mixinOptions.addAll(DEFAULT_SETTING_OVERRIDES.keySet());
        for(String optionName : mixinOptions) {
            boolean defaultEnabled = DEFAULT_SETTING_OVERRIDES.getOrDefault(optionName, true);
            Option<Boolean> option = new Option<>(optionName, OptionType.BOOLEAN, defaultEnabled, false);
            this.options.putIfAbsent(optionName, option);
            this.optionsByCategory.put(OptionCategories.getCategoryForOption(optionName), option);
        }
        this.addBuiltInOptions();
        for(Map.Entry<String, Option<?>> entry : this.options.entrySet()) {
            int idx = entry.getKey().lastIndexOf('.');
            if(idx <= 0)
                continue;
            String potentialParentKey = entry.getKey().substring(0, idx);
            Option<?> potentialParent = this.options.get(potentialParentKey);
            if(potentialParent != null) {
                entry.getValue().setParent(potentialParent);
            }
        }
        // Defines the default rules which can be configured by the user or other mods.
        // You must manually add a rule for any new mixins not covered by an existing package rule.
        this.addMixinRule("launch.class_search_cache", true);

        /* Mod compat */
        disableIfModPresent("mixin.perf.thread_priorities", "smoothboot", "threadtweak");
        disableIfModPresent("mixin.perf.boost_worker_count", "smoothboot", "threadtweak");
        disableIfModPresent("mixin.perf.compress_biome_container", "chocolate", "betterendforge" ,"skyblockbuilder", "modern_beta", "worldedit");
        disableIfModPresent("mixin.bugfix.mc218112", "performant");
        disableIfModPresent("mixin.bugfix.remove_block_chunkloading", "performant");
        disableIfModPresent("mixin.bugfix.paper_chunk_patches", "c2me");
        disableIfModPresent("mixin.bugfix.preserve_early_window_pos", "better_loading_screen");
        disableIfModPresent("mixin.perf.dynamic_dfu", "litematica");
        disableIfModPresent("mixin.perf.cache_strongholds", "littletiles", "c2me");
        // content overlap
        disableIfModPresent("mixin.perf.deduplicate_wall_shapes", "dashloader");
        disableIfModPresent("mixin.perf.nbt_memory_usage", "c2me");
        disableIfModPresent("mixin.bugfix.item_cache_flag", "lithium", "canary", "radium");
        // DimThread makes changes to the server chunk manager (understandably), C2ME probably does the same
        disableIfModPresent("mixin.bugfix.chunk_deadlock", "c2me", "dimthread");
        disableIfModPresent("mixin.perf.release_protochunks", "c2me", "moonrise");
        disableIfModPresent("mixin.launch.class_search_cache", "optifine");
        disableIfModPresent("mixin.perf.faster_texture_stitching", "optifine");
        disableIfModPresent("mixin.bugfix.entity_pose_stack", "optifine");
        disableIfModPresent("mixin.perf.datapack_reload_exceptions", "cyanide");
        disableIfModPresent("mixin.bugfix.buffer_builder_leak", "isometric-renders", "witherstormmod");
        disableIfModPresent("mixin.feature.remove_chat_signing", "nochatreports");
        disableIfModPresent("mixin.perf.faster_texture_loading", "stitch", "optifine", "changed");
        disableIfModPresent("mixin.perf.faster_ingredients", "vmp", "prefab");
        disableIfModPresent("mixin.perf.smart_ingredient_sync", "crafttweaker");
        if(isFabric) {
            disableIfModPresent("mixin.bugfix.packet_leak", "memoryleakfix");
        }

        checkModelDataManager();
    }

    private void checkModelDataManager() {
        if(!isFabric && modPresent("rubidium") && !modPresent("embeddium")) {
            Option<?> option = this.options.get("mixin.bugfix.model_data_manager_cme");
            if(option != null) {
                LOGGER.warn("ModelDataManager bugfixes have been disabled to prevent broken rendering with Rubidium installed. Please migrate to Embeddium.");
                option.asBoolean().addModOverride(false, "rubidium");
            }
        }
    }

    private void disableIfModPresent(String configName, String... ids) {
        for(String id : ids) {
            if(!FixerUpperPlatformHooks.INSTANCE.isEarlyLoadingNormally() || modPresent(id)) {
                Option<?> option = this.options.get(configName);
                if(option != null)
                    option.asBoolean().addModOverride(false, id);
            }
        }
    }

    private <T> void addBuiltInOption(String name, OptionType<T> type, T initialValue) {
        this.options.putIfAbsent(name, new Option<>(name, type, initialValue, false));
    }

    private void addBuiltInOptions() {
        this.addBuiltInOption(BuiltInOptions.STABILITY_LEVEL, OptionType.enumType(FeatureLevel.class), FeatureLevel.GA);
    }

    /**
     * Defines a Mixin rule which can be configured by users and other mods.
     * @throws IllegalStateException If a rule with that name already exists
     * @param mixin The name of the mixin package which will be controlled by this rule
     * @param enabled True if the rule will be enabled by default, otherwise false
     */
    private void addMixinRule(String mixin, boolean enabled) {
        String name = getMixinRuleName(mixin);

        if (this.options.putIfAbsent(name, new Option<>(name, OptionType.BOOLEAN, enabled, false)) != null) {
            throw new IllegalStateException("Mixin rule already defined: " + mixin);
        }
    }

    private void readJVMProperties() {
        for(String optionKey : this.options.keySet()) {
            String value = System.getProperty("duty.config." + optionKey);
            if(value == null || value.length() == 0)
                continue;
            try {
                this.options.get(optionKey).setFromString(value, true);
                FixerUpperMixinPlugin.instance.logger.info("Configured {} to '{}' via JVM property.", optionKey, value);
            } catch(RuntimeException e) {
                FixerUpperMixinPlugin.instance.logger.warn("Invalid value '{}' for JVM property '{}', ignoring", value, optionKey);
            }
        }
    }

    private void readGlobalProperties() {
        try {
            Path minecraftFolder;
            if (SystemUtils.IS_OS_MAC) {
                minecraftFolder = Paths.get(System.getProperty("user.home"), "Library", "Application Support", "minecraft");
            } else if (SystemUtils.IS_OS_WINDOWS) {
                minecraftFolder = Paths.get(System.getenv("APPDATA"), ".minecraft");
            } else {
                minecraftFolder = Paths.get(System.getProperty("user.home"), ".minecraft");
            }
            Path globalPropsFile = minecraftFolder.resolve("global").resolve("duty_fixerupper-global-mixins.properties");
            if (Files.exists(globalPropsFile)) {
                Properties properties = new Properties();
                try (var is = Files.newInputStream(globalPropsFile)) {
                    properties.load(is);
                }
                if (!properties.isEmpty()) {
                    LOGGER.info("Global properties specified: [{}]", properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
                    readProperties(properties);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reading global properties file", e);
        }
    }

    private void readProperties(Properties props) {
        if(ALLOW_OVERRIDE_OVERRIDES)
            LOGGER.fatal("JVM argument given to override mod overrides. Issues opened with this option present will be ignored unless they can be reproduced without.");

        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();

            Option<?> option = this.options.get(key);

            if (option == null) {
                LOGGER.warn("No configuration key exists with name '{}', ignoring", key);
                continue;
            }

            if(ALLOW_OVERRIDE_OVERRIDES || !option.isModDefined()) {
                try {
                    option.setFromString(value, true);
                } catch(RuntimeException e) {
                    LOGGER.warn("Invalid value '{}' encountered for configuration key '{}', ignoring", value, key);
                }
            } else
                LOGGER.warn("Option '{}' already disabled by a mod. Ignoring user configuration", key);
        }
    }

    /**
     * Returns the effective option for the specified class name. This traverses the package path of the given mixin
     * and checks each root for configuration rules. If a configuration rule disables a package, all mixins located in
     * that package and its children will be disabled. The effective option is that of the highest-priority rule, either
     * a enable rule at the end of the chain or a disable rule at the earliest point in the chain.
     *
     * @return Null if no options matched the given mixin name, otherwise the effective option for this Mixin
     */
    public Option<?> getEffectiveOptionForMixin(String mixinClassName) {
        int lastSplit = 0;
        int nextSplit;

        Option<?> rule = null;

        while ((nextSplit = mixinClassName.indexOf('.', lastSplit)) != -1) {
            String key = getMixinRuleName(mixinClassName.substring(0, nextSplit));

            Option<?> candidate = this.options.get(key);

            if (candidate != null) {
                rule = candidate;

                if (!rule.asBoolean().getValue()) {
                    return rule;
                }
            }

            lastSplit = nextSplit + 1;
        }

        return rule;
    }

    /**
     * Builds the options and puts their values in {@link DutyConfig}.
     *
     * <p>{@code file} is not where the options live any more. It names an older build's standalone
     * properties file, and the only thing done with it is the one-time migration below: read it if
     * it is there, copy the values across, delete it. Nothing is written back to it, and nothing is
     * created if it is absent.
     */
    public static FixerUpperEarlyConfig load(File file) {
        FixerUpperEarlyConfig config = new FixerUpperEarlyConfig(file);
        if(!Boolean.getBoolean("duty.ignoreConfigForTesting")) {
            // FixerUpper's options live in Duty's config file rather than one of their own, so
            // every Duty option is in one place and reachable from the settings screen.
            //
            // Registration happens first and only once, while every option still holds the value
            // the constructor gave it. That is what makes the registered default the real default:
            // do this after reading a user's file and every value they changed gets recorded as
            // though it were built in, and "reset to default" in the screen resets to their old
            // setting instead.
            config.registerWithDutyConfig();

            // An older build's standalone file, if one is still there. Read once, copied across,
            // then removed so it cannot keep overriding the settings screen.
            if(file.exists()) {
                Properties legacy = new Properties();
                try (FileInputStream fin = new FileInputStream(file)) {
                    legacy.load(fin);

                    config.readProperties(legacy);
                    config.saveToDutyConfig();
                    if (file.delete()) {
                        LOGGER.info("Migrated {} into Duty's config and removed it", file.getName());
                    } else {
                        LOGGER.warn("Migrated {} into Duty's config, but could not remove the old file; "
                                + "it will be read again next launch and override the settings screen",
                                file.getName());
                    }
                } catch (IOException e) {
                    // This runs inside the mixin config plugin, so throwing fails mixin application
                    // rather than failing the read, and takes FixerUpper down over a file that only
                    // exists to be migrated away. Leaving the registered defaults in place is what a
                    // fresh install gets, and the file stays put for a later attempt.
                    LOGGER.warn("Could not read {} to migrate it, so the defaults apply and the file"
                            + " is left where it is: {}", file.getName(), e.toString());
                }
            }

            config.readProperties(config.readFromDutyConfig());

            config.readGlobalProperties();
            config.readJVMProperties();

            config.finalizeLoad();
        }

        return config;
    }

    /**
     * Called after all properties have been read.
     */
    public void finalizeLoad() {
        var stabilityLevel = this.getOptionValue(BuiltInOptions.STABILITY_LEVEL, FeatureLevel.class);
        for (var entry : mixinsRequiringLowerStability.entrySet()) {
            if (!stabilityLevel.isAtLeast(entry.getValue())) {
                mixinsMissingMods.put(entry.getKey(), "[feature level: requires " + entry.getValue() + "]");
            }
        }
    }

    /**
     * Persists user-set options.
     *
     * <p>FixerUpper's own settings screen calls this. It used to write a standalone properties
     * file; now it writes through {@link DutyConfig}, so that screen and Duty's Cloth screen edit
     * the same values and neither can be silently undone by the other.
     *
     * <p>Still declared to throw {@link IOException} because callers catch it, and because
     * DutyConfig deliberately swallows write failures -- it keeps every value in memory, so a
     * read-only config directory degrades to "defaults apply", never to "the game will not start".
     */
    public void save() throws IOException {
        saveToDutyConfig();
    }

    /**
     * The prefix every FixerUpper option carries inside {@code duty.properties}.
     *
     * <p>FixerUpper's own names ({@code mixin.perf.dynamic_resources}, {@code stability_level}) are
     * generic enough to collide with another module's, and the settings screen groups by the text
     * before the first dot, so the prefix is what puts them under their own heading.
     */
    public static final String DUTY_PREFIX = "fixerupper.";

    /**
     * Publishes every option to {@link DutyConfig} and returns the values stored there.
     *
     * <p>This is the whole of the move onto Duty's config. The options themselves are still built
     * here -- discovered by scanning the mixin packages, given parents by their dotted names, and
     * subject to per-mod overrides -- because none of that is storage and none of it belongs in a
     * properties file. What moves is where the values live and who writes them.
     *
     * <p>The result is fed through {@link #readProperties}, the same method that used to consume
     * the standalone file, so the mod-override guard and the value validation are unchanged. That
     * matters more than the saving: these options decide which of roughly two hundred mixins apply,
     * and a value that silently reads back differently would change what is patched.
     *
     * <p>Called after the constructor has built every option, so the values seen here are the
     * defaults -- which is exactly what should be registered as each option's default.
     */
    private void registerWithDutyConfig() {
        List<DutyConfig.Option> toRegister = new ArrayList<>(this.options.size());
        for (Map.Entry<String, Option<?>> entry : this.options.entrySet()) {
            String name = entry.getKey();
            Option<?> option = entry.getValue();
            toRegister.add(new DutyConfig.Option(
                    DUTY_PREFIX + name,
                    option.getSerializedValue(),
                    describe(name, option)));
        }
        DutyConfig.registerAll(toRegister);
    }

    /** {@return the stored value of every option, keyed by its FixerUpper name} */
    private Properties readFromDutyConfig() {
        Properties props = new Properties();
        for (String name : this.options.keySet()) {
            String stored = DutyConfig.rawOrDefault(DUTY_PREFIX + name);
            if (stored != null) {
                props.setProperty(name, stored);
            }
        }
        return props;
    }

    /**
     * FixerUpper carries no per-option help text -- upstream's file documents the options as one
     * block of prose at the top. The settings screen shows a tooltip per entry, so this builds the
     * nearest useful thing from what the option does know about itself.
     */
    private String describe(String name, Option<?> option) {
        StringBuilder out = new StringBuilder();

        // The description first, because it is the only line that says what the option does.
        // Seventy-four options previously shared three comment lines between them -- a category
        // word and two sentences of boilerplate -- which told a reader nothing they could act on.
        String help = OptionDescriptions.get(name);
        if (help != null) {
            for (String line : wrap(help, 76)) {
                out.append(line).append('\n');
            }
            out.append('\n');
        }

        String category = OptionCategories.getCategoryForOption(name);
        if (category != null && !category.isEmpty()) {
            out.append("Category: ").append(category).append('\n');
        }
        if (option.isModDefined()) {
            out.append("Overridden for compatibility with another installed mod; changing this\n")
                    .append("takes effect only if that mod is removed.\n");
        }
        out.append("Requires a restart. Delete the line to return to the default.");
        return out.toString();
    }

    /** Greedy wrap, so a long description does not become one unreadable comment line. */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder(width);
        for (String word : text.split("\\s+")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > width) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Writes every user-set option back to {@link DutyConfig}. */
    private void saveToDutyConfig() {
        for (Map.Entry<String, Option<?>> entry : this.options.entrySet()) {
            Option<?> option = entry.getValue();
            if (option.isUserDefined()) {
                DutyConfig.set(DUTY_PREFIX + entry.getKey(), option.getSerializedValue());
            }
        }
    }

    private static String getMixinRuleName(String name) {
        return "mixin." + name;
    }

    public int getOptionCount() {
        return this.options.size();
    }

    public int getOptionOverrideCount() {
        return (int) this.options.values()
                .stream()
                .filter(Option::isOverridden)
                .count();
    }

    public Map<String, Option<?>> getOptionMap() {
        return Collections.unmodifiableMap(this.options);
    }

    public Multimap<String, Option<?>> getOptionCategoryMap() {
        return Multimaps.unmodifiableMultimap(this.optionsByCategory);
    }

    public <T> T getOptionValue(String optionName, Class<T> type) {
        var option = this.options.get(optionName);

        if (option == null) {
            throw new IllegalStateException("Attempting to read option '" + optionName + "' that is not registered!");
        }

        return option.asType(type).getValue();
    }
}

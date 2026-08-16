// Duty: FixerUpper  =  ModernFix (LGPL-3.0)  +  Modernica (LGPL-3.0)
//
// ModernFix has a native NeoForge 26.1 branch and forms the base. Modernica is a
// Fabric-only fork of ModernFix; its *additional* features are ported across rather
// than merged wholesale. See NOTICE.

base.archivesName = "duty-fixerupper"

// Annotation classes are copied into the jar rather than left as an external dependency:
// the retained ones have to resolve at class load or mixin application fails.
val embed: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

tasks.named<Jar>("jar") {
    from(embed.map { if (it.isDirectory) it else zipTree(it) })
}

neoForge {
    mods {
        create("duty_fixerupper") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("client") { client() }
        create("server") { server() }
    }
    // Several fixes reach vanilla internals that are private or protected. NeoForge
    // widens them at load time from this file; without it the module does not compile.
    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")
}

repositories {
    // Optional integrations below are published on CurseForge only.
    maven("https://cursemaven.com") {
        name = "CurseMaven"
        content { includeGroup("curse.maven") }
    }
}

dependencies {
    implementation(project(":duty-core"))
    jarJar(project(":duty-core"))

    // The annotations the mixins carry, plus the processor that turns them into mixin
    // configs during compilation. See fixerupper-mixin-ap and duty-annotations.
    implementation(project(":duty-annotations"))
    embed(project(":duty-annotations"))
    // Consumed as a shadow jar, the way ModernFix consumes its own. The jar bundles
    // sponge-mixin and the dist markers so they never reach this project's
    // annotationProcessor path, where they would shadow ModDevGradle's copy; gson,
    // auto-common, guava and duty-annotations arrive as ordinary transitive deps.
    annotationProcessor(project(path = ":fixerupper-mixin-ap", configuration = "shadow"))

    // ModernFix also shipped per-mod compat shims for spark, CTM, TerraBlender, CoFH Core
    // and SuperMartijn642's Core Lib. Each guards a single file against one third-party mod,
    // and each needs that mod on the compile classpath from CurseForge by numeric file id --
    // ids that no longer resolve. They were removed rather than pinned to a guess. To restore
    // one: re-add its compileOnly dependency with a current file id and restore its package
    // from the ModernFix upstream at the matching commit.
}

// The processor writes the generated mixin configs here; they have to end up in the jar.
sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/sources/annotationProcessor/java/main/resources"))
    }
}

// The "shadow" configuration hands over the shadow jar as a plain file without carrying the
// task that builds it, so Gradle sees compileJava consuming an output it has no dependency on
// and refuses. Stating the dependency is the fix.
tasks.named("compileJava") {
    dependsOn(":fixerupper-mixin-ap:shadowJar")
}

tasks.withType<JavaCompile>().configureEach {
    if (!name.lowercase().contains("test")) {
        // The processor names its output "<rootProject.name>-<project.name>.mixins.json",
        // and omits the suffix when project.name is absent. Passing only the first, set to
        // the mod id, yields "duty_fixerupper.mixins.json" -- which is what neoforge.mods.toml
        // refers to -- instead of "duty-duty-fixerupper.mixins.json".
        options.compilerArgs.add("-ArootProject.name=duty_fixerupper")
    }
}

// The generated mixin configs land in a resources source directory, so every task that
// reads resources has to wait for the processor to have written them.
tasks.named<ProcessResources>("processResources") {
    dependsOn(tasks.named("compileJava"))
}

tasks.named("sourcesJar") {
    dependsOn(tasks.named("compileJava"))
}

// FixerUpper carries nine locales, 268 KB of the jar and its single largest content by far --
// more than every class in the module put together. Duty is a private single-language build, so
// only en_us is shipped by default.
//
// This is a visible change, not a free one: a player switching language sees raw keys like
// duty.option.category.performance instead of text. Restoring them is one property:
//   -Pduty.languages=all       or   -Pduty.languages=en_us,de_de,ja_jp
val shippedLanguages: String = (findProperty("duty.languages") as String? ?: "en_us")

tasks.named<ProcessResources>("processResources") {
    if (shippedLanguages != "all") {
        val keep = shippedLanguages.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        // exclude() rather than deleting sources: the translations stay in git, and a build that
        // wants them back does not need the files restored.
        eachFile {
            if (path.contains("/lang/") && path.endsWith(".json")) {
                val locale = name.removeSuffix(".json")
                if (locale !in keep) exclude()
            }
        }
    }
}

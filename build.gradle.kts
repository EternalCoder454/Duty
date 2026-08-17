plugins {
    id("net.neoforged.moddev") version "2.0.144" apply false
}

val minecraftVersion: String = property("minecraft_version") as String
val neoforgeVersion: String = property("neoforge_version") as String
val javaVersion: String = property("java_version") as String
val modVersion: String = property("mod_version") as String

// The mod jars. Everything else in this build (currently the mixin-config annotation
// processor) is a plain Java library and must not have ModDevGradle applied to it --
// it does not compile against Minecraft and would only pay the setup cost.
val modProjects = setOf("duty-framework", "duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials", "duty-all")

// The loader this branch builds for, and the name of the source set holding its code.
// Set in gradle.properties; see the target table there.
val loaderName: String = property("duty.loader") as String

subprojects {
    apply(plugin = "java")

    group = rootProject.property("group") as String
    version = modVersion

    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        // The mixin-config annotation processor understands Fabric's @Environment and
        // Forge's @OnlyIn as well as NeoForge's marker, so its dependencies have to be
        // resolvable wherever it is applied -- not just in its own module.
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // Ported sources are not warning-clean; do not fail the build on them.
        options.compilerArgs.add("-Xlint:-options")
        // Porting produces long error lists; seeing all of them at once is the difference
        // between one iteration and twenty.
        options.compilerArgs.addAll(listOf("-Xmaxerrs", "1000"))
    }
}

// The loader axis.
//
// Duty targets four combinations and only ever these four:
//
//     Forge     1.20.1    Java 17
//     NeoForge  1.21.1    Java 21
//     NeoForge  26.1.2+   Java 25
//     Fabric    26.1.2+   Java 25
//
// Every mod project is split into `src/main`, which names no loader, and `src/neoforge`, which is
// the only place allowed to import net.neoforged. Adding Fabric is then a `src/fabric` source set
// holding that loader's entry point and event wiring, with `src/main` untouched.
//
// This is worth doing on its own terms even before another loader exists: 27 of Duty's 574 source
// files touch NeoForge, and having them in one directory per module rather than scattered is the
// difference between "port this" and "find what needs porting".
//
// Modules with nothing loader-specific simply have no src/neoforge directory; the source set is
// still created, and is empty.
val loaderSourceSets = setOf(
    "duty-framework", "duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials",
)

configure(subprojects.filter { it.name in modProjects }) {
    apply(plugin = "net.neoforged.moddev")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }

    extensions.configure<net.neoforged.moddevgradle.dsl.NeoForgeExtension> {
        enable {
            version = neoforgeVersion
        }
        parchment {
            minecraftVersion = rootProject.property("parchment_mc_version") as String
            mappingsVersion = rootProject.property("parchment_version") as String
        }
    }

    if (project.name in loaderSourceSets) {
        // `sourceSets` as a bare accessor is only generated inside a project's own build script;
        // in this shared block it has to be looked up on the extension container.
        val sets = extensions.getByType(SourceSetContainer::class.java)
        val mainSet = sets.getByName("main")
        val loaderSet = sets.maybeCreate(loaderName).apply {
            // ModDevGradle only puts Minecraft and NeoForge on `main`; inheriting its classpath is
            // what lets this set see them, and its output is what lets it extend main's classes.
            compileClasspath += mainSet.compileClasspath + mainSet.output
            runtimeClasspath += mainSet.runtimeClasspath + mainSet.output
        }

        extensions.configure<net.neoforged.moddevgradle.dsl.NeoForgeExtension> {
            mods.configureEach { sourceSet(loaderSet) }
        }

        tasks.named<Jar>("jar") { from(loaderSet.output) }

        // Consumers must resolve the jar, not main's classes directory, or the loader source set
        // would be invisible to anything depending on this project.
        listOf("apiElements", "runtimeElements").forEach { name ->
            configurations.findByName(name)?.outgoing?.variants?.removeIf { it.name == "classes" }
        }

        // Keeps src/main honest. ModDevGradle puts NeoForge on main's compile classpath, so a
        // `net.neoforged` import there compiles happily and would only fail once someone built the
        // Fabric target -- far from the edit that caused it. This fails the build here instead.
        val checkLoaderNeutral = tasks.register("checkMainIsLoaderNeutral") {
            group = "verification"
            description = "Fails if ${project.name}'s loader-neutral source set names a loader."

            val mainJava = mainSet.allJava
            inputs.files(mainJava)
            val marker = layout.buildDirectory.file("tmp/loader-neutral.ok")
            outputs.file(marker)
            val projectName = project.name

            doLast {
                val loaders = listOf("net.neoforged", "net.fabricmc", "net.minecraftforge", "cpw.mods")
                val offences = mainJava.files.flatMap { file ->
                    file.readLines().withIndex()
                        .filter { (_, line) -> line.startsWith("import ") && loaders.any { line.contains(it) } }
                        .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
                }
                if (offences.isNotEmpty()) {
                    throw GradleException(
                        "$projectName/src/main must not name a mod loader; move this to " +
                            "src/neoforge:\n  " + offences.joinToString("\n  ")
                    )
                }
                marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok")
            }
        }
        tasks.named("check") { dependsOn(checkLoaderNeutral) }
    }

    tasks.withType<Jar>().configureEach {
        manifest.attributes(
            mapOf(
                "Specification-Title" to project.name,
                "Specification-Version" to "1",
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Duty"
            )
        )
    }

    tasks.withType<ProcessResources>().configureEach {
        val replacements = mapOf(
            "mod_version" to project.version.toString(),
            "minecraft_version" to minecraftVersion,
            "neoforge_version" to neoforgeVersion
        )
        inputs.properties(replacements)
        filesMatching(listOf("META-INF/neoforge.mods.toml", "pack.mcmeta")) {
            expand(replacements)
        }
    }
}

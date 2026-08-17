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

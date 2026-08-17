// Duty Framework: the plumbing every Duty module shares -- config, logging, the settings screen,
// the mixin-plugin base, and the loader abstraction.
//
// Shipped nested inside each module jar via JarJar, so installing any one module (or all of them)
// pulls in exactly one copy.
//
// == Source sets, and why there are two ==
//
// Duty targets four combinations and only ever these four:
//
//     Forge     1.20.1    Java 17
//     NeoForge  1.21.1    Java 21
//     NeoForge  26.1.2+   Java 25
//     Fabric    26.1.2+   Java 25
//
// `src/main` is loader-neutral: config, logging, the mixin-plugin base, the platform interface and
// its lookup. `src/neoforge` holds everything that names a loader -- the DutyPlatform
// implementation and the settings-screen registration, which goes through NeoForge's
// IConfigScreenFactory.
//
// That split is what makes another target mechanical rather than invasive: adding Fabric is a
// `src/fabric` source set with two classes and one META-INF/services entry, and nothing in
// `src/main` changes.
//
// It is enforced by `checkMainIsLoaderNeutral` below rather than left to discipline. ModDevGradle
// puts Minecraft *and* NeoForge on the main compile classpath, so the compiler alone would happily
// accept a `net.neoforged` import in `src/main` and only fail once someone tried to build the
// Fabric target. The check turns that into a build failure here.
//
// Only the NeoForge set is wired into the jar today, because that is the target this build produces.
// The remaining three need their own toolchains; see FEATURES.md for what each still needs.

base.archivesName = "duty-framework"

val neoforge: SourceSet by sourceSets.creating {
    // ModDevGradle only puts Minecraft and NeoForge on `main`. Inheriting its classpath is what
    // lets this set see them, and its output is what lets it implement DutyPlatform.
    compileClasspath += sourceSets.main.get().compileClasspath + sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
}

neoForge {
    mods {
        create("duty_framework") {
            sourceSet(sourceSets.main.get())
            sourceSet(neoforge)
        }
    }
}

repositories {
    maven("https://maven.shedaniel.me/") {
        name = "shedaniel"
        content { includeGroup("me.shedaniel.cloth") }
    }
}

dependencies {
    // The loader-specific set compiles against the loader-neutral one, not the other way round.
    "neoforgeImplementation"(sourceSets.main.get().output)

    // Cloth Config is optional at runtime and compile-only here. Nothing outside
    // net.dutymod.framework.screen touches it, and that package is only reached when the
    // mod is actually loaded -- see DutyConfigScreens.
    compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}")
}

// Both source sets ship in the one jar. The services entry in src/neoforge/resources is what binds
// the implementation to the interface at runtime.
tasks.named<Jar>("jar") {
    from(neoforge.output)
}

// Consumers must see the jar, not just main's classes directory.
//
// For compile avoidance Gradle offers dependents a "classes" variant pointing at
// build/classes/java/main, which does not contain the loader source set -- so a module compiling
// against this project would not find DutyConfigScreens even though it ships in the jar. Dropping
// that variant makes dependents resolve the jar, which has both source sets. It costs a little
// build time here and removes a whole class of "compiles alone, missing when consumed".
listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.findByName(name)?.outgoing?.variants?.removeIf { it.name == "classes" }
}

// Keeps src/main honest. Without this the split is only a convention, because Minecraft and
// NeoForge are both on main's compile classpath and a `net.neoforged` import there would compile
// happily -- and then fail on Fabric, far from the edit that caused it.
val checkMainIsLoaderNeutral by tasks.registering {
    group = "verification"
    description = "Fails if duty-framework's loader-neutral source set names a loader."

    val mainJava = sourceSets.main.get().allJava
    inputs.files(mainJava)
    // No meaningful output; the marker keeps Gradle from rerunning it on an unchanged source set.
    val marker = layout.buildDirectory.file("tmp/loader-neutral.ok")
    outputs.file(marker)

    doLast {
        val loaderPackages = listOf("net.neoforged", "net.fabricmc", "net.minecraftforge", "cpw.mods")
        val offences = mainJava.files.flatMap { file ->
            file.readLines()
                .withIndex()
                .filter { (_, line) ->
                    line.startsWith("import ") && loaderPackages.any { line.contains(it) }
                }
                .map { (index, line) -> "${file.name}:${index + 1}  ${line.trim()}" }
        }
        if (offences.isNotEmpty()) {
            throw GradleException(
                "duty-framework/src/main must not name a mod loader; move this to a " +
                    "loader source set such as src/neoforge:\n  " + offences.joinToString("\n  ")
            )
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok")
    }
}

tasks.named("check") { dependsOn(checkMainIsLoaderNeutral) }

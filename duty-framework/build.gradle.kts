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
// It is enforced by `checkMainIsLoaderNeutral`, defined once in the root build for every mod
// project, rather than left to discipline. ModDevGradle
// puts Minecraft *and* NeoForge on the main compile classpath, so the compiler alone would happily
// accept a `net.neoforged` import in `src/main` and only fail once someone tried to build the
// Fabric target. The check turns that into a build failure here.
//
// Only the NeoForge set is wired into the jar today, because that is the target this build produces.
// The remaining three need their own toolchains; see FEATURES.md for what each still needs.

base.archivesName = "duty-framework"

repositories {
    maven("https://maven.shedaniel.me/") {
        name = "shedaniel"
        content { includeGroup("me.shedaniel.cloth") }
    }
}

dependencies {
    // Cloth Config is optional at runtime and compile-only here. Nothing outside
    // net.dutymod.framework.screen touches it, and that package is only reached when the
    // mod is actually loaded -- see DutyConfigScreens.
    compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}")
}

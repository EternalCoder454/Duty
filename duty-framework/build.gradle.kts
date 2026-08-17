// Shared plumbing for the three Duty modules: config, annotations, mixin gating.
// Shipped nested inside each module jar via JarJar, so installing any one module
// (or all three) pulls in exactly one copy.

base.archivesName = "duty-framework"

neoForge {
    mods {
        create("duty_framework") {
            sourceSet(sourceSets.main.get())
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
    // Cloth Config is optional at runtime and compile-only here. Nothing outside
    // net.dutymod.framework.screen touches it, and that package is only reached when the
    // mod is actually loaded -- see DutyConfigScreens.
    compileOnly("me.shedaniel.cloth:cloth-config-neoforge:${rootProject.property("cloth_config_version")}")
}

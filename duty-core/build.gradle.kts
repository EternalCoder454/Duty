// Shared plumbing for the three Duty modules: config, annotations, mixin gating.
// Shipped nested inside each module jar via JarJar, so installing any one module
// (or all three) pulls in exactly one copy.

base.archivesName = "duty-core"

neoForge {
    mods {
        create("duty_core") {
            sourceSet(sourceSets.main.get())
        }
    }
}

// Duty: WorldGen = FastNoise (MPL-2.0)
//
// World generation, which is the whole cost of a pregenerating pack: noise sampling, surface
// building and biome lookup. Separate from duty-server because that module is about work a running
// server does -- searches, the network pipeline, saves -- and this is about work done once per
// chunk, before anyone sees it.
//
// Ported from Yarn to Mojang mappings rather than shipped as a prebuilt jar. Upstream is
// https://codeberg.org/ZenXArch/FastNoise at tag 1.0.40+26.1+neoforge, built by neo-loom-remap:
// Yarn source, remapped on the way out. Duty is Mojang throughout, so the source was translated
// with tools/yarn2mojang.py from Loom's own three-namespace mapping file, and the accessWidener
// converted by tools/aw2at.py. Both are reusable; most Fabric-origin performance mods are Yarn,
// and hand-renaming is the failure mode where a wrong name compiles clean and dies at load.
//
// One upstream fix carried over: the published ver/neoforge/26.1 branch imports
// fastnoise.noise.container.BlockCountingPalettedContainer and ships that directory empty, so it
// does not compile as released. The file exists on origin/26.1 and is restored.

base.archivesName = "duty-worldgen"

neoForge {
    mods {
        create("duty_worldgen") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("server") { server() }
        create("client") { client() }
    }
    // Noise, surface and palette internals. Generated from the upstream accessWidener; regenerate
    // with tools/aw2at.py rather than editing.
    accessTransformers.from(
        "src/main/resources/META-INF/accesstransformer.cfg",
        "src/main/resources/META-INF/accesstransformer.extra.cfg",
    )
}

dependencies {
    implementation(project(":duty-framework"))
    jarJar(project(":duty-framework"))
}

// Duty: Essentials = Necessities (Apache-2.0)
//
// The one module in Duty that is not about performance. It exists because the pack has no
// essentials mod at all: no homes, no warps, no /back, no moderation tools. Keeping it out of
// duty-server is deliberate -- someone installing Duty: Server for its network, redstone and save
// work should not also get forty gameplay commands and a homes file.
//
// Upstream is built on three of its author's own libraries (knot, uilib, yamlconfig). None ship
// here. uilib was declared but never imported; knot's surface came to five methods, which live in
// DutyEssentials; and yamlconfig is replaced by DutyConfig, so the options appear in Duty's own
// settings screen alongside every other module's.

base.archivesName = "duty-essentials"

neoForge {
    mods {
        create("duty_essentials") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("server") { server() }
        create("client") { client() }
    }
}

dependencies {
    implementation(project(":duty-core"))
    jarJar(project(":duty-core"))
}

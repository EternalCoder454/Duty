// Duty: Memory  =  Jasione (LGPL-3.0)  +  FerriteCore (MIT)
// Heap-footprint and allocation-pressure work. No rendering, no client-only code.

base.archivesName = "duty-memory"

neoForge {
    mods {
        create("duty_memory") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("client") { client() }
        create("server") { server() }
    }
    // TagKey and ResourceKey have private constructors; the interning cache builds shared
    // instances directly, so they need widening.
    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")
}

dependencies {
    implementation(project(":duty-core"))
    jarJar(project(":duty-core"))
}

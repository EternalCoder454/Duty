// Duty: Innovative = Async (GPL-3.0)
//
// The experimental module. Everything here is work that is genuinely unproven rather than merely
// new: it changes when the game does things, not just how fast. Nothing in it is on by default.
//
// Async ticks entities on several threads instead of one. Minecraft's entity code was written on
// the assumption that exactly one thread touches the world at a time, and Async's answer is a very
// large number of targeted mixins that make the specific things entities touch thread-safe. That
// is a real approach and it does work, but the failure mode when it does not is entities behaving
// incorrectly rather than an exception, which is why this module exists separately from the ones
// that only make existing work cheaper.
//
// Kept in Async's own packages on purpose. Its `api` package is a published integration surface --
// other mods mark their entities with AsyncCompatible or opt out through it -- and renaming it
// would silently break every one of them while compiling perfectly.

base.archivesName = "duty-innovative"

neoForge {
    mods {
        create("duty_innovative") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("server") { server() }
        create("client") { client() }
    }
    // Entity ticking reaches fields vanilla keeps to itself.
    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")
}

repositories {
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
    maven("https://jitpack.io") {
        name = "JitPack"
        content { includeGroup("com.github.bawnorton.mixinsquared") }
    }
}

dependencies {
    implementation(project(":duty-framework"))
    jarJar(project(":duty-framework"))

    // MixinSquared lets Async cancel other mods' mixins on the entity tick path when they would
    // conflict. It is a hard requirement of the upstream code, not an optional compat shim.
    val mixinSquared = "com.github.bawnorton.mixinsquared:mixinsquared-neoforge:" +
            rootProject.property("mixinsquared_version")
    // Declared twice rather than nested: Groovy's compileOnly(annotationProcessor(...)) idiom
    // does not translate to the Kotlin DSL, which types the inner call as Dependency?.
    val mixinSquaredCommon = "com.github.bawnorton.mixinsquared:mixinsquared-common:" +
            rootProject.property("mixinsquared_version")
    compileOnly(mixinSquaredCommon)
    annotationProcessor(mixinSquaredCommon)

    implementation(mixinSquared)
    jarJar(mixinSquared)

    // Compile-only: the compat mixins reference Lithium's classes but the mod is optional at
    // runtime and gated by its own mixin plugin.
    compileOnly("maven.modrinth:lithium:${rootProject.property("lithium_version")}")
}

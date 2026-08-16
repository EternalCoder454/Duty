// Duty: Server = BiomeSpy (LGPL-3.0) + KryptonReno's network pipeline (LGPL-3.0)
//
// Work that runs on the logical server and has no client half: the biome and structure
// search behind /locate, which vanilla implements as a brute-force scan, and the Netty
// pipeline, where vanilla still compresses and enciphers every packet through heap byte[].

base.archivesName = "duty-server"

neoForge {
    mods {
        create("duty_server") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("server") { server() }
        create("client") { client() }
    }
}

repositories {
    // KryptonReno's author republishes Velocity's native codecs here. Scoped to that one
    // group so a personal Maven can never shadow a Minecraft or NeoForge artifact.
    maven("https://mvnc.pkg.one/snapshots") {
        name = "pkgOneSnapshots"
        content { includeGroup("one.pkg.velocity_rc") }
    }
}

dependencies {
    implementation(project(":duty-core"))
    jarJar(project(":duty-core"))

    // libdeflate + OpenSSL bindings, with JavaVelocityCompressor/JavaVelocityCipher as the
    // fallback when the native cannot load. Netty is excluded because Minecraft already
    // ships it; bundling a second copy would split the ByteBuf class identity.
    val velocityNative = "one.pkg.velocity_rc:velocity-native:${rootProject.property("velocity_native_version")}"
    implementation(velocityNative) { exclude(group = "io.netty") }
    jarJar(velocityNative) { exclude(group = "io.netty") }
}

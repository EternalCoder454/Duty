import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
    // The light engine indexes chunk sections' palettes directly and hands work back to the chunk
    // map's thread; neither is reachable at vanilla's visibility.
    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")
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
    implementation(project(":duty-framework"))
    jarJar(project(":duty-framework"))

    // libdeflate + OpenSSL bindings, with JavaVelocityCompressor/JavaVelocityCipher as the
    // fallback when the native cannot load. Netty is excluded because Minecraft already
    // ships it; bundling a second copy would split the ByteBuf class identity.
    val velocityNative = "one.pkg.velocity_rc:velocity-native:${rootProject.property("velocity_native_version")}"
    implementation(velocityNative) { exclude(group = "io.netty") }
    jarJar(velocityNative) { exclude(group = "io.netty") }
}

// The artifact above ships natives for five platforms: 1141 KB of .so/.dll/.dylib of which the
// 284 KB under windows_x86_64 is the only part this build can ever execute. The rest is Linux
// glibc and musl, macOS arm64, and Windows arm64.
//
// Stripping them is safe because the library degrades rather than fails: NativeCodeLoader catches
// the load error and falls back to JavaVelocityCompressor / JavaVelocityCipher, both of which stay
// in the jar. A build stripped for the wrong platform is therefore slower, not broken.
//
// `duty.nativePlatforms` overrides the keep-list, so a build for another machine is one property
// away: -Pduty.nativePlatforms=windows_x86_64,linux_x86_64
val keptNativePlatforms: List<String> =
    (findProperty("duty.nativePlatforms") as String? ?: "windows_x86_64")
        .split(',').map(String::trim).filter(String::isNotEmpty)

tasks.named<Jar>("jar") {
    // Applies to the nested jar as JarJar writes it into the output.
    doLast {
        val jarFile = archiveFile.get().asFile
        val stripped = File(jarFile.parentFile, jarFile.name + ".stripping")
        var removed = 0L
        ZipFile(jarFile).use { zip ->
            ZipOutputStream(stripped.outputStream().buffered()).use { out ->
                for (entry in zip.entries()) {
                    val nested = entry.name.startsWith("META-INF/jarjar/velocity-native")
                    if (!nested) {
                        out.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                        continue
                    }
                    // rebuild the nested jar without the platforms we cannot run
                    val inner = ByteArrayOutputStream()
                    ZipOutputStream(inner).use { innerOut ->
                        ZipInputStream(zip.getInputStream(entry)).use { innerIn ->
                            while (true) {
                                val e = innerIn.nextEntry ?: break
                                val isNative = e.name.substringAfterLast('.', "") in
                                        setOf("so", "dll", "dylib")
                                val keep = !isNative ||
                                        keptNativePlatforms.any { e.name.startsWith("$it/") }
                                if (keep) {
                                    innerOut.putNextEntry(ZipEntry(e.name))
                                    innerIn.copyTo(innerOut)
                                    innerOut.closeEntry()
                                } else {
                                    removed += e.size.coerceAtLeast(0)
                                }
                            }
                        }
                    }
                    out.putNextEntry(ZipEntry(entry.name))
                    out.write(inner.toByteArray())
                    out.closeEntry()
                }
            }
        }
        stripped.copyTo(jarFile, overwrite = true)
        stripped.delete()
        logger.lifecycle("duty-server: kept natives for $keptNativePlatforms, dropped ${removed / 1024} KB")
    }
}

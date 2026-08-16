// ModernFix's mixin-config annotation processor, carried over with Duty's package names.
//
// ModernFix does not check its .mixins.json files into source control; this processor
// generates them from the @Mixin classes and the annotations in
// net.dutymod.fixerupper.annotation. Without it, duty-fixerupper builds a jar whose
// mixins are never registered.
//
// Plain Java library: it runs inside javac, not inside Minecraft.
//
// Consumed as a shadow jar. The split between the two dependency configurations below is
// the whole point, so it is worth stating plainly:
//
//   implementation-only  -> bundled INTO the shadow jar, invisible to consumers
//   implementation+shadow -> left OUT of the jar, passed to consumers as normal deps
//
// sponge-mixin and the dist markers are bundled. If they were exported instead, they would
// land on duty-fixerupper's annotationProcessor path and shadow the sponge-mixin that
// ModDevGradle puts there; that mismatch activates Mixin's own annotation processor with an
// MCP obfuscation service, which then fails every @Inject with "Unable to locate obfuscation
// mapping". NeoForge runs Mojang mappings in dev and production, so no remapping should be
// happening at all.

plugins {
    id("java-library")
    id("com.gradleup.shadow") version "8.3.9"
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") { name = "Fabric" }
    maven("https://maven.neoforged.net/releases") { name = "NeoForged" }
    maven("https://maven.minecraftforge.net") { name = "MinecraftForge" }
}

dependencies {
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
    compileOnly("com.google.auto.service:auto-service:1.1.1")

    // Exported to consumers: ordinary libraries with no bearing on mixin processing.
    implementation("com.google.code.gson:gson:2.10.1")
    shadow("com.google.code.gson:gson:2.10.1")
    implementation("com.google.auto:auto-common:1.2.1")
    shadow("com.google.auto:auto-common:1.2.1")
    implementation("com.google.guava:guava:33.6.0-jre")
    shadow("com.google.guava:guava:33.6.0-jre")

    // Depends on the annotations module, never on duty-fixerupper: the latter depends on
    // this processor to compile, so pointing back at it would be a dependency cycle.
    implementation(project(":duty-annotations"))
    shadow(project(":duty-annotations"))

    // Bundled, not exported -- see the note at the top of this file. The validator reads
    // all three dist markers so it can tell a client-only mixin from a common one whichever
    // annotation the source carries.
    implementation("net.fabricmc:sponge-mixin:0.15.2+mixin.0.8.7")
    implementation("net.fabricmc:fabric-loader:0.16.10")
    implementation("net.minecraftforge:mergetool:1.1.7")
    implementation("net.neoforged:mergetool:2.0.2")
}

tasks.shadowJar {
    // Deliberately keeps the default "-all" classifier. Clearing it makes the shadow jar
    // overwrite the plain jar's output path, and Gradle then rejects the build because
    // duty-fixerupper consumes a file produced by a task it does not depend on.
    // Keep this processor's own service registration; do not merge in the one sponge-mixin
    // carries, or Mixin's annotation processor would be activated from inside this jar as
    // well as from ModDevGradle's copy.
    exclude("META-INF/services/org.spongepowered.**")
}

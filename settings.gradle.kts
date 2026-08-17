pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "duty"

// Shared plumbing: config, annotations, the mixin plugin base. Not shipped alone.
include("duty-core")

// Mixin annotations, shared by duty-fixerupper and the processor that reads them.
include("duty-annotations")

// Generates duty-fixerupper's mixin configs at compile time. Not shipped.
include("fixerupper-mixin-ap")

// The shipped jars.
include("duty-memory")
include("duty-client")
include("duty-fixerupper")
include("duty-server")
include("duty-essentials")
include("duty-all")

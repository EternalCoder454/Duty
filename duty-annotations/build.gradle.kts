// The annotations duty-fixerupper's mixins carry and the mixin-config processor reads.
//
// Their own module on purpose. With them inside duty-fixerupper, the processor had to
// depend on duty-fixerupper to see them while duty-fixerupper depended on the processor
// to compile -- a dependency cycle Gradle rejects outright. ModernFix splits them the
// same way for the same reason.
//
// Plain Java library: no Minecraft, no ModDevGradle.
plugins {
    id("java-library")
}

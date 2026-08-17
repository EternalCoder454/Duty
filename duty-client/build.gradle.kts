// Duty: Client  =  Particle Core (MIT) + EntityCulling (tr7zw Protective)
//                  + OptimisedBlockEntities (LGPL-3.0) + OcclusionCulling (MIT)
//
// NOT DISTRIBUTABLE. EntityCulling's licence grants use, modification and compilation
// but never redistribution, so this jar is a personal build only. See NOTICE.

base.archivesName = "duty-client"

neoForge {
    mods {
        create("duty_client") {
            sourceSet(sourceSets.main.get())
        }
    }
    runs {
        create("client") { client() }
    }
    // Baking block entities into the chunk mesh needs at vanilla internals that are not
    // public -- model parts, the block state model map, section compilation.
    accessTransformers.from("src/main/resources/META-INF/accesstransformer.cfg")
}

repositories {
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
    maven("https://maven.caffeinemc.net/releases") {
        name = "CaffeineMC"
        content { includeGroup("net.caffeinemc") }
    }
}

dependencies {
    implementation(project(":duty-framework"))
    jarJar(project(":duty-framework"))

    // ImmediatelyFast uses Reflect for its Iris compatibility probe and to reach
    // MapTextureManager$MapInstance. Bundled, the way upstream bundles it.
    implementation("net.lenni0451:Reflect:1.6.2")
    jarJar("net.lenni0451:Reflect:[1.6.2,2.0)") { version { prefer("1.6.2") } }

    // Optional integrations, all compile-only: each guards a compat path that only runs
    // when that mod is installed, and the matching mixins are gated on its presence.
    val sodiumVersion = rootProject.property("sodium_version") as String
    compileOnly("net.caffeinemc:sodium-neoforge-api:$sodiumVersion")
    compileOnly("net.caffeinemc:sodium-neoforge-mod:$sodiumVersion")
    compileOnly("maven.modrinth:iris:${rootProject.property("iris_version")}")
    compileOnly("maven.modrinth:entity-model-features:${rootProject.property("emf_version")}")
    compileOnly("maven.modrinth:entitytexturefeatures:${rootProject.property("etf_version")}")
    compileOnly("maven.modrinth:lootr:${rootProject.property("lootr_version")}")
}

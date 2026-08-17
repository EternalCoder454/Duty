// Duty: the performance modules in one jar.
//
// This module has no source of its own. It exists so there are two ways to install Duty:
// the four modules separately, for anyone who wants only some of them, or this one jar,
// which nests all four through JarJar and loads them as the same four mods.
//
// duty-essentials is deliberately not among them. It is the one module that is not about
// performance, and this jar's whole meaning is "install Duty and the game gets faster".
// Nesting forty gameplay commands in it would change what an existing install does on the
// next update, which is not something a packaging jar should do quietly. Anyone who wants
// the commands installs that module on purpose. Adding it here is one line if that ever
// stops being the right call.
//
// Nothing about the modules changes. They keep their own mod ids, mixin configs and config
// keys, so the settings screen, duty.properties and every log line read identically either
// way. This is packaging, not a fifth implementation.

base.archivesName = "duty-all"

neoForge {
    mods {
        create("duty_all") {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // JarJar only, deliberately: nothing here compiles against the modules, and adding them
    // to the compile classpath would let source drift into this jar unnoticed.
    //
    // Each module already nests duty-framework. Four copies arrive with identical coordinates and
    // JarJar resolves them to one, which is why duty-framework is not listed again here -- adding
    // it would be a fifth identical candidate, not a fix for anything.
    jarJar(project(":duty-memory"))
    jarJar(project(":duty-client"))
    jarJar(project(":duty-fixerupper"))
    jarJar(project(":duty-server"))
}

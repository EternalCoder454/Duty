# external/

Mods that Duty ships but does not absorb into its own source.

Everything under `duty-*` is Duty's code, written against Mojang mappings and built by the
root Gradle build. What lives here is the opposite case: a mod that already builds correctly
for 26.1.2 with a toolchain of its own, where translating it into Duty's would cost more than
it returns.

## fastnoise

Faster world generation — noise sampling, surface building and biome lookup, which is the
critical path for anything that generates chunks in bulk. Upstream is
<https://codeberg.org/ZenXArch/FastNoise>, MPL-2.0, vendored from the
`ver/neoforge/26.1` branch at tag `1.0.40+26.1+neoforge`.

**Why it is not a `duty-` module.** It is written in Yarn names and built with
`neo-loom-remap`, which compiles against Yarn and remaps to Mojang on the way out. Duty uses
Mojang mappings and ModDevGradle. Absorbing it means translating 59 Minecraft types, 13
accessor and invoker method names, and every call site across 3182 lines -- and mixin
accessors into world generation internals are exactly the code where a wrong name compiles
cleanly and fails at load. The jar it produces is already remapped to Mojang names, so
shipping that jar gets the same result with none of that risk.

**One local change.** The published `ver/neoforge/26.1` branch does not compile: it imports
`fastnoise.noise.container.BlockCountingPalettedContainer` and the directory is empty on that
branch. The file exists on `origin/26.1`, so it is restored here. Without it the build fails
with six errors before it reaches anything version-specific.

Build and install it with:

    bash tools/deploy.sh            # builds this too, alongside the duty modules

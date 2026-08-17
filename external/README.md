# external

Mods Duty ships but does not absorb.

Everything under `duty-*` is Duty's own code, Mojang mappings, built by the root Gradle build.
What lives here is the opposite case. A mod that already builds correctly for 26.1.2 with a
toolchain of its own, where translating it into Duty's costs more than it returns.

## fastnoise

Upstream <https://codeberg.org/ZenXArch/FastNoise>, MPL-2.0, branch `ver/neoforge/26.1` at tag
`1.0.40+26.1+neoforge`.

**Status: translation input only. Not shipped.** `duty-worldgen` is the Mojang mapped port of
this source and is what the pack installs. The two declare each other incompatible and would
apply the same mixins twice. `deploy.sh` skips this unless `WITH_EXTERNAL=1`.

Keep this tree untouched. It is the input to `tools/yarn2mojang.py`. Never translate a tree
that has already been translated.

**One local fix.** The published `ver/neoforge/26.1` branch does not compile. It imports
`fastnoise.noise.container.BlockCountingPalettedContainer` and ships that directory empty. The
file exists on `origin/26.1` and is restored here. Without it the build stops with six errors
before reaching anything version specific.

# duty-worldgen — Yarn to Mojang port, in progress

Not in the build. `settings.gradle.kts` and `build.gradle.kts` have it commented out, so
`gradlew build` and `tools/deploy.sh` work normally; the pack currently ships the upstream
jar from `external/fastnoise` instead, which is functionally the same mod.

## Where it stands

`gradlew :duty-worldgen:compileJava` reports **294 errors**, down from 700 on the first pass.
Everything below the source level is finished:

- `META-INF/accesstransformer.cfg` — all 47 accessWidener entries converted and each one
  verified against the real 26.1.2 jar with javap.
- `accesstransformer.extra.cfg` — the two `Climate$RTree` override widenings NeoForge needs
  and Fabric infers.
- The Minecraft artifacts task passes, so the access transformer is correct.
- Mixin config cleaned of one entry naming a class that does not exist upstream.
- `MOD_ID` is `duty_worldgen`, matching the metadata.

## What is left

All 294 are in the translated Java, in four groups:

1. **Ambiguous members** — `getDefaultState` (defaultBlockState vs defaultFluidState),
   `getBottomY`, `name`, `hasOnlyAir`, `setHeight`. The translator declines these by design
   rather than guessing. Owner-aware resolution is implemented but only fires when exactly one
   candidate's declaring class is imported by the file; these need the receiver's type, which
   means reading each call site.
2. **Nested type names** — `Configuration.Static`, `BlockPos.Mutable`, `.Type`. Written bare
   after an import of the outer class, so the import-scoped rule does not cover them.
3. **Fields** — `log`, `possibleBiomes`.
4. Whatever those are hiding.

## Doing the next pass

    T="$HOME/.gradle/caches/fabric-loom/26.1.2/net.neoforged.neoforge_26.1.2.75/loom.mappings.26_1_2.layered+hash.561494802-v2/mappings.tiny"
    rm -rf duty-worldgen/src/main/java && mkdir -p duty-worldgen/src/main/java
    cp -r external/fastnoise/src/main/java/. duty-worldgen/src/main/java/
    python tools/yarn2mojang.py "$T" duty-worldgen/src/main/java
    # then re-apply the MOD_ID change and compile

Re-translating from `external/fastnoise` is always safe, because that tree is upstream's
untouched Yarn source. Never translate an already-translated tree.

Before re-enabling, run `python tools/check-mixin-targets.py duty-worldgen`. Compiling proves
the Java; it does not prove a single mixin annotation, and those are strings.

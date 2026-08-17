# Duty

Duty does what it needs to do, perform its duty.

A performance mod built by combining several existing performance mods and dropping
everything that exists only to support older versions.

## Branches

One target per branch. Every branch is a full checkout that builds one thing, so a build
is never a matrix of conditions and a checkout is never half-configured for a version you
are not on.

| Branch | Loader | Minecraft | Java | State |
|---|---|---|---|---|
| **`main`** | NeoForge | 26.1.2+ | 25 | Builds and runs. The development line. |
| `neoforge-1.21.1` | NeoForge | 1.21.1 | 21 | Branched from main; not yet ported. |
| `fabric-26.1.2` | Fabric | 26.1.2+ | 25 | Branched from main; not yet ported. |
| `forge-1.20.1` | Forge | 1.20.1 | 17 | Branched from main; not yet ported. |

The three port branches start as copies of `main` with their build retargeted. **They do
not compile yet**, and that is their intended state rather than a fault -- see
[PORTING.md](PORTING.md) for what each still needs and why some features cannot exist on
some targets at all.

### How code moves between branches

`main` is where shared work lands first. A fix that is not version-specific is cherry-picked
outward:

```bash
git checkout fabric-26.1.2
git cherry-pick <commit-from-main>
```

The loader split is what keeps that cheap. Every module is `src/main`, which names no
loader, plus `src/<loader>`, which is the only place allowed to import one -- and 36 of
Duty's 574 source files are in the second category. A cherry-pick that touches only
`src/main` applies to every branch unchanged; the build fails on any branch where it does
not, because `checkMainIsLoaderNeutral` refuses a loader import in the neutral set.

Each branch names its own loader in `gradle.properties` (`duty.loader`), so the build
finds `src/fabric` on the Fabric branch without any build-script difference.

## Modules

Duty ships as three independent jars over a shared core. Installing one does not
require the others; `duty-framework` is nested inside each jar and loads once.

| Jar | Combines | Licence |
|---|---|---|
| **Duty: Memory** | Jasione + FerriteCore | LGPL-3.0 |
| **Duty: Client** | Particle Core + EntityCulling + OptimisedBlockEntities | Personal build only |
| **Duty: FixerUpper** | ModernFix | LGPL-3.0 |

> [!IMPORTANT]
> **Do not distribute `duty-client`.** It contains EntityCulling, whose licence grants
> use, modification and compilation but not redistribution. Building and running it
> yourself is exactly what that licence permits; uploading it, putting it in a modpack,
> or handing it to a friend is not. `duty-memory` and `duty-fixerupper` contain none of
> that code and remain freely distributable under LGPL-3.0. See [NOTICE.md](NOTICE.md).

The three-way split is the fallback the spec called for, and it earns its place: it
quarantines the non-distributable code in one jar, so the other two stay clean. It also
suits a stability-first mod, since the modules fail independently.

## Absolute rules

- Must work alongside Sodium, C2ME and other mods.
- Stability first, performance second.

Both are enforced structurally: every optimization sits behind a config toggle in
`config/duty.properties`, the mixins are gated on those toggles, and each module
declares itself `incompatible` with the upstream mod it replaces so users cannot
accidentally run both.

## Current state

**Working in-game.** All four jars build, load, and the world creates.

| Jar | Size | Contents |
|---|---|---|
| `duty-memory-0.1.0.jar` | 44 KB | Jasione's `Enum.values()` transformer + FerriteCore's shared block-state table |
| `duty-client-0.1.0.jar` | 220 KB | EntityCulling's occlusion culling + Particle Core's 17 mixins + OptimisedBlockEntities' chunk-mesh baking |
| `duty-fixerupper-0.1.0.jar` | 592 KB | ModernFix, 120 mixins registered |
| `duty-framework-0.1.0.jar` | 7 KB | shared config and logging, nested into the others via JarJar |

### Modernica was removed

FixerUpper is plain ModernFix. Modernica's 97 extra files were ported in and then taken back
out, because every one of six consecutive runtime crashes traced to them and none to
ModernFix's own code:

1. `@Shadow neighbours` -- 26.1.2 spells it `neighbors`
2. a descriptor naming `LevelChunkTicks` in the wrong package
3. `@Overwrite` on `LevelChunkSection.recalcBlockCounts`, which broke Lithium's injection and
   stopped the integrated server from starting
4. writing a `final` field from a non-constructor handler
5. a half-gated group leaving its accessors applying alone
6. an `@Invoker` declaring `void` for a method returning `boolean`

The cause is structural rather than a run of bad luck: Modernica is Fabric-only and written
against a different Minecraft, so its mixins compile against 26.1.2 while targeting an API
that has moved. ModernFix maintains a real 26.1 branch and needed none of this.

### Verified before every deploy

```bash
python tools/check-shadows.py        # @Shadow members exist on the target (incl. inherited)
python tools/check-descriptors.py    # classes and method targets named in annotations exist
python tools/check-conflicts.py      # classes Duty and an installed perf mod both patch
bash   tools/deploy.sh               # stage, remove stale jars, install, verify by hash
```

Each exists because of a crash that a clean build did not catch. A green build proves the code
compiles; it says nothing about whether a mixin applies.

### Dependencies dropped along the way

Targeting exactly one version is what makes this possible, and it removes real weight:

| Dropped | Was needed for | Replaced by |
|---|---|---|
| KotlinForForge | Particle Core's 4 Kotlin files | plain Java |
| fzzy-config | Particle Core's config | `DutyConfig` |
| ConditionalMixin | Particle Core's mixin gating | `DutyClientMixinPlugin` |
| TRansitionLib | EntityCulling's cross-version shims | direct 26.1 calls |
| Lombok | EntityCulling's generated setters | written out |
| `net.lenni0451:reflect` | Jasione's classloader lookup | `StackWalker` |

## Build

```bash
./gradlew build
```

### Set TMP/TEMP first, every time

```powershell
$env:TMP="C:\gtmp"; $env:TEMP="C:\gtmp"
.\gradlew.bat build --console=plain
```

Without this, Gradle dies at startup with `Unable to establish loopback connection`. The
cause is not networking: `TEMP` resolves to the 8.3 short path
`C:\Users\ZACHAR~1\AppData\Local\Temp`, Windows AF_UNIX sockets reject short-name paths,
and `Selector.open()` uses AF_UNIX. Reproduce it either way with:

```bash
java tools/SelectorLoopbackCheck.java
```

It fails under the short TEMP and passes under `C:\gtmp`. The setting does not persist
between shell invocations, so it has to be repeated every time. See `../BUILDING.md` for
the rest of the local build conventions.

### Verifying without Gradle

Because of the above, there is an offline compile check that reuses what Gradle already
downloaded -- the recompiled Minecraft jar from the NeoForm cache, plus NeoForge, Mixin
and friends from the module cache -- and runs `javac` directly:

```bash
bash tools/verify-compile.sh
```

That covers `duty-memory` and `duty-client`. It confirms the code compiles and that every
Minecraft symbol it names exists in 26.1.2; it does not confirm a mixin injection point
resolves, and says nothing about runtime behaviour. `duty-fixerupper` is excluded because
it needs an access transformer applied to the Minecraft jar and its annotation processor
run, neither of which works outside Gradle.

To check a mixin target by hand -- worth doing, it caught two real bugs here:

```bash
javap -p -cp "$TMPDIR/duty-verify/cp/minecraft.jar" net.minecraft.client.renderer.LevelRenderer
```

## The Jasione mass-ASM warning

The spec asked whether this is normal and to fix it if not:

```
[ne.ne.fm.cl.tr.ClassTransformStatistics/]: Class processor jasione:main transformed
100.00% of loaded class which is suspiciously high; it may be attempting mass-ASM.
Please report this to the mod author.
```

**It is normal, and the number does not mean what the message says.**

Reading FancyModLoader's `ClassTransformStatistics` and `ClassProcessorSet`: the
numerator is incremented in `ClassProcessorSet#transformersFor`, every time a
processor's `handlesClass` returns `true`. It is never incremented from the result of
`processClass`. So the percentage counts classes a processor asked to *look at*, not
classes it *changed*. A processor that inspects everything and rewrites almost nothing
reports 100%, exactly like one that rewrites everything.

Jasione returns a constant `true` from `handlesClass`, so it reports 100%. It has
little choice: `SelectionContext` exposes only the class's `Type` and whether it is
empty. There is no bytecode at selection time, and no way to tell from a class name
whether it contains an `Enum.values()` call. The `BytecodeProvider` from `link()` is
not an escape hatch either — it runs the processor chain to produce bytes, so calling
it for the class being loaded would re-enter the pipeline.

So the warning is a false positive in substance. Jasione is not doing mass-ASM; it is
doing a targeted rewrite on a small fraction of classes while being *offered* all of
them. Nothing is wrong with your setup and there is no bug to report to decce.

What Duty does about it:

- `handlesClass` rejects packages that cannot benefit — the JDK, ASM, the Mixin
  runtime, FML itself, and Duty's own generated holders. This is a real saving, not
  cosmetic: rejected classes never get a `ClassNode` built for this processor.
- `processClass` returns `NO_REWRITE` whenever nothing changed, so untouched classes
  cost one cheap instruction scan.
- Generated cache holders use FML's `generatesPackages()` mechanism instead of
  Jasione's reflective `defineClass` into the transforming classloader. That removes
  the reflection, and with it a race that upstream papers over by catching
  `LinkageError` when two threads define the same class at once.

The warning will still appear, because Minecraft and mod classes are most of what
gets loaded and this optimization is inherently whole-program. **The accurate fix
belongs in FancyModLoader**: counting `ComputeFlags != NO_REWRITE` instead of
`handlesClass` would make the statistic mean what its message claims, and would put
this processor far below the 25% threshold. That is worth filing upstream — it
currently tells users to report a non-bug to mod authors, for any whole-program
optimizer.

## Configuration

`config/duty.properties`, plain key/value, written on first run. It is deliberately
not JSON or TOML: the class transformer reads it during class loading, before the mod
list exists, so it cannot touch NeoForge, Mixin or a JSON library without risking a
classloading cycle.

Options belonging to a module you do not have installed are preserved rather than
deleted, so adding and removing Duty jars does not lose your settings.

## Licensing

Duty is assembled from LGPL-3.0 and MIT code. Read [NOTICE.md](NOTICE.md) before
distributing anything — in particular the EntityCulling section.

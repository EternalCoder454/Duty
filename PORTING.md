# Porting Duty to another target

One target per branch. This is what each still needs, and what cannot be done at all.

## The four targets

| Branch | Loader | Minecraft | Java |
|---|---|---|---|
| `main` | NeoForge | 26.1.2+ | 25 |
| `neoforge-1.21.1` | NeoForge | 1.21.1 | 21 |
| `fabric-26.1.2` | Fabric | 26.1.2+ | 25 |
| `forge-1.20.1` | Forge | 1.20.1 | 17 |

## What is already done

Every module is split into two source sets:

- `src/main` — names no loader. 538 of Duty's 574 source files.
- `src/<duty.loader>` — the only place allowed to import one. 36 files.

`gradle.properties` names the loader for the branch (`duty.loader`), and the build derives
the source set and its task names from that, so the Fabric branch finds `src/fabric` with
no build-script difference from `main`.

The split is enforced, not conventional: `checkMainIsLoaderNeutral` fails the build if
`src/main` imports a loader. That is what makes a cherry-pick from `main` safe — if it
touches only the neutral set it applies everywhere, and if it does not, the branch that
receives it fails loudly rather than at runtime.

## Porting the loader axis (Fabric, Forge)

The 36 loader files fall into three groups.

**Entry points and event wiring** — mechanical. Each loader's equivalent of `@Mod`, the
mod event bus, and lifecycle events. Roughly a dozen files.

**Flags only the loader can set.** `FixerUpperState.registryEventsFired` is written by the
loader entry point and read by loader-neutral mixins. Fabric has no registry events in the
NeoForge sense; its implementation sets the flag at the equivalent point in its own
lifecycle. Look for this pattern rather than only for imports — a flag leaks the loader
into shared code without any import to find.

**NeoForge-only features.** Capabilities, registry events, and NeoForge's model pipeline
(`ModelData`, `UnbakedModelParser`, `StandaloneModelLoader`) have no Fabric equivalent.
These are not awaiting a port; they are permanently loader-specific, and a Fabric build
ships without them. This is why FixerUpper generates two mixin configs —
`duty_fixerupper.mixins.json` and `duty_fixerupper_<loader>.mixins.json` — so a Fabric
build can ship the first without the second.

## Porting the version axis (1.21.1, 1.20.1)

Three of these are ordinary work. Three are not.

| Depends on | Since | Where | Consequence |
|---|---|---|---|
| `Identifier` | 26.1 | 62 files, four modules | A rename of `ResourceLocation`. Mechanical. |
| `ValueInput` / `ValueOutput` | 26.1 | duty-essentials player data | Pre-26.1 uses `CompoundTag`. Abstractable. |
| `PermissionSet` / `PermissionLevel` | 26.1 | 20 duty-essentials files | Pre-26.1 uses integer levels. Abstractable. |
| `RenderType` / `RenderSetup` rework | 26.1 | 7 duty-client batching files | A rewrite per version, not a rename. |
| Recipe display system | 1.21.2 | duty-client ClientCrafting | **Cannot exist** on 1.20.1 or 1.21.1. |
| Data components | 1.20.5 | duty-memory's core work, 2 FixerUpper mixins | **Cannot exist** on 1.20.1. |

On 1.20.1, duty-memory's central feature and duty-client's crafting prediction have nothing
to attach to. Those builds ship the module with the feature absent rather than
reimplemented — the alternative is inventing a different optimisation and calling it the
same name.

## Verification, which is the part that is easy to skip

Every checker in `tools/` compares against **one** Minecraft jar. `check-shadows` and
`check-descriptors` are only meaningful against the jar for the branch being built, so each
branch needs its own run. A port that builds green without them is unverified, and this
project has repeatedly found that worse than a red build — a green build has proven nothing
here on at least four occasions:

- a mixin whose `@Overwrite` bound to the wrong overload,
- an annotation processor that stopped listing 14 mixins after a source-set move,
- checkers that silently skipped a whole module because their module list was hardcoded,
- a `META-INF/services` file naming a class that a package rename had left behind, which
  crashed startup before any mod loaded.

Run all six after any port, per branch:

```bash
python tools/check-shadows.py
python tools/check-descriptors.py
python tools/check-mixin-configs.py
python tools/check-orphan-mixins.py
python tools/check-class-strings.py
python tools/check-conflicts.py
```

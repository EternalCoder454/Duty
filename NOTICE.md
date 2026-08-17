# Attribution and licensing

Duty is assembled from other people's work. This file records where each part came
from and what its licence permits, because "combine these mods" is a licensing
question before it is an engineering one.

## Upstream licences

| Mod | Author | Licence | Redistribution permitted? |
|---|---|---|---|
| [ModernFix](https://github.com/embeddedt/ModernFix) | embeddedt | LGPL-3.0 | Yes, under LGPL-3.0 |
| [Modernica](https://git.nostalgica.net/Reverie-Projects/monorepo) (`mods/modernica`) | Reverie Projects | LGPL-3.0 | Yes, under LGPL-3.0 |
| [Jasione](https://github.com/decce6/Jasione) | decce | LGPL-3.0 | Yes, under LGPL-3.0 |
| [FerriteCore](https://github.com/malte0811/FerriteCore) | malte0811 | MIT | Yes |
| [Particle Core](https://github.com/fzzyhmstrs/pc) | fzzyhmstrs | MIT | Yes |
| [EntityCulling](https://github.com/tr7zw/EntityCulling) | tr7zw | tr7zw Protective License | **No** |
| [OcclusionCulling](https://github.com/LogisticsCraft/OcclusionCulling) | LogisticsCraft | MIT | Yes |
| [OptimisedBlockEntities](https://github.com/maDU59/OptimisedBlockEntities) | maDU59 | LGPL-3.0 | Yes, under LGPL-3.0 |
| [Fast-Tag](https://github.com/nutant233/Fast-Tag) | nutant233 | LGPL-3.0 | Yes, under LGPL-3.0 |
| [Resource-Trimmer](https://github.com/Darkhax-Minecraft/Resource-Trimmer) | Darkhax | LGPL-3.0 | Yes, under LGPL-3.0 |
| [BiomeSpy](https://github.com/MoePus/BiomeSpy) | MoePus | LGPL-3.0 | Yes, under LGPL-3.0 |
| [Stfu](https://github.com/ItsFelix5/stfu) | ItsFelix5 | MIT | Yes |
| [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast) | RaphiMC | LGPL-3.0 | Yes, under LGPL-3.0 |
| [KryptonReno](https://github.com/404Setup/KryptonReno) | 404Setup | LGPL-3.0 | Yes, under LGPL-3.0 |
| velocity-native (bundled whole, not merged) | see below | **unstated in the artifact** | Unclear — see below |

`duty-server` bundles `one.pkg.velocity_rc:velocity-native`, a republished build of
[Velocity's](https://github.com/PaperMC/Velocity) native compression and encryption bindings, via
JarJar. It is the only third-party binary Duty ships that it did not compile itself, and its
licence position is the least clear thing in this file, so state it plainly rather than guess:

- The jar contains **no LICENSE or NOTICE file**, and its POM declares **no `<licenses>` element**.
- Upstream Velocity is **GPL-3.0**. The class names, package (`com.velocitypowered.natives`) and
  the `JavaVelocityCompressor` / `LibdeflateVelocityCompressor` split are Velocity's.
- It is republished on a personal Maven (`mvnc.pkg.one`) as a **snapshot**, by KryptonReno's
  author rather than by PaperMC.

Duty pins the resolved timestamp (`3.4.0-20260111.140946-9`) rather than the floating
`-SNAPSHOT`, so a rebuild cannot silently pull different native code into the network pipeline.
Nothing here is a problem for a private build; it would need resolving before any distribution,
and GPL-3.0 is the assumption to work from until the author states otherwise.

## EntityCulling: bundled, personal build only

`Duty: Client` contains a port of EntityCulling. This is fine for a personal
build and not fine for a public one, so the distinction matters.

The tr7zw Protective License grants permission "to use, modify and compile the
Software", subject to it not being used for commercial advantage or monetary
compensation. Building Duty for your own machine and running it is exactly *use*,
*modification* and *compilation* — squarely inside what the licence grants. LGPL-3.0's
obligations likewise attach to conveying a work, not to modifying one privately, so
combining it with LGPL code in a jar that never leaves your machine triggers nothing.

What the licence does **not** grant is redistribution. Compare the MIT wording it is
derived from, which grants permission "to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell". The words `copy`, `publish`, `distribute` and
`sublicense` are absent. Their omission from an otherwise near-verbatim MIT text
reads as deliberate, and the licence's name points the same way.

**So: do not publish, upload, or hand anyone a `duty-client` jar.** Not to Modrinth
or CurseForge, not in a modpack, not to a friend, not on a server that distributes
client mods. Sharing it also drags in the second problem — the no-commercial-advantage
clause is an added restriction LGPL-3.0 section 7 forbids a conveyed combined work from
carrying, so a shared jar would breach the LGPL modules' terms too.

`duty-memory` and `duty-fixerupper` contain no EntityCulling code and are unaffected;
those two remain distributable under LGPL-3.0.

Two ways to make `Duty: Client` distributable:

1. **Ask tr7zw for permission.** A written grant to redistribute under a specific
   licence would settle it.
2. **Reimplement instead of porting.** The *idea* — tracing entity bounding boxes
   against world geometry on a worker thread — is not protected. An independent
   implementation written without consulting the original source would be Duty's own
   code. Note the vendored occlusion tracer underneath it
   ([OcclusionCulling](https://github.com/LogisticsCraft/OcclusionCulling), MIT) is
   already freely redistributable; only tr7zw's Minecraft integration layer is not.

### What was actually taken

Ported into `net.dutymod.client.cull`, rewritten against 26.1 APIs directly rather
than copied: the culling thread and its snapshot handoff, the `Cullable` state
interface, the world data provider, and five mixins. Dropped in the process:
tr7zw's TRansitionLib cross-version abstraction, Lombok, the ModMenu config screen,
and the debug collector — all of which existed to support versions and loaders Duty
does not target.

`net.dutymod.client.occlusion` is LogisticsCraft's OcclusionCulling library (MIT),
vendored and relocated from `com.logisticscraft.occlusionculling`. Unmodified except
for the package rename.

`net.dutymod.client.obe` is OptimisedBlockEntities (LGPL-3.0), relocated from
`fr.madu59.obe`. Its two `@Mod` entrypoints were folded into `DutyClient` so the jar
registers one mod rather than three, its bespoke config screen was dropped in favour of
`duty-blockentities.json`, and its Sodium and Lootr compat mixins are now gated on those
mods actually being installed. Note that adding LGPL-3.0 code to this jar makes the
EntityCulling licence conflict above concrete rather than theoretical -- another reason
this module stays a personal build.

### Modernica: what was taken, and what was not

Modernica is a Fabric-only fork of ModernFix, so it could not be merged wholesale onto a
NeoForge base. What was carried across is the set of features Modernica *adds* over
ModernFix -- 26 mixin groups plus their supporting classes, all platform-neutral Minecraft
code. Its shared-with-ModernFix packages were deliberately left alone; ModernFix's own
NeoForge-targeted versions are what Duty builds on.

Three groups were excluded on purpose:

- `perf/blockstate_propertyaccess` and `perf/state_definition_construct` mix into
  `StateHolder` and `StateDefinition`, which Duty: Memory already replaces wholesale with
  FerriteCore's shared table. Two competing block-state implementations in sibling jars
  that may or may not both be installed is exactly the instability the project's rules
  forbid. Modernica's own `FerriteCorePostProcess` shows the upstream author reached the
  same conclusion, detecting FerriteCore at runtime and deferring to it.
- `perf/network_optimizations` is the merged-in Krypton code. It depends on Fabric
  networking and a shaded `velocity-native`, neither of which applies here.

Modernica's `MixinGate` was dropped as well -- it reads a fzzy-config layer that does not
exist in Duty -- and the one mixin using it now takes its value from a system property,
gated by ModernFix's own package-derived option system like every other mixin here.

## Licence of each Duty module

Combining LGPL-3.0 and MIT code is fine in one direction only: MIT code can be
taken into an LGPL-3.0 work, and the result is LGPL-3.0. The reverse does not hold.
That determines each module's licence:

| Module | Built from | Licence |
|---|---|---|
| `duty-framework` | original code | MIT |
| `duty-memory` | Jasione (LGPL-3.0) + FerriteCore (MIT) + Fast-Tag (LGPL-3.0) | **LGPL-3.0** |
| `duty-client` | Particle Core (MIT) + OptimisedBlockEntities (LGPL-3.0) + OcclusionCulling (MIT) + Stfu (MIT) + EntityCulling (Protective) | **Not distributable** |
| `duty-fixerupper` | ModernFix (LGPL-3.0) + Resource-Trimmer (LGPL-3.0) | **LGPL-3.0** |
| `duty-server` | BiomeSpy (LGPL-3.0) + KryptonReno (LGPL-3.0), bundling velocity-native | **LGPL-3.0** |

`duty-framework` is deliberately MIT even though it is original work: had it been
LGPL-3.0, it would have dragged `duty-client` to LGPL-3.0 along with it for no
benefit to anyone.

## Obligations this places on Duty

Under LGPL-3.0, for `duty-memory` and `duty-fixerupper`:

- Ship the full LGPL-3.0 text (`LICENSE`) and this attribution file.
- Keep the corresponding source available to anyone who receives a jar.
- Keep copyright notices in ported files intact, and mark modified files as changed.
- Do not add restrictions on top — no "personal use only", no anti-redistribution
  clause on the modules as a whole.

Under MIT, for `duty-framework` and the MIT-licensed parts of `duty-client`: keep the
copyright notice and the permission notice. That is all it asks.

For `duty-client` as a whole: build it, run it, keep it. Do not hand it to anyone.

## A note on the "merge everything into one jar" plan

The README's first preference was a single combined mod. The three-module split is
its stated fallback, and it is the better answer here for a reason that outlived the
licensing question: it quarantines the non-distributable code. Everything that cannot
be shared lives in `duty-client` and nowhere else, so `duty-memory` and
`duty-fixerupper` stay clean LGPL-3.0 jars you could publish tomorrow. A single
merged jar would have made the whole project unshareable.

The split also earns its keep operationally: the modules fail independently, and a
user who only wants the memory work does not have to take the particle renderer.

## Later additions

**Fast-Tag** (`net.dutymod.memory.tags`) interns TagKey and ResourceKey. Lombok removed; the
private constructors it builds through are widened by duty-memory's access transformer.

**Resource-Trimmer** (`...mixin.perf.compact_identifier_encoding`) shortens identifiers on the
wire. Off by default: it changes the protocol, so both ends must run Duty.

**BiomeSpy** (`net.dutymod.server.biome`) replaces the brute-force scan behind /locate. Its
TerraBlender compat was dropped -- it compiles against a CurseForge-only API and TerraBlender is
not in use.

**Stfu** (`net.dutymod.client.stfu`) ported from the `multiversion` branch, which targets 26.1.2
with Mojang mappings. Its Fabric entrypoint became a NeoForge one, YACL config became DutyConfig,
and its access widener was translated to an access transformer. `CombineBars` was not carried
over: it is dense with 26.1-versus-26.2 conditionals and is a cosmetic HUD change.

**ScalableLux** (`ca.spottedleaf.starlight`, LGPL-3.0) replaces the light engine in
`duty-server`. Derived from PaperMC's Starlight by Spottedleaf, forked and maintained by
RelativityMC after Starlight stopped being maintained in March 2024; taken from that fork's
`port/neoforge/26.1.2` NeoForge patch. It ships with `FlowSched` (RelativityMC), vendored
under `net.dutymod.server.flowsched`.

Two packaging decisions worth recording, because they look inconsistent and are not:

- The engine **keeps** its `ca.spottedleaf.starlight` package rather than moving into
  `net.dutymod`. C2ME's `threading-lighting` module ships a mixin targeting
  `ca/spottedleaf/starlight/common/thread/SchedulingUtil` by literal name, which hands the
  engine's scheduling to C2ME's prioritised scheduler. Renaming would silently cost that
  integration and leave both mods scheduling lighting independently.
- FlowSched **is** relocated, because C2ME shades its own copy of
  `com.ishland.flowsched` and two copies on one classloader is a real conflict. Nothing
  outside the engine references it by name, so moving it costs nothing.

One upstream bug was fixed in the process: `BlockStarLightEngine.getSources` set
`mutablePos4` to the block being tested and then read light emission at `mutablePos1`,
which that method never writes. See the comment at the call site.

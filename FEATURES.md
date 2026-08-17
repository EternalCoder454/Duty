# Feature sources: assessed, categorized, and why

Eight mods were assessed for merging into Duty. Each was checked for licence, 26.1.2 support,
platform, and whether it duplicates something Duty or the modpack already has. Four are worth
taking, one is already in the pack, and three are blocked.

## Verdicts

| Mod | Licence | 26.1.2 | Platform | Verdict |
|---|---|---|---|---|
| [Fast-Tag](https://github.com/nutant233/Fast-Tag) | LGPL-3.0 | `neoforge/26.1` | **NeoForge** | **merged** into Duty: Memory |
| [KryptonReno](https://github.com/404Setup/KryptonReno) | LGPL-3.0 | `26.1.2` | NeoForge | **merged** → `duty-server`, see below |
| [BiomeSpy](https://github.com/MoePus/BiomeSpy) | LGPL-3.0 | `26.1` | Fabric + NeoForge | **merged** into Duty: Server |
| [Resource-Trimmer](https://github.com/Darkhax-Minecraft/Resource-Trimmer) | LGPL-3.0 | `26.1.2` | Fabric + NeoForge | **merged** into Duty: FixerUpper |
| [Stfu](https://github.com/ItsFelix5/stfu) | MIT | `multiversion` targets 26.1.2 | Fabric | **merged** into Duty: Client |
| [ImmediatelyFast](https://github.com/RaphiMC/ImmediatelyFast) | LGPL-3.0 | `26.1` | NeoForge | **merged** into Duty: Client |
| [Fastload](https://github.com/BumbleSoftware/FastLoad) | none (ARR) | 1.20.1 max | Fabric (Yarn) | **rejected** — all three features are vanilla in 26.1.2, see below |
| [LightLoad](https://github.com/mynamexiaopiao/LightLoad) | none (ARR) | **1.20.1** | Forge | approved by owner; mostly duplicates ModernFix |

### Licensing

Fastload and LightLoad carry no licence file. The project owner has assessed this, keeps these
builds private and non-commercial, and accepts responsibility for it. Recorded here as a fact
about the build, not as an open question.

### Why the three are set aside

**ImmediatelyFast** is already in the pack as `ImmediatelyFast-NeoForge-1.15.3+26.1.jar`, and
Duty's conflict scan shows it and Duty already overlap on six classes. Merging it would put two
copies of the same batching logic in the game. If you want it inside Duty instead, the standalone
jar has to come out first — say so and it becomes a straightforward merge.

**Fastload** (68 files, 3.4k lines) and **LightLoad** (86 files, 8.6k lines) both target
**1.20.1** — five versions behind. That is the real obstacle, and it is technical rather than
legal. Modernica was *one* version out of step and produced six consecutive runtime crashes
before being removed; twelve thousand lines from 1.20.1 is the same failure profile at four times
the size.

Two further findings from reading them:

- **LightLoad overlaps ModernFix.** Six of its 42 mixin classes share names with ModernFix's own
  (`BrandingControlMixin`, `FilePackResourcesMixin`, `PathPackResourcesMixin`,
  `ReloadableResourceManagerMixin`, `SimpleReloadInstanceMixin`, `MinecraftMixin`), and its README
  states the resource and model optimization was adapted from ModernFix. Duty already ships
  current ModernFix, so the headline features are present and newer. The remaining 36 mixins are
  the part that could add something.
- **Fastload overlaps C2ME.** Its chunk preloading works on the chunk system that C2ME rewrites
  wholesale in this pack. Two mods restructuring chunk loading is the class of overlap that
  produced the Lithium crash.

LightLoad is not off the table and is a session of work, to be done against a build that is known
good with the checkers run between each group. **Fastload is closed — see below.**

### Fastload: rejected, superseded by vanilla 26.1.2

Investigated 2026-08-15 and **not merged**. The C2ME overlap above turned out not to be the
deciding factor. All three of Fastload's advertised features target vanilla machinery that Mojang
has since deleted and rebuilt, so there is nothing left to port.

Upstream state: nine branches, newest MC target **1.20.1**; `Fabric-universal` is the default and
its last commit is **2023-06-22**. Yarn mappings, Fabric, Java 17.

| Fastload feature | What it patched | State in 26.1.2 |
|---|---|---|
| 441 pre-generator | `@ModifyConstant` on `prepareStartRegion`, `intValue = 441` | **Constant and method both gone.** `MinecraftServer.prepareLevels()` now builds a `ChunkLoadCounter`, calls `track(level, …)` per level to activate ticket storage (`TicketStorage.activateAllDeactivatedTickets`, plus NeoForge's `ForcedChunkManager`), and waits only on ticket-backed chunks. The blind 21×21 square no longer exists. |
| Cancellable loading screens | replaced `DownloadingTerrainScreen` | **Class does not exist.** Replaced by staged `LevelLoadListener` (`start`/`update`/`finish`/`updateFocus`) with `Stage` = `START_SERVER`, `PREPARE_GLOBAL_SPAWN`, `LOAD_INITIAL_CHUNKS`, `LOAD_PLAYER_CHUNKS`, driven through `LevelLoadProgressTracker` and `LevelLoadingScreen`. |
| Pre-rendering phase | bolted `BuildingTerrainScreen` on to wait for player chunks | **Vanilla does this.** `net.minecraft.server.network.config.PrepareSpawnTask$Preparing` waits on `LOAD_PLAYER_CHUNKS` during the configuration phase, before the player enters. |

Roughly a third of the 68 files (`api/abstraction/**`) is multi-version plumbing that exists only
to span 1.18.2–1.20.1 from one codebase, and the `Fastload-Fapi-Forwarding-*` modules are Fabric
API shims. None of that has any meaning for a single-version NeoForge build.

Worth noting for anyone revisiting: Fastload credits the 441 removal to **Ksyxis (MIT)**, which
would have been a cleaner licence to work from than Fastload's own ARR. It is moot — the feature
is vanilla now — but if a similar idea comes up again, start from Ksyxis.

**Verify before reopening this** (all against a real 26.1.2 jar, not memory):

```bash
javap -p -c -constants -cp <minecraft.jar> net.minecraft.server.MinecraftServer \
  | sed -n '/private void prepareLevels();/,/^$/p'      # no 441, ChunkLoadCounter instead
javap -p -cp <minecraft.jar> net.minecraft.client.gui.screens.DownloadingTerrainScreen
                                                        # expect: class not found
```

## Categories

The split follows what a jar is *for*, which is also what decides where a feature can safely go.

### Duty: Memory — heap footprint and allocation rate

Jasione (`Enum.values()`), FerriteCore (block-state tables), **Fast-Tag** (TagKey/ResourceKey
interning). Fast-Tag belongs here rather than in a "tags" category because interning is
deduplication — the same idea as FerriteCore's shared tables, applied to a different object.

### Duty: Client — anything that only exists when there is a screen

EntityCulling, Particle Core, OptimisedBlockEntities, plus **Resource-Trimmer** (drops resources
that were loaded but never used) and eventually **Stfu** (suppresses log and chat noise).

### Duty: FixerUpper — startup time and general throughput

ModernFix. Stfu could sit here instead if its suppression turns out to be mostly startup log
noise rather than in-game chat; that call is worth making after reading its mixins properly.

### Duty: Server — proposed, not yet created

For work that runs on the logical server and has no client half: **KryptonReno** (network
compression, encryption pipeline, entity tracking) and **BiomeSpy** (biome-source lookups during
worldgen). Both are meaningful on a dedicated server, where the client modules are dead weight.

This module does not exist yet. It is the right home for those two, and creating it for a single
merged feature would be premature — it should land together with them.

## Order of work

Stability first, so easiest and least invasive first:

1. **Fast-Tag** — done. 4 files, already NeoForge, no conflicts.
2. **Resource-Trimmer** — 5 files, 73 lines, already has a NeoForge source set.
3. **Stfu** — 36 files, MIT, Fabric; needs a real port but touches only logging and chat.
4. **Duty: Server** + **BiomeSpy** — new module, worldgen-adjacent, needs care around C2ME.
5. **KryptonReno** — 50 files, network pipeline. Largest and riskiest: it rewrites compression
   and encryption, and a mistake there breaks multiplayer rather than degrading it.

## KryptonReno: what was taken

Merged 2026-08-15 from the dedicated **`26.1.2` branch** (last commit 2026-07-06). The easiest
upstream so far: exact Minecraft version, Java 25, LGPL-3.0 matching `duty-server`, and a real
`neoforge/` source set — no Yarn translation and no version guessing.

It passes the vanilla check that closed Fastload. 26.1.2's `CompressionEncoder` still deflates
through a heap `byte[]` with `java.util.zip.Deflater`, and `CipherBase` still copies every packet
through `heapIn`/`heapOut` with `javax.crypto.Cipher`. The waste is real and still there.

**Taken** (13 mixins, package `net.dutymod.server.mixin.net`):

| Feature | Replaces |
|---|---|
| Native compression | `java.util.zip.Deflater` → libdeflate, on the direct buffer Netty already holds |
| Native encryption | `javax.crypto.Cipher` → OpenSSL AES, in place, no heap copies |
| Velocity frame decoder | `Varint21FrameDecoder.decode`, plus a fix for "nullping" |
| Varint prepender | `Connection.createFrameEncoder` |
| VarInt/VarLong/Utf8String writers | branch-reduced encoders, same output |
| Packet-processor drain | `while(!isEmpty()) poll()` → `while((e = poll()) != null)` |

**Not taken, and why** — this is the important half:

- **`Util.makeIoExecutor` virtual-thread overwrite.** Duty's own Stfu mixin `@WrapOperation`s
  `lambda$makeIoExecutor$0`, the lambda inside that method. An `@Overwrite` of the enclosing
  method leaves that wrap applying to an orphaned lambda: it reports success and silently never
  runs. Upstream marks this one `@reason test`.
- **Server-side entity culling** (`network/chunk/*`). Overlaps Lithium on `ServerEntity`,
  `ServerLevel` and `LevelChunkSection`, and `duty-client` already ships EntityCulling. Upstream's
  own README calls it "(possibly) asynchronous".
- **Particle packet mixins.** Upstream's config plugin disables all three on client installs, so
  they would never apply in this pack regardless.
- **RCON** (`@Overwrite`, upstream-flagged experimental) and **Happy Eyeballs** (RFC 8305 dual-stack
  connect — multiplayer-only, and it drags in a Netty resolver).
- **Sewlia-config, LibSL, tiny-utils.** Replaced with `NetOptions` on Duty's own config, the same
  substitution made for Stfu's YACL.

**One upstream behaviour was deliberately not preserved.** Its frame decoder makes the
`BandwidthDebugMonitor` call optional and defaults it *off*, which leaves vanilla's F3 bandwidth
readout silently empty. Duty calls it unconditionally, as vanilla does; the monitor is null unless
bandwidth debugging is on, so it costs one null check.

`velocity-native` is bundled via JarJar and is the only third-party binary Duty ships without
compiling it. It falls back to `JavaVelocityCompressor`/`JavaVelocityCipher` if the native will not
load, so a failure degrades rather than crashes. See `NOTICE.md` for its unresolved licence
position.

## Stfu: what was taken

Ported from the `multiversion` branch, not the version branches. That branch uses **Mojang
mappings** and its Stonecutter tree targets 26.1.2 directly, which made this far cheaper than the
1.21.9 branch suggested — my first assessment said "Yarn, needs full translation" and was wrong.

The checked-in source resolves for 26.2, so two Stonecutter sites had to be flipped to their 26.1
form: `Minecraft.overlay` as a field rather than `Gui.overlay()`, and `Gui` rather than `Hud`.

**38 of 43 mixins were taken.** Five were not:

- `CombineBars` — dense with 26.1-vs-26.2 conditionals for a cosmetic HUD change.
- `LoadingOverlayMixin` — targets `render`; 26.1 has `extractRenderState`.
- `ParticleCulling` — targets `ParticleEngine.render`/`renderParticleType`, both restructured
  around `ParticleGroup` in 26.1. Duty already has Particle Core's culling regardless.
- `SpriteMixin` — `uvShrinkRatio` does not exist in 26.1.
- `DisableToasts` — targets `method_34011`, an intermediary name that cannot resolve on NeoForge.

All five were caught by `check-descriptors.py` before deploying, not by a crash. The
`advancementToasts` and `recipeToasts` options remain in the config but have no effect while
`DisableToasts` is absent.

Dropped dependencies: YACL and ModMenu (config now in `duty.properties`), Fabric API (keybinds
via `RegisterKeyMappingsEvent`, the F3 entry via `RegisterDebugEntriesEvent`, ticking from
`DutyClient`), and fletching-table. The access widener was translated to an access transformer.

## ImmediatelyFast: what was taken

All 20 mixins from the common config, plus its feature and util packages -- enhanced batching,
font atlas resizing, fast text lookup, map atlas generation, sign text buffering, and the
framebuffer-switching and Apple-GPU fixes.

The standalone jar was removed from the pack first; running both would have put two copies of the
same batching logic in the game.

Three things needed doing beyond the package rename:

- Its `.classtweaker` (the newer access-widener format) became access transformer entries. The
  `DirectStateAccess` methods had to be widened on the interface *and* both implementations --
  see the note in the AT file.
- `net.lenni0451:Reflect` is a real runtime dependency here, used for the Iris compatibility probe
  and to reach `MapTextureManager$MapInstance`. Bundled via JarJar, as upstream bundles it.
- The `@Mod` entrypoint's resource-reload listener moved into `DutyClient`; its config file is now
  `duty-immediatelyfast.json` and its version lookup asks for `duty_client`.

## Duty: Memory — coverage audit

Audited 2026-08-15. Duty carried three of upstream FerriteCore's six options; the other three were
never ported. Two are now in, one is deliberately out.

| FerriteCore option | Duty | Note |
|---|---|---|
| `replaceNeighborLookup` | had it | `memory.block_state_deduplication` |
| `replacePropertyMap` | had it | `memory.property_map_compaction` |
| `compactFastMap` | had it | `memory.compact_state_encoding`, opt-in upstream and here |
| `blockstateCacheDeduplication` | **added** | `memory.block_state_cache_deduplication`, default on |
| `dataComponentPatch` | **added** | `memory.data_component_deduplication`, default on |
| `useSmallThreadingDetector` | **rejected** | Lithium already does it, and harder — see below |

**Block state cache dedup** shares the collision shape and face-sturdy table between block states
whose caches are equal, which across a large modpack is nearly all of them. It also rewrites the
internals of discarded shapes to point at the kept one, because mods routinely hold shape
references outside the block state cache. Verified against 26.1.2: `BlockStateBase.cache`,
`Cache.collisionShape`, `Cache.faceSturdy`, `ArrayVoxelShape.xs/ys/zs`, `VoxelShape.shape/faces`
and all six accessor targets still exist. Upstream resolves the `cache` field name through a
per-loader hook; Duty targets one loader with Mojang mappings, so it is the literal `"cache"`.

**Threading detector: rejected.** It `@Overwrite`s `PalettedContainer.acquire()`/`release()` and
nulls `threadingDetector`. Lithium's `chunk.no_locking.PalettedContainerMixin` does exactly that
already — same field nulled, same two methods — and removes the locking entirely rather than just
shrinking the detector. Two `@Overwrite`s of one method is the Lithium crash profile, for a
benefit Lithium has already delivered.

**No conflict with ModernFix.** Its `cache_blockstate_cache_arrays` only avoids `values()` array
clones inside the `Cache` constructor; FerriteCore dedupes the contents afterwards. The two are
designed to coexist — ModernFix warns when FerriteCore is absent.

## Duty: FixerUpper — coverage audit

Audited 2026-08-15 against upstream ModernFix `26.1` (last commit 2026-07-18). Coverage was
already near-complete: of ~55 feature packages only five were missing, and three of those target
mods that are not in the pack (`cofh_core_crash`, `ctm_resourceutil_cme`,
`spark_profile_world_join` — CTM and spark appear in `gradle.properties` as compile-only deps for
compat code, not as installed mods).

**`capability_list_compaction` — added.** This one was **half-ported**: `CapProviderGetter` and
`ITrackingCapEvent` were already in the tree under `neoforge/caps`, but the two mixins that drive
them were never brought across, so both classes were dead code and the feature did nothing. Adding
`CapabilityHooksMixin` and `RegisterCapabilitiesEventMixin` completes it. It dedupes capability
provider lists after registration, which matters in proportion to how many mods register
capabilities. Verified `CapabilityHooks.init()` and all four `RegisterCapabilitiesEvent.register*`
overloads against NeoForge 26.1.2.95.

**`optimize_surface_rules` — rejected.** Duty already `@Overwrite`s `SurfaceRules$Context` through
`perf/worldgen_allocation`, so this would put a second Duty mixin on a class Duty already
overwrites. On top of that `lithostitched` patches `SurfaceSystem`, `SurfaceRules$Context` and
`NoiseBasedChunkGenerator`, and `NoiseBasedChunkGenerator` is patched by four installed mods
(lithium, lithostitched, tectonic, greatchasms). Upstream ships its `SurfaceSystemMixin` at
`priority = 2000`, which is itself a sign of contention. Worldgen-only gain, high blast radius.

## Six mods reviewed 2026-08-16

Assessed for anything worth taking. One yielded code; the rest did not.

| Mod | Licence | Target | Verdict |
|---|---|---|---|
| [Lomka](https://github.com/Starlevka/Lomka) | MIT | **26.1 NeoForge** | **two mixins taken**, rest deferred or rejected |
| [Sodium-Relief](https://github.com/Etoryx/Sodium-Relief) | MIT | 1.21–26.2 Fabric | plausible, unverified — see below |
| [PulseNet](https://github.com/Pulse-MC/PulseNet) | **unstated** | 1.26.1 Fabric | real idea, wrong shape for this pack |
| [AudioThrottle](https://github.com/Noslw/AudioThrottle) | unclear | Fabric | idea has merit, claims do not |
| [GPUBooster](https://github.com/ITsMrToad/GPUBooster) | GPL-3.0 | 1.21.1 Fabric | rejected — fights Sodium |
| [Opticores](https://github.com/anibalmolina-debug/Opticores) | CC0 | NeoForge | rejected — 3 commits, template README |

**Lomka is the only one that was a real fit**: MIT, actively maintained, and it ships a `26.1-neoforge`
variant, so no version or loader translation. 47 mixin targets, of which 9 overlap Duty's own
(`BlockStateBase$Cache`, `CompressionDecoder`, `FriendlyByteBuf`, `BitSetDiscreteVoxelShape`,
`GameRenderer`, `IdMapper`, `Minecraft`, `PoseStack`, `SoundEngine`) and were left alone.

Taken: `VertexFormat.hashCode` and `InputConstants$Key.hashCode` caching. Both hash only final
fields of effectively immutable objects, both are consulted constantly as map keys, and both are
about ten lines. Verified Iris's `MixinVertexFormat` — the only other installed mod on that class --
touches neither `hashCode` nor the shadowed fields.

Deferred rather than rejected: Lomka's sound, texture-atlas and mipmap work is aimed at exactly the
memory and startup targets that matter here, but each needs the same per-target conflict check
against Sodium and Iris before it can be trusted.

Rejected outright from Lomka: `Lightmap.render` and the other whole-method `@Overwrite`s of the
render pipeline. Iris rewrites lightmap handling for shaders, and an `@Overwrite` there is the
Lithium-clash pattern.

**GPUBooster** replaces Minecraft's GL layer with DSA and bindless textures. Sodium already owns
that layer and is installed, its README states RBO is incompatible with shader packs while Iris is
installed, and it targets 1.21.1 Fabric. It fails the project's first rule.

**Opticores** is three commits with an unedited template README, and its one stated feature --
async culling -- is what `duty-client` already does via EntityCulling.

**AudioThrottle**'s underlying idea is sound: Minecraft's mixer has a hard channel count and sound
floods cause stutter. Its headline claim is not: an audio cap does not raise average frame rate 11%,
because sound mixing does not run on the render thread. If sound flooding becomes a real complaint,
Lomka's `SoundEngine`/`Channel`/`Listener` allocation work is the better-evidenced starting point.

**PulseNet** batches packet flushes per tick instead of per packet, which is genuinely distinct from
KryptonReno's compression and encryption work already in `duty-server`. It is also Fabric-only, has
no stated licence, and targets the syscall cost of flushing to remote clients -- close to nothing on
an integrated server talking to a client in the same process.

**Sodium-Relief** caches tooltip layouts and text widths, reporting 2429 rebuilds down to 2 while
hovering one item. Duty already ships ImmediatelyFast, which batches text and tooltip *draws*;
whether layout is still rebuilt per frame on top of that was not verified, and that check is the
prerequisite for taking any of it.

## Java 25 runtime flags — measured

Duty targets Java 25 but runs under whatever the launcher passes. Two production flags in 25.0.4
are unset in the test pack and go directly at memory and startup.

**`-XX:+UseCompactObjectHeaders`** shrinks the object header from 12 bytes to 8. Measured on the
pack's own Temurin 25.0.4 JRE, three million two-int objects:

```
default headers : 25.2 bytes/object
compact headers : 16.8 bytes/object      -33%
```

A real heap is a mix of shapes so the whole-heap figure is smaller, but Minecraft's heap is
overwhelmingly small objects -- block states, chunk sections, entity data, the tag and component
maps `duty-memory` already dedupes -- which is the shape this helps most. Verified to run with ZGC
and `UseStringDeduplication` together on that exact JRE, no warning, clean exit. It was experimental
in 24 and is `product` in 25.

**`-XX:AOTCache`** (JEP 483, finalised in 25) records class loading and linking in a training run
and replays it, which is aimed squarely at startup. Not measured here: it needs a training launch of
the actual pack, and a cache produced from a different mod set is worse than none.

**Worth removing rather than adding:** the instance currently launches with
`-XX:StartFlightRecording=name=voxy,settings=profile,...,dumponexit=true`. That is the high-overhead
JFR profile, on every launch, writing to disk. If the Voxy investigation it was for is finished,
dropping it reclaims a few percent for free -- the cheapest performance change available here.

These are launcher settings rather than mod code, so Duty cannot set them; they are recorded here
because they outweigh several of the code-level wins above.

### Lomka: audio taken, mipmap and texture atlas declined

**`Channel.setSelfPosition`** built a `float[3]` per sound position update and **`Listener.setTransform`**
a `float[6]` per frame, the latter running whether or not anything is playing because the listener
tracks the camera. Both confirmed against the 26.1.2 bytecode -- one `newarray` each -- rather than
taken on the README's word. `alSource3f` takes loose floats so the first array disappears entirely;
orientation genuinely needs an array for `alListenerfv`, so that one is allocated once and refilled.
Nothing else installed touches either class.

**`MipmapGenerator.alphaTestCoverage` -- declined.** Lomka rewrites it with precomputed bilinear
weight tables. Vanilla's inner loop does contain the 22 float operations that would make it a valid
constant hoist, and nothing else patches the class, so it is plausible. It is also a numeric rewrite
whose failure mode is subtly wrong alpha-cutout mipmaps -- leaves, grass, glass edges -- and the win
is bounded to texture stitching at load. Accepting it needs the two formulas compared term by term
against vanilla's source and a visual check, not a bytecode op count.

**`TextureAtlas` -- declined.** Iris ships `MixinTextureAtlas` plus two accessors and sodium-extra
ships its own `MixinTextureAtlas`. Two mods already rewriting atlas stitching is not a place to add
a third with an `@Overwrite`.

## BadOptimizations — analysed, port mapped, not yet merged

MIT, `26.1` branch, **Mojang mappings despite being multiloader**, so no name translation. 34 files,
11 features, each already gated by its own option upstream.

**None of its declared incompatibilities are in the pack** — twilightforest, bedrockskinutility,
lazyyyyy, polytone, biomeswevegone, performant and camera_lock_on are all absent, so no feature is
ruled out on that axis.

**Already covered by Duty — do not port.** `enable_particle_manager_optimization` cancels
`renderParticles*` at HEAD when the particle map is empty. Stfu's `DisableParticles` in
`duty-client` already injects `render` and `extract` at HEAD and cancels on `particles.isEmpty()`,
plus a config-driven disable. Porting it would double-cancel the same method.

**The headline feature, and the reason to do this port:** entity and block-entity renderer caching.
Vanilla's `EntityRenderDispatcher.getRenderer(entity)` is a map lookup per entity per frame.
BadOptimizations caches the renderer on the `EntityType` and gives each `Entity` a reference to its
type, turning the lookup into a field read. The block-entity side is the same shape.

Port groups, each of which must be gated as a unit -- the dispatcher casts to the duck interfaces,
so a partially applied group is a `ClassCastException`:

| Group | Files |
|---|---|
| Entity renderer cache | `EntityMethods`, `EntityTypeMethods`, `MixinEntity`, `MixinEntityType`, `MixinEntityRendererDispatcher` |
| Block entity renderer cache | `BlockEntityTypeMethods`, `MixinBlockEntityType`, `MixinBlockEntityRenderDispatcher` |

Two conflict checks to finish before merging: `MixinEntityRendererDispatcher` **`@Overwrite`s**
`getRenderer`, and Duty already patches both `Entity` (particles, culling) and
`BlockEntityRenderDispatcher` (EntityCulling). Class overlap is fine; the check is whether any Duty
injection targets those same methods.

`CacheHooks` is only needed by the lightmap and sky-colour features, not by either renderer cache.

### BadOptimizations: renderer caching taken

Both outstanding checks came back clear. Duty's `CullableMixin` adds only `@Unique` fields to
`Entity` with no method injections, and its `BlockEntityRenderDispatcherMixin` targets
`tryExtractRenderState`, where BadOptimizations targets `onResourceManagerReload` and the renderer
getter. Different members, different methods.

`EntityRenderDispatcher.getRenderer(entity)` is a map lookup for every entity, every frame. The
renderer is now cached on the `EntityType`, with each `Entity` holding a reference to its type, so
the lookup becomes a field read. Players resolve by skin model instead, which is why
`MixinClientPlayer` and `MixinMannequinPlayer` override the same duck method. Block entities get
the identical treatment through `BlockEntityType`.

Gated as one unit on `client.renderer_caching`. The dispatcher casts to the duck interfaces the
type and entity mixins provide, so a partly applied group is a `ClassCastException` -- the failure
this project already hit once during the EntityCulling port.

**The risk worth recording:** `getRenderer` is *overloaded* on both dispatchers in 26.1.2 --
`(Entity)` and `(EntityRenderState)`, `(BlockEntity)` and `(BlockEntityRenderState)` -- and both
mixins `@Overwrite` it. No checker covers `@Overwrite` binding, and picking the wrong overload is
what took Liteminer down. Verified by comparing the compiled mixin descriptors against vanilla:

```
Duty    (Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;
vanilla (Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;
```

Exact match, and the sibling overload erases differently, so the binding is unambiguous. The block
entity pair checks out the same way.

Not taken, beyond the particle overlap already recorded: the remaining eight features (sky colour,
lightmap, entity flags, FOV, toasts, tutorial, debug renderer, sky angle). Each is independent and
individually small; they need their own conflict passes rather than a blanket port.

### BadOptimizations: the remaining features

Seven more taken, all under `client.renderer_caching`: the FOV wrapper on `Camera.tickFov`, the
lightmap extractor's tick short-circuit, the tutorial skip, the toast `hideGui` check, the debug
renderer early-out, and the two field accessors the lightmap needs
(`GameRenderer.bossOverlayWorldDarkening`, `LocalPlayer.waterVisionTime`).

**`enable_sky_angle_caching_in_worldrenderer` — rejected, and not for the reason it first looked.**
Its target is written as `method_62215`, a Fabric intermediary name for a lambda inside `renderSky`,
which would never resolve under Mojang mappings. That turned out not to matter: `LevelRenderer` has
no `renderSky` in 26.1.2, and `getSunAngle` does not exist anywhere in the jar. Mojang restructured
sky rendering into `SkyRenderState`. Same shape as Fastload -- the code it optimises is gone.

**`MixinDebugHud_AddText` — skipped.** It injects `DebugScreenEntries.<clinit>`, which Duty already
injects. Two injections into one static initialiser is not automatically a conflict, but it is not
worth the ordering question for an F3 text tweak.

**`CacheHooks` — dropped rather than ported.** It is an API letting *other* mods declare extra
reasons the lightmap must recompute. Nothing outside BadOptimizations implements it, and carrying
it meant carrying its config and platform layers. The call site in the lightmap mixin now returns
false with a comment marking where it would go back.

Two things the tooling caught that a green build would not have:

- `ToastManager.render` does not exist in 26.1.2 -- it is `extractRenderState`, part of the same
  render/extract split seen across the 26.x GUI. `check-descriptors.py` flagged the stale name, and
  the `hideGui` read the injection point needs was confirmed to live in the new method.
- `ToastManager$ToastInstance` is a private inner class, so shadowing a field of that type needs an
  access transformer entry. That is a compile error rather than a runtime one, but it is the same
  category as the `public-f` lesson already recorded.

## CPU pass, 2026-08-16

Taken from Lomka (MIT), all uncontested -- nothing installed patches any of these classes:

| Mixin | Module | What it removes |
|---|---|---|
| `DataLayerMixin` | server | the four-call delegation chain on every light and biome nibble lookup |
| `Cursor3DMixin` | server | the same on `advance()`, the 3D block iterator |
| `ByIdMapMixin` | server | boxing and a lambda per registry id lookup, via a fastutil map with a default |
| `ArgbMixin` | client | recomputed divisions in 15 colour ops, replaced with lookup tables |
| `QuadrantMixin` | client | a modulo per vertex rotation |
| `ItemTransformMixin` | client | a quaternion rebuilt per item transform, now cached |
| `LightCoordsUtilMixin` | client | branch and merge work on four light-packing helpers |

`DataLayer` and `Cursor3D` went to `duty-server` rather than `duty-client` because both run on
either side; `duty-server` is declared `side = "BOTH"`, so its mixins apply in single player too.

**Contested and therefore skipped**, each verified against the installed jars rather than assumed:
`Mth` (Lithium), `BufferBuilder`, `ItemInHandRenderer`, `LightTexture`, `ModelPart`, `TextureAtlas`
(Iris and Sodium), `Model` (six mods), `BlockableEventLoop` (voxy), `SoundBufferLibrary`
(AmbientSounds).

**Skipped on judgement:** `ArrayListDeque`. Its mixin shadows `modCount`, which 26.1.2's
`ArrayListDeque` does not declare -- it inherits it from `java.util.AbstractList`. Shadowing a JDK
field is not worth it for capacity rounding.

Two version drifts caught by compiling rather than by reading:

- `ItemTransform` moved to `net.minecraft.client.resources.model.cuboid`. The correct import was
  present in the file but commented out, and the active one named the old package.
- `LightCoordsUtil.getLightCoords` does not exist in 26.1.2; it lives on `LevelRenderer` now. Four
  of that mixin's five overwrites still resolve, so those were kept and the moved one dropped --
  retargeting it would mean adding an `@Overwrite` to a class Duty's culling and Iris both patch.

## Storage pass, 2026-08-16

Measured before cutting. Installed footprint went from 2125 KB to 1559 KB, a 27% reduction, and
`duty-all` from 2046 KB to 1442 KB.

| | Before | After | How |
|---|---|---|---|
| `duty-server` | 719 KB | 343 KB | dropped natives for platforms this build cannot run |
| `duty-fixerupper` | 566 KB | 451 KB | ships one locale instead of nine |
| `duty-memory` | 163 KB | 125 KB | icon |
| `duty-client` | 677 KB | 640 KB | icon |

**Natives, 857 KB.** `velocity-native` carries 1141 KB of `.so`/`.dll`/`.dylib` across five
platforms; only the 284 KB under `windows_x86_64` can ever execute here. The rest is Linux glibc
and musl, macOS arm64, and Windows arm64. Safe to drop because the library degrades rather than
fails -- `NativeCodeLoader` catches the load error and falls back to `JavaVelocityCompressor` and
`JavaVelocityCipher`, both still in the jar. A build stripped for the wrong platform is slower, not
broken. Override with `-Pduty.nativePlatforms=windows_x86_64,linux_x86_64`.

This also shrinks the JarInJar extraction cache: `velocity-native` was 516 KB of
`.cache/jij` and is now around 180 KB.

**Icon, 45 KB per module.** It was 1010x1010 RGBA for something the mod list draws at roughly 64
pixels. Rescaled to 256x256: 52478 bytes to 7234.

**Languages, 115 KB.** FixerUpper's nine locales were 268 KB, the single largest content in that
jar -- more than all of its classes together. Only `en_us` ships now. This one is a visible
change rather than a free one: switching language shows raw keys. The translations stay in git and
`-Pduty.languages=all` brings them back. Note the property is read at configuration time, so it
needs a `clean` to take effect.

**Looked at and left alone.** `duty-core` is extracted four times into the JarInJar cache, once per
module, costing about 136 KB. That is the price of every module standing alone, which is a
deliberate design decision recorded in HANDOFF, so it stays. The 38 MB `.cache/jij` is
overwhelmingly other mods; Duty's share was 652 KB before this pass.

## Structure Essentials — one feature taken, the headline one rejected

Reviewed on 2026-08-16 (`someaddons/structureessentials`, branch `neo26.1`, 19 mixins). Most of it
is worldgen *tuning* — minimum distance between structures, spacing overrides, `/locate` radius —
or diagnostic logging and timing. Neither is a performance change, and the tuning half alters what
generates, which is not something a performance mod should be doing quietly.

### Rejected: `StructureSearchSpeedupMixin`

This is the mod's headline feature, and it is the one thing here Duty cannot use.

It injects into `ChunkGenerator.getStructureGeneratingAt` and, before vanilla can load the chunk to
`STRUCTURE_STARTS`, samples the biome at four diagonal positions across a handful of Y levels. If
none of the target structures list any sampled biome, it returns "nothing here" and skips the chunk
load. That is a sound idea — the chunk load is the entire cost — but:

* **Duty already does this, better, on the path that matters.** BiomeSpy's `ChunkGeneratorMixin`
  cancels the scattered-grid overload at HEAD and runs its own loop against proper biome envelopes
  derived from the `MultiNoiseBiomeSource` parameter list. Vanilla's `getStructureGeneratingAt` is
  never reached there at all, so the injection would apply and then never execute.
* **What is left is the stronghold path, where the filter cannot pay.** Strongholds are valid in
  very nearly every overworld land biome, so a biome probe rejects almost nothing.
* **It is a heuristic with false negatives.** Four sample columns cannot see a structure whose
  biome only occupies a corner of the chunk. On the grid path Duty's envelope test is exact.
* It hard-casts the `LevelReader` parameter to `ServerLevel`, and depends on a duck interface fed
  by the min-distance tuning feature, which Duty does not take.

### Taken: the search watchdog, rewritten

`server.structure_search_timeout`, default 60 seconds, in `net.dutymod.server.structure`.

Nothing in the pack bounds a structure search. `findNearestMapStructure` walks outwards in rings
and can force a chunk load per candidate; when the structure is genuinely not in range the walk
runs to the full radius and the world is frozen until it finishes. Treasure map generation takes
the same path, so this is not only a `/locate` problem. Timing out returns "not found", which is
what an exhausted search returns anyway — the change is that it takes a bounded time to say so.

Two corrections to upstream's version:

* **Its deadline is a `static` field.** Structure searches are not confined to the server thread —
  loot generation runs on workers, and dimensions search independently — so two overlapping
  searches clobber each other's start time and whichever armed last decides when the other gives
  up. Duty's is a `ThreadLocal`, read once per ring rather than per block.
* **Its enforcement is half-dead here.** Of its two check sites, the scattered-grid one sits inside
  the method BiomeSpy cancels at HEAD, so it would never fire. Duty arms at
  `findNearestMapStructure`, enforces the stronghold path by mixin, and checks the budget directly
  inside BiomeSpy's own loop — the loop that actually runs for ordinary structures.

Also rejected: `MappedRegistryMixin` (forces `freeze()` to succeed and logs unbound entries — a
crash workaround that hides a real error, not a performance change) and `LegacyRandomSourceMixin`,
which makes `compareAndSet` always report success to drop the CAS retry loop. That removes the
atomicity of the random source; with C2ME generating chunks in parallel, that is not a trade worth
taking for a contended-CAS saving.

## Connectivity — rejected, nothing in it is a performance change

Reviewed on 2026-08-16 (`someaddons/connectivity`, branch `neo26.1`, 57 source files, 26 mixins).
Duty takes nothing from it.

The name suggests a network optimization mod. It is not one. Categorising every injection in the
mod by what it does:

* **Six `@ModifyConstant` limit raises** — custom payload size, chunk packet size, login timeout
  ticks, keepalive. These make a heavy pack survive registry sync; they do not make it faster.
* **Diagnostics** — per-packet-type traffic accounting with commands to dump it, packet hexdumps on
  decode errors, Gson type handlers that produce better messages for malformed datapack JSON.
* **Reliability guards** — a longer `ReadTimeoutHandler`, a raised `NbtAccounter` quota, and a
  redirect that skips a zero-byte chunk section read rather than throwing on a desync.

Not one hot-path change, no caching, no allocation work. Several of the injections make things
marginally *slower*: `CompressionDecoderMixin` wraps every `decode` call in a HEAD and a RETURN
inject to toggle a validation flag, and the traffic accounting adds work per packet encoded.

### It also overlaps what Duty already has

Duty's KryptonReno work already covers the frame layer — `Varint21FrameDecoderMixin` and
`PrependerConnectionMixin` handle the 5-byte length prefix, and `server.permit_oversized_packets`
relaxes the same decompressed-size bounds check Connectivity's `disablePacketLimits` relaxes. What
would be genuinely new is the *content* limits: the NBT quota and the per-payload caps. Those are
complementary rather than duplicative — but they are still reliability, not performance.

### And this pack has never needed it

Checked every log in the instance, current and archived. No `Payload may not be larger than`, no
`Badly compressed packet`, no NBT quota failure, no network read timeout. The three `timed out`
lines are Xaero's HTTP version check against its own update server, and the single disconnect is a
normal quit. The failure class Connectivity exists to prevent is a dedicated-server-with-large-
registry-sync problem, and this instance does not hit it.

Worth revisiting only if a dedicated server is ever set up and starts dropping clients during login
— at which point the two pieces to take are the login timeout constant and the NBT quota, not the
mod.

## BetterChunkLoading — rejected on three independent grounds

Reviewed on 2026-08-16 (`someaddons/betterchunkloading`). There is no 26.1 branch; the newest is
`neo1.21`, so this would be a port rather than a merge. Duty takes nothing from it.

### 1. The APIs it is built on are gone

Checked every target against 26.1.2 before reading further:

| What it needs | State in 26.1.2 |
| --- | --- |
| `ChunkTaskPriorityQueueSorter` | class removed |
| `ChunkHolder.futures`, `scheduleChunkGenerationTask` | removed |
| `TicketType.create(String, Comparator, long)`, generic `TicketType<T>` | removed -- `TicketType` is now a `record(long timeout, int flags)` |

Those are not incidental. `ServerChunkCacheMixin`'s headline trick nulls a `ChunkHolder.futures`
slot, schedules a duplicate generation task, restores the future and forces a resort -- all on
state that no longer exists. The prediction feature allocates a `TicketType` per player, which the
new record cannot express. This is a rewrite of the mod's foundations, not a port.

### 2. C2ME already owns the whole domain

C2ME 0.4.0-alpha.0.98 is installed and ships 27 nested modules. Its mixin packages include
`task_scheduling`, `scheduler`, `mid_tick_chunk_tasks`, `MixinChunkHolder`, `MixinServerChunkManager`,
`MixinThreadedAnvilChunkStorage`, `MixinStorageIoWorker`, `async_serialization`,
`ordering/player_move`, `fluid_postprocessing` and `ext_render_distance`. Every BetterChunkLoading
mixin lands inside one of those. The `ordering/player_move` module in particular already reorders
chunk loading by player movement, which is BetterChunkLoading's prediction feature.

Reaching into `ChunkHolder`'s future slots underneath a mod that has rewritten the scheduler is a
way to deadlock or to corrupt chunk state, not a way to load chunks faster.

### 3. ServerCore covers what is left

ServerCore 1.5.19 is installed with `features.dynamic.ChunkMapMixin` (dynamic view distance, which
is what `ChunkMapViewDistanceFixed` and its sibling do), a whole `optimizations.sync_loads.*` family
preventing synchronous chunk loads, `optimizations.tickets.*`, and `optimizations.misc.PathFinderMixin`
-- covering `PathRecomputeChunkLoadPrevention`, the two fluid mixins and `PlayerMovementNoUnloaded`.

Nothing here is unique, portable, and safe at the same time.

## RecipeEssentials — one mixin taken, the rest superseded

Reviewed on 2026-08-16 (`someaddons/recipeessentials`, newest branch `neo1.21`, 31 files).

### Rejected: the recipe cache, because Duty already has a better one

Both mods ship a class called `CachedRecipeList`, and they are not the same idea:

* **RecipeEssentials memoizes.** It remembers the input stacks of the last lookup and replays the
  result when the input looks the same. Its own class carries a `report(...)` method that logs
  "caching errors" behind a config flag -- the author knew it can return the wrong recipe and
  shipped a diagnostic for it rather than a fix.
* **Duty indexes.** The FastSuite work already merged into `duty-fixerupper` builds a static index
  at recipe load, filing each recipe under the items its most-selective ingredient accepts, with
  unindexable recipes kept in a small always-checked list. There is no invalidation problem because
  nothing is remembered between lookups, and it narrows the candidate set for *any* input, not just
  a repeated one.

A memoization layer on top of an index that already narrows to a handful of candidates buys
approximately nothing and reintroduces a correctness hazard. The recipe book sorting and hiding
mixins are UI features, not performance.

### Taken: caching the component prototype's hash

Vanilla's `PatchedDataComponentMap.hashCode()` is `prototype.hashCode() + patch.hashCode() * 31`.
Confirmed in bytecode. The prototype is the item's default component set: `final`, written exactly
once in the constructor, and shared by every stack of that item -- so hashing it again on every
call is pure repetition, and component maps are hashed constantly by anything that puts an
`ItemStack` in a hash-based collection.

Duty caches the prototype's hash in the constructor and redirects only that half of the expression.
The value produced is bit-identical to vanilla's; only the time to reach it changes. The patch's
hash stays live because the patch is mutable.

Two deliberate departures from upstream:

* **No `@Overwrite` of `hashCode`.** A `@Redirect` on the one `DataComponentMap.hashCode()` call
  leaves vanilla's arithmetic in place, which matters on a class three mods now touch.
* **The `equals` half is not taken.** Upstream also short-circuits `equals` on the cached hash,
  which needs a duck-interface cast on the `other` argument. Vanilla does guard that call with an
  `instanceof PatchedDataComponentMap`, so the cast is safe -- but the check only pays when two
  *different* prototypes are compared, and `ItemStack` filters on the item before it ever compares
  components. It would add an int compare to the common case to speed up a case that rarely occurs.

It went into `duty-memory`, which already owns the only other mixin on this class. One owner per
target class avoids two Duty modules injecting into the same method.

## AsyncLogger — rejected, not a feature Duty can hold

Reviewed on 2026-08-16 (`decce6/AsyncLogger`). It is not a mixin mod. It works by being a
`TransformationService`, `ModLocator`, `ImmediateWindowProvider` and `LanguageAdapter` that run
*before* mod loading, then: expanding module reads between the log4j-api and log4j-core modules,
loading a bundled LMAX Disruptor into the game's classloader, removing its own classes from the
service layer, and reconfiguring Log4j2 to async loggers.

Duty is a normal `@Mod`. By the time any Duty constructor runs, Log4j is long since configured and
most of a startup's log lines are already written. Taking this would mean adopting the mod's entire
launch-time architecture -- classloader surgery and module-system reflection that breaks on
NeoForge updates -- rather than a feature. Note also that the NeoForge half of its bootstrapper is
Stonecutter-commented in the checked-out branch, so what is on screen is inert.

And the evidence does not support the need. A full session of this pack produces a 2.4 MB
`debug.log` and a 511 KB `latest.log`, with archived sessions at 1,200-2,400 lines. That is not a
logging-bound workload. LMAX Disruptor is not present anywhere in the instance either, so the
mod-free route -- `-Dlog4j2.contextSelector=...AsyncLoggerContextSelector` -- would not work as-is
either.

If logging I/O ever does become a concern, the larger lever is that `debug.log` is on at all: it is
five times the size of `latest.log`. It stays on for now because it is how Duty's mixin application
gets verified.

## Cupboard — rejected, it is a library not a feature set

Reviewed on 2026-08-16 (`someaddons/cupboard`, branch `neo26.1` -- the only one of these that is
current for 26.1). It is the shared dependency the other someaddons mods pull in, and it contains
nothing Duty wants:

* **Config plumbing** -- `CommonConfiguration`, `ICommonConfig`, `CupboardConfig`, `ConfigLoader`.
  Duty has `DutyConfig`, which already generates the Cloth screen from registered options. Adopting
  a second config system to run someone else's mixins is backwards.
* **Five mixins, all diagnostic** -- `ChunkLoadDebug`, `CommandExceptionLoggingMixin`,
  `EntityLoadMixin`, `ServerAddEntityMixin`, `ThreadingDetectorMixin`. The last was already
  rejected earlier in this file: Lithium's equivalent is stricter.
* **Utility classes with nothing in them.** `MathUtil` is six one-line wrappers around
  `Math.min`/`Math.max` -- and `limitToMin(int, int)` is written `Math.min(value, min)` where it
  plainly means `Math.max`, so it returns the wrong answer for every input above the minimum. That
  is the state of the code being offered as shared infrastructure.

`RegistryLookup` is the only class with a real job, and it exists to paper over Fabric/NeoForge
registry differences, which Duty does not have because it targets one loader.

## ChunkSending — rejected: this pack has one player

Reviewed on 2026-08-16 (`someaddons/chunksending`, newest branch `neo1.21`, so a port).

The headline is worth understanding because the idea is sound. Building a
`ClientboundLevelChunkWithLightPacket` serialises the whole chunk -- every block state, the biomes,
the light data. Vanilla does that once **per player per chunk**. ChunkSending caches the built
packet on the `LevelChunk` and reuses it for the next player who needs the same chunk.

That is a real win on a busy server. It is worth nothing here, and it costs.

* **One player has ever joined this instance.** Every log, current and archived, contains exactly
  one `joined the game` name. With a single recipient each chunk is sent once, so the cache is
  never read: the code builds the packet, stores it, and later clears it. Pure overhead, and the
  stored packet is a fully serialised chunk retained per recently-sent chunk -- straight against
  the memory pass.
* **Light is not invalidated.** The cache clears on `setBlockState`, `setBlockEntity` and
  `removeBlockEntity`, but the packet also embeds the light data and two `BitSet`s. Light changes
  that do not come with a block change in that chunk -- propagation from a neighbour, for one --
  leave a cached packet carrying stale light. ScalableLux is installed and updates light
  asynchronously, which widens that window rather than narrowing it.
* **C2ME is in this code path.** Its `notickvd` module `@Overwrite`s `sendChunkToPlayer`, and the
  chunk-system rewrite owns `ServerAccessibleChunkSending`.

Nothing installed caches the chunk packet server-side, so the feature is genuinely unoccupied --
it is just not worth occupying for one player. Revisit if a dedicated server with real player
counts ever exists, and fix the light invalidation before shipping it if so.

## LightLoad — rejected, it is ModernFix's territory and Duty already holds it

Reviewed on 2026-08-16 (`mynamexiaopiao/LightLoad`, branch `neoforge`, targeting 1.21.1 -- the
`master` branch is 1.20.1). 39 mixins, all client, all about startup and resource loading.

### The distinctive feature is the same mixin Duty already ships

LightLoad's resource-pack work injects into `FilePackResources`, `PathPackResources` and
`FilePackResources.SharedZipFileAccess` to avoid rescanning zips on every `listResources` call.
Duty: FixerUpper's `perf/resourcepacks` feature contains `FilePackResourcesMixin`,
`PathPackResourcesMixin` and `SharedZipFileAccessAccessor` -- the same three classes, for the same
reason. Duty's builds a dedicated `ZipPackIndex` behind double-checked locking; LightLoad's sorts
every entry name into a `List<String>` and binary-searches it. Duty's is at least as good and is
already installed.

That is not a coincidence. FixerUpper *is* ModernFix, and LightLoad ships
`compat.ModernFixModelBakeEventHelperMixin` and `compat.ModernFixResourceNodeMixin` -- it is built
to run alongside ModernFix and patch around it. Its whole feature list maps onto FixerUpper's:
`resourcepacks`, `model_optimizations`, `dynamic_resources`, `cache_blockstate_cache_arrays`,
`deduplicate_wall_shapes`, `dedicated_reload_executor`, `lazy_search_tree_registry`, `tag_id_caching`.

### The hash caching is a net loss

`ResourceLocationHashMixin` `@Overwrite`s `Identifier.hashCode()` to cache the result. Vanilla's, in
26.1.2 bytecode, is `31 * namespace.hashCode() + path.hashCode()` -- and `String.hashCode()` already
caches internally. So vanilla is two field reads, two cached-hash reads, a multiply and an add, all
of which the JIT inlines. Caching that saves a multiply and an add in exchange for four bytes on
*every* `Identifier` -- hundreds of thousands of them in a pack this size -- plus a branch. That is
the wrong direction immediately after a memory pass, and doubly so under
`-XX:+UseCompactObjectHeaders`, where the point is object density. `MaterialHashMixin` has the same
shape and the same problem.

(For contrast, the prototype-hash cache taken from RecipeEssentials above *is* worth it: the thing
being cached is a walk over a whole component map, not two already-cached string hashes. The test is
what the cache replaces, not that caching sounds fast.)

### A third of it targets mods that are not here

Thirteen of the 39 mixins are in `compat/`, patching AdAstra, CableFacades, Lightspeed, ModernFix,
ProbeJS, WaterMedia, FramedBlocks, Titanium, TravelersBackpack, YACL and Embeddium. **None of those
eleven mods is installed.** They are inert weight that has to be maintained across every update.

## Necessities — taken, as a fifth module

Reviewed and merged on 2026-08-16 (`DAQEM/Necessities`, branch `26.1.2`, Apache-2.0). The first of
these where the pack had a real gap: no essentials mod of any kind is installed, so there were no
homes, no warps, no `/back`, and no moderation commands.

### Why it is its own module

`duty-essentials` is the only Duty module that is not about performance. Folding it into
`duty-server` would have meant anyone installing that module for its network, redstone and save
work also getting thirty-five gameplay commands and a homes file. `duty-all` deliberately does not
nest it either: that jar's meaning is "install Duty and the game gets faster", and quietly adding
commands to it would change what an existing install does on the next update.

### What was dropped

* **Kits**, as asked. That also removed `KitManager`, the `Kit` model, the kit-cooldown map on every
  player, and its entry in the persisted player codec.
* **All three of upstream's library dependencies.** `uilib` was declared in the build and never
  imported once. `knot` came to five methods -- `hasPermission`, `translatable`, `getId`, `info`,
  `error` -- which are now on `DutyEssentials.Api`. `yamlconfig` is replaced by `DutyConfig`, so
  these options appear in Duty's settings screen with everything else rather than in a YAML file of
  their own.
* **The networking package.** Upstream ships a ping payload purely so the server can ask "does this
  client have the mod?", and uses the answer to decide whether to send a rich component or a
  flattened one. Nothing else called it. Duty: Essentials has no client half, so the answer is
  always no -- messages are now always flattened server-side, which is what makes them legible on a
  vanilla client instead of showing raw translation keys.
* **Two dead classes inherited from upstream**, `InvseeContainer` and `IModel`, neither referenced
  there or here. `/invsee` opens a vanilla `ChestMenu` directly.

### Permissions

Upstream checks a permission node first and falls back to a level. No permission mod is installed,
so there is nothing to ask: the node is kept in the signature and the decision falls to the vanilla
`PermissionSet`. Upstream's two-argument overload has no level at all, and every call site using it
is administrative (`/broadcast`, `/delwarp`, `/nick` on others), so it maps to game-master rather
than to "anyone".

### Verification

35 commands registered, which is upstream's 36 less kits. Every command's registered name was
cross-checked against its config switch -- 35 of each, no command without a switch and no switch
without a command, after `time` and `weather` turned out to be abstract base classes rather than
commands. All 96 config keys land in a named settings sub-category with none falling to "Other",
and no other module's key changed category as a result.

## Two dangling references, cleared 2026-08-16

Both were found by `tools/check-class-strings.py`, written after the service-file rename crashed
startup. Neither was fatal; both named something that did not exist, so a feature quietly did
nothing.

### FixerUpper's blockstate cache patch: removed, not restored

`FixerUpperMixinPlugin.postApply` ran an ASM scan whenever it saw a mixin called
`perf.reduce_blockstate_cache_rebuilds.BlockStateBaseMixin`. That mixin was never ported into Duty,
so the name matched nothing and roughly 120 lines of ASM never ran. The option override in
`FixerUpperEarlyConfig` and the help text in nine language files were orphaned the same way -- and
the help text called it "**A key optimization**", which is why this needed deciding rather than
just deleting.

Restoring it would mean writing the lazy-cache mixin from scratch. It is not worth it, on the same
grounds Fastload was rejected: the problem it solves has mostly gone. ModernFix's description is
explicit that the cost came from Forge rebuilding the blockstate cache "at many points" during
startup. On 26.1.2 the cache is rebuilt from three call sites in total -- `Blocks`' bootstrap, plus
NeoForge's `DataMapHooks` and `NeoForgeRegistryCallbacks$BlockCallbacks` -- and all three run at
startup or reload rather than during play. Against that, a lazy blockstate cache is an intricate
change to a structure that drives collision, lighting and solidity, and FixerUpper's
`cache_blockstate_cache_arrays` already reduces what each rebuild costs.

So the hook, the override and the nine orphaned help entries are gone. If the feature is ever
wanted, it is a fresh implementation, not a rewiring.

### BiomeSpy's compat gate: replaced with a report

`BiomeMixinPlugin` gated a `compat.terrablender` mixin package that was never ported, so the test
could only ever be false. TerraBlender is also not how a 26.1 pack layers biomes; Lithostitched and
Biolith are.

Removing the gate exposed the more interesting problem. BiomeSpy begins with

    if (!(biomeSource instanceof MultiNoiseBiomeSource)) return;

and Lithostitched's `InjectorBiomeSource` extends `BiomeSource`, not `MultiNoiseBiomeSource`, and
holds the real source as a delegate. So whenever its biome injector is in use, BiomeSpy turns
itself off and `/locate` silently returns to vanilla speed.

**Standing aside is the right behaviour.** The search decides where a structure can generate by
testing its biome list against the climate envelope of the multi-noise source. A mod that wraps the
source changes which biomes actually generate, so reading the envelope underneath would describe a
world that is not being played, and `/locate` would confidently report a structure missing when it
is there. Slow and correct beats fast and wrong.

What was wrong was the silence. `BiomeSpyCompat` now logs one line the first time it meets a source
type it cannot use, naming the class. Once per type, not per call -- a single `/locate` reaches
this thousands of times.

Checked against the installed pack: five mods ship biome data, but all five use NeoForge's
`biome_modifier` system, which does not wrap the source. Nothing uses Lithostitched's
`biome_injector`, so BiomeSpy is active today. The report is there for the mod that changes that.

## Speed and gamemode commands, added 2026-08-16

Six commands added to Duty: Essentials, none of them from an upstream mod.

### /flyspeed and /walkspeed

Both take a multiplier of vanilla, 1 to 10, rather than a raw speed. Vanilla's values are 0.05 and
0.1, which are not numbers anyone wants to type; a multiplier of 1 is exactly vanilla and is how the
command is undone.

**The two are implemented through different mechanisms, and the obvious one is a trap.**

`Abilities` has both a `flyingSpeed` and a `walkingSpeed` field with public setters, so setting both
looks correct. Flying is: `Abilities.flyingSpeed` feeds
`LivingEntity.getFrictionInfluencedSpeed`, which is the movement calculation. Walking is not. In
26.1.2 the only readers of `Abilities.walkingSpeed` are
`AbstractClientPlayer.getFieldOfViewModifier` and the save/load path, so setting it changes the
player's field of view and nothing else. It moves the player on Bukkit-derived servers, which is
where the belief that it works comes from.

So `/walkspeed` goes through the `minecraft:movement_speed` attribute instead, with a permanent
`ADD_MULTIPLIED_BASE` modifier under a fixed id. The fixed id matters: it makes the command
idempotent, so running it ten times leaves one modifier rather than ten stacked. A multiplier of 1
removes the modifier outright rather than leaving one that adds zero.

Neither needs Duty to persist anything. `Abilities` is serialised by vanilla through
`Abilities.Packed`, and permanent attribute modifiers are written into the player's saved data, so
both survive a relog on their own. `/flyspeed` calls `onUpdateAbilities()` because the client is
what actually moves the player and has to be told.

### /gmc, /gms, /gma, /gmsp

Gamemode switches with the mode already chosen. Deliberately not aliases: an alias in
`CommandManager` redirects to the same Brigadier node and would still demand the argument
(`/gmc creative`), which defeats the point. Each is its own command calling
`GamemodeCommand.setMode` directly, so the messages, the command-feedback game rule and the
"other player" wording stay in one place rather than being copied four times.

All six take an optional player argument like the commands they sit beside, all six have a config
switch, and `/gmc` and friends share `/gamemode`'s permission node because they are the same act
spelled shorter.

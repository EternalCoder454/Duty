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

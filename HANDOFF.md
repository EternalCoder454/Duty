# Duty — handoff

Everything a fresh assistant needs to pick this up. Read this before touching anything;
several of the traps below cost a full session each to find.

---

## 1. What Duty is

A performance mod for **Minecraft 26.1.2 on NeoForge 26.1.2.95**, Java 25, built by merging
existing mods and dropping everything that only exists to support older versions. Private,
personal build. Owner: Zachary Smith. Project root:

```
C:\Users\Zachary Smith\Documents\Projects\Minecraft\Duty
```

Read `../BUILDING.md` too — it covers conventions shared with the other mods in that folder.

### Absolute rules, from the owner

1. **Must work alongside Sodium, C2ME, Lithium and other mods.**
2. **Stability first, then performance.**

Rule 1 is not decorative. Four separate crashes came from breaking it.

### Modules

| Jar | Contains | Licence |
|---|---|---|
| `duty-memory` | Jasione (`Enum.values()`), FerriteCore (block-state tables, block state cache dedup, data component dedup), Fast-Tag (TagKey/ResourceKey interning) | LGPL-3.0 |
| `duty-client` | EntityCulling, Particle Core, OptimisedBlockEntities, OcclusionCulling, Stfu, ImmediatelyFast | **not distributable** |
| `duty-fixerupper` | ModernFix, plus Resource-Trimmer's compact identifier encoding | LGPL-3.0 |
| `duty-server` | BiomeSpy (`/locate` biome + structure search), KryptonReno network pipeline | LGPL-3.0 |
| `duty-core` | shared config/logging, JarJar-nested into each of the above — **never installed separately** |
| `duty-annotations`, `fixerupper-mixin-ap` | build-time only, never shipped |

`duty-client` contains EntityCulling, whose licence permits use/modify/compile but not
redistribution. The owner has assessed this, keeps builds private, and accepts responsibility.
**Do not re-litigate it.** Do flag it if publishing, modpacks or sharing come up.

---

## 2. Build and deploy

**Set TMP/TEMP before every Gradle invocation. Not optional.**

```powershell
$env:TMP="C:\gtmp"; $env:TEMP="C:\gtmp"
.\gradlew.bat build --console=plain
```

Without it Gradle dies with `Unable to establish loopback connection`. The cause is not
networking: `TEMP` resolves to the 8.3 short path `C:\Users\ZACHAR~1\AppData\Local\Temp`,
Windows AF_UNIX sockets reject short names, and `Selector.open()` uses AF_UNIX. Reproduce with
`java tools/SelectorLoopbackCheck.java`. Already ruled out and not worth retesting: JDK version,
Gradle version, `--no-daemon`, `preferIPv4Stack`, `netsh winsock reset`.

Deploy after every working build — this is a standing instruction from the owner:

```bash
bash tools/deploy.sh
```

It stages to `install-mods/`, deletes every `duty-*.jar` already in the pack, copies, and verifies
by SHA-256. Test pack:

```
C:\Users\Zachary Smith\AppData\Roaming\PrismLauncher\instances\Eternally Planetary(1)\minecraft
```

---

## 3. A green build means almost nothing

This is the single most important thing to internalise. **Ten runtime failures reached the game
after a completely clean compile.** javac does not verify mixin annotations. Run all three
checkers before every deploy:

```bash
python tools/check-mixin-configs.py # every mixin named in a config exists in the built jar
python tools/check-shadows.py       # @Shadow members exist on the target (walks superclasses)
python tools/check-descriptors.py   # classes + method targets named in annotations exist
python tools/check-conflicts.py     # classes Duty and an installed perf mod both patch
```

`check-mixin-configs.py` reads the **built jar**, so run it after a build. The others read
source and can run any time.

Each exists because of a specific crash:

| Failure | Symptom | Checker |
|---|---|---|
| `@Shadow neighbours` | 26.1.2 spells it `neighbors` | check-shadows |
| descriptor named `LevelChunkTicks` in the wrong package | import was right, string was stale | check-descriptors |
| `@Overwrite` on a method Lithium injects into | Lithium fails to apply, **server won't start — presents as "cannot create a world"** | check-conflicts |
| writing a `final` field from a non-constructor | `IllegalAccessError` | see §4 |
| half-gated mixin group | accessors applied alone and failed | — |
| `@Invoker` declaring `void` for a `boolean` method | `InvalidAccessorException` | manual `javap` |
| a mixin listed in the config with no class in the jar | `InvalidMixinException ... was not found`, dies at *prepare* | check-mixin-configs |
| `method = "name"` where the target is **overloaded** | `Scanned 0 target(s)`, and it **breaks other mods** loading that class | none — check `javap -c` by hand |

`check-conflicts.py` is a **screening tool, not a verdict.** It byte-greps for class names, so it
reports overlaps that are incidental. Always confirm by checking whether the other mod actually
has a mixin *targeting* that class before acting. It falsely flagged Lithium on `TagKey`.

To check anything by hand:

```bash
javap -p -cp "$TMPDIR/duty-verify/cp/minecraft.jar" net.minecraft.some.Class
```

There is also `bash tools/verify-compile.sh` — an offline javac check that reuses Gradle's caches.
Faster than a full build for "does this still compile", but it cannot apply access transformers.

---

## 4. Porting rules learned the hard way

**Fabric access widener → NeoForge access transformer.** Wideners do things transformers do not,
in two distinct ways, and both bite:

- An AW `mutable` entry strips `final` implicitly. An AT needs `public-f` spelled out. Plain
  `public` widens visibility and still dies with `IllegalAccessError: Update to non-static final
  field`.
- Widening an *interface method* in an AW implicitly covers its implementors. An AT does not, so
  the interface and every implementation must be listed together. Widening only the interface
  makes Minecraft's own recompile fail with "cannot override ... weaker access privileges";
  widening none leaves the call inaccessible. See `DirectStateAccess` in duty-client's AT.

Newer Fabric mods may ship a `.classtweaker` file instead of `.accesswidener`; same idea, same
translation, same traps.

**`@Overwrite` is the conflict predictor.** Overlap on a *class* is harmless and normal. An
`@Overwrite` on a *method* another mod injects into is fatal every time. When it happens, defer
with `@RequiresMod("!lithium")` rather than fighting priorities.

**Gate mixin groups as whole units.** Gating only the `@Overwrite` files leaves that group's
accessors, invokers and duck interfaces applying alone — they then fail on their own, or code
elsewhere casts to a duck interface that is no longer applied (`ClassCastException`).

**Yarn vs Mojang mappings.** Fabric mods often use Yarn (`MinecraftClient`, `ClientWorld`,
`DrawContext`). NeoForge uses Mojang official (`Minecraft`, `ClientLevel`, `GuiGraphics`). Every
name must be translated. A mod using Yarn *and* targeting a different MC version is a rewrite, not
a port — budget accordingly.

**Overloaded target methods must be pinned by descriptor.** `method = "tooltip"` matched two
overloads of `GuiGraphicsExtractor.tooltip`; mixin resolved the wrong one, found no injection
point, and failed the whole config. Because the failure happens when the *target class* loads,
it takes down whatever mod triggered that load — here Liteminer, which looked like the culprit
and was not. Find the right overload with:

```bash
javap -p -c -cp <minecraft.jar> <Class> | awk '/^  [a-z].*\(/{m=$0} /calledMethod/{print m}'
```

then pin the full descriptor with `javap -p -s`.

**Never capture `Minecraft.getInstance()` in a `static final` field.** Mod construction runs
*inside* `Minecraft.<init>`, before the singleton is assigned, so any class loaded during
construction captures null — permanently, and silently until something dereferences it. Fabric
mods get away with it because their initializer runs later. Use a `client()` accessor instead.
Find them with:

```bash
grep -rn "static final Minecraft" duty-*/src/main/java/
```

**Merging a mod's mixins into a shared config orphans its mixin config plugin.** A config names
exactly one `plugin`, so folding another mod's mixins in silently drops whatever that mod's own
plugin did. ImmediatelyFast loads its config from `onLoad`, so losing it left
`ImmediatelyFast.config` null and killed startup inside `RenderSystem.initRenderer`. Stfu's
handles its `@DisableIf` compat gating. **Delegate instead of dropping**: keep an instance of the
upstream plugin and forward `onLoad`/`shouldApplyMixin` for its own package -- see
`DutyClientMixinPlugin`. Check for `implements IMixinConfigPlugin` in anything you port.

**NeoForge has two event buses, and putting an event on the wrong one throws at mod
construction.** `IModBusEvent` types (registration, lifecycle, `AddClientReloadListenersEvent`)
go on the mod bus passed to the `@Mod` constructor; everything else goes on
`NeoForge.EVENT_BUS`. The error is explicit -- "IModBusEvent events are not allowed on the
common NeoForge bus" -- but it only appears at runtime. Check with:

```bash
javap -cp <neoforge-universal.jar> <EventClass> | head -3   # look for IModBusEvent
```

Note that reads *direct* interfaces only; lifecycle events inherit it through
`ParallelDispatchEvent`, so a negative result is not proof.

**Stonecutter leaves dead files behind.** A version-inapplicable file is left fully commented
out, so the `.java` exists but compiles to nothing. Never build a mixin config from the file
tree alone — check the jar. Delete such files outright when porting; they are inert text that
only gets mis-registered again.

**Watch for Lombok.** Several upstreams use it; Duty does not carry it. Write the accessors out.

**Check what vanilla already does before porting anything.** This closed Fastload outright and it
is the cheapest test available, so run it before assessing licence or mod conflicts. `javap` the
exact method the upstream mixin targets, in a real 26.1.2 jar. A `class not found` or a missing
constant means the feature was absorbed into vanilla and there is nothing to port. Record the
result in `FEATURES.md` with the commands, so nobody re-investigates it.

**An `@Overwrite` can silently delete another mixin's target without any error.** KryptonReno
overwrites `Util.makeIoExecutor`; Duty's Stfu mixin wraps `lambda$makeIoExecutor$0`, the lambda
*inside* it. The lambda still exists as a synthetic method, so the wrap still applies and reports
success -- it is simply never called any more, and the feature dies silently. Neither the mixin
log nor any checker shows this. When two mods touch one method, check whether one of them is
overwriting the body the other injects into.

**Do not use bash heredocs to write Java/regex containing backslash escapes.** `\b` becomes a
literal backspace byte and `\n` becomes a real newline, silently corrupting the file. This
happened four times. Use the editor tooling, or Python with raw strings written via a file.

---

## 5. The Modernica lesson

FixerUpper briefly contained Modernica (a Fabric-only ModernFix fork). All six of the first six
runtime crashes traced to it; **none** to ModernFix's own code. It was removed entirely — 97 files.

The cause was structural, not bad luck: Modernica is Fabric-only and written against a different
Minecraft, so its mixins compiled against 26.1.2 while targeting an API that had moved. ModernFix
maintains a real 26.1 branch and needed no repairs at all.

**Apply the same scepticism to any Fabric-only or older-version source.** Prefer upstreams with a
current NeoForge branch. If a port produces two or three runtime failures in a row, stop repairing
and reconsider whether it belongs at all.

---

## 6. What is left to do

See `FEATURES.md` for the full assessment of all eight candidate mods. Remaining, in the order the
owner wants (small before big):

2. ~~**Fastload**~~ — **rejected 2026-08-15, do not reopen without re-running the checks in
   `FEATURES.md`.** Not a licence or C2ME problem: Mojang deleted the machinery all three of its
   features patch. `prepareStartRegion` and its `441` constant are gone (`prepareLevels()` now
   waits on ticket-backed chunks via `ChunkLoadCounter`), `DownloadingTerrainScreen` no longer
   exists (staged `LevelLoadListener` replaces it), and `PrepareSpawnTask` already waits on
   `LOAD_PLAYER_CHUNKS` before the player spawns. Nothing left to port.
3. ~~**KryptonReno**~~ — **merged 2026-08-15** into `duty-server` as `net.dutymod.server.net`
   (+ `mixin.net`), from its dedicated `26.1.2` branch. Took the network pipeline: native
   compression and encryption, the Velocity frame decoder and varint prepender, the
   VarInt/VarLong/Utf8String writers, and the packet-processor drain. **Deliberately not taken**:
   the `Util` virtual-thread overwrite (collides with Duty's own thread-priority mixins, below),
   server-side entity culling (overlaps Lithium on `ServerEntity`/`ServerLevel`/
   `LevelChunkSection`, and `duty-client` already has EntityCulling), the particle packet mixins
   (upstream disables them on client installs anyway), RCON, and Happy Eyeballs.
4. **LightLoad** (no licence, **1.20.1**, 86 files) — owner-approved, but six of its 42 mixins
   duplicate ModernFix, which Duty already ships newer. Only the other 36 are worth considering.

Done since this was first written: Fast-Tag, Resource-Trimmer, BiomeSpy (in a new `duty-server`
module) and Stfu are all merged, and `NOTICE.md` attribution is up to date.

**A caution learned from Stfu:** check every branch of a multi-version repo before judging the
port cost. Stfu's version branches use Yarn mappings; its `multiversion` branch uses Mojang and
targets 26.1.2. I assessed the wrong branch first and nearly declined a port that turned out to be
straightforward.

---

## 7. Working style the owner expects

- Deploy every working jar to the pack without being asked; remove stale jars first.
- Read the actual crash log rather than guessing; `logs/latest.log` and `crash-reports/`.
- Confirm a mixin applied with `grep 'from duty_' logs/debug.log` — a mixin that applied and did
  nothing looks identical in-game to one that never applied.
- Be direct about what was not done and why. Do not report a build success as if it were a
  working feature.

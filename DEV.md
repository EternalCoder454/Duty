# Duty — developer notes

Player-facing documentation is in [README.md](README.md). This is everything else.

## Branches

One target per branch. Every branch is a full checkout that builds one thing, so a build is
never a matrix of conditions and a checkout is never half-configured for a version you are
not on.

| Branch | Loader | Minecraft | Java | State |
|---|---|---|---|---|
| **`main`** | NeoForge | 26.1.2+ | 25 | Builds and runs. The development line. |
| `neoforge-1.21.1` | NeoForge | 1.21.1 | 21 | Framework builds; feature modules being ported. |
| `fabric-26.1.2` | Fabric | 26.1.2+ | 25 | Retargeted, not yet ported. |
| `forge-1.20.1` | Forge | 1.20.1 | 17 | Retargeted, not yet ported. |

`gradle.properties` names the loader (`duty.loader`), Minecraft version and Java level for
the branch, and the build derives everything from those — so a version branch differs from
`main` by configuration rather than by build logic.

Shared work lands on `main` first and is cherry-picked outward. See [PORTING.md](PORTING.md)
for what each target still needs and which features cannot exist on which version.

## Building

```bash
export TMP=C:/gtmp TEMP=C:/gtmp   # required, see below
./gradlew.bat build
```

**Set `TMP`/`TEMP` first, every time.** Gradle talks to its worker processes over a loopback
socket, and on a path with a space in it the workers fail to start with an error that reads
like a build-script fault and is not one. `org.gradle.jvmargs` in `gradle.properties` also
pins `-Djava.net.preferIPv4Stack=true` for the same class of problem.

`./gradlew.bat build` builds every module. Note that a **Kotlin build-script error does not
print `error:`** — it says `Unresolved reference`. Check the exit code, not a grep.

## Modules

| Module | Combines | Licence |
|---|---|---|
| `duty-framework` | original | MIT |
| `duty-memory` | Jasione + FerriteCore | LGPL-3.0 |
| `duty-client` | Particle Core + EntityCulling + OptimisedBlockEntities + ImmediatelyFast/Batching + Stfu | Mixed, **not redistributable** |
| `duty-fixerupper` | ModernFix | LGPL-3.0 |
| `duty-server` | BiomeSpy + KryptonReno + Alternate Current + async saving | LGPL-3.0 |
| `duty-essentials` | Necessities | Apache-2.0 |
| `duty-all` | packaging only — nests the four performance modules | — |

`duty-framework` is nested into every module via JarJar, so exactly one copy loads however
many modules are installed. `duty-all` deliberately does **not** nest `duty-essentials`:
that jar means "install Duty and the game gets faster", and adding gameplay commands to it
would change what an existing install does on update.

## Source sets: the loader axis

Every module is split in two:

- **`src/main`** — names no loader. 538 of Duty's 574 source files.
- **`src/<duty.loader>`** — the only place allowed to import one. 36 files.

Adding a loader is a new source set with that loader's entry points and event wiring;
`src/main` does not change.

This is **enforced, not conventional**. ModDevGradle puts Minecraft *and* NeoForge on the
main compile classpath, so a `net.neoforged` import in `src/main` would compile happily here
and only fail once someone built the Fabric target. `checkMainIsLoaderNeutral` fails the
build instead, and runs as part of `check`.

Watch for the two leaks an import scan does not find:

- **A shared class with one loader-specific method.** `Quiet` holds keybinds and state that
  eighteen loader-neutral mixins depend on, plus one `register(IEventBus)`. The class stays
  neutral; only the registration moved, as `QuietNeoForge`.
- **A flag only the loader can set.** `FixerUpperState.registryEventsFired` is written by the
  entry point and read by neutral mixins. No import, invisible to a scan, and a missing class
  on another loader.

## Verification

A green build proves the code compiles. It says nothing about whether a mixin applies.

```bash
python tools/check-shadows.py         # @Shadow members exist on the target, including inherited
python tools/check-mutable-shadows.py # @Shadow fields that get written are not final without @Mutable
python tools/check-descriptors.py     # classes and method targets named in annotations exist
python tools/check-mixin-configs.py   # every config entry resolves to a shipped class
python tools/check-orphan-mixins.py   # every shipped mixin is listed in some config
python tools/check-class-strings.py   # service files, access transformers, class-name literals
python tools/check-conflicts.py       # classes Duty and an installed mod both patch
python tools/check-dead-code.py       # classes nothing reaches
bash   tools/deploy.sh                # stage, remove stale jars, install, verify by hash
```

Every one exists because of a specific failure a clean build did not catch:

- an `@Overwrite` that bound to the wrong overload of an overloaded method,
- a `META-INF/services` file naming a class a package rename left behind, which crashed
  startup before any mod loaded,
- an annotation processor that silently stopped listing 14 mixins after a source-set move —
  classes still shipped, build still green, mixins never applied,
- checkers that skipped a whole module because their module list was hardcoded,
- access transformer entries naming classes that no longer exist, which nothing read,
- a ported Fabric mixin assigning a shadowed **final** field, which the JVM only permits
  from the declaring class's own `<init>` — green build, clean apply, `IllegalAccessError`
  partway through world creation. Fabric mods rarely say `@Mutable` because loom's access
  widener drops `final` game-wide, so ported mixins are where this bites.

The checkers compare against **one** Minecraft jar, so each branch needs its own run.
`tools/_minecraft_jar.py` refuses to run against another version's jar rather than producing
confident nonsense — build directories are gitignored and survive a checkout, so this is a
real hazard, not a theoretical one.

CI runs the same suite per branch; see `.github/workflows/build.yml`.

## Measuring

The checkers above answer "is it wired up". `DutyMetrics` in the framework answers "is it
worth keeping".

Before it, every module measured itself differently — culling kept `lastPassMillis` on the
task object, the structure watchdog tracked its own elapsed time, FixerUpper timed startup
with bespoke mixins — and most of those numbers were written and never read. The culling
timings sat there for months with nothing displaying them.

Two primitives. Hold the handle in a `static final` field so the registry lookup happens
once at class-init, never on the measured path:

```java
private static final DutyMetrics.Timer PASS = DutyMetrics.timer("client.culling.pass");
private static final DutyMetrics.Counter HIDDEN = DutyMetrics.counter("client.culling.hidden");

long started = PASS.begin();
try { runPass(); } finally { PASS.end(started); }
HIDDEN.add(count);
```

`PASS.open()` gives the same thing as try-with-resources, at one allocation per call — fine
off the hot paths, not fine on them.

**Off by default, and off means off.** `begin()` returns on a `volatile boolean` read
without touching a clock; `end()` and `record()` do the same. That matters because these are
meant for per-frame and per-tick paths, where `System.nanoTime()` is itself the expensive
part — a VDSO call on Linux, a QPC on Windows, tens of nanoseconds, which is real money at
sixty frames a second across thousands of entities.

Counters are the exception: they always count, because a `LongAdder` increment is cheap and
the number is usually wanted precisely when nobody thought to switch measuring on first.

Reading it:

| | |
|---|---|
| `/duty metrics` | the report, in chat |
| `/duty metrics on` / `off` | toggle without a restart |
| `/duty metrics reset` / `log` | clear, or dump to the log |
| `framework.metrics` | on at startup |
| `framework.metrics_report_seconds` | periodic report to the log; 0 is off |
| `/duty reload` | re-read `duty.properties` without a restart |

Both routes are live: the command sets the flag directly, and editing the file then
reloading works because `DutyConfig.onReload` lets a module refresh a value it caches.
`DutyMetrics` caches `framework.metrics` precisely because reading it per call would cost
more than the work being timed — and without the hook, a reloaded file would update the map
and leave that copy stale, so the file and the behaviour would disagree. Any module that
caches an option should register the same way.

Timers report calls, total, mean and worst, sorted by **total** rather than mean — a cheap
thing done constantly is usually the problem, and sorting by mean hides it behind whatever
ran twice. `recentMillis()` is a rolling average for debug-screen lines, since an all-time
mean stops moving after a few thousand samples and cannot show that something just got
slower.

Counts and totals go through `LongAdder` because Duty measures from the render thread, the
server thread and its own workers, sometimes on the same timer. The rolling average is an
unsynchronised `volatile double`: two threads finishing at once lose one sample, which is
not worth a CAS loop on a number whose only job is to be displayed.

### What is measured

| name | what it tells you |
|---|---|
| `client.culling.pass` / `.traced` / `.hidden` | whether occlusion tracing earns its thread |
| `client.particle.tick_async` / `.tick_sync` | both arms, so async can be compared against what it replaced |
| `server.lighting.chunk` / `.chunks_lit` | what the light engine costs per chunk |
| `server.structure.search` / `.search_timed_out` | how long `/locate` really takes, and how often the watchdog fires |
| `server.biome.search` | the same for `/locate biome` |
| `server.save.write` / `.count` | whether the single save worker keeps up with autosave |
| `server.net.compress` / `.decompress` | what native compression costs per packet |
| `memory.blockstate.shapes_shared` / `.face_sturdy_shared` | objects that did not stay on the heap |

Two of those are deliberately paired against a baseline rather than measured alone. Timing
only the async particle path would show what it costs without showing what it replaced, and
the save timer sits around the write itself rather than around `submit()` — `submit` returns
the moment work is queued, so timing it would measure the handoff and report that saving is
free.

Where a module already keeps a last-value field for its own display, it keeps it — the F3
culling line needs the last pass exactly, and has to work whether or not measurement is on.
The metrics call sits alongside and adds the shape over time.

## Two rules the design enforces

- **Must work alongside Sodium, C2ME and other mods.** Every optimization sits behind a
  config toggle, mixins are gated on those toggles, and each module declares itself
  `incompatible` with the upstream mod it replaces.
- **Stability first, performance second.**

## The Jasione mass-ASM warning

`duty-memory` rewrites `Enum.values()` call sites across the game. It is the single most
invasive thing Duty does. If a crash appears with no obvious cause, turn
`memory.enum_values` off first and re-test before investigating anything else.

## Modernica was removed

FixerUpper is plain ModernFix. Modernica's 97 extra files were ported and taken back out
after six consecutive runtime crashes traced to them and none to ModernFix's own code:
`@Shadow neighbours` where 26.1.2 spells it `neighbors`; a descriptor naming
`LevelChunkTicks` in the wrong package; an `@Overwrite` on
`LevelChunkSection.recalcBlockCounts` that broke Lithium's injection; a `final` field written
from a non-constructor handler; a half-gated group leaving its accessors applying alone; and
an `@Invoker` declaring `void` for a method returning `boolean`.

Structural rather than bad luck: Modernica is Fabric-only and written against a different
Minecraft, so its mixins compile against 26.1.2 while targeting an API that has moved.

## Dependencies dropped

Targeting one version at a time is what makes this possible:

| Dropped | Was needed for | Replaced by |
|---|---|---|
| KotlinForForge | Particle Core's 4 Kotlin files | plain Java |
| fzzy-config | Particle Core's config | `DutyConfig` |
| ConditionalMixin | Particle Core's mixin gating | `DutyClientMixinPlugin` |
| TRansitionLib | EntityCulling's cross-version shims | direct calls |
| Lombok | EntityCulling's generated setters | written out |
| `net.lenni0451:reflect` | Jasione's classloader lookup | `StackWalker` |
| knot, uilib, yamlconfig | Necessities' platform, UI and config | `DutyPlatform`, `DutyConfig` — uilib was declared and never imported |

## Where the reasoning lives

[FEATURES.md](FEATURES.md) records every mod assessed, what was taken, what was rejected and
why — including the ones rejected on evidence after the work was done. It is the file to read
before proposing that Duty absorb another mod.

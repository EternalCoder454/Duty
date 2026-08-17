# Duty

*Duty does what it needs to do, perform its duty.*

A performance mod for **Minecraft 26.1.2** on **NeoForge**, built by combining several
existing performance mods into a few jars and dropping everything that only existed to
support older versions.

Duty is five separate jars. Install the ones you want — none of them requires the others.

| Jar | What it does for you |
|---|---|
| **Duty: Memory** | Uses less RAM. Deduplicates the tables Minecraft keeps per block state, and removes an array copy the game performs constantly. |
| **Duty: Client** | Higher frame rate. Bakes chests, signs and banners into the chunk mesh instead of redrawing them every frame, hides entities behind walls so they cost nothing, and speeds up particles. |
| **Duty: FixerUpper** | Faster startup. Trims datafixer work and makes resource and registry loading lazy, cutting the long tail of loading stalls. |
| **Duty: Server** | Smoother worlds. Faster `/locate`, faster redstone, a quicker network path, and autosaves that no longer stall the game. |
| **Duty: Essentials** | Homes, warps, `/back`, random teleport, teleport requests, and the usual moderation commands. The one module that is not about performance. |

> [!IMPORTANT]
> **Do not share `duty-client`.** It contains EntityCulling, whose licence lets you build
> and run it yourself but not pass it on. Uploading it, putting it in a public modpack or
> handing it to a friend is not permitted. The other four jars are unaffected. See
> [NOTICE.md](NOTICE.md).

## Installing

Drop the jars you want into `mods/`. That is all — there are no library dependencies to
install alongside them.

Duty declares itself incompatible with the mods it replaces (FerriteCore, ModernFix,
EntityCulling and so on), so the game will tell you rather than let you run both.

**Java 25** is required, which is what NeoForge 26.1 runs on anyway.

## Configuring

Two ways, and they are the same settings:

- **In game** — Mods → any Duty module → Config. This needs [Cloth Config](https://modrinth.com/mod/cloth-config)
  installed. Without it Duty works exactly the same, you just edit the file instead.
- **`config/duty.properties`** — plain `key=value`, written on first run with a comment
  above every option explaining what it does.

Everything is on by default except the options that change how the game looks or behaves;
those are off and say so.

**Settings need a restart.** Almost every option is read once, while the game is loading —
some of them before the mod list even exists — so changing one takes effect next launch.
The settings screen marks every entry accordingly rather than pretending otherwise.

Removing a Duty jar does not lose its settings: options belonging to a module you do not
have installed are kept in the file rather than deleted.

## If something goes wrong

Duty logs under `Duty` in `logs/latest.log`, and each module logs under its own name
(`Duty/Server`, `Duty/Client`). For more detail, set `framework.verbose_logging=true` in
`config/duty.properties`.

Every optimization sits behind its own toggle, so if something misbehaves you can turn off
one feature rather than the whole jar. The option comments say what each one does.

## Recommended JVM flags

Duty does not need these, but they pair well with it on Java 25:

```
-XX:+UseZGC -XX:+UseCompactObjectHeaders -XX:ZAllocationSpikeTolerance=5.0
-XX:+ExplicitGCInvokesConcurrent -XX:+UseStringDeduplication -XX:+PerfDisableSharedMem
-Djava.net.preferIPv4Stack=true
```

`UseCompactObjectHeaders` is production-ready in Java 25 and does work with ZGC; it makes
every object smaller, which compounds with what Duty: Memory already does.

## Licensing

Duty is assembled from LGPL-3.0, MIT and Apache-2.0 code, plus one component that is not
redistributable. Read [NOTICE.md](NOTICE.md) before sharing anything.

## For developers

Building it, how the modules are put together, the other Minecraft versions, and the
verification tooling are all in **[DEV.md](DEV.md)**.

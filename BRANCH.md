# fabric-26.1.2

**Fabric 26.1.2, Java 25.**

This branch is a copy of `main` with its build retargeted. **It does not compile yet.**
That is its intended state, not a fault: the source is still written against NeoForge
26.1.2 and has to be ported.

Start with [PORTING.md](PORTING.md), which lists what this target needs and which
features cannot exist on it at all.

The loader-specific code is in `src/fabric` in each module. `src/main` names
no loader and should mostly port unchanged; `checkMainIsLoaderNeutral` fails the build if
anything puts a loader import back into it.

Shared fixes land on `main` first and are cherry-picked here.

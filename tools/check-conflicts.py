#!/usr/bin/env python3
"""Find Minecraft classes that Duty mixes into and another installed mod also mixes into.

Duty's first world-creation failure was not a Duty bug in isolation: its block-counting
mixin merged into LevelChunkSection.recalcBlockCounts, and Lithium -- at the same priority
-- could then no longer inject there, so the integrated server refused to start. Two mods
optimising the same method is the failure mode the project's "must work with Sodium, C2ME
and other mods" rule exists to prevent, and it only shows up at runtime.

This lists overlaps so they can be reviewed deliberately. An overlap is not automatically a
bug -- two mixins on one class are usually fine -- but an overlap is where to look first
when another mod fails to apply.

Usage:
    python tools/check-conflicts.py [--pack PATH]
"""

import os
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# The pack Duty is actually deployed to, matching tools/deploy.sh. Override with DUTY_PACK to
# check against a different one -- the kitchen-sink pack has far more mods and therefore far more
# conflict surface, which is worth a run before shipping something that patches a shared class.
DEFAULT_PACK = Path(os.environ.get(
    "DUTY_PACK",
    r"C:\Users\Zachary Smith\AppData\Roaming\PrismLauncher"
    r"\instances\Eternally Dutified\minecraft",
))

MIXIN_TARGET = re.compile(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?([^)]*?)\)", re.S)
CLASS_TOKEN = re.compile(r"([A-Za-z_][\w.]*)\.class")
IMPORT_RE = re.compile(r"^import\s+((?:net\.minecraft|com\.mojang)[\w.]*)\s*;", re.M)
TARGETS_STR = re.compile(r'"([\w/$.]+)"')

# Mods worth checking against: the ones the project promises to coexist with.
INTERESTING = ("lithium", "sodium", "c2me", "scalablelux", "ferritecore", "modernfix",
               "noisium", "immediatelyfast", "entityculling", "moreculling")



def source_files(module_dir):
    """Every Java file in a module, across all source sets.

    Duty is split into `src/main/java`, which names no loader, and `src/<loader>/java`. Scanning
    only main would silently skip the loader-specific half -- which is exactly what happened when
    the split landed: this checker's count fell and nothing failed.
    """
    for src_set in sorted(p for p in (module_dir / "src").iterdir() if p.is_dir()):
        java = src_set / "java"
        if java.is_dir():
            yield from java.rglob("*.java")

def duty_targets() -> dict[str, set[str]]:
    """Internal names of Minecraft classes each Duty module mixes into."""
    found: dict[str, set[str]] = {}
    for module in ("duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials"):
        for src in source_files(ROOT / module):
            text = src.read_text(encoding="utf-8", errors="replace")
            m = MIXIN_TARGET.search(text)
            if not m:
                continue
            imports = IMPORT_RE.findall(text)
            names = set()
            for simple in CLASS_TOKEN.findall(m.group(1)):
                binary = next(
                    (i for i in imports if i.rsplit(".", 1)[-1] == simple.split(".")[-1]), None
                )
                if binary:
                    names.add(binary.replace(".", "/"))
            for literal in TARGETS_STR.findall(m.group(1)):
                names.add(literal.replace(".", "/"))
            for n in names:
                if n.startswith("net/minecraft"):
                    found.setdefault(n, set()).add(module)
    return found


def mod_touches(jar: Path, targets: set[str]) -> set[str]:
    """Which of `targets` this jar's own classes reference.

    Reading the constant pool as raw bytes is crude but effective: a mixin that targets a
    class necessarily names it, and this avoids disassembling several hundred classes.
    """
    hits = set()
    try:
        with zipfile.ZipFile(jar) as zf:
            names = [n for n in zf.namelist() if n.endswith(".class") and "mixin" in n.lower()]
            if not names:
                names = [n for n in zf.namelist() if n.endswith(".class")]
            for entry in names:
                try:
                    blob = zf.read(entry)
                except (KeyError, zipfile.BadZipFile):
                    continue
                for t in targets:
                    if t.encode() in blob:
                        hits.add(t)
    except (zipfile.BadZipFile, OSError):
        pass
    return hits


def main() -> int:
    pack = DEFAULT_PACK
    if "--pack" in sys.argv:
        pack = Path(sys.argv[sys.argv.index("--pack") + 1])
    mods = pack / "mods"
    if not mods.is_dir():
        # Not a failure. This check reports which installed mods share a target class with Duty,
        # which is context for a person rather than a verdict on the code -- and on a CI runner
        # there is no modpack to compare against. Saying so and exiting cleanly keeps a green
        # build honest; returning 1 here would train everyone to ignore the result.
        print(f"No mods folder at {mods}; nothing to compare against, skipping.")
        return 0

    targets = duty_targets()
    print(f"Duty mixes into {len(targets)} Minecraft classes\n")

    any_overlap = False
    for jar in sorted(mods.glob("*.jar")):
        if not any(k in jar.name.lower() for k in INTERESTING):
            continue
        if jar.name.startswith("duty-"):
            continue
        hits = mod_touches(jar, set(targets))
        if not hits:
            continue
        any_overlap = True
        print(f"  {jar.name}")
        for t in sorted(hits):
            owners = ", ".join(sorted(targets[t]))
            print(f"    {t}  (Duty: {owners})")
        print()

    if not any_overlap:
        print("No overlaps with the installed performance mods.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

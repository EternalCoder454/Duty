#!/usr/bin/env python3
"""Find mixin classes that ship in a jar but are listed in no mixin config.

`check-mixin-configs.py` verifies the other direction -- that every listed entry resolves to a
class. That does not catch a mixin falling *out* of a config, because the entries left behind are
all still valid; the missing one simply stops being mentioned, and a mixin nobody lists is a mixin
that never applies.

This is not hypothetical. Splitting each module into a loader-neutral `src/main` and a
loader-specific `src/neoforge` moved twenty FixerUpper mixins, and the annotation processor that
generates its config only ran over `src/main`. The generated config went from 114 entries to 100.
The classes still shipped, the build was green, and fourteen mixins would have silently stopped
applying.

Run after a build; reads the built jars, so it sees what actually ships.
"""
import json
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODULES = sys.argv[1:] or [
    "duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials",
]

VERSION = "0.1.0"


def main() -> int:
    total_orphans = 0

    for module in MODULES:
        jar_path = os.path.join(ROOT, module, "build", "libs", f"{module}-{VERSION}.jar")
        if not os.path.isfile(jar_path):
            print(f"  {module}: no jar built, skipped")
            continue

        with zipfile.ZipFile(jar_path) as jar:
            names = jar.namelist()

            listed = set()
            for cfg_name in (n for n in names if n.endswith(".mixins.json")):
                cfg = json.loads(jar.read(cfg_name).decode("utf-8"))
                package = cfg.get("package", "")
                for key in ("mixins", "client", "server"):
                    for entry in cfg.get(key, []) or []:
                        listed.add(f"{package}.{entry}")

            # A mixin class lives under a package containing "mixin". Inner classes are covered by
            # their outer class, and package-info carries annotations rather than injections.
            shipped = {
                name[:-len(".class")].replace("/", ".")
                for name in names
                if name.endswith(".class")
                and "$" not in name
                and "/mixin" in name
                and not name.endswith("package-info.class")
            }

            orphans = sorted(cls for cls in shipped if cls not in listed)
            print(f"  {module:18s} shipped {len(shipped):3d}  listed {len(listed):3d}  "
                  f"orphaned {len(orphans)}")
            for orphan in orphans:
                print(f"       {orphan}")
            total_orphans += len(orphans)

    print(f"\n{total_orphans} mixin classes ship without being listed in any config")
    return 1 if total_orphans else 0


if __name__ == "__main__":
    raise SystemExit(main())

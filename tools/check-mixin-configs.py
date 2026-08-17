#!/usr/bin/env python3
"""Check that every mixin named in a config actually exists in the built jar.

A mixin config lists classes by name. If one of those classes is not in the jar, mixin fails
at *prepare* time with "The specified mixin ... was not found" and the game dies before it
reaches any of the other checkers' failure modes.

This is easy to cause when porting. Stonecutter leaves version-inapplicable files fully
commented out -- the .java file exists, so a script that builds the config from the file tree
registers it, but it compiles to nothing. That is exactly how
`stfu.keybinds.MultipleBindingsPerKey.KeyBindingMixin` got listed and crashed the game.

Run after a build, before deploying:

    python tools/check-mixin-configs.py
"""

import io
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION = "0.1.0"


def main() -> int:
    modules = sys.argv[1:] or ["duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials"]
    problems = checked = 0

    for module in modules:
        jar = ROOT / module / "build/libs" / f"{module}-{VERSION}.jar"
        if not jar.is_file():
            print(f"  {module}: no jar built, skipping")
            continue

        with zipfile.ZipFile(jar) as zf:
            names = zf.namelist()
            present = {n[:-6].replace("/", ".") for n in names if n.endswith(".class")}
            configs = [n for n in names if n.endswith(".mixins.json")]

            for config in configs:
                cfg = json.loads(zf.read(config).decode("utf-8"))
                pkg = cfg.get("package", "")
                listed = list(cfg.get("mixins", [])) + list(cfg.get("client", [])) \
                    + list(cfg.get("server", []))
                for entry in listed:
                    checked += 1
                    if f"{pkg}.{entry}" not in present:
                        problems += 1
                        print(f"  {module}/{config}")
                        print(f"    '{entry}' is listed but has no class in the jar")

                # A plugin that does not exist fails the whole config the same way.
                plugin = cfg.get("plugin")
                if plugin:
                    checked += 1
                    if plugin not in present:
                        problems += 1
                        print(f"  {module}/{config}")
                        print(f"    plugin '{plugin}' has no class in the jar")

    print(f"\n{checked} mixin entries checked, {problems} missing")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())

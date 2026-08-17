#!/usr/bin/env python3
"""Check that every mixin a config names actually exists.

A mixin config listing a class that is not there is fatal: mixin aborts the whole config with
"The specified mixin ... was not found" and the mod does not load. Nothing catches this earlier --
the Java compiles, the jar builds, the build is green, and the failure only appears on the first
launch. That is exactly the gap this fills.

Hand-written configs go stale when a mixin is deleted and its entry is not; imported ones can
arrive stale, which is how this was found (four dead entries in a config copied from upstream).

Run with no arguments to check every module. Exit status is 1 if anything is missing.
"""
import json
import pathlib
import sys

import os

# Shared copy. MODULE_GLOB selects which subdirectories count as modules, so this runs
# against any project rather than only Duty. Default keeps the original behaviour.
MODULE_GLOB = os.environ.get('MODULE_GLOB', 'duty-*')

ROOT = pathlib.Path(__file__).resolve().parent.parent


def source_roots(module: pathlib.Path):
    """Every java source root in a module, including per-loader source sets."""
    return [p for p in module.glob("src/*/java") if p.is_dir()]


def check(config: pathlib.Path, module: pathlib.Path) -> list[str]:
    try:
        data = json.loads(config.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return [f"{config.relative_to(ROOT)}: not valid JSON ({e})"]

    package = data.get("package", "").replace(".", "/")
    roots = source_roots(module)
    problems = []

    for key in ("mixins", "client", "server"):
        for entry in data.get(key, []):
            relative = pathlib.Path(package) / (entry.replace(".", "/") + ".java")
            if not any((root / relative).exists() for root in roots):
                problems.append(
                    f"{config.relative_to(ROOT)}: [{key}] {entry} -- no source at {relative}"
                )
    return problems


def main() -> int:
    problems = []
    checked = 0
    for config in sorted(ROOT.glob(MODULE_GLOB + "/src/*/resources/**/*.mixins.json")):
        # Walk up to the directory holding src. Walking down from ROOT instead only works when
        # ROOT is the project itself. From a shared tools folder it stops at the project name
        # and every source lookup misses, which read as 329 missing mixins that all existed.
        module = config
        while module.parent != module and module.name != "src":
            module = module.parent
        module = module.parent
        problems.extend(check(config, module))
        checked += 1

    print(f"checked {checked} mixin config(s)")
    for problem in problems:
        print(f"  MISSING  {problem}")
    if problems:
        print(f"\n{len(problems)} mixin(s) named but not present. Mixin drops the whole config "
              f"for any one of these, so the mod would fail to load.")
        return 1
    print("  all named mixins have sources")
    return 0


if __name__ == "__main__":
    sys.exit(main())

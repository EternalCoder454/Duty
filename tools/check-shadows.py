#!/usr/bin/env python3
"""Check every @Shadow member against the real Minecraft class.

A wrong @Shadow compiles cleanly and then kills the game at mixin-apply time, because
javac never verifies that the shadowed member exists in the target. That is exactly how
`@Shadow ... neighbours` (which is spelled `neighbors` in 26.1.2) got as far as a crash
report. This finds them before the game does.

Usage:
    python tools/check-shadows.py [module ...]      # default: all three mod modules

Reports one line per @Shadow whose name does not appear in the target class. Targets that
cannot be resolved (inner classes named via `targets = {...}`, non-Minecraft classes) are
skipped and counted, not reported as failures.
"""

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVAP = Path(r"C:\Program Files\Java\jdk-25.0.4\bin\javap.exe")

MIXIN_RE = re.compile(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z_][\w.]*)\.class")
# Annotations are stripped rather than skipped by line. "@Shadow @Final private X y;" keeps
# its member on the same line as the @Shadow, so skipping the line would grab whatever field
# came next -- which produced a page of phantom findings the first time this was written.
LEADING_ANNOTATION = re.compile(r"^\s*@\w+(\([^)]*\))?\s*")
DECL_NAME = re.compile(r"(\w+)\s*[;(=]")
IMPORT_RE = re.compile(r"^import\s+((?:net\.minecraft|com\.mojang)[\w.]*)\s*;", re.M)

_members_cache: dict[str, set[str] | None] = {}


EXTENDS_RE = re.compile(r"\bextends\s+([\w.$]+)")



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

def members_of(binary_name: str, jar: Path) -> set[str] | None:
    """Every field and method name visible on a class, including inherited ones.

    Walking superclasses is not optional: mixins routinely @Shadow a protected field that
    the target inherits rather than declares. Checking only the target class reports those
    as missing, which is how `PoiManager.levelHeightAccessor` -- declared on SectionStorage
    -- first showed up as a false positive here.
    """
    if binary_name in _members_cache:
        return _members_cache[binary_name]

    names: set[str] | None = None
    current, seen = binary_name, set()
    while current and current not in seen and current != "java.lang.Object":
        seen.add(current)
        try:
            out = subprocess.run(
                [str(JAVAP), "-p", "-cp", str(jar), current],
                capture_output=True, text=True, timeout=60,
            )
        except (OSError, subprocess.SubprocessError):
            break
        if out.returncode != 0:
            break
        names = (names or set()) | set(re.findall(r"(\w+)\s*[;(]", out.stdout))
        parent = EXTENDS_RE.search(out.stdout.split("{", 1)[0])
        current = parent.group(1) if parent else None

    _members_cache[binary_name] = names
    return names


def strip_leading_annotations(fragment: str) -> str:
    """Remove any run of leading annotations, so the declaration itself is what remains."""
    while True:
        stripped = LEADING_ANNOTATION.sub("", fragment, count=1)
        if stripped == fragment:
            return fragment
        fragment = stripped


def collect_shadows(text: str) -> list[str]:
    """Names declared by @Shadow, read line by line rather than by one big regex."""
    names, lines = [], text.splitlines()
    for i, line in enumerate(lines):
        if "@Shadow" not in line:
            continue
        # The member may be on the @Shadow line itself, or on a following line. Strip any
        # leading annotations from the remainder first: "@Shadow @Final private X y;" keeps
        # its member on the same line, and skipping the line would grab the next field.
        rest = strip_leading_annotations(line.split("@Shadow", 1)[1])
        candidates = ([rest] if rest.strip() else []) + lines[i + 1: i + 5]
        for candidate in candidates:
            if not candidate.strip():
                break                       # blank line: the shadow had no member, bail out
            candidate = strip_leading_annotations(candidate)
            if not candidate.strip():
                continue                    # the line held only @Final / @Mutable and friends
            m = DECL_NAME.search(candidate)
            if m:
                names.append(m.group(1))
            break
    return names


def main() -> int:
    jar = next(ROOT.glob("duty-*/build/moddev/artifacts/minecraft-patched-*-merged.jar"), None)
    if jar is None:
        print("No patched Minecraft jar found. Run a Gradle build first.")
        return 1
    print(f"checking against {jar.name}\n")

    modules = sys.argv[1:] or ["duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials"]
    problems = skipped = checked = 0

    for module in modules:
        for src in sorted(source_files(ROOT / module)):
            text = src.read_text(encoding="utf-8", errors="replace")
            shadows = collect_shadows(text)
            if not shadows:
                continue
            target = MIXIN_RE.search(text)
            if not target:
                skipped += len(shadows)   # targets = {"..."} form, or a non-class mixin
                continue
            simple = target.group(1)
            binary = next(
                (i for i in IMPORT_RE.findall(text) if i.rsplit(".", 1)[-1] == simple), None
            )
            if binary is None:
                skipped += len(shadows)
                continue
            declared = members_of(binary, jar)
            if declared is None:
                skipped += len(shadows)
                continue
            for name in shadows:
                checked += 1
                if name not in declared:
                    problems += 1
                    print(f"  {src.relative_to(ROOT)}")
                    print(f"    @Shadow '{name}' is not declared on {binary}")

    print(f"\n{checked} shadows checked, {problems} unresolved, {skipped} skipped")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())

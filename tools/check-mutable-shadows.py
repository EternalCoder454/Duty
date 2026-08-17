#!/usr/bin/env python3
"""Check that every @Shadow field a mixin writes to is writable.

The JVM only lets a final field be assigned from its declaring class's own <init>. A mixin
handler is a separate method, so a mixin that assigns to a shadowed *final* field compiles
cleanly, applies cleanly, and then throws

    java.lang.IllegalAccessError: Update to non-static final field <X> attempted from a
    different method (handler$...) than the initializer method <init>

the first time that code path runs. For ScalableLux's LevelLightEngineMixin that path is world
creation, so the failure arrived as a crash report after a green build and six passing checkers.

Mixin's answer is @Final @Mutable on the shadow, which strips ACC_FINAL from the target field.
Fabric mods rarely say it, because loom's access widener drops final across the whole game --
which is exactly why ported Fabric mixins are where this bites.

Usage:
    python tools/check-mutable-shadows.py [module ...]

Reports one line per shadowed field that is final in the target and assigned in the mixin
without @Mutable.
"""

import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _minecraft_jar import patched_minecraft_jar

ROOT = Path(__file__).resolve().parent.parent
JAVAP = Path(r"C:\Program Files\Java\jdk-25.0.4\bin\javap.exe")

MODULES = ["duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials"]

MIXIN_RE = re.compile(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z_][\w.]*)\.class")
IMPORT_RE = re.compile(r"^import\s+((?:net\.minecraft|com\.mojang)[\w.]*)\s*;", re.M)

# A field declaration preceded by its whole annotation block, in whatever order they were written.
#
# Matching from @Shadow forwards is the obvious thing and is wrong: `@Mutable @Shadow @Final` puts
# the annotation that matters *before* the anchor, so a forwards-only match reports the one file in
# this tree that already got it right. The block is captured whole and the two annotations are
# looked for inside it.
#
# The type is left deliberately loose so generics and arrays need no enumerating; the field name is
# the last identifier before the semicolon.
SHADOW_FIELD = re.compile(
    r"(?P<annos>(?:@\w+(?:\([^)]*\))?\s*)+)"
    r"(?P<decl>(?:public|protected|private|static|transient|volatile|final)\s+"
    r"[\w.$<>?,\[\]\s]+?\s+(?P<name>\w+)\s*;)"
)

_final_fields_cache: dict[str, set[str] | None] = {}


def final_fields_of(binary_name: str, jar: Path) -> set[str] | None:
    """{field names declared final on the target}, or None if the class cannot be read."""
    if binary_name in _final_fields_cache:
        return _final_fields_cache[binary_name]
    try:
        out = subprocess.run(
            [str(JAVAP), "-p", "-classpath", str(jar), binary_name],
            capture_output=True, text=True, timeout=60,
        )
    except Exception:
        _final_fields_cache[binary_name] = None
        return None
    if out.returncode != 0:
        _final_fields_cache[binary_name] = None
        return None

    fields = set()
    for line in out.stdout.splitlines():
        line = line.strip()
        # A field line ends in ';' and has no parentheses; methods have '('.
        if not line.endswith(";") or "(" in line:
            continue
        if " final " not in f" {line} ":
            continue
        name = line[:-1].split()[-1]
        fields.add(name)
    _final_fields_cache[binary_name] = fields
    return fields


def resolve_target(text: str) -> str | None:
    m = MIXIN_RE.search(text)
    if not m:
        return None
    simple = m.group(1)
    if "." in simple:
        return simple
    for imported in IMPORT_RE.findall(text):
        if imported.rsplit(".", 1)[-1] == simple:
            return imported
    return None


def main() -> int:
    jar = patched_minecraft_jar()
    print(f"checking against {jar.name}\n")

    modules = sys.argv[1:] or MODULES
    findings = []
    checked = 0
    skipped = 0

    for module in modules:
        base = ROOT / module / "src"
        if not base.is_dir():
            continue
        for path in base.rglob("*.java"):
            text = path.read_text(encoding="utf-8", errors="ignore")
            if "@Shadow" not in text:
                continue
            target = resolve_target(text)
            if target is None:
                continue
            finals = final_fields_of(target, jar)
            if finals is None:
                skipped += 1
                continue

            for m in SHADOW_FIELD.finditer(text):
                annos = m.group("annos") or ""
                if "@Shadow" not in annos:
                    continue
                name = m.group("name")
                checked += 1
                # Does the mixin assign to it anywhere? `this.x =` or a bare `x =` statement.
                assigned = re.search(rf"\bthis\.{re.escape(name)}\s*=(?!=)", text) or \
                           re.search(rf"^\s*{re.escape(name)}\s*=(?!=)", text, re.M)
                if not assigned:
                    continue
                if name not in finals:
                    continue
                if "@Mutable" in annos:
                    continue
                line = text[:m.start()].count("\n") + 1
                findings.append(
                    f"  {path.relative_to(ROOT).as_posix()}:{line}\n"
                    f"      writes {target.rsplit('.', 1)[-1]}.{name}, which is final, "
                    f"without @Mutable"
                )

    for f in findings:
        print(f)
    print(f"\n{checked} shadowed fields checked, {len(findings)} written-but-final, "
          f"{skipped} targets skipped")
    return 1 if findings else 0


if __name__ == "__main__":
    raise SystemExit(main())

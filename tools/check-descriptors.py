#!/usr/bin/env python3
"""Check every class named inside a mixin annotation string against the real jar.

Mixin annotations carry hand-written descriptors:

    @Inject(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunkTicks;...)V")
    @Redirect(at = @At(target = "Lnet/minecraft/.../Foo;bar()V"))

Those strings are opaque to javac. If a class moved package between versions, the file
still compiles -- the *import* is fine -- and the mixin then fails at apply time with
"could not find any targets matching". That is exactly how `LevelChunkTicks`, which lives
in `world.ticks` and not `world.level.chunk`, survived a clean build and crashed the game.

This extracts every `Lsome/internal/Name;` from annotation strings and asks the jar whether
that class exists. It does not verify full signatures -- a wrong package is the failure mode
that actually happens, and a class-existence check catches it cheaply.

Usage:
    python tools/check-descriptors.py [module ...]      # default: all three mod modules
"""

import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Only look inside string literals that sit in a mixin annotation, so ordinary code and
# comments mentioning a class name are not dragged in.
ANNOTATION_STRING = re.compile(
    r'@(?:Inject|Redirect|WrapOperation|WrapMethod|ModifyArg|ModifyArgs|ModifyVariable|'
    r'ModifyConstant|ModifyReturnValue|At|Accessor|Invoker|Mixin)\b[^;{]*?'
    r'"((?:[^"\\]|\\.)*)"',
    re.S,
)
CLASS_REF = re.compile(r"L([a-z][\w/$]*/[\w$]+);")
MIXIN_TARGET = re.compile(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z_][\w.]*)\.class")
IMPORT_RE = re.compile(r"^import\s+((?:net\.minecraft|com\.mojang)[\w.]*)\s*;", re.M)
# Stonecutter puts inline comments between "method =" and the string literal, e.g.
#   method = /*? > 1.21.11 {*/"tooltip"/*?}else{*//*"renderTooltip"*//*?}*/
# A plain \s* skips such an entry silently, which is how stfu.Tooltips reached the game
# targeting a method that does not exist in 26.1.2 and took Liteminer down with it.
METHOD_NAME = re.compile(r'method\s*=\s*(?:/\*.*?\*/\s*)*"([^"]+)"', re.S)
# An injector with require = 0 tolerates a missing target by design. Reporting those makes
# a clean run impossible and trains you to ignore the output.
TOLERANT = re.compile(r"require\s*=\s*0")
EXTENDS_RE = re.compile(r"extends\s+([\w.$]+)")

_members_cache: dict[str, set[str] | None] = {}


def members_of(binary_name: str, jar: Path) -> set[str] | None:
    """Field and method names visible on a class, including inherited ones."""
    if binary_name in _members_cache:
        return _members_cache[binary_name]
    javap = Path(r"C:\Program Files\Java\jdk-25.0.4\bin\javap.exe")
    names: set[str] | None = None
    current, seen = binary_name, set()
    while current and current not in seen and current != "java.lang.Object":
        seen.add(current)
        try:
            out = subprocess.run(
                [str(javap), "-p", "-cp", str(jar), current],
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


def main() -> int:
    jar = next(ROOT.glob("duty-*/build/moddev/artifacts/minecraft-patched-*-merged.jar"), None)
    if jar is None:
        print("No patched Minecraft jar found. Run a Gradle build first.")
        return 1
    print(f"checking against {jar.name}\n")

    with zipfile.ZipFile(jar) as zf:
        present = {n[:-6] for n in zf.namelist() if n.endswith(".class")}

    modules = sys.argv[1:] or ["duty-memory", "duty-client", "duty-fixerupper", "duty-server"]
    problems = checked = 0
    seen_missing: dict[str, list[str]] = {}
    bad_methods: list[tuple[str, str, str]] = []

    for module in modules:
        for src in sorted((ROOT / module / "src/main/java").rglob("*.java")):
            text = src.read_text(encoding="utf-8", errors="replace")
            refs: set[str] = set()
            for literal in ANNOTATION_STRING.findall(text):
                refs.update(CLASS_REF.findall(literal))
            # Method names named by @Inject/@Redirect/etc must exist on the target class.
            # A renamed method compiles fine and fails at apply time the same way a moved
            # class does -- "could not find any targets matching".
            target = MIXIN_TARGET.search(text)
            if target:
                simple = target.group(1)
                binary = next(
                    (i for i in IMPORT_RE.findall(text) if i.rsplit(".", 1)[-1] == simple), None
                )
                if binary:
                    declared = members_of(binary, jar)
                    if declared:
                        for m in METHOD_NAME.finditer(text):
                            # Look at the annotation this method= belongs to: back to the
                            # preceding '@' and forward to the end of its argument list.
                            start = text.rfind("@", 0, m.start())
                            window = text[start if start != -1 else m.start(): m.end() + 400]
                            if TOLERANT.search(window.split("\n\n", 1)[0]):
                                continue
                            name = m.group(1)
                            base = name.split("(", 1)[0]
                            # Mixin accepts an explicit owner prefix: "Lnet/minecraft/Foo;bar".
                            if ";" in base:
                                base = base.rsplit(";", 1)[1]
                            # ...and glob patterns, which cannot be matched by name.
                            if "*" in base or not base:
                                continue
                            if base in ("<init>", "<clinit>") or base.startswith("lambda$"):
                                continue
                            checked += 1
                            if base not in declared:
                                problems += 1
                                bad_methods.append(
                                    (str(src.relative_to(ROOT)), base, binary)
                                )

            for ref in sorted(refs):
                # net/minecraft only. com/mojang/* (DataFixerUpper, serialization) lives in
                # separate artifacts that are not inside the patched Minecraft jar, so
                # checking them here reports absences that are not real.
                if not ref.startswith("net/minecraft/"):
                    continue
                checked += 1
                if ref not in present:
                    problems += 1
                    seen_missing.setdefault(ref, []).append(
                        str(src.relative_to(ROOT))
                    )

    for path, name, owner in bad_methods:
        print(f"  {path}")
        print(f"    target method '{name}' is not declared on {owner}")

    for ref, files in sorted(seen_missing.items()):
        print(f"  {ref} does not exist in the jar")
        for f in files:
            print(f"    {f}")

    print(f"\n{checked} class references checked, {problems} missing")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())

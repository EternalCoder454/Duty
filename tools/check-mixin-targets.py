#!/usr/bin/env python3
"""Check that every method a mixin injects into exists, with that exact signature.

check-descriptors.py answers "does this class exist". This answers the harder question:
does the *method* exist, with the descriptor the annotation claims. That is the failure
mode when code written for one Minecraft version is moved to another -- the class is still
there, the method is still called the same thing, and one parameter has changed:

    Critical injection failure: @WrapMethod annotation on knockback could not find any
    targets matching 'knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V'

Nothing before launch notices. The annotation is a string, so javac has no opinion; the
mixin config is well-formed; the jar builds. It fails at apply time, and a critical
injection failure takes the whole mod down with it.

Method specs are read from `method = "..."` in @Inject/@Redirect/@ModifyArg/@WrapOperation/
@WrapMethod/@ModifyVariable and friends. A spec carrying a descriptor is checked exactly.
A bare name is checked by name only, which is all the information the annotation gives.

Targets outside the Minecraft jar (another mod's classes) are reported separately rather
than as failures -- they cannot be checked here and are usually deliberate.

WHAT THIS IS AND IS NOT
-----------------------
This is a pre-flight check for freshly ported code, not a gate on the whole repo. It
resolves against the NeoForm *vanilla* recompile jar, while the game runs the NeoForge
*patched* one, so anything NeoForge adds by patch -- Minecraft.getDeltaFrameTime,
LocalPlayer.handleNetherPortalClient and friends -- is reported missing while being
perfectly real. Running it across the mature modules currently produces nineteen such
reports, every one of which is a mixin that has been applying happily for months.

So: a report here is a question, not a verdict. Treat it as one on code that already runs.
Treat it seriously on code that has just been moved between Minecraft versions, which is
what it was written for -- it found Async's knockback(DDDLDamageSource;FZ)V, a 26.2
signature that does not exist in 26.1.2 and that took the entire mod down at launch.

Usage:
    python tools/check-mixin-targets.py <module> [module ...]
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

JAVAP = next(
    (p for p in pathlib.Path("C:/Program Files/Java").glob("jdk-*/bin/javap.exe")),
    None,
)

MIXIN_TARGET = re.compile(r"@Mixin\s*\(([^)]*)\)", re.S)
CLASS_LITERAL = re.compile(r"([A-Za-z_][\w.]*)\.class")
# method = "a"  |  method = {"a", "b"}
METHOD_SPEC = re.compile(r"method\s*=\s*(\{[^}]*\}|\"[^\"]*\")", re.S)
STRING = re.compile(r"\"([^\"]*)\"")
# @Accessor("name") / @Invoker("name") -- a member on the @Mixin target itself.
ACCESSOR_SPEC = re.compile(r'@(?:Accessor|Invoker)\s*\(\s*"([A-Za-z_$][A-Za-z0-9_$]*)"')


def find_minecraft_jar() -> pathlib.Path | None:
    cache = pathlib.Path.home() / ".gradle/caches/neoformruntime/intermediate_results"
    best = None
    for jar in cache.glob("recompile_*_output.jar"):
        if best is None or jar.stat().st_size > best.stat().st_size:
            best = jar
    return best


_method_cache: dict[str, set[str] | None] = {}


def methods_of(binary_name: str, jar: pathlib.Path) -> set[str] | None:
    """{"name(descriptor)"} for a class, or None if the jar does not have it."""
    if binary_name in _method_cache:
        return _method_cache[binary_name]

    result = subprocess.run(
        [str(JAVAP), "-p", "-s", "-cp", str(jar), binary_name],
        capture_output=True, text=True,
    )
    if result.returncode != 0 or "Error:" in result.stdout:
        _method_cache[binary_name] = None
        return None

    # javap prints a constructor under the class's own simple name, never as <init>, so
    # without this every `method = "<init>"` looks missing. That was ten false positives
    # out of fourteen the first time this ran.
    # Both spellings: javap writes an inner class's constructor as Outer$Inner(), while a
    # top-level one is just Name(). Comparing against only the innermost segment missed the
    # first kind and reported a constructor that plainly exists as missing.
    tail = binary_name.split(".")[-1]
    constructor_names = {tail, tail.split("$")[-1]}

    names: set[str] = set()
    pending: str | None = None
    for line in result.stdout.splitlines():
        stripped = line.strip()
        if stripped.startswith("descriptor:"):
            if pending is not None:
                names.add(f"{pending}{stripped.split(':', 1)[1].strip()}")
                pending = None
            continue
        match = re.search(r"([\w$<>]+)\s*\(", stripped)
        if match:
            pending = "<init>" if match.group(1) in constructor_names else match.group(1)
        else:
            pending = None
            # A field: no parentheses, ends in a semicolon. Collected because @Accessor names
            # fields far more often than methods, and without these every field accessor read
            # as missing.
            field = re.match(r"^[\w.$<>\[\], ]*?([\w$]+);$", stripped)
            if field:
                names.add(field.group(1))
    _method_cache[binary_name] = names
    return names


def resolve(simple: str, imports: dict[str, str], package: str) -> str | None:
    """Turn `LivingEntity` into `net.minecraft.world.entity.LivingEntity` using the imports."""
    head = simple.split(".")[0]
    if head in imports:
        # Inner class written as Outer.Inner -> Outer$Inner
        rest = simple.split(".")[1:]
        return "$".join([imports[head]] + rest) if rest else imports[head]
    if "." in simple:
        return simple
    return f"{package}.{simple}" if package else None


def check_file(path: pathlib.Path, jar: pathlib.Path):
    text = path.read_text(encoding="utf-8", errors="replace")

    package_match = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
    package = package_match.group(1) if package_match else ""
    imports = {}
    for imp in re.findall(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M):
        imports[imp.split(".")[-1]] = imp

    target_match = MIXIN_TARGET.search(text)
    if not target_match:
        return [], []

    targets = []
    for simple in CLASS_LITERAL.findall(target_match.group(1)):
        resolved = resolve(simple, imports, package)
        if resolved:
            targets.append(resolved)
    if not targets:
        return [], []

    problems, unknown = [], []
    known = {}
    for target in targets:
        found = methods_of(target, jar)
        if found is None:
            unknown.append(f"{path.name}: target {target} is not in the Minecraft jar")
        else:
            known[target] = found
    if not known:
        return problems, unknown

    # @Accessor / @Invoker name a member on the @Mixin target directly. They were not checked
    # here at first, and duty_worldgen shipped @Accessor("storage") against a field Mojang calls
    # data: it compiled, both other checks passed, and the game died at load with "No candidates
    # were found matching storage". Exactly the failure this file exists to prevent, missed
    # because the name lives somewhere the method= scan never looked.
    for accessor_name in ACCESSOR_SPEC.findall(text):
        ok = False
        for found in known.values():
            ok = ok or any(m.split("(")[0] == accessor_name for m in found)
        if not ok:
            problems.append(
                f"{path.name}: @Accessor/@Invoker '{accessor_name}' not found in "
                + ", ".join(known)
            )

    for spec_group in METHOD_SPEC.findall(text):
        for spec in STRING.findall(spec_group):
            if not spec or spec.startswith("@"):
                continue

            # A static initialiser is real but javap never prints one, so every <clinit>
            # would otherwise be reported missing.
            if spec.startswith("<clinit>"):
                continue

            # Mixin allows a fully-qualified spec, Lowner/Name;method(descriptor)result.
            # The owner is already known from @Mixin, so drop it and check the rest.
            owner = re.match(r"^L[\w/$]+;(.*)$", spec)
            if owner:
                spec = owner.group(1)
                if not spec:
                    continue

            # Specs built by concatenating Java string constants reach here in pieces. A
            # descriptor that never closes its parameter list is one of those, and guessing
            # at the missing half would invent failures rather than find them.
            if "(" in spec and not re.search(r"\)\[?[BCDFIJSZVL]", spec):
                continue

            has_descriptor = "(" in spec
            name = spec.split("(")[0]
            # Mixin accepts a trailing * as a wildcard on the name.
            wildcard = name.endswith("*")
            stem = name.rstrip("*")
            ok = False
            for found in known.values():
                if has_descriptor and not wildcard:
                    ok = ok or spec in found
                elif wildcard:
                    ok = ok or any(m.split("(")[0].startswith(stem) for m in found)
                else:
                    ok = ok or any(m.split("(")[0] == name for m in found)
            if not ok:
                where = ", ".join(known)
                problems.append(f"{path.name}: '{spec}' not found in {where}")
    return problems, unknown


def main() -> int:
    if JAVAP is None:
        print("javap not found under C:/Program Files/Java")
        return 2
    jar = find_minecraft_jar()
    if jar is None:
        print("no recompiled Minecraft jar in the NeoForm cache")
        return 2
    print(f"checking against {jar.name}")

    modules = sys.argv[1:]
    if not modules:
        print("usage: check-mixin-targets.py <module> [module ...]")
        return 2

    all_problems, all_unknown, count = [], [], 0
    for module in modules:
        for java in sorted((ROOT / module).glob("src/*/java/**/*.java")):
            if "mixin" not in str(java).lower():
                continue
            count += 1
            problems, unknown = check_file(java, jar)
            all_problems.extend(problems)
            all_unknown.extend(unknown)

    print(f"checked {count} mixin file(s)")
    for u in all_unknown:
        print(f"  (skipped) {u}")
    for p in all_problems:
        print(f"  MISMATCH  {p}")
    if all_problems:
        print(f"\n{len(all_problems)} injection target(s) do not exist. Each is a critical "
              f"injection failure that takes the whole mod down at launch.")
        return 1
    print("  every injection target exists")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""Verify every place a Duty class is named as a *string* still resolves.

The compiler checks code. It does not check a `META-INF/services` filename, the class name inside
that file, a `Class.forName` literal, or a mixin package root held as a constant. All of those name
classes in text, so a package rename leaves them pointing at nothing and the build stays green.

This is not hypothetical. Renaming duty-client's `ifast` package to `batching` left both the
service filename and its contents on the old name, and NeoForge refused to build the module
descriptor at startup:

    InvalidModuleDescriptorException: Service provider file
    net.dutymod.client.ifast.service.PlatformService contains service that is not in this Jar file

That is a hard crash before any mod loads, from a file no checker read.

Run after a build; reads the built jars rather than the source tree, so it sees what actually ships.
"""
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _minecraft_jar import patched_minecraft_jar

ROOT = Path(__file__).resolve().parent.parent
MODULES = sys.argv[1:] or [
    "duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials",
]

# Only Duty's own names are checked. A missing third-party class is usually an optional-mod hook
# guarded by a try/catch, which is legitimate.
OWN = "net.dutymod."

# Names that are correctly absent from the jar, with the reason. Anything not listed here and not
# present is a real dangling reference.
EXPECTED_ABSENT = {
    # duty-memory defines classes into this package at runtime; nothing ships in it.
    "net.dutymod.memory.generated":
        "package is generated at runtime by the enum transformer",
    # FixerUpperMixinPlugin sanitises a mixin name before this prefix test, and sanitise strips the
    # platform segment -- (neoforge|fabric|common). so common.mixin becomes mixin. The prefix is
    # therefore correct even though no class ships under it.
    "net.dutymod.fixerupper.mixin":
        "matched only after sanitize() strips the platform segment",
}



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

def classes_in(jar: zipfile.ZipFile) -> set[str]:
    return {
        name[:-len(".class")].replace("/", ".")
        for name in jar.namelist()
        if name.endswith(".class")
    }


def packages_in(names: set[str]) -> set[str]:
    return {name.rsplit(".", 1)[0] for name in names}


def minecraft_classes() -> set[str]:
    """Every class in the patched Minecraft jar, for checking access transformer targets."""
    jar = patched_minecraft_jar()
    if jar is None:
        return set()
    with zipfile.ZipFile(jar) as zf:
        return {
            name[:-len(".class")].replace("/", ".")
            for name in zf.namelist() if name.endswith(".class")
        }


MINECRAFT_CLASSES: set[str] = set()


def main() -> int:
    global MINECRAFT_CLASSES
    MINECRAFT_CLASSES = minecraft_classes()
    if not MINECRAFT_CLASSES:
        print("  no patched Minecraft jar found; access transformers not checked")
    problems: list[str] = []
    checked = 0

    for module in MODULES:
        jars = sorted((ROOT / module / "build" / "libs").glob(f"{module}-*.jar"))
        jars = [j for j in jars if "sources" not in j.name]
        if not jars:
            print(f"  {module}: no jar built, skipped")
            continue

        with zipfile.ZipFile(jars[0]) as jar:
            present = classes_in(jar)
            packages = packages_in(present)

            # 1. Service files: the filename is the interface, each line is an implementation.
            for entry in jar.namelist():
                if not entry.startswith("META-INF/services/") or entry.endswith("/"):
                    continue
                service = entry[len("META-INF/services/"):]
                if service.startswith(OWN):
                    checked += 1
                    if service not in present:
                        problems.append(
                            f"{module}: service file names {service}, which is not in the jar")
                for line in jar.read(entry).decode("utf-8").splitlines():
                    impl = line.strip()
                    if not impl or impl.startswith("#") or not impl.startswith(OWN):
                        continue
                    checked += 1
                    if impl not in present:
                        problems.append(
                            f"{module}: service {service} lists {impl}, which is not in the jar")

        # 2. Access transformer entries. These name Minecraft classes and descriptors as text,
        # so a version port leaves them pointing at classes that no longer exist -- and the AT is
        # applied at load time, long after any compiler could have complained. Porting to 1.21.1
        # left six entries naming net/minecraft/resources/Identifier, which 1.21.1 calls
        # ResourceLocation. Verified against the Minecraft jar rather than the module's own.
        at_path = ROOT / module / "src/main/resources/META-INF/accesstransformer.cfg"
        if at_path.is_file() and MINECRAFT_CLASSES:
            for lineno, line in enumerate(at_path.read_text(encoding="utf-8").splitlines(), 1):
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                parts = line.split()
                if len(parts) < 2:
                    continue
                named = [parts[1]] + re.findall(r"L([\w/$]+);", line)
                for raw in named:
                    binary = raw.replace("/", ".")
                    if not binary.startswith("net.minecraft"):
                        continue
                    checked += 1
                    if binary not in MINECRAFT_CLASSES:
                        problems.append(
                            f"{module}: accesstransformer.cfg:{lineno} names {binary}, "
                            f"which is not in the Minecraft jar")

        # 3. String literals in source that name a Duty class or package.
        # Both source sets: loader-specific code moved out of src/main and must still be checked.
        for src in source_files(ROOT / module):
            text = src.read_text(encoding="utf-8", errors="ignore")
            for literal in re.findall(r'"(net\.dutymod\.[A-Za-z0-9_.]+)"', text):
                # A trailing dot, or a name whose last segment is lowercase, is a package root
                # used as a prefix rather than a class.
                bare = literal.rstrip(".")
                is_package = literal.endswith(".") or bare.rsplit(".", 1)[-1][:1].islower()
                checked += 1
                if bare in EXPECTED_ABSENT:
                    continue
                if is_package:
                    if not any(p == bare or p.startswith(bare + ".") for p in packages):
                        problems.append(
                            f"{module}: {src.name} names package {bare}, which has no classes")
                elif bare not in present:
                    problems.append(
                        f"{module}: {src.name} names class {bare}, which is not in the jar")

    print()
    for problem in problems:
        print(f"  {problem}")
    print(f"\n{checked} class-naming strings checked, {len(problems)} unresolved")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())

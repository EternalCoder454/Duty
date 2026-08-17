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


def classes_in(jar: zipfile.ZipFile) -> set[str]:
    return {
        name[:-len(".class")].replace("/", ".")
        for name in jar.namelist()
        if name.endswith(".class")
    }


def packages_in(names: set[str]) -> set[str]:
    return {name.rsplit(".", 1)[0] for name in names}


def main() -> int:
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

        # 2. String literals in source that name a Duty class or package.
        for src in (ROOT / module / "src/main/java").rglob("*.java"):
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

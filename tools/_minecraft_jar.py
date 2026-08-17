"""Locating the patched Minecraft jar for the branch you are actually on.

Duty carries one target per branch. Build directories are gitignored and survive a checkout, so
after switching from `main` to `neoforge-1.21.1` the 26.1.2 jar is still sitting in `build/` --
and a checker that globs for "the" Minecraft jar will find it and compare 1.21.1 sources against
26.1.2 classes.

That is worse than not checking. `check-shadows` and `check-descriptors` exist to answer "does this
member exist in Minecraft", and against the wrong Minecraft they answer confidently and wrongly in
both directions: real problems pass, and correct code is reported broken. This module makes the
lookup demand the version `gradle.properties` names, and say so plainly when it cannot have it.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def expected_minecraft_version() -> str:
    """{@return the Minecraft version this branch builds, from gradle.properties}"""
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("minecraft_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit("gradle.properties has no minecraft_version")


def patched_minecraft_jar():
    """The patched Minecraft jar matching this branch, or None with the reason printed."""
    wanted = expected_minecraft_version()
    candidates = sorted(ROOT.glob("duty-*/build/moddev/artifacts/minecraft-patched-*-merged.jar"))

    for jar in candidates:
        if jar.name.startswith(f"minecraft-patched-{wanted}"):
            return jar

    if candidates:
        others = sorted({c.name for c in candidates})
        print(f"  No Minecraft {wanted} jar, but these are present from another branch:")
        for name in others:
            print(f"    {name}")
        print("  Build this branch first. Checking against the wrong Minecraft is worse than")
        print("  not checking: it passes real problems and fails correct code.")
    else:
        print(f"  No patched Minecraft jar for {wanted}. Run a Gradle build first.")
    return None

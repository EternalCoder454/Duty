#!/usr/bin/env python3
"""Find "never happened yet" sentinels that overflow when subtracted from nanoTime().

The pattern:

    private long lastReport = Long.MIN_VALUE;
    ...
    if (now - lastReport < INTERVAL) return;      // now = System.nanoTime()

reads as "report the first time, then at most once per interval". It does the opposite.
System.nanoTime() is a large positive number on this platform, and

    positive - Long.MIN_VALUE

overflows to a negative value. Negative is below any positive interval, so the branch
returns every single time and the code after it is unreachable for the life of the process.

This cost real diagnostics: both the slow-lighting-chunk warning and the slow-culling-pass
warning were written specifically to explain spikes that a report had flagged, and neither
had ever produced a line while those spikes were happening. Nothing catches it -- it
compiles, it runs, it simply never fires, and silence from a warning reads exactly like
"nothing was wrong".

The fix is a separate boolean for "has this happened yet", so the subtraction only ever
involves two real nanoTime readings.

Exit status is 1 if any suspect field is found.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# A long field initialised to a saturating sentinel, whose name suggests a timestamp.
SENTINEL = re.compile(
    r"^\s*(?:private|protected|public)?\s*(?:static\s+)?(?:volatile\s+)?long\s+"
    r"(\w*(?:[Ll]ast|[Pp]rev|[Ss]ince|[Ss]tart|[Rr]eport|[Tt]ime|[Nn]anos)\w*)\s*=\s*"
    r"(Long\.MIN_VALUE|Long\.MAX_VALUE)\s*;",
    re.M,
)


def main() -> int:
    modules = sys.argv[1:] or [p.name for p in ROOT.glob("duty-*") if p.is_dir()]
    findings = []
    scanned = 0

    for module in modules:
        for java in sorted((ROOT / module).glob("src/*/java/**/*.java")):
            text = java.read_text(encoding="utf-8", errors="replace")
            if "nanoTime" not in text:
                continue
            scanned += 1
            for match in SENTINEL.finditer(text):
                field, sentinel = match.group(1), match.group(2)
                # Only a problem if that field is actually subtracted from something.
                if re.search(rf"-\s*{re.escape(field)}\b", text) or \
                        re.search(rf"\b{re.escape(field)}\s*-", text):
                    line = text[: match.start()].count("\n") + 1
                    findings.append(
                        f"{java.relative_to(ROOT)}:{line}: {field} = {sentinel}, "
                        f"then used in a subtraction"
                    )

    print(f"scanned {scanned} file(s) that use nanoTime()")
    for f in findings:
        print(f"  OVERFLOW  {f}")
    if findings:
        print(f"\n{len(findings)} sentinel(s) overflow when subtracted from nanoTime(). The guard "
              f"they feed is always taken, so whatever follows it never runs. Use a separate "
              f"boolean for 'has this happened yet'.")
        return 1
    print("  no overflowing sentinels")
    return 0


if __name__ == "__main__":
    sys.exit(main())

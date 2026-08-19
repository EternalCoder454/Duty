"""The writing rules, and the check for them. This file is the only statement of them.

They used to be written out in nine documents as well as here, which is why removing the semicolon
ban meant editing nine files and why several of them disagreed with each other in between. A rule
restated is a rule that drifts. Every document now points at this file and none of them describes a
rule.

Read them without opening the code:

    python check-writing-rules.py --rules

Check documents:

    python check-writing-rules.py README.md DEV.md
    python check-writing-rules.py $(git ls-files '*.md')

Copies live in each project that has to run this without reaching the shared tools directory, which
is not version controlled. They are byte identical. If a rule changes, change it here and copy.
"""
import pathlib
import sys

# A violating line can hold characters the console cannot encode, an arrow or a box drawing rune,
# and printing one used to end the run with a UnicodeEncodeError that named the checker rather than
# the document. Reporting a violation must never be able to fail.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(errors="replace")

# The rules. Adding an entry here is what adding a rule means.
BANNED = {
    "em dash": "—",
    "en dash": "–",
    "double hyphen": "--",
}

# What to do instead. Printed by --rules, read by nothing.
INSTEAD = {
    "em dash": "a full stop, or a comma, or restructure",
    "en dash": "a full stop, or a comma, or restructure",
    "double hyphen": "a full stop, or a comma",
}

# Not machine checkable, and there is nowhere better for it to live than beside what is.
STYLE = """Semicolons are allowed, sparingly. They used to be banned and are not any more.

Style target: dense. Say the thing once, in the fewest words that stay exact. Tables over
paragraphs. No warmup sentences."""


def print_rules():
    print("Writing rules. This file is the source, every document points here.")
    print("Applies to every README, every DEV.md, every PORT-STATUS.md, and to chat.\n")
    for label, token in BANNED.items():
        print("  banned  %-22s use %s" % ("%s  %s" % (label, token), INSTEAD[label]))
    print("\n  exempt  fenced code blocks, inline code spans, table separator rows")
    print("          command flags need the double hyphen and cannot be reworded\n")
    print(STYLE)


def check(targets):
    failures = 0
    for target in targets:
        path = pathlib.Path(target)
        in_code = False
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            if line.lstrip().startswith("```"):
                in_code = not in_code
                continue
            if in_code:
                continue
            # A markdown table separator row is required syntax, not prose.
            if set(line.strip()) <= set("|-: "):
                continue
            stripped = line
            # inline code spans are code too
            while "`" in stripped:
                first = stripped.find("`")
                second = stripped.find("`", first + 1)
                if second == -1:
                    break
                stripped = stripped[:first] + " " * (second - first + 1) + stripped[second + 1:]
            for label, token in BANNED.items():
                if token in stripped:
                    print(f"{path.name}:{number}: {label}: {line.strip()[:90]}")
                    failures += 1

    print(f"{failures} violation(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if a != "--rules"]
    if "--rules" in sys.argv[1:] or not args:
        print_rules()
        sys.exit(0)
    sys.exit(check(args))

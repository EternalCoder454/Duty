#!/usr/bin/env python3
"""Translate Java source written against Yarn mappings into Mojang mappings.

Most Fabric-origin performance mods are written in Yarn names. Duty is Mojang throughout, so
absorbing any of them means renaming every Minecraft class, method and field in the source.
Doing that by hand is both enormous and quietly dangerous: a wrong name inside a mixin
annotation is a string, so it compiles perfectly and fails at load with "could not find any
targets matching". This does it from the real mapping data instead.

WHERE THE MAPPINGS COME FROM
----------------------------
Fabric Loom, when it builds a Yarn-authored mod against NeoForge, produces a three-namespace
tiny v2 file:

    tiny 2 0 official intermediary named
    c  net/minecraft/world/level/levelgen/NoiseChunk  net/minecraft/class_6568  net/minecraft/world/gen/chunk/ChunkNoiseSampler
       m  (...)V  getOrCreateNoiseChunk  method_38255  getOrCreateChunkNoiseSampler

For a NeoForge target, "official" is the Mojang name, not an obfuscated one. So Yarn to Mojang
is a column lookup in one file -- no intermediary hop to chain by hand. Find it under
~/.gradle/caches/fabric-loom/<mc>/<neoforge>/loom.mappings.*/mappings.tiny after any Loom build.

WHAT IT WILL AND WILL NOT DO
----------------------------
Classes are renamed by fully-qualified name, which is exact. Methods and fields are renamed by
identifier, which is not: Java source does not say which type a call belongs to without a full
type-resolver. So a member is only renamed when its Yarn name maps to exactly one Mojang name
across the entire mapping set. Anything ambiguous is left alone and listed, because renaming it
on a guess is worse than leaving it for javac to complain about.

That is the point of the design: javac is the oracle. This gets the bulk right, then compiling
against the real jar finds whatever it could not resolve. A name it declined to touch shows up
as a compile error, which is exactly where you want the remaining work to appear.

Usage:
    python tools/yarn2mojang.py <mappings.tiny> <source-dir> [--dry-run]
"""
import collections
import pathlib
import re
import sys


def load(tiny_path: pathlib.Path):
    """{yarn -> mojang} for classes (fq, slashed), and for method/field identifiers."""
    classes: dict[str, str] = {}
    methods: dict[str, set[str]] = collections.defaultdict(set)
    fields: dict[str, set[str]] = collections.defaultdict(set)
    # yarn member name -> {(mojang name, mojang owner)}. What lets an ambiguous name be resolved
    # by asking which candidate is declared on a class this file actually uses.
    owned: dict[str, set[tuple[str, str]]] = collections.defaultdict(set)
    # intermediary -> mojang, for the method_NNNNN names that survive in mixin
    # annotations because Loom would have remapped them at build time.
    intermediary: dict[str, str] = {}

    with tiny_path.open(encoding="utf-8", errors="replace") as handle:
        header = handle.readline().rstrip("\n").split("\t")
        if len(header) < 5 or header[0] != "tiny":
            raise SystemExit(f"not a tiny v2 file: {tiny_path}")
        namespaces = header[3:]
        try:
            mojang_col = namespaces.index("official")
            yarn_col = namespaces.index("named")
            inter_col = namespaces.index("intermediary")
        except ValueError:
            raise SystemExit(f"need 'official' and 'named' namespaces, found {namespaces}")

        mojang_owner = None
        for line in handle:
            if not line or line.startswith("\t\t"):
                continue
            parts = line.rstrip("\n").split("\t")
            if parts[0] == "c" and len(parts) > 1 + max(mojang_col, yarn_col):
                mojang, yarn = parts[1 + mojang_col], parts[1 + yarn_col]
                if yarn and mojang:
                    classes[yarn] = mojang
                if len(parts) > 1 + inter_col and parts[1 + inter_col]:
                    intermediary[parts[1 + inter_col]] = mojang
                mojang_owner = mojang or None
            elif len(parts) > 3 and parts[0] == "" and parts[1] in ("m", "f"):
                # "", kind, descriptor, then one name per namespace
                names = parts[3:]
                if len(names) <= max(mojang_col, yarn_col):
                    continue
                mojang, yarn = names[mojang_col], names[yarn_col]
                if not yarn or not mojang:
                    continue
                if yarn != mojang:
                    (methods if parts[1] == "m" else fields)[yarn].add(mojang)
                # Identity mappings are kept here on purpose, even though they are useless as
                # renames. They are what lets "leave it alone" compete: BitStorage.get is called
                # get in both namespaces, so without this entry the only visible candidate for
                # `get` was Heightmap.getFirstAvailable and storage.get(i) was rewritten to
                # storage.getFirstAvailable(i). Same for List.of becoming List.direct.
                if mojang_owner:
                    owned[yarn].add((mojang, mojang_owner))
                if len(names) > inter_col and names[inter_col]:
                    intermediary[names[inter_col]] = mojang

    return classes, methods, fields, owned, intermediary


def unambiguous(table: dict[str, set[str]]) -> dict[str, str]:
    return {name: next(iter(targets)) for name, targets in table.items() if len(targets) == 1}


IDENT = re.compile(r"\b([A-Za-z_$][A-Za-z0-9_$]*)\b")

# A member is only renamed where it is actually a member access: after a dot, and not itself
# followed by one. Renaming bare identifiers everywhere rewrites the mod's own locals, fields
# and parameters too -- and Yarn has member names like "key" and "name" that collide with them
# constantly.
MEMBER_ACCESS = re.compile(r"(?<=\.)\s*([A-Za-z_$][A-Za-z0-9_$]*)\b")

# `this.x` and `super.x` are always the mod's own members, never Minecraft's. Without this,
# `this.context = context` became `this.setDataFixContextTag = context`, because Yarn has a
# member named context and the pass could not tell whose field it was looking at.
OWN_MEMBER = re.compile(r"\b(?:this|super)\s*\.\s*$")

# Types the file declares itself. Renaming one of these is always wrong: it is the mod's class,
# not Minecraft's, however much its name happens to look like one.
DECLARED_TYPE = re.compile(
    r"\b(?:class|interface|enum|record|@interface)\s+([A-Za-z_$][A-Za-z0-9_$]*)"
)

# A legal Java identifier. Mojang's mapping data contains local and anonymous classes such as
# SurfaceRules$1BiomeCondition, whose innermost segment starts with a digit; substituting one
# produced `public final class 1BiomeCondition`, which is not a name at all.
VALID_IDENT = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$]*$")

# Java keywords must never be treated as identifiers. Yarn genuinely has members called
# "record", "new" and "default"; substituting those turned `public record Foo(` into
# `public forJukeboxSong Foo(`, which is not a subtle failure but is an entirely avoidable one.
JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
    "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
    "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
    "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
    "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
    "volatile", "while", "var", "yield", "record", "sealed", "permits", "non-sealed", "true",
    "false", "null",
}

# String literals, char literals and comments, so their contents are never rewritten. Without
# this, the "n" in "\n" was matched as an identifier and replaced, producing "\width".
MASKABLE = re.compile(
    r'"""(?:\\.|[^\\])*?"""'      # text block
    r"|\"(?:\\.|[^\"\\\n])*\""    # string literal
    r"|'(?:\\.|[^'\\\n])*'"       # char literal
    r"|//[^\n]*"                  # line comment
    r"|/\*.*?\*/",                # block comment
    re.S,
)


def mask(text: str):
    """Replace literals and comments with placeholders so substitution cannot reach inside."""
    saved: list[str] = []

    def stash(match: re.Match) -> str:
        saved.append(match.group(0))
        return f"\x00{len(saved) - 1}\x00"

    return MASKABLE.sub(stash, text), saved


SLASHED_TYPE = re.compile(r"L([a-z][A-Za-z0-9_/$]*);")
INTERMEDIARY = re.compile(r"\b((?:method|field|class)_\d+)\b")
ACCESSOR = re.compile(r'@(?:Accessor|Invoker)\s*\(\s*"([A-Za-z_$][A-Za-z0-9_$]*)"')


def translate_accessors(text: str, mixin_target: str | None, owned) -> tuple[str, int]:
    """Rename the field and method names inside @Accessor and @Invoker.

    These are the one case that resolves exactly. Everywhere else the receiver's type has to
    be guessed at; here the @Mixin annotation names the owning class outright, so the member
    can be looked up on that class and nothing else. No ambiguity, no heuristic.

    They are also invisible to every other pass: the name lives in a string, strings are
    masked to protect escapes, and the descriptor pass skips anything that does not look like
    a descriptor. So @Accessor("storage") sailed through a clean compile and both checkers and
    failed at load with "No candidates were found matching storage" -- Mojang calls that field
    data.
    """
    if not mixin_target or not owned:
        return text, 0

    count = 0

    def swap(match: re.Match) -> str:
        nonlocal count
        yarn_name = match.group(1)
        hits = {mojang for mojang, owner in owned.get(yarn_name, ()) if owner == mixin_target}
        if len(hits) != 1:
            return match.group(0)
        mojang_name = next(iter(hits))
        if mojang_name == yarn_name:
            return match.group(0)
        count += 1
        return match.group(0).replace(f'"{yarn_name}"', f'"{mojang_name}"')

    return ACCESSOR.sub(swap, text), count


def translate_annotation_strings(saved: list[str], classes, intermediary, members, overrides=None):
    """Translate mixin descriptors inside the string literals that were masked.

    Masking literals is what stops "\\n" being mangled, but it also hides the one kind of
    string that really is code: a mixin's method= or target=. Those carry Yarn class
    descriptors, and sometimes a raw intermediary name like method_38332 that Loom would have
    remapped at build time. Java has no opinion on any of it, so the file compiles perfectly
    and the mixin fails to apply at load -- the exact failure this whole tool exists to avoid.

    Only strings that look like descriptors are touched: something with an Lsome/type; in it,
    or an intermediary name. A message or a config key is left alone.
    """
    for index, literal in enumerate(saved):
        if not literal.startswith('"') or ("L" not in literal and "_" not in literal):
            continue
        original = literal

        literal = SLASHED_TYPE.sub(
            lambda m: "L" + classes.get(m.group(1), m.group(1)) + ";", literal)
        literal = INTERMEDIARY.sub(lambda m: intermediary.get(m.group(1), m.group(1)), literal)

        # A leading bare method name, as in "populateBiomes(...)V".
        head = re.match(r'"([A-Za-z_$][A-Za-z0-9_$]*)\(', literal)
        if head and head.group(1) in members:
            literal = '"' + members[head.group(1)] + literal[1 + len(head.group(1)):]

        # Overrides apply here too. A method= string is the one place a name appears with no
        # surrounding code to infer its owner from, so the hand-verified table is often the
        # only thing that can resolve it.
        for key, value in (overrides or {}).items():
            name = key.rpartition("@")[2]
            if "." in name or name.startswith("literal:"):
                continue
            literal = re.sub(r"\b" + re.escape(name) + r"\b", value, literal)

        if literal != original:
            saved[index] = literal


def unmask(text: str, saved: list[str]) -> str:
    return re.sub(r"\x00(\d+)\x00", lambda m: saved[int(m.group(1))], text)


DECLARATION = re.compile(r"^[ \t]*(?:package|import)[ \t][^\n;]*;", re.M)


def mask_declarations(text: str, saved: list[str]):
    """Hide package/import lines, appending to the same placeholder table."""
    def stash(match: re.Match) -> str:
        saved.append(match.group(0))
        return f"\x00{len(saved) - 1}\x00"

    return DECLARATION.sub(stash, text), saved


def translate(text: str, classes, members, simple_names, owned=None, overrides=None,
              path_hint=None, intermediary=None):
    """Rewrite one source file. Returns (new_text, renamed_counter)."""
    renamed = collections.Counter()

    # The accessor pass runs on raw text: its names live inside string literals, which
    # mask() is about to hide. The @Mixin target tells us exactly which class owns them.
    target_match = re.search(r'@Mixin\s*\(([^)]*)\)', text, re.S)
    mixin_target = None
    if target_match:
        literal = re.search(r'([A-Za-z_$][\w.$]*)\.class', target_match.group(1))
        if literal:
            simple = literal.group(1).split('.')[-1]
            for yarn_fq, mojang_fq in classes.items():
                if yarn_fq.rsplit('/', 1)[-1].rsplit('$', 1)[-1] == simple:
                    mixin_target = mojang_fq
                    break
                if mojang_fq.rsplit('/', 1)[-1].rsplit('$', 1)[-1] == simple:
                    mixin_target = mojang_fq
                    break
    text, accessor_count = translate_accessors(text, mixin_target, owned)
    renamed['accessor'] += accessor_count

    text, saved = mask(text)

    # Fully-qualified names first: imports and any inline use. Longest first so an inner class
    # is rewritten before its outer name can match a prefix of it.
    #
    # Both spellings of a nested class are registered. Mappings store them as Outer$Inner while
    # Java source writes Outer.Inner, so matching only the mapping's form rewrote the outer name
    # and left the inner one behind -- MultiNoiseUtil.SearchTree became Climate.SearchTree, which
    # does not exist. The Mojang side is emitted dotted either way, because that is what source
    # has to say.
    replacements: dict[str, str] = {}
    for yarn_fq, mojang_fq in classes.items():
        replacements[yarn_fq.replace("/", ".")] = mojang_fq.replace("/", ".")
        if "$" in yarn_fq:
            replacements[yarn_fq.replace("/", ".").replace("$", ".")] = \
                mojang_fq.replace("/", ".").replace("$", ".")

    for yarn_dotted in sorted(replacements, key=len, reverse=True):
        if yarn_dotted in text:
            text = text.replace(yarn_dotted, replacements[yarn_dotted])
            renamed["class"] += 1

    # Import and package declarations are finished after the step above, and must be hidden from
    # the identifier passes that follow. Every segment of a qualified name sits after a dot, so
    # the member pass reads "net.minecraft.world.level.biome.BiomeSource" as a member access on
    # `biome` -- and Yarn has a field called biome that maps to biomes. That one mistake produced
    # net.minecraft.world.level.biomes, net.neoforged.fml.gameEvent.lifecycle and
    # it.unimi.dsi.fastutil.list, and 700 compile errors between them.
    # Captured before the imports are hidden, because the imports are where a file says which
    # Minecraft classes it uses -- and that is exactly what disambiguates a member name below.
    in_scope = set(re.findall(r"net\.minecraft\.[A-Za-z0-9_.$]+", text))
    in_scope |= {n.replace(".", "/") for n in in_scope}
    simple_in_scope = {n.rsplit(".", 1)[-1] for n in in_scope}

    text, imports = mask_declarations(text, saved)

    declared = set(DECLARED_TYPE.findall(text))

    # Only rename a bare type name if this file actually imports the Minecraft class it maps to.
    # Renaming every identifier that merely matches some Yarn class name reaches far outside
    # Minecraft: java.lang.Override became OverrideText, because Yarn has a class called Override
    # and nothing said this file had never heard of it. Scoping to the imports is what makes the
    # rename mean "the type this file is using" rather than "a string that appears in a mapping".
    importable = {simple_in_scope_name for simple_in_scope_name in simple_in_scope}
    usable = {yarn: mojang for yarn, mojang in simple_names.items() if mojang in importable}

    def swap_simple(match: re.Match) -> str:
        name = match.group(1)
        if name in JAVA_KEYWORDS or name in declared:
            return name
        if name in usable:
            renamed["simple"] += 1
            return usable[name]
        return name

    text = IDENT.sub(swap_simple, text)

    # Nested types written as Outer.Inner where Outer has just been renamed and Inner has not.
    # The bare-name pass cannot do these: when a file declares its own class with the same simple
    # name as the Minecraft one -- FastNoise has a MaterialRuleContext extending Minecraft's --
    # the bare name must stay the mod's and only the qualified form is Minecraft's. Keying on the
    # already-Mojang outer name is what tells the two apart.
    #
    # `extends SurfaceRules.MaterialRuleContext` left unfixed is not one error; every inherited
    # field and method then fails too, which is where twenty of these came from.
    for yarn_fq, mojang_fq in classes.items():
        if "$" not in yarn_fq:
            continue
        yarn_inner = yarn_fq.rsplit("$", 1)[-1]
        mojang_outer = mojang_fq.rsplit("/", 1)[-1].rsplit("$", 2)[0]
        mojang_inner = mojang_fq.rsplit("$", 1)[-1]
        if yarn_inner == mojang_inner or not VALID_IDENT.match(mojang_inner):
            continue
        qualified = f"{mojang_outer}.{yarn_inner}"
        if qualified in text:
            text = text.replace(qualified, f"{mojang_outer}.{mojang_inner}")
            renamed["nested"] += 1


    def resolve(name: str) -> str | None:
        """The Mojang name, but only when the owning class is one this file uses.

        Requiring the owner to be in scope is what keeps this off types that are not
        Minecraft's. Accepting any globally-unambiguous name instead rewrote Integer.MIN_VALUE
        to Integer.MIN_AMPLIFIER and List.toArray to List.adjustArgs, because Yarn happens to
        have members by those names and nothing asked whose object was being called.

        It also removes the need to special-case this. and super.: a class extending a
        Minecraft type reaches inherited fields that way and must be translated, while a mod's
        own field survives because its name is not owned by anything in scope.
        """
        if not owned or name not in owned:
            return None
        # The outer name counts as in scope for a nested owner. A file importing SurfaceRules
        # uses SurfaceRules$Context without ever naming it, so comparing the whole tail found
        # nothing and every inherited member of such a class went untranslated.
        def visible(owner: str) -> bool:
            tail = owner.rsplit("/", 1)[-1]
            return (owner in in_scope
                    or tail in simple_in_scope
                    or tail.split("$", 1)[0] in simple_in_scope)

        hits = {mojang for mojang, owner in owned[name] if visible(owner)}
        return next(iter(hits)) if len(hits) == 1 else None

    def swap_member(match: re.Match) -> str:
        name = match.group(1)
        if name in JAVA_KEYWORDS:
            return match.group(0)
        # A static call on a type this file does not import from Minecraft is not Minecraft's.
        # List.of(x) became List.direct(x) until this looked at what was left of the dot.
        before = text[max(0, match.start() - 80):match.start()]
        recv = re.search(r"([A-Za-z_$][A-Za-z0-9_$]*)\s*\.\s*$", before)
        if recv and recv.group(1)[:1].isupper() and recv.group(1) not in simple_in_scope:
            return match.group(0)
        target = resolve(name)
        if target is None or target == name:
            return match.group(0)
        renamed["member"] += 1
        return match.group(0).replace(name, target)

    text = MEMBER_ACCESS.sub(swap_member, text)

    # Hand-verified last word. Everything above works from the mapping alone and stops where the
    # mapping stops being enough -- a name whose owner cannot be inferred from the text, or one
    # that is legitimately ambiguous. Those are looked up once against the jar with javap and
    # listed in an overrides file, which is a small honest table rather than a cleverer guess.
    for key, mojang_name in (overrides or {}).items():
        scope, _, yarn_name = key.rpartition("@")
        if scope and scope not in (path_hint or ""):
            continue
        if yarn_name.startswith("literal:"):
            # Plain text, no word boundaries. The escape hatch for a name whose correct
            # translation depends on the receiver and where the receiver is not a bare
            # identifier -- sections[i].isEmpty() is LevelChunkSection.hasOnlyAir(), while
            # state.getFluidState().isEmpty() in the same file keeps its name.
            literal = yarn_name[len("literal:"):]
            if literal in text:
                text = text.replace(literal, mojang_name)
                renamed["override"] += 1
            continue
        if "." in yarn_name:
            # Qualified: Owner.member, so only that receiver is touched. Needed where the bare
            # name is far too common to rewrite globally -- Data.data() becomes storage() while
            # every other data in the file stays put.
            owner, _, member = yarn_name.partition(".")
            pattern = re.compile(r"\b" + re.escape(owner) + r"\s*\.\s*" + re.escape(member) + r"\b")
            replacement = f"{owner}.{mojang_name}"
        else:
            pattern = re.compile(r"\b" + re.escape(yarn_name) + r"\b")
            replacement = mojang_name
        if pattern.search(text):
            text = pattern.sub(replacement, text)
            renamed["override"] += 1

    translate_annotation_strings(saved, classes, intermediary or {}, members, overrides)
    return unmask(text, saved), renamed


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    tiny = pathlib.Path(sys.argv[1])
    root = pathlib.Path(sys.argv[2])
    dry = "--dry-run" in sys.argv

    overrides: dict[str, str] = {}
    for arg in sys.argv[3:]:
        if arg.startswith("--overrides="):
            for line in pathlib.Path(arg.split("=", 1)[1]).read_text(encoding="utf-8").splitlines():
                line = line.split("#", 1)[0].strip()
                if "=" in line:
                    yarn, mojang = line.split("=", 1)
                    overrides[yarn.strip()] = mojang.strip()

    classes, method_table, field_table, owned, intermediary = load(tiny)
    print(f"loaded {len(classes)} classes, {len(method_table)} method names, "
          f"{len(field_table)} field names")

    members = unambiguous(method_table) | unambiguous(field_table)
    ambiguous = {n for n, t in (method_table | field_table).items() if len(t) > 1}
    print(f"{len(members)} member names are unambiguous; {len(ambiguous)} are not and are left alone")

    # Simple class names, only where they actually differ and are unique.
    # Innermost segment, not the last path element: a nested class is stored as Outer$Inner, and
    # source refers to it as plain Inner once imported. Keying on Outer$Inner meant every bare
    # nested-class reference was left untranslated.
    simple_counts: dict[str, set[str]] = collections.defaultdict(set)
    for yarn_fq, mojang_fq in classes.items():
        yarn_simple = yarn_fq.rsplit("/", 1)[-1].rsplit("$", 1)[-1]
        mojang_simple = mojang_fq.rsplit("/", 1)[-1].rsplit("$", 1)[-1]
        simple_counts[yarn_simple].add(mojang_simple)
    simple_names = {y: next(iter(m)) for y, m in simple_counts.items()
                    if len(m) == 1 and y != next(iter(m))
                    and VALID_IDENT.match(y) and VALID_IDENT.match(next(iter(m)))}
    print(f"{len(simple_names)} simple class names differ and are unique")

    total = collections.Counter()
    files = sorted(root.rglob("*.java"))
    for path in files:
        original = path.read_text(encoding="utf-8", errors="replace")
        updated, renamed = translate(original, classes, members, simple_names, owned, overrides,
                                     str(path).replace(chr(92), '/'), intermediary)
        total.update(renamed)
        if updated != original and not dry:
            path.write_text(updated, encoding="utf-8")

    print(f"\n{len(files)} file(s): {total['class']} fq-class, {total['simple']} simple-class, "
          f"{total['member']} member rename(s)"
          + ("  [dry run, nothing written]" if dry else ""))
    print("Now compile. Whatever this could not resolve is a javac error, which is where the "
          "remaining work belongs.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

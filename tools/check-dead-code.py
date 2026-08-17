#!/usr/bin/env python3
"""Find genuinely unreachable classes. Corrected for duplicate simple names.

The previous version keyed files by class name, so the 24 duplicate names in this tree
(BootstrapMixin, BlockEntityMixin, ChunkAccessMixin and friends) collapsed onto one entry and the
others' contents were never scanned at all -- which reported classes as unreferenced when the file
referencing them had been silently dropped. The compiler caught it; this reads every file.

A class counts as reachable if it is named by any other file, by any mixin config (including the
one the annotation processor generates into the jar), by a resource, or if it is a @Mod entry.
"""

import json
import pathlib
import re
import sys
import zipfile

for module in sys.argv[1:] or ["duty-memory", "duty-client", "duty-fixerupper", "duty-server", "duty-essentials"]:
    src_roots = [d / "java" for d in sorted(pathlib.Path(module, "src").iterdir())
                 if (d / "java").is_dir()]
    if not src_roots:
        continue

    paths = [f for root in src_roots for f in root.rglob("*.java")]
    texts = {p: p.read_text(encoding="utf-8", errors="ignore") for p in paths}

    registered = set()
    jar = pathlib.Path(module, "build/libs", f"{module}-0.1.0.jar")
    if jar.exists():
        with zipfile.ZipFile(jar) as z:
            for n in z.namelist():
                if n.endswith(".mixins.json"):
                    d = json.loads(z.read(n).decode())
                    for k in ("mixins", "client", "server"):
                        for e in d.get(k) or []:
                            registered.add(e.split(".")[-1])
                    if d.get("plugin"):
                        registered.add(d["plugin"].split(".")[-1])

    res = ""
    rdir = pathlib.Path(module, "src/main/resources")
    if rdir.exists():
        for p in rdir.rglob("*"):
            if p.is_file():
                res += p.read_text(encoding="utf-8", errors="ignore") + "\n"

    dead = []
    for path in sorted(paths):
        name = path.stem
        if name in registered:
            continue
        body = texts[path]
        if "@Mod(" in body:
            continue
        pattern = re.compile(r"\b" + re.escape(name) + r"\b")
        # every OTHER file, by path -- duplicates included
        if any(pattern.search(t) for q, t in texts.items() if q != path):
            continue
        if pattern.search(res):
            continue
        dead.append(path.relative_to(src).as_posix())

    print(f"\n{module}: {len(paths)} files, {len(dead)} unreachable")
    for d in dead:
        print("   ", d)

"""Read a spark sampler profile and say where the server thread actually spent its time.

spark writes uncompressed protobuf with no schema attached, so this decodes the wire
format directly. The layout, worked out by probing a real file rather than guessing:

    top level      1 = metadata, 2 = ThreadNode (repeated), 6, 7
    ThreadNode     1 = name, 3 = flat pool of StackTraceNode, 4 = tree
    StackTraceNode 3 = class, 4 = method, 6 = line, 7 = descriptor,
                   8 = packed doubles (time), 9 = packed varints (child indices)

The pool-and-index shape is the part worth knowing: nodes are not nested, they are a flat
array referring to each other by position, so a naive recursive walk finds one frame and
reports every time as zero. That is what two earlier attempts at this did.

Self time is a node's own time minus its children's, which is the number that answers
"what is actually running" rather than "what is on the stack".
"""
import struct, sys, collections

data = open(sys.argv[1], "rb").read()

def varint(b, i):
    s = r = 0
    while True:
        x = b[i]; i += 1
        r |= (x & 0x7F) << s
        if not x & 0x80: return r, i
        s += 7

def fields(buf):
    i, n = 0, len(buf)
    while i < n:
        key, i = varint(buf, i); f, w = key >> 3, key & 7
        if w == 0: v, i = varint(buf, i)
        elif w == 1: v = struct.unpack_from("<d", buf, i)[0]; i += 8
        elif w == 2:
            L, i = varint(buf, i); v = buf[i:i+L]; i += L
        elif w == 5: v = struct.unpack_from("<f", buf, i)[0]; i += 4
        else: return
        yield f, w, v

def unpack_doubles(b):
    return list(struct.unpack_from("<%dd" % (len(b)//8), b, 0)) if len(b) >= 8 else []

def unpack_varints(b):
    out, i = [], 0
    while i < len(b):
        v, i = varint(b, i); out.append(v)
    return out

top = {}
for f, w, v in fields(data): top.setdefault(f, []).append(v)

def owner_of(cls):
    low = cls.lower()
    if ".dutymod." in low or low.startswith("net.dutymod"): return "Duty"
    if "spottedleaf" in low: return "Duty (light engine)"
    if "axalotl" in low: return "Duty: Innovative (Async)"
    if "lectern" in low: return "Lectern"
    if "caffeinemc" in low or "jellysquid" in low:
        return "Lithium" if "lithium" in low else "Sodium"
    if "irisshaders" in low: return "Iris"
    if "voxyworldgen" in low or "ethan" in low: return "Voxy World Gen"
    if "voxy" in low or "cortex" in low: return "Voxy"
    if low.startswith(("java.", "jdk.", "sun.")): return "JDK"
    if low.startswith(("net.minecraft", "com.mojang")): return "Minecraft"
    if "neoforged" in low: return "NeoForge"
    if "lwjgl" in low: return "LWJGL"
    if "spark" in low: return "spark"
    return "other: " + cls.split(".")[0] + "." + (cls.split(".")[1] if "." in cls[cls.find(".")+1:] else "")

grand_self = collections.Counter()
grand_frame = collections.Counter()
thread_totals = {}

for thread in top.get(2, []):
    name = None; pool = []
    for f, w, v in fields(thread):
        if f == 1 and name is None: name = v.decode("utf-8", "replace")
        elif f == 3: pool.append(v)
    nodes = []
    for entry in pool:
        cls = meth = None; times = []; kids = []
        for f, w, v in fields(entry):
            if f == 3: cls = v.decode("utf-8", "replace")
            elif f == 4: meth = v.decode("utf-8", "replace")
            elif f == 8: times = unpack_doubles(v)
            elif f == 9: kids = unpack_varints(v)
        nodes.append((cls, meth, sum(times), kids))
    total = 0.0
    for idx, (cls, meth, t, kids) in enumerate(nodes):
        child_time = sum(nodes[k][2] for k in kids if 0 <= k < len(nodes))
        self_time = max(0.0, t - child_time)
        total += self_time
        if cls:
            grand_self[owner_of(cls)] += self_time
            grand_frame[f"{cls}.{meth}"] += self_time
    thread_totals[name or "?"] = total

overall = sum(grand_self.values()) or 1.0
print("== sampled SELF time by owner ==")
for owner, t in grand_self.most_common(14):
    print(f"   {owner:26s} {t:12.1f}ms  {100.0*t/overall:5.1f}%")
print("\n== hottest frames (self time) ==")
for frame, t in grand_frame.most_common(20):
    print(f"   {t:10.1f}ms  {frame[:92]}")
print("\n== threads ==")
for n, t in sorted(thread_totals.items(), key=lambda kv: -kv[1])[:8]:
    print(f"   {t:10.1f}ms  {n[:60]}")

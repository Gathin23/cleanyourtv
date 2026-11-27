#!/usr/bin/env python3
"""List the bitmaps in an Android Java heap dump, largest first, with what holds each one.

Usage:
    hprof_bitmaps.py <dump.hprof>
    hprof_bitmaps.py --help      this text

The dump comes from a debuggable build (a release build refuses `am dumpheap`): install the debug APK over the
release one, then `tv.py "am dumpheap -g com.gathin.tvhome /data/local/tmp/l.hprof"` (-g collects garbage first,
so bitmaps already let go are not listed) and `tv.py --pull`. The size column counts 4 bytes a pixel: a 16-bit
(RGB_565) bitmap takes half of it, and a hardware bitmap sits in graphics memory rather than the native heap.
docs/workflows/perf-ab.md says when to use it. Offline, standard library only.
"""
import struct
import sys

if len(sys.argv) != 2 or sys.argv[1] in ("-h", "--help"):
    print(__doc__.strip())
    sys.exit(0 if sys.argv[1:] in (["-h"], ["--help"]) else 2)
from collections import defaultdict

TYPE_SIZE = {2: None, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}

data = open(sys.argv[1], "rb").read()
end = data.index(b"\0")
idsize = struct.unpack(">I", data[end + 1:end + 5])[0]
pos = end + 1 + 4 + 8
TYPE_SIZE[2] = idsize
idfmt = ">I" if idsize == 4 else ">Q"


def rid(p):
    return struct.unpack(idfmt, data[p:p + idsize])[0]


strings, class_names = {}, {}
classes = {}          # class id -> (super id, [(name, type)])
instances = {}        # obj id -> (class id, start, length)
arrays_of_obj = []    # (obj id, class id, [ids])

while pos < len(data):
    tag = data[pos]
    length = struct.unpack(">I", data[pos + 5:pos + 9])[0]
    body = pos + 9
    if tag == 0x01:
        strings[rid(body)] = data[body + idsize:body + length].decode("utf-8", "replace")
    elif tag == 0x02:
        class_names[rid(body + 4)] = rid(body + 8 + idsize)
    elif tag in (0x0C, 0x1C):
        p, stop = body, body + length
        while p < stop:
            sub = data[p]; p += 1
            if sub == 0xFF or sub in (0x05, 0x07, 0x89, 0x8A, 0x8B, 0x8C, 0x8D, 0x90):
                p += idsize
            elif sub == 0x01:
                p += 2 * idsize
            elif sub in (0x02, 0x03, 0x8E):
                p += idsize + 8
            elif sub in (0x04, 0x06):
                p += idsize + 4
            elif sub == 0x08:
                p += idsize + 8
            elif sub == 0xFE:
                p += 4 + idsize
            elif sub == 0x20:
                cid = rid(p); sup = rid(p + idsize + 4)
                p += idsize + 4 + 6 * idsize + 4
                n = struct.unpack(">H", data[p:p + 2])[0]; p += 2
                for _ in range(n):
                    t = data[p + 2]; p += 3 + TYPE_SIZE[t]
                n = struct.unpack(">H", data[p:p + 2])[0]; p += 2
                for _ in range(n):
                    t = data[p + idsize]; p += idsize + 1 + TYPE_SIZE[t]
                n = struct.unpack(">H", data[p:p + 2])[0]; p += 2
                fields = []
                for _ in range(n):
                    fields.append((rid(p), data[p + idsize])); p += idsize + 1
                classes[cid] = (sup, fields)
            elif sub == 0x21:
                oid = rid(p); cid = rid(p + idsize + 4)
                ln = struct.unpack(">I", data[p + 2 * idsize + 4:p + 2 * idsize + 8])[0]
                start = p + 2 * idsize + 8
                instances[oid] = (cid, start, ln)
                p = start + ln
            elif sub == 0x22:
                oid = rid(p); n = struct.unpack(">I", data[p + idsize + 4:p + idsize + 8])[0]
                cid = rid(p + idsize + 8)
                start = p + 2 * idsize + 8
                arrays_of_obj.append((oid, cid, [rid(start + i * idsize) for i in range(n)]))
                p = start + n * idsize
            elif sub == 0x23:
                n = struct.unpack(">I", data[p + idsize + 4:p + idsize + 8])[0]
                t = data[p + idsize + 8]
                p += idsize + 9 + n * TYPE_SIZE[t]
            elif sub == 0xC3:
                p += idsize + 9
            else:
                raise SystemExit(f"unknown sub-tag {sub:#x} at {p}")
    pos = body + length

name_of = {cid: strings.get(sid, "?") for cid, sid in class_names.items()}


def layout(cid):
    out = []
    while cid in classes:
        sup, fields = classes[cid]
        out.extend((strings.get(n, "?"), t) for n, t in fields)
        cid = sup
    return out


def read_fields(oid):
    cid, start, ln = instances[oid]
    vals, p = {}, start
    for fname, t in layout(cid):
        size = TYPE_SIZE[t]
        raw = data[p:p + size]
        if t == 2:
            vals.setdefault(fname, struct.unpack(idfmt, raw)[0])
        elif t == 10:
            vals.setdefault(fname, struct.unpack(">i", raw)[0])
        p += size
    return vals


bitmap_cid = [c for c, n in name_of.items() if n == "android.graphics.Bitmap"][0]
bitmaps = {}
for oid, (cid, _, _) in instances.items():
    if cid == bitmap_cid:
        f = read_fields(oid)
        bitmaps[oid] = (f.get("mWidth", 0), f.get("mHeight", 0))

holders = defaultdict(list)
for oid, (cid, _, _) in instances.items():
    for fname, val in read_fields(oid).items():
        if val in bitmaps:
            holders[val].append(f"{name_of.get(cid, '?')}.{fname}")
for oid, cid, ids in arrays_of_obj:
    for v in ids:
        if v in bitmaps:
            holders[v].append(name_of.get(cid, "?") + "[]")

rows = sorted(bitmaps.items(), key=lambda kv: -(kv[1][0] * kv[1][1]))
total = 0
for oid, (w, h) in rows:
    kb = w * h * 4 // 1024
    total += kb
    print(f"{w:5d}x{h:<5d} {kb:6d} KB  " + ", ".join(sorted(set(holders.get(oid, ['(no Java holder)'])))[:3]))
print(f"{len(bitmaps)} bitmaps, about {total // 1024} MB at 4 bytes a pixel")

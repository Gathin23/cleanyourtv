"""Writes a demo playlist and guide for testing TV Hub on a TV, with programmes around the current time.

Usage:
    python3 tools/make_fixtures.py 192.168.1.20        # this computer's LAN address
    python3 -m http.server --directory fixtures/out 8000

Then add http://192.168.1.20:8000/demo.m3u as an M3U source (its header names demo.xml as the guide),
and optionally http://192.168.1.20:8000/backup.m3u to test merging and stream fallback.

Content: the only working stream is Big Buck Bunny (c) Blender Foundation, CC BY 3.0
(https://peach.blender.org), as published by Mux at test-streams.mux.dev for player testing.
Every other entry is made up to exercise error handling. Logos are drawn here, not downloaded.
"""
import struct
import sys
import time
import zlib
from pathlib import Path

HOST = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
PORT = sys.argv[2] if len(sys.argv) > 2 else "8000"
BASE = f"http://{HOST}:{PORT}"
OUT = Path(__file__).resolve().parent.parent / "fixtures" / "out"
OUT.mkdir(parents=True, exist_ok=True)
BUNNY = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"


def png(path, rgb):
    """A 160x90 solid PNG, so logo loading can be tested without third-party logos."""
    w, h = 160, 90
    raw = b"".join(b"\x00" + bytes(rgb) * w for _ in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")
    path.write_bytes(data)


(OUT / "logos").mkdir(exist_ok=True)
for name, rgb in {"bunny": (79, 163, 224), "news": (229, 72, 77), "sport": (91, 199, 130), "film": (200, 162, 107)}.items():
    png(OUT / "logos" / f"{name}.png", rgb)

(OUT / "demo.m3u").write_text(f'''#EXTM3U x-tvg-url="{BASE}/demo.xml"
#EXTINF:-1 tvg-id="bunny.demo" tvg-chno="1" tvg-logo="{BASE}/logos/bunny.png" group-title="Demo",Big Buck Bunny
{BUNNY}
#EXTINF:-1 tvg-id="news.demo" tvg-chno="2" tvg-logo="{BASE}/logos/news.png" group-title="Demo",Demo News (dead stream)
{BASE}/missing/news.m3u8
#EXTINF:-1 tvg-id="sport.demo" tvg-chno="3" tvg-logo="{BASE}/logos/sport.png" group-title="Sport",Demo Sport (falls back)
{BASE}/missing/sport.m3u8
#EXTINF:-1 tvg-chno="4" tvg-logo="{BASE}/logos/film.png" group-title="Films",Film Channel (no guide)
{BUNNY}
#EXTINF:-1 tvg-chno="5" group-title="Demo",Unreachable host
http://192.0.2.1:9/never.m3u8
#EXTINF:-1,Multicast (skipped, not http)
udp://@239.0.0.1:1234
''')

# A second source that shares tvg-id sport.demo, with a working stream: with merging on, channel 3
# fails on the first source and the player moves to this one.
(OUT / "backup.m3u").write_text(f'''#EXTM3U
#EXTINF:-1 tvg-id="sport.demo" tvg-chno="3" group-title="Sport",Demo Sport backup
{BUNNY}
''')

def t(ms): return time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(ms / 1000))

now = int(time.time() * 1000)
slot = now - now % 1_800_000
progs = []
for ch, names, length in [
    ("bunny.demo", ["Big Buck Bunny", "Behind the Bunny", "Meadow Live"], 60),
    ("news.demo", ["Morning Headlines", "Weather", "World Report", "Local News"], 30),
    ("sport.demo", ["Match Preview", "Live Match", "Highlights"], 90),
]:
    start = slot - 3_600_000
    i = 0
    while start < now + 2 * 86_400_000:
        stop = start + length * 60_000
        title = names[i % len(names)]
        progs.append(f'  <programme start="{t(start)}" stop="{t(stop)}" channel="{ch}"><title lang="en">{title}</title>'
                     f'<desc lang="en">Made-up listing number {i + 1} for testing the guide.</desc><category>Demo</category></programme>')
        start = stop
        i += 1

(OUT / "demo.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n<tv generator-info-name="tv-hub make_fixtures.py">\n'
    '  <channel id="bunny.demo"><display-name>Big Buck Bunny</display-name></channel>\n'
    '  <channel id="news.demo"><display-name>Demo News</display-name></channel>\n'
    '  <channel id="sport.demo"><display-name>Demo Sport</display-name></channel>\n'
    + "\n".join(progs) + "\n</tv>\n")
(OUT / "empty.m3u").write_text("")
(OUT / "invalid.xml").write_text("<tv><programme start=")
print(f"Wrote {OUT}: demo.m3u, backup.m3u, demo.xml ({len(progs)} programmes), empty.m3u, invalid.xml")
print(f"Serve with: python3 -m http.server --directory {OUT} {PORT}")

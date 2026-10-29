#!/usr/bin/env python3
"""Draws themes/ruska/wallpaper.jpg from nothing but code: a Finnish autumn ("ruska") dusk over a
lake, with spruce silhouettes, birches in autumn colour and a few falling leaves. No photos, artwork
or fonts are used, so the picture is original and can be redrawn by anyone.

    python3 make_wallpaper.py            # needs Pillow and NumPy; seed 1409 gives the same picture
    python3 make_wallpaper.py --night    # wallpaper_night.jpg: the same shore under a crescent moon
                                         # (not shipped: the launcher has no night picture)

Kept dark on purpose (see themes/README.md, Wallpapers): the clock, the hero name and the dock band
are designed for black, so the top corners and the bottom third stay close to it.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
S = 2                                   # draw at 2x, scale down for clean edges
w, h = W * S, H * S
HORIZON = 0.575                         # lake line, as a fraction of the height
rng = np.random.default_rng(1409)
NIGHT = "--night" in sys.argv[1:]
# The night picture draws the same shore (same seed, same order of random draws); only the light changes.
night_rng = np.random.default_rng(2210)
MOON = (0.56, 0.30)                     # moon centre, as fractions of width and height


def lerp(a, b, t):
    return a + (b - a) * t


def gradient(stops, n):
    """Rows of colour from (position, (r, g, b)) stops."""
    ys = np.linspace(0, 1, n)
    out = np.zeros((n, 3))
    for c in range(3):
        out[:, c] = np.interp(ys, [p for p, _ in stops], [col[c] for _, col in stops])
    return out


# ---- sky and its warm afterglow --------------------------------------------------------------
hy = int(h * HORIZON)
if NIGHT:
    sky_rows = gradient([(0.0, (3, 5, 12)), (0.45, (6, 9, 20)), (0.85, (12, 17, 32)), (1.0, (16, 21, 36))], hy)
else:
    sky_rows = gradient([(0.0, (7, 9, 20)), (0.35, (12, 14, 30)), (0.70, (30, 21, 32)),
                         (0.90, (58, 32, 28)), (1.0, (76, 40, 26))], hy)
img = np.zeros((h, w, 3))
img[:hy] = sky_rows[:, None, :]
yy, xx = np.mgrid[0:h, 0:w]
if NIGHT:
    # a cool halo round the moon instead of the warm afterglow
    mx, my = w * MOON[0], h * MOON[1]
    halo = np.exp(-(((xx - mx) ** 2 + (yy - my) ** 2) / (h * 0.11) ** 2))
    img[:hy] += halo[:hy, :, None] * np.array([18, 24, 34])
else:
    glow = np.exp(-(((xx - w * 0.64) / (w * 0.30)) ** 2) - (((yy - hy) / (h * 0.16)) ** 2))
    img[:hy] += (glow[:hy, :, None] * np.array([46, 20, 4]))

# a few faint stars high up
for _ in range(70):
    x, y = rng.integers(0, w), rng.integers(0, int(h * 0.32))
    b = rng.uniform(25, 70)
    img[y:y + S, x:x + S] += b
if NIGHT:                               # many more stars on a clear night, from their own generator
    for _ in range(260):
        x, y = night_rng.integers(0, w), night_rng.integers(0, int(h * 0.48))
        img[y:y + S, x:x + S] += night_rng.uniform(20, 85)
    # a crescent moon: a pale disc with its shadowed side cut away
    mx, my, mr = w * MOON[0], h * MOON[1], 26 * S
    moon = ((xx - mx) ** 2 + (yy - my) ** 2 <= mr ** 2) & ((xx - (mx - mr * 0.55)) ** 2 + (yy - my) ** 2 > mr ** 2)
    img[moon] = np.array([206, 210, 196])

scene = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB")
d = ImageDraw.Draw(scene)

# ---- far hills ---------------------------------------------------------------------------------
def ridge(base, amp, freq, phase):
    pts = []
    for x in range(0, w + 40, 40):
        y = base - amp * (0.6 * math.sin(x * freq + phase) + 0.4 * math.sin(x * freq * 2.7 + phase * 1.9))
        pts.append((x, y))
    return pts

d.polygon(ridge(hy - 30 * S, 22 * S, 0.0011 / S, 0.7) + [(w, hy), (0, hy)], fill=(20, 19, 30))
d.polygon(ridge(hy - 10 * S, 12 * S, 0.0019 / S, 2.1) + [(w, hy), (0, hy)], fill=(13, 13, 20))

# ---- spruce shoreline: taller at the edges, low in the middle so the afterglow shows --------------
def spruce(x, base, height, width, col):
    tiers = 5
    for i in range(tiers):
        t0 = i / tiers
        top = base - height * (1 - t0 * 0.82) - height * 0.18
        bot = base - height * t0 * 0.78
        half = width * (1 - t0 * 0.75) * 0.5
        d.polygon([(x, top), (x - half, bot), (x + half, bot)], fill=col)
    d.rectangle([x - width * 0.04, base - height * 0.1, x + width * 0.04, base], fill=col)

x = -20 * S
while x < w + 20 * S:
    edge = abs(x / w - 0.5) * 2               # 0 in the middle, 1 at the edges
    height = lerp(40, 260, edge ** 1.6) * S * rng.uniform(0.7, 1.15)
    width = height * rng.uniform(0.30, 0.42)
    spruce(x, hy + 2 * S, height, width, (6, 7, 10))
    x += width * rng.uniform(0.35, 0.65)

# ---- birches in ruska colours, right foreground ------------------------------------------------
RUSKA = [(150, 58, 22), (172, 92, 26), (118, 38, 24), (184, 122, 40), (96, 30, 22), (140, 104, 30)]
if NIGHT:                               # moonlight takes most of the colour out of the leaves
    RUSKA = [(int(r * 0.45 + 18), int(g * 0.45 + 20), int(b * 0.45 + 30)) for r, g, b in RUSKA]
foliage = Image.new("RGBA", (w, h), (0, 0, 0, 0))
fd = ImageDraw.Draw(foliage)
trunks = Image.new("RGBA", (w, h), (0, 0, 0, 0))
td = ImageDraw.Draw(trunks)
for bx, top in ((0.865, 0.26), (0.935, 0.20), (0.79, 0.36)):
    cx = int(w * bx)
    tw = int(rng.uniform(9, 14) * S)
    ytop = int(h * top)
    td.rectangle([cx - tw // 2, ytop, cx + tw // 2, hy + 4 * S], fill=(62, 66, 72, 255) if NIGHT else (80, 76, 68, 255))
    for _ in range(14):                    # the dark marks on birch bark
        y = rng.integers(ytop, hy)
        td.rectangle([cx - tw // 2, y, cx + rng.integers(-tw // 4, tw // 2), y + rng.integers(2, 5) * S],
                     fill=(22, 20, 18, 255))
    for _ in range(620):                   # leaf clusters around the upper trunk
        ang = rng.uniform(0, 2 * math.pi)
        r = abs(rng.normal(0, 95 * S))
        lx = cx + math.cos(ang) * r * 1.25
        ly = ytop + 120 * S + math.sin(ang) * r * 0.85
        if ly > hy - 6 * S:
            continue
        rad = rng.uniform(3, 9) * S
        col = RUSKA[rng.integers(len(RUSKA))]
        k = rng.uniform(0.34, 0.56)        # kept dim
        fd.ellipse([lx - rad, ly - rad * 0.8, lx + rad, ly + rad * 0.8],
                   fill=(int(col[0] * k), int(col[1] * k), int(col[2] * k), 235))
scene = scene.convert("RGBA")
scene.alpha_composite(trunks.filter(ImageFilter.GaussianBlur(0.8 * S)))
scene.alpha_composite(foliage.filter(ImageFilter.GaussianBlur(2.4 * S)))

# a few birches far away on the left shore, small and dim
for bx in (0.10, 0.155, 0.205):
    cx, ytop = int(w * bx), hy - int(rng.uniform(90, 130) * S)
    sd = ImageDraw.Draw(scene)
    sd.rectangle([cx - 3 * S, ytop, cx + 3 * S, hy], fill=(58, 54, 48, 255))
    for _ in range(140):
        ang, r = rng.uniform(0, 2 * math.pi), abs(rng.normal(0, 28 * S))
        lx, ly = cx + math.cos(ang) * r, ytop + 25 * S + math.sin(ang) * r * 0.8
        col, rad, k = RUSKA[rng.integers(len(RUSKA))], rng.uniform(2, 5) * S, rng.uniform(0.30, 0.46)
        sd.ellipse([lx - rad, ly - rad, lx + rad, ly + rad],
                   fill=(int(col[0] * k), int(col[1] * k), int(col[2] * k), 255))

# ---- lake: rippled, darkened reflection ---------------------------------------------------------
arr = np.asarray(scene.convert("RGB")).astype(np.float64)
above = arr[:hy][::-1]
lake_h = h - hy
refl = np.zeros((lake_h, w, 3))
for i in range(lake_h):
    src = above[min(int(i * 0.92), hy - 1)]
    shift = int((math.sin(i * 0.05 / S) + 0.35 * math.sin(i * 0.17 / S)) * (1 + i / lake_h * 4) * S)
    refl[i] = np.roll(src, shift, axis=0)
fade = np.linspace(0.42, 0.10, lake_h)[:, None, None]
water = refl * fade + np.array([4, 5, 9]) * (1 - fade)
for i in range(0, lake_h, 7 * S):          # soft horizontal glints
    water[i:i + S] *= 1.18
if NIGHT:                                  # the moon's broken path on the water
    lyy, lxx = np.mgrid[0:lake_h, 0:w]
    width = (10 + lyy / lake_h * 70) * S
    path = np.exp(-((lxx - w * MOON[0]) / width) ** 2) * np.exp(-lyy / (lake_h * 0.55))
    broken = (np.sin(lyy * 0.9 / S) > 0.2).astype(np.float64)
    water += (path * broken)[:, :, None] * np.array([70, 74, 70])
arr[hy:] = water

# ---- falling leaves -----------------------------------------------------------------------------
scene = Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGB").convert("RGBA")
leaves = Image.new("RGBA", (w, h), (0, 0, 0, 0))
for _ in range(16):
    lx, ly = rng.uniform(0.35, 0.98) * w, rng.uniform(0.18, 0.62) * h
    lw, lh = rng.uniform(5, 10) * S, rng.uniform(3, 5) * S
    col, k = RUSKA[rng.integers(len(RUSKA))], rng.uniform(0.45, 0.7)
    leaf = Image.new("RGBA", (int(lw * 2 + 4), int(lw * 2 + 4)), (0, 0, 0, 0))
    ImageDraw.Draw(leaf).ellipse([lw - lw, lw - lh, lw + lw, lw + lh],
                                 fill=(int(col[0] * k), int(col[1] * k), int(col[2] * k), 220))
    leaf = leaf.rotate(rng.uniform(0, 180), resample=Image.BICUBIC, expand=False)
    leaves.alpha_composite(leaf, (int(lx), int(ly)))
scene.alpha_composite(leaves.filter(ImageFilter.GaussianBlur(0.6 * S)))

# ---- keep the text areas dark: top band, left side, and the bottom third under the dock -----------
out = np.asarray(scene.convert("RGB")).astype(np.float64)
v = np.ones((h, w))
v *= np.clip(1 - np.exp(-((yy / h) / 0.16) ** 2) * 0.35, 0, 1)            # top band
v *= np.clip(1 - np.exp(-((xx / w) / 0.28) ** 2) * 0.30, 0, 1)            # left side (clock, hero name)
v *= np.clip(1 - ((yy / h - 0.62) / 0.38).clip(0, 1) ** 1.3 * 0.55, 0, 1)  # bottom third
out *= v[:, :, None]
out += rng.normal(0, 1.4, out.shape)        # a little grain against banding

final = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB").resize((W, H), Image.LANCZOS)
path = Path(__file__).with_name("wallpaper_night.jpg" if NIGHT else "wallpaper.jpg")
final.save(path, "JPEG", quality=84, optimize=True, progressive=True)
print(f"{path} {path.stat().st_size // 1024} KB")

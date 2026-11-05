#!/usr/bin/env python3
"""Draws themes/istanbul/wallpapers/*.jpg from nothing but code: Istanbul in flat layers - Topkapı Palace's
Gate of Salutation and Tower of Justice, Hagia Sophia, the Blue Mosque, the houses up to Galata Tower and the
Bosphorus Bridge - over the water with a ferry and two boats, under a faint eight-pointed-star lattice, at
three times of day. No photos, artwork or fonts are used, so the pictures are original.

    python3 make_wallpapers.py            # needs Pillow and NumPy; the same seed gives the same pictures

Kept dark on purpose (see themes/README.md, Wallpapers): the clock sits top left, the buttons top right and
the dock over the bottom third of the water, so the city lives between them and the boats just above the dock.
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
S = 2                                   # draw at 2x, scale down for clean edges
w, h = W * S, H * S
Y0 = int(0.585 * h)                     # the shore
OUT = Path(__file__).resolve().parent / "wallpapers"

MOODS = {
    "morning":   dict(sky=[(0, "#04060c"), (0.36, "#0c1828"), (0.54, "#2a2a24"), (0.585, "#5a4018")],
                      water=("#1b2733", "#04070b"), sun=(0.36, 0.50, 0.020, "#d9a441", 0.30), lights=0.0),
    "evening":   dict(sky=[(0, "#050407"), (0.32, "#120c0a"), (0.52, "#3c1e0a"), (0.585, "#8a4612")],
                      water=("#2c1b0e", "#050404"), sun=(0.615, 0.53, 0.026, "#e07a1f", 0.38), lights=0.5),
    "night":     dict(sky=[(0, "#000000"), (0.45, "#03060c"), (0.585, "#122340")],
                      water=("#0b1424", "#000000"), moon=(0.70, 0.235, 0.016), stars=True, lights=1.0),
}
GOLD = np.array([168, 132, 58], float)
WINDOW = np.array([217, 164, 65], float)


def rgb(hexs):
    return np.array([int(hexs[i:i + 2], 16) for i in (1, 3, 5)], float)


def gradient(stops, top=0, bottom=h):
    ys = np.linspace(0, 1, bottom - top)
    cols = np.stack([np.interp(ys, [p for p, _ in stops], [rgb(c)[k] for _, c in stops]) for k in range(3)], 1)
    return np.repeat(cols[:, None, :], w, 1)


def glow(img, cx, cy, radius, colour, strength):
    yy, xx = np.ogrid[:h, :w]
    d2 = ((xx - cx * w) ** 2 + (yy - cy * h) ** 2) / (radius * w) ** 2
    img += np.exp(-d2)[..., None] * rgb(colour) * strength


def layer():
    m = Image.new("L", (w, h), 0)
    return m, ImageDraw.Draw(m)


def star_lattice():
    """Faint eight-pointed stars (two overlapping squares) with an octagon inside, on a square grid."""
    m, d = layer()
    s = int(0.072 * w)
    R, lw = 0.40 * s, 3
    for gy in range(0, int(0.55 * h) + s, s):
        for gx in range(-s // 2 if (gy // s) % 2 else 0, w + s, s):
            for rot in (0, math.pi / 4):
                d.polygon([(gx + R * math.cos(rot + k * math.pi / 2), gy + R * math.sin(rot + k * math.pi / 2))
                           for k in range(4)], outline=255, width=lw)
            r2 = R * 0.42
            d.polygon([(gx + r2 * math.cos(math.pi / 8 + k * math.pi / 4), gy + r2 * math.sin(math.pi / 8 + k * math.pi / 4))
                       for k in range(8)], outline=255, width=lw)
    fade = np.clip(1 - np.arange(h) / (0.52 * h), 0, 1) ** 1.6
    return np.asarray(m, float) / 255 * fade[:, None]


def minaret(d, x, base, height, width):
    top = base - height
    d.rectangle([x - width / 2, top, x + width / 2, base], fill=255)
    for f in (0.62, 0.80):
        by = base - height * f
        d.rectangle([x - width * 0.95, by - width * 0.45, x + width * 0.95, by], fill=255)
    cone = width * 4.2
    d.polygon([(x - width * 0.62, top), (x + width * 0.62, top), (x, top - cone)], fill=255)
    d.line([(x, top - cone), (x, top - cone - width * 1.2)], fill=255, width=max(2, int(width * 0.18)))


def dome(d, cx, base, rx, ry, finial=True):
    d.ellipse([cx - rx, base - ry, cx + rx, base + ry], fill=255)
    if finial:
        d.line([(cx, base - ry), (cx, base - ry - rx * 0.28)], fill=255, width=max(2, int(rx * 0.04)))


def far_layer():
    """The Asian shore and the bridge, in the tone nearest the sky."""
    m, d = layer()
    xs = np.linspace(0, w, 200)
    hills = Y0 - h * (0.020 + 0.012 * np.sin(xs / w * 9) + 0.022 * np.exp(-((xs / w - 0.88) / 0.10) ** 2))
    d.polygon([(0.62 * w, Y0)] + [(x, y) for x, y in zip(xs, hills) if x > 0.62 * w] + [(w, Y0)], fill=255)
    deck, t1, t2, top = 0.548 * h, 0.775 * w, 0.955 * w, 0.425 * h
    d.rectangle([0.70 * w, deck, w, deck + 0.010 * h], fill=255)
    for tx in (t1, t2):                                        # portal towers with two cross-beams
        for k in (-1, 1):
            d.rectangle([tx + k * 0.007 * w - 0.0035 * w, top, tx + k * 0.007 * w + 0.0035 * w, Y0], fill=255)
        for by in (top + 0.006 * h, deck - 0.03 * h):
            d.rectangle([tx - 0.011 * w, by, tx + 0.011 * w, by + 0.006 * h], fill=255)
    cable = []
    for x in np.linspace(0.70 * w, w, 260):                    # main cable: up to each tower, sagging between
        if x < t1:
            y = deck - (top - deck) * -((x - 0.70 * w) / (t1 - 0.70 * w)) ** 1.6
        elif x < t2:
            u = (x - t1) / (t2 - t1)
            y = top + (deck - 0.012 * h - top) * (1 - (2 * u - 1) ** 2)
        else:
            y = top + (deck - top) * ((x - t2) / (w - t2)) ** 1.2 * 0.8
        cable.append((x, y))
    d.line(cable, fill=255, width=5)
    for x, y in cable[::5]:
        if y < deck - 4:
            d.line([(x, y), (x, deck)], fill=255, width=2)
    return m


def mid_layer(rng):
    """Houses climbing from the shore to Galata Tower."""
    m, d = layer()
    xs = np.linspace(0, w, 300)
    hill = Y0 - h * (0.012 + 0.050 * np.exp(-((xs / w - 0.685) / 0.07) ** 2) + 0.018 * np.exp(-((xs / w - 0.35) / 0.15) ** 2))
    d.polygon([(0, Y0)] + list(zip(xs, hill)) + [(w, Y0)], fill=255)
    x = 0.0
    while x < 0.76 * w:                                        # pitched-roof houses along the slopes
        bw = rng.uniform(0.012, 0.026) * w
        bh = rng.uniform(0.010, 0.028) * h
        y = float(np.interp(x + bw / 2, xs, hill))
        d.rectangle([x, y - bh, x + bw, Y0], fill=255)
        d.polygon([(x - 2, y - bh), (x + bw + 2, y - bh), (x + bw / 2, y - bh - bw * 0.25)], fill=255)
        x += bw * rng.uniform(0.9, 1.15)
    gx, gb, tw = 0.690 * w, float(np.interp(0.690 * w, xs, hill)) + 0.004 * h, 0.019 * w
    d.rectangle([gx - tw / 2, gb - 0.150 * h, gx + tw / 2, gb], fill=255)
    d.rectangle([gx - tw * 0.64, gb - 0.160 * h, gx + tw * 0.64, gb - 0.146 * h], fill=255)
    d.polygon([(gx - tw * 0.60, gb - 0.160 * h), (gx + tw * 0.60, gb - 0.160 * h), (gx, gb - 0.215 * h)], fill=255)
    d.line([(gx, gb - 0.215 * h), (gx, gb - 0.228 * h)], fill=255, width=4)
    return m


def front_layer():
    """The historic peninsula: Topkapı's gate and Tower of Justice, Hagia Sophia, the Blue Mosque, the quay."""
    m, d = layer()
    U = w
    d.rectangle([0, Y0 - 0.012 * h, 0.56 * U, Y0 + 0.006 * h], fill=255)
    # Gate of Salutation: a crenellated wall, two octagonal towers with tall cones, an arched gate between
    gx, wall = 0.075 * U, Y0 - 0.060 * h
    d.rectangle([0, wall, 0.135 * U, Y0], fill=255)
    for x in np.arange(0, 0.135 * U, 0.006 * U):
        d.rectangle([x, wall - 0.008 * h, x + 0.0032 * U, wall], fill=255)
    for k in (-1, 1):
        tx = gx + k * 0.022 * U
        d.rectangle([tx - 0.009 * U, wall - 0.075 * h, tx + 0.009 * U, Y0], fill=255)
        d.polygon([(tx - 0.0105 * U, wall - 0.075 * h), (tx + 0.0105 * U, wall - 0.075 * h), (tx, wall - 0.150 * h)], fill=255)
        d.line([(tx, wall - 0.150 * h), (tx, wall - 0.160 * h)], fill=255, width=4)
    d.rectangle([gx - 0.013 * U, wall - 0.030 * h, gx + 0.013 * U, Y0], fill=255)
    d.rounded_rectangle([gx - 0.0065 * U, wall - 0.012 * h, gx + 0.0065 * U, Y0 + 2], radius=0.0065 * U, fill=0)
    d.rectangle([0.13 * U, Y0 - 0.040 * h, 0.21 * U, Y0], fill=255)      # the palace's low buildings
    # Every minaret stands on its mosque's courtyard wall, spaced so no two overlap.
    # Hagia Sophia: wide shallow dome on a square body, buttresses, four thicker minarets
    cx, b = 0.265 * U, Y0 - 0.075 * h
    d.rectangle([cx - 0.080 * U, Y0 - 0.022 * h, cx + 0.080 * U, Y0], fill=255)
    d.rectangle([cx - 0.060 * U, b, cx + 0.060 * U, Y0], fill=255)
    for k in (-1, 1):
        d.rectangle([cx + k * 0.048 * U - 0.010 * U, b - 0.022 * h, cx + k * 0.048 * U + 0.010 * U, b], fill=255)
        dome(d, cx + k * 0.029 * U, b, 0.025 * U, 0.015 * U, False)
    d.rectangle([cx - 0.034 * U, b - 0.028 * h, cx + 0.034 * U, b], fill=255)
    dome(d, cx, b - 0.028 * h, 0.036 * U, 0.019 * U)
    for dx, mh in ((-0.076, 0.185), (-0.052, 0.200), (0.052, 0.200), (0.076, 0.185)):
        minaret(d, cx + dx * U, Y0, mh * h, 0.0072 * U)
    # Blue Mosque: cascading domes, six minarets (four at the mosque, two at the far corners of its courtyard)
    cx, b = 0.540 * U, Y0 - 0.062 * h
    d.rectangle([cx - 0.098 * U, Y0 - 0.022 * h, cx + 0.098 * U, Y0], fill=255)
    d.rectangle([cx - 0.062 * U, b, cx + 0.062 * U, Y0], fill=255)
    for k in (-1, 1):
        dome(d, cx + k * 0.048 * U, b + 0.004 * h, 0.012 * U, 0.012 * U, False)
        dome(d, cx + k * 0.029 * U, b - 0.006 * h, 0.021 * U, 0.018 * U, False)
    d.rectangle([cx - 0.027 * U, b - 0.032 * h, cx + 0.027 * U, b], fill=255)
    dome(d, cx, b - 0.032 * h, 0.029 * U, 0.027 * U)
    for dx, mh in ((-0.095, 0.195), (-0.072, 0.235), (-0.050, 0.240), (0.050, 0.240), (0.072, 0.235), (0.095, 0.195)):
        minaret(d, cx + dx * U, Y0, mh * h, 0.0053 * U)
    return m


def boats():
    """A ferry heading right and two small boats; returns the hull mask and the window mask."""
    m, d = layer()
    win, dw = layer()
    fx, fy, L = 0.30 * w, 0.668 * h, 0.17 * w               # ferry: waterline centre and length
    d.polygon([(fx - L / 2, fy - 0.022 * h), (fx + L / 2 + 0.012 * w, fy - 0.026 * h), (fx + L / 2 - 0.004 * w, fy),
               (fx - L / 2 + 0.006 * w, fy)], fill=255)
    d.rectangle([fx - L * 0.42, fy - 0.044 * h, fx + L * 0.40, fy - 0.022 * h], fill=255)
    d.rectangle([fx - L * 0.30, fy - 0.062 * h, fx + L * 0.24, fy - 0.044 * h], fill=255)
    d.rectangle([fx + L * 0.18, fy - 0.072 * h, fx + L * 0.27, fy - 0.062 * h], fill=255)  # wheelhouse
    d.polygon([(fx - L * 0.02, fy - 0.062 * h), (fx + L * 0.05, fy - 0.062 * h), (fx + L * 0.035, fy - 0.098 * h),
               (fx - L * 0.035, fy - 0.098 * h)], fill=255)                                  # raked funnel
    d.line([(fx + L * 0.22, fy - 0.072 * h), (fx + L * 0.22, fy - 0.120 * h)], fill=255, width=4)
    for row, (a, b) in ((fy - 0.036 * h, (-0.38, 0.36)), (fy - 0.055 * h, (-0.26, 0.20))):
        for x in np.arange(fx + a * L, fx + b * L, 0.0105 * w):
            dw.rectangle([x, row - 0.006 * h, x + 0.006 * w, row], fill=255)
    for bx, by, bl in ((0.78 * w, 0.655 * h, 0.040 * w), (0.885 * w, 0.672 * h, 0.032 * w)):
        d.polygon([(bx - bl / 2, by - 0.010 * h), (bx + bl / 2 + 0.006 * w, by - 0.013 * h), (bx + bl / 2 - 0.004 * w, by),
                   (bx - bl / 2 + 0.003 * w, by)], fill=255)
        d.rectangle([bx - bl * 0.15, by - 0.026 * h, bx + bl * 0.20, by - 0.010 * h], fill=255)
        d.line([(bx - bl * 0.05, by - 0.026 * h), (bx - bl * 0.05, by - 0.045 * h)], fill=255, width=3)
        dw.rectangle([bx - bl * 0.08, by - 0.021 * h, bx + bl * 0.13, by - 0.015 * h], fill=255)
    return m, win


def soft(mask):
    return np.asarray(mask.filter(ImageFilter.GaussianBlur(0.6)), float)[..., None] / 255


def draw(name, mood, art, lattice):
    rng = np.random.default_rng(1923)
    img = gradient(mood["sky"])
    if mood.get("stars"):
        n = 420
        for x, y, b in zip(rng.uniform(0, w, n).astype(int), rng.uniform(0, 0.52 * h, n).astype(int), rng.uniform(40, 150, n)):
            img[y:y + 3, x:x + 3] += b * (1 - y / (0.56 * h))
    if mood.get("moon"):
        mx, my, mr = mood["moon"]
        glow(img, mx, my, 0.10, "#2b3a55", 0.6)
        yy, xx = np.ogrid[:h, :w]
        disc = (xx - mx * w) ** 2 + (yy - my * h) ** 2 < (mr * w) ** 2
        bite = (xx - (mx + mr * 0.45) * w) ** 2 + (yy - (my - mr * 0.25 * w / h) * h) ** 2 < (mr * 0.88 * w) ** 2
        img[disc & ~bite] = rgb("#d6cfb4")
    if mood.get("sun"):
        sx, sy, sr, col, st = mood["sun"]
        glow(img, sx, sy, 0.30, col, st * 0.55)
        glow(img, sx, sy, 0.06, col, st)
        yy, xx = np.ogrid[:h, :w]
        img[(xx - sx * w) ** 2 + (yy - sy * h) ** 2 < (sr * w) ** 2] = rgb(col)
    img += lattice[..., None] * (0.055 if mood.get("stars") else 0.075) * GOLD
    top, bottom = rgb(mood["water"][0]), rgb(mood["water"][1])
    t = np.linspace(0, 1, h - Y0)[:, None, None] ** 0.8
    img[Y0:] = top * (1 - t) + bottom * t
    for y in range(Y0 + 10, int(0.69 * h), 14):              # a few lighter wave strokes near the shore
        for _ in range(4):
            x0, ln = rng.uniform(0, w), rng.uniform(0.02, 0.07) * w
            img[y:y + 2, int(x0):int(x0 + ln)] += top * 0.35
    light = mood.get("sun") or (mood.get("moon") and (*mood["moon"][:2], 0, "#8c8a7a", 0.5))
    if light:
        lx, col = light[0] * w, rgb(light[3])
        for y in range(Y0 + 6, int(0.95 * h), 9):
            f = 1 - (y - Y0) / (0.95 * h - Y0)
            half = (0.010 + 0.04 * f) * w * rng.uniform(0.3, 1.0)
            x0 = lx + rng.uniform(-0.01, 0.01) * w
            img[y:y + 3, int(x0 - half):int(x0 + half)] += col * 0.20 * f
    horizon = rgb(mood["sky"][-1][1])
    for mask, tone in ((art["far"], horizon * 0.42), (art["mid"], horizon * 0.20), (art["front"], np.array([5., 6, 9])),
                       (art["boats"], np.array([4., 5, 8]))):
        a = soft(mask)
        img = img * (1 - a) + tone * a
    a = soft(art["windows"])
    img = img * (1 - a) + (WINDOW * mood["lights"] + horizon * 0.5 * (1 - mood["lights"])) * a
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((W, H), Image.LANCZOS)
    OUT.mkdir(exist_ok=True)
    path = OUT / f"{name}.jpg"
    out.save(path, quality=84, optimize=True, progressive=True)
    print(f"{path.name}: {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    rng = np.random.default_rng(1453)
    hulls, windows = boats()
    art = dict(far=far_layer(), mid=mid_layer(rng), front=front_layer(), boats=hulls, windows=windows)
    lattice = star_lattice()
    for f in OUT.glob("*.jpg"):
        f.unlink()
    for name, mood in MOODS.items():
        draw(name, mood, art, lattice)

#!/usr/bin/env python3
"""Draws the wallpapers of themes/stockholm from nothing but code: Riddarfjärden seen from Södermalm, with
Stadshuset and its three crowns, Riddarholmen's openwork spire, Gamla stan, the Royal Palace and the Hötorget
towers behind, in eight lights from dawn to a winter night. No photos, artwork or fonts are used, so the
pictures are original and anyone can redraw them.

    python3 make_wallpapers.py            # needs Pillow and NumPy; writes wallpapers/*.jpg and captions.txt
    python3 make_wallpapers.py natt       # only the pictures whose file name contains "natt"

The same seeds give the same pictures. They stay dark where the launcher draws (themes/README.md,
Wallpapers): the clock and the buttons sit on the top corners and the dock on the bottom third, so the
light lives on the horizon.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

W, H = 1920, 1080
S = 2                                   # shapes are drawn at 2x and scaled down for clean edges
HY = 704                                # the waterline
OUT = Path(__file__).resolve().parent / "wallpapers"
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)
BICUBIC, BOX = Image.Resampling.BICUBIC, Image.Resampling.BOX

# One picture per light. The sky stops run from the top (0) to the waterline (1).
MOODS = {
    "01-gryning": dict(
        caption="Gryning över Riddarfjärden · Dawn over Riddarfjärden",
        sky=[(0.00, (8, 14, 34)), (0.40, (26, 38, 80)), (0.70, (92, 80, 128)), (0.88, (206, 134, 134)),
             (1.00, (242, 182, 144))],
        silhouette=(14, 17, 34), haze=0.42, water=(12, 16, 36),
        sun=dict(x=1580, y=HY + 34, disc=0, glow=(255, 166, 120), size=520, strength=0.55),
        clouds=dict(lit=(255, 158, 146), dark=(44, 42, 76), amount=0.55, low=330, high=610),
        windows=0.10, stars=0.22, boat=True, mist=0.22),
    "02-morgondis": dict(
        caption="Morgondis vid Stadshuset · Morning mist at City Hall",
        sky=[(0.00, (20, 30, 52)), (0.45, (62, 82, 112)), (0.80, (136, 146, 160)), (1.00, (178, 176, 172))],
        silhouette=(44, 52, 72), haze=0.55, water=(34, 44, 62),
        sun=dict(x=1290, y=455, disc=15, glow=(255, 234, 196), size=300, strength=0.30),
        windows=0.0, stars=0.0, boat=False, mist=0.62, flag=True),
    "03-gyllene-timmen": dict(
        caption="Gyllene timmen bakom Stadshuset · Golden hour behind City Hall",
        sky=[(0.00, (14, 13, 30)), (0.35, (56, 36, 62)), (0.65, (164, 82, 58)), (0.85, (236, 142, 66)),
             (1.00, (255, 194, 110))],
        silhouette=(22, 13, 17), haze=0.32, water=(24, 16, 20),
        sun=dict(x=470, y=598, disc=25, glow=(255, 186, 104), size=560, strength=0.9),
        clouds=dict(lit=(255, 168, 88), dark=(62, 30, 40), amount=0.45, low=300, high=620),
        windows=0.05, stars=0.0, boat=True, rim=0.85, flag=True),
    "04-bla-timmen": dict(
        caption="Blå timmen i Gamla stan · Blue hour in Gamla stan",
        sky=[(0.00, (4, 8, 26)), (0.45, (13, 28, 72)), (0.78, (34, 62, 126)), (0.93, (104, 100, 146)),
             (1.00, (190, 124, 110))],
        silhouette=(8, 12, 28), haze=0.40, water=(7, 13, 32),
        sun=dict(x=430, y=HY + 46, disc=0, glow=(255, 148, 98), size=500, strength=0.30),
        windows=0.40, stars=0.10, boat=True, flood=0.9, cityglow=0.10),
    "05-natt": dict(
        caption="Natt över Riddarholmen · Night over Riddarholmen",
        sky=[(0.00, (2, 3, 10)), (0.55, (6, 11, 28)), (1.00, (20, 28, 54))],
        silhouette=(4, 6, 14), haze=0.30, water=(4, 7, 18),
        moon=dict(x=1010, y=330), windows=0.30, stars=1.0, boat=True, flood=1.0, cityglow=0.12),
    "06-norrsken": dict(
        caption="Norrsken över Stockholm · Northern lights over Stockholm",
        sky=[(0.00, (2, 4, 12)), (0.55, (5, 12, 26)), (1.00, (12, 28, 40))],
        silhouette=(3, 6, 12), haze=0.30, water=(3, 8, 16),
        aurora=1.0, windows=0.14, stars=0.85, flood=0.6, cityglow=0.05),
    "07-vinternatt": dict(
        caption="Vinternatt med snö · Winter night with snow",
        sky=[(0.00, (4, 7, 18)), (0.55, (14, 22, 44)), (1.00, (44, 52, 78))],
        silhouette=(10, 13, 24), haze=0.35, water=(9, 13, 25),
        snow=True, advent=True, windows=0.42, stars=0.30, flood=0.8, cityglow=0.14),
    "08-midsommarnatt": dict(
        caption="Midsommarnatt · Midsummer night",
        sky=[(0.00, (18, 20, 52)), (0.40, (62, 56, 114)), (0.72, (174, 124, 162)), (0.90, (236, 180, 166)),
             (1.00, (250, 208, 176))],
        silhouette=(28, 26, 50), haze=0.45, water=(28, 28, 54),
        sun=dict(x=820, y=HY + 62, disc=0, glow=(255, 188, 150), size=600, strength=0.5),
        clouds=dict(lit=(255, 188, 178), dark=(86, 76, 126), amount=0.32, low=380, high=640),
        windows=0.05, stars=0.0, boat=True, mist=0.18),
}


# ----------------------------------------------------------------------------------------------- helpers

def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def gradient(stops, n):
    """n rows of colour from (fraction, (r, g, b)) stops."""
    t = np.linspace(0.0, 1.0, n)
    out = np.zeros((n, 3), np.float32)
    for c in range(3):
        out[:, c] = np.interp(t, [p for p, _ in stops], [rgb[c] for _, rgb in stops])
    return out


def box(a, r, axis):
    """Box blur of radius r along one axis, edges clamped."""
    if r < 1:
        return a
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float64)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(a, r, axis=None, passes=3):
    """Close to a Gaussian: box passes, on one axis or on both."""
    for _ in range(passes):
        for ax in ((0, 1) if axis is None else (axis,)):
            a = box(a, int(r), ax)
    return a


def noise(shape, cy, cx, rng):
    """Smooth value noise in 0..1 with cells cy tall and cx wide."""
    h, w = shape
    grid = rng.random((int(h / cy) + 3, int(w / cx) + 3)).astype(np.float32)
    return np.asarray(Image.fromarray(grid).resize((w, h), BICUBIC), np.float32).clip(0, 1)


def fbm(shape, cy, cx, rng, octaves=4):
    out, total, amp = np.zeros(shape, np.float32), 0.0, 1.0
    for i in range(octaves):
        out += amp * noise(shape, max(1.0, cy / 2 ** i), max(1.0, cx / 2 ** i), rng)
        total += amp
        amp *= 0.5
    return out / total


def over(img, rgb, alpha):
    """Paint colour rgb (an (r, g, b) or an image) over img with alpha (rows x columns)."""
    a = alpha[..., None]
    return img * (1.0 - a) + np.asarray(rgb, np.float32) * a


class Layer:
    """A silhouette drawn at 2x, plus the boxes its lit windows may fall in."""

    def __init__(self):
        self.im = Image.new("L", (W * S, H * S), 0)
        self.d = ImageDraw.Draw(self.im)
        self.facades = []

    def poly(self, pts, fill=255):
        self.d.polygon([(x * S, y * S) for x, y in pts], fill=fill)

    def rect(self, x0, y0, x1, y1, fill=255):
        self.d.rectangle([x0 * S, y0 * S, x1 * S - 1, y1 * S - 1], fill=fill)

    def ellipse(self, x0, y0, x1, y1, fill=255):
        self.d.ellipse([x0 * S, y0 * S, x1 * S, y1 * S], fill=fill)

    def mask(self):
        return np.asarray(self.im.resize((W, H), BOX), np.float32) / 255.0


def stepped(x0, x1, top, steps, rise):
    """A house with a stepped gable, as in Gamla stan."""
    run = (x1 - x0) / (2 * steps + 1)
    left, x, y = [], x0, top
    for _ in range(steps):
        left += [(x, y), (x, y - rise)]
        x, y = x + run, y - rise
    left.append((x, y))
    right = [(x1 - (px - x0), py) for px, py in reversed(left)]
    return [(x0, HY)] + left + right + [(x1, HY)]


# ---------------------------------------------------------------------------------------------- skyline

def far_layer():
    """Norrmalm and Kungsholmen across the water: low roofs, the Hötorget towers, Klara's spire, Kaknäs."""
    L, rng, x = Layer(), np.random.default_rng(59), 0
    while x < W:
        w = int(rng.integers(26, 84))
        top = HY - int(rng.integers(14, 44))
        L.rect(x, top, x + w, HY)
        if rng.random() < 0.3:
            L.poly([(x, top), (x + w / 2, top - int(rng.integers(6, 14))), (x + w, top)])
        L.facades.append((x + 3, top + 4, x + w - 3, HY - 3, 0.9))
        x += w
    for i in range(5):                                       # Hötorgsskraporna
        x0 = 772 + i * 30
        L.rect(x0, 588, x0 + 18, HY)
        L.rect(x0 + 5, 582, x0 + 13, 588)
        L.facades.append((x0 + 2, 594, x0 + 16, HY - 3, 1.0))
    L.rect(726, 610, 744, HY)                                # Klara kyrka
    L.rect(724, 604, 746, 610)
    L.poly([(726, 604), (744, 604), (735, 468)])
    L.rect(1709, 520, 1715, HY)                              # Kaknästornet
    L.rect(1700, 503, 1724, 521)
    L.rect(1711, 452, 1713, 503)
    return L


def mid_layer():
    """Stadshuset, Riddarholmen, Riddarhuset, Gamla stan and the Royal Palace."""
    L = Layer()
    # the trees along Norr Mälarstrand: an uneven canopy, a few trunks showing under it
    rng = np.random.default_rng(1923)
    tops = HY - 30 - 10 * np.sin(np.arange(0, 120, 4) / 9.0) - rng.integers(0, 9, 30)
    L.poly([(0, HY)] + [(x, float(t)) for x, t in zip(range(0, 120, 4), tops)] + [(118, HY - 18), (118, HY)])
    for x in (10, 34, 61, 88):
        L.rect(x, HY - 18, x + 2, HY, fill=0)
    # Stadshuset, its tower at the water
    L.rect(118, 640, 210, HY)
    L.rect(200, 612, 600, HY)
    L.poly([(200, 612), (212, 602), (588, 602), (600, 612)])
    L.rect(200, 588, 218, 612)
    L.poly([(198, 588), (220, 588), (209, 566)])
    for x in (262, 318, 446, 540):
        L.rect(x, 594, x + 5, 603)
    L.rect(380, 586, 398, 602)
    L.poly([(378, 586), (400, 586), (389, 570)])
    L.rect(600, 628, 722, HY)
    L.poly([(600, 628), (610, 620), (712, 620), (722, 628)])
    L.rect(590, 410, 650, 612)
    L.rect(584, 402, 656, 410)
    L.rect(598, 362, 642, 402)
    for x in (592, 640):
        L.poly([(x, 402), (x + 8, 402), (x + 4, 382)])
    L.rect(609, 334, 631, 362)
    L.ellipse(606, 318, 634, 346)
    L.rect(618, 296, 622, 320)
    L.facades += [(122, 646, 206, HY - 4, 1.0), (222, 618, 590, HY - 4, 0.3), (604, 634, 720, HY - 4, 0.6),
                  (598, 424, 642, 600, 0.4, (4, 10, 11, 24))]
    # Riddarholmen: Wrangel's tower, the church and its openwork spire, Birger Jarl's tower
    L.rect(905, 640, 1000, HY)
    L.rect(912, 610, 940, 640)
    L.poly([(910, 610), (942, 610), (926, 584)])
    L.rect(980, 640, 1120, HY)
    L.poly([(980, 640), (995, 622), (1105, 622), (1120, 640)])
    L.rect(1012, 560, 1046, 640)
    L.rect(1008, 552, 1050, 560)
    L.poly([(1006, 552), (1052, 552), (1029, 404)])
    for k in range(9):
        t = (k + 0.7) / 10.5
        y = 552 - t * 148
        r = max(1.1, (1 - t) * 4.6)
        for side in (-1, 1):
            cx = 1029 + side * (1 - t) * 23 * 0.45
            L.poly([(cx, y - r * 1.7), (cx + r, y), (cx, y + r * 1.7), (cx - r, y)], fill=0)
    L.rect(1120, 646, 1186, HY)
    L.rect(1140, 625, 1168, 646)
    L.poly([(1138, 625), (1170, 625), (1154, 600)])
    L.facades += [(908, 648, 998, HY - 4, 0.6), (1124, 652, 1184, HY - 4, 0.7)]
    # Riddarhuset and its two-storey roof
    L.rect(1195, 642, 1330, HY)
    L.poly([(1195, 642), (1206, 624), (1214, 622), (1224, 606), (1301, 606), (1311, 622), (1319, 624),
            (1330, 642)])
    L.facades.append((1200, 648, 1326, HY - 4, 0.35))
    # Gamla stan's gables, Storkyrkan and Tyska kyrkan
    rng, x = np.random.default_rng(1252), 1330
    while x < 1640:
        w = int(rng.integers(18, 36))
        top = HY - int(rng.integers(54, 92))
        if rng.random() < 0.45:
            L.poly(stepped(x, x + w, top, int(rng.integers(2, 4)), int(rng.integers(4, 7))))
        elif rng.random() < 0.6:
            L.poly([(x, HY), (x, top), (x + w / 2, top - int(rng.integers(8, 16))), (x + w, top), (x + w, HY)])
        else:
            L.rect(x, top, x + w, HY)
        L.facades.append((x + 3, top + 6, x + w - 3, HY - 4, 1.1))
        x += w
    L.rect(1462, 528, 1490, HY)
    L.rect(1458, 522, 1494, 528)
    L.ellipse(1463, 496, 1489, 530)
    L.rect(1471, 484, 1481, 500)
    L.poly([(1471, 486), (1481, 486), (1476, 458)])
    L.rect(1561, 556, 1583, HY)
    L.rect(1557, 550, 1587, 556)
    L.poly([(1559, 550), (1585, 550), (1572, 416)])
    # Kungliga slottet, its balustrade, and Skeppsbron
    L.rect(1640, 598, 1906, HY)
    L.rect(1740, 590, 1806, 598)
    for x in range(1642, 1904, 6):
        L.rect(x, 593, x + 2, 598)
    L.rect(1906, 650, 1920, HY)
    L.facades += [(1646, 606, 1902, HY - 4, 0.4)]
    return L


FLOODS = [  # (x0, y0, x1, y1, colour, strength): floodlit at night, brightest at the foot
    (584, 290, 660, 612, (255, 176, 104), 0.34),
    (1004, 400, 1054, 640, (190, 200, 222), 0.20),
    (1456, 456, 1496, 640, (255, 186, 120), 0.20),
    (1640, 588, 1906, HY, (255, 196, 140), 0.12),
]


def boat_layer(wl=HY + 46, x0=742):
    """A Waxholm steamer heading west, its windows lit."""
    L = Layer()
    L.poly([(x0, wl - 12), (x0 + 116, wl - 12), (x0 + 110, wl), (x0 + 10, wl)])
    L.rect(x0 + 18, wl - 22, x0 + 98, wl - 12)
    L.rect(x0 + 26, wl - 30, x0 + 58, wl - 22)
    L.rect(x0 + 70, wl - 40, x0 + 80, wl - 22)
    L.rect(x0 + 14, wl - 46, x0 + 16, wl - 12)
    L.facades.append((x0 + 20, wl - 20, x0 + 96, wl - 13, 1.0, (4, 4, 7, 10)))
    return L, wl


# ------------------------------------------------------------------------------------------------- sky

def sun_glow(img, s):
    r = np.hypot(XX[:HY] - s["x"], (YY[:HY] - s["y"]) * 1.25)
    glow = s["strength"] * (0.6 * np.exp(-r / (s["size"] * 0.16)) + 0.4 * np.exp(-r / s["size"]))
    img[:HY] += glow[..., None] * np.asarray(s["glow"], np.float32)
    if s.get("disc"):
        disc = 1.0 - smoothstep(s["disc"] - 1.5, s["disc"] + 1.5, r)
        img[:HY] = over(img[:HY], (430, 400, 330), disc)   # brighter than white, so the vignette cannot grey it
    return img


def add_clouds(img, c, sun, rng):
    n = fbm((HY, W), 26, 300, rng)
    yy = YY[:HY]
    band = smoothstep(c["low"] - 60, c["low"], yy) * (1.0 - smoothstep(c["high"], c["high"] + 70, yy))
    a = np.clip((n - 0.5) * 3.6, 0, 1) * band * c["amount"]
    lit = smoothstep(c["low"], HY, yy)
    if sun:
        lit = np.clip(lit + 0.8 * np.exp(-np.hypot(XX[:HY] - sun["x"], yy - sun["y"]) / 520), 0, 1)
    dark, bright = np.asarray(c["dark"], np.float32), np.asarray(c["lit"], np.float32)
    colour = dark * (1 - lit[..., None]) + bright * lit[..., None]
    img[:HY] = img[:HY] * (1 - a[..., None]) + colour * a[..., None]
    return img


def add_stars(img, amount, rng):
    n = int(2600 * amount)
    xs = rng.integers(0, W, n)
    ys = (rng.random(n) ** 1.5 * (HY - 70)).astype(int)
    lay = np.zeros((H, W), np.float32)
    np.add.at(lay, (ys, xs), rng.random(n) ** 3.2 * 0.95 + 0.06)
    lay = lay * 0.75 + blur(lay, 1, passes=2) * 2.2
    img += lay[..., None] * np.asarray((230, 236, 255), np.float32)
    return img


def add_moon(img, m):
    r = np.hypot(XX - m["x"], YY - m["y"])
    img += (0.45 * np.exp(-r / 70) + 0.18 * np.exp(-r / 260))[..., None] * np.asarray((150, 170, 210), np.float32)
    disc = 1.0 - smoothstep(20.5, 22.5, r)
    bite = 1.0 - smoothstep(19.5, 21.5, np.hypot(XX - m["x"] - 9, YY - m["y"] + 5))
    return over(img, (236, 232, 218), disc * (1.0 - bite))


def add_aurora(img, strength, rng):
    """Two curtains, a bright one in front and a fainter one behind: a lit lower edge, rays rising from it."""
    x, y = XX[:HY], YY[:HY]
    side = smoothstep(-80, 420, x) * (1 - 0.6 * smoothstep(1500, 1980, x))
    clock = 1.0 - 0.75 * np.exp(-np.hypot(x - 380, y - 120) / 260)
    green = np.asarray((40, 255, 150), np.float32)
    teal = np.asarray((40, 190, 200), np.float32)
    violet = np.asarray((190, 80, 230), np.float32)
    img[:HY] += (0.07 * strength * np.exp(-(HY - y) / 170.0))[..., None] * teal   # airglow over the horizon
    for base, tilt, wave, gain, height in ((345, -0.07, 1.0, 1.0, 120.0), (230, 0.05, 0.6, 0.5, 90.0)):
        phase = rng.random(3) * 6.28
        edge = (base + tilt * (x - W / 2)
                + wave * (64 * np.sin(x / 300 + phase[0]) + 34 * np.sin(x / 131 + phase[1])
                          + 12 * np.sin(x / 43 + phase[2]))
                + 40 * (noise((1, W), 1, 220, rng)[0] - 0.5))
        above = edge - y
        profile = np.where(above > 0, np.exp(-above / height), np.exp(above / 6.0))
        rays = 0.40 + 0.60 * fbm((HY, W), 420, 7, rng, octaves=3)
        curtain = 0.55 + 0.45 * noise((HY, W), 400, 260, rng)
        folds = 0.10 + 0.90 * noise((1, W), 1, 170, rng)[0] ** 1.4
        k = profile * rays * curtain * folds * side * clock * strength * gain
        up = np.clip(above / (2.0 * height), 0, 1)[..., None]
        colour = green * (1 - up) ** 2 + teal * 2 * up * (1 - up) + violet * 1.3 * up ** 2
        img[:HY] += k[..., None] * colour * 1.1
    return img


# --------------------------------------------------------------------------------------------- render

def windows(facades, p, rng, cell=(5, 7, 9, 12)):
    """Lit windows in the facade boxes: (x0, y0, x1, y1[, weight[, (w, h, step x, step y)]])."""
    lay = np.zeros((H, W), np.float32)
    warm = np.zeros((H, W), np.float32)
    for f in facades:
        x0, y0, x1, y1 = (int(v) for v in f[:4])
        chance = p * (f[4] if len(f) > 4 else 1.0)
        cw, ch, gx, gy = f[5] if len(f) > 5 else cell
        for yy in range(y0, y1 - ch, gy):
            for xx in range(x0, x1 - cw, gx):
                if rng.random() < chance:
                    lay[yy:yy + ch, xx:xx + cw] = 0.55 + 0.45 * rng.random()
                    warm[yy:yy + ch, xx:xx + cw] = 1.0 if rng.random() < 0.86 else 0.0
    return lay, warm


def inside(mask):
    m = mask > 0.98
    return (m & np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1)).astype(np.float32)


def render(name, p):
    rng = np.random.default_rng(sum(map(ord, name)) * 7919)
    sky = gradient(p["sky"], HY)
    img = np.zeros((H, W, 3), np.float32)
    img[:HY] = sky[:, None, :]
    horizon = sky[-40]
    sun = p.get("sun")
    if p.get("cityglow"):                                   # the city's own light on the horizon at night
        img[:HY] += (p["cityglow"] * np.exp(-(HY - YY[:HY]) / 70.0))[..., None] * np.asarray((255, 160, 100), np.float32)
    if p.get("stars"):
        img = add_stars(img, p["stars"], rng)
    if p.get("aurora"):
        img = add_aurora(img, p["aurora"], rng)
    if sun:
        img = sun_glow(img, sun)
    if p.get("moon"):
        img = add_moon(img, p["moon"])
    if p.get("clouds"):
        img = add_clouds(img, p["clouds"], sun, rng)

    far, mid = far_layer(), mid_layer()
    fm, mm = far.mask(), mid.mask()
    sil = np.asarray(p["silhouette"], np.float32)
    img = over(img, sil * (1 - p["haze"]) + horizon * p["haze"], fm)
    mid_colour = np.broadcast_to(sil, (H, W, 3)).copy()
    if p.get("rim") and sun:
        near = np.exp(-np.abs(XX - sun["x"]) / 700)
        rim = np.clip(mm - np.roll(mm, 3, 1), 0, 1) * 0.9 + np.clip(mm - np.roll(mm, 2, 0), 0, 1) * 0.6
        mid_colour += (rim * near * p["rim"])[..., None] * np.asarray(sun["glow"], np.float32)
    if p.get("flood"):
        for x0, y0, x1, y1, c, k in FLOODS:
            area = ((XX >= x0) & (XX < x1) & (YY >= y0) & (YY < y1)).astype(np.float32)
            ramp = 0.35 + 0.65 * smoothstep(y0, y1, YY)
            mid_colour += (area * ramp * k * p["flood"])[..., None] * np.asarray(c, np.float32)
    img = over(img, mid_colour, mm)

    # lit windows, the three crowns and the quay lamps: one light layer, reflected below
    light = np.zeros((H, W, 3), np.float32)
    if p.get("windows"):
        for layer, mask, scale, cell in ((far, fm, 0.55, (3, 4, 6, 8)), (mid, mm, 1.0, (5, 7, 9, 12))):
            lay, warm = windows(layer.facades, p["windows"], rng, cell)
            lay *= inside(mask) * scale
            colour = (np.asarray((255, 196, 112), np.float32) * warm[..., None]
                      + np.asarray((200, 214, 255), np.float32) * (1 - warm[..., None]))
            light += lay[..., None] * colour
        if p.get("advent"):                                  # Advent candle bridges in Gamla stan's windows
            for cx in rng.choice(np.arange(1340, 1630, 4), 9, replace=False):
                cy = int(rng.integers(650, 690))
                for dx, dy in ((-6, 0), (-4, -2), (-2, -4), (0, -6), (2, -4), (4, -2), (6, 0)):
                    light[cy + dy, cx + dx] += (255, 214, 140)
        lamps = np.zeros((H, W), np.float32)
        for x0, x1 in ((126, 720), (906, 1906)):
            for x in range(x0, x1, 30):
                lamps[HY - 6:HY - 3, x:x + 3] = 1.0
        light += lamps[..., None] * np.asarray((255, 210, 150), np.float32) * min(1.0, p["windows"] * 3)
    crowns = sum(1.0 - smoothstep(r0, r0 + 1.4, np.hypot(XX - cx, YY - cy))
                 for cx, cy, r0 in ((620, 291, 2.0), (616, 286, 1.6), (624, 286, 1.6)))
    light += np.clip(crowns, 0, 1)[..., None] * np.asarray((255, 204, 96), np.float32)
    soft = blur(light, 18) * 0.55
    scene = img + light + blur(light, 4) * 0.9 + soft
    still = img + soft                     # what the water mirrors; the lamps come back as streaks instead
    scene[HY:] = 0
    still[HY:] = 0

    if p.get("mist"):
        band = np.exp(-((YY - (HY - 4)) / 40.0) ** 2)
        haze = p["mist"] * band * (0.5 + 0.5 * fbm((H, W), 10, 320, rng))
        scene = over(scene, np.minimum(horizon * 1.15 + 18, 255), haze)
        still = over(still, np.minimum(horizon * 1.15 + 18, 255), haze)

    # the water: a rippled mirror of the scene, a long streak under every lamp
    d = np.arange(H - HY, dtype=np.float32)
    src = np.clip(HY - 1 - (d * 0.94).astype(int), 0, HY - 1)
    amp = 2.0 + 10.0 * (d / (H - HY))
    dx = ((fbm((H - HY, W), 3, 140, rng, octaves=3) - 0.5) * 2.0 * amp[:, None]).astype(np.int32)
    xs = np.clip(np.arange(W)[None, :] + dx, 0, W - 1)
    reflection = blur(still[src[:, None], xs], 2, axis=0, passes=2)
    fresnel = (0.82 - 0.42 * smoothstep(0, H - HY, d))[:, None, None]
    water = reflection * fresnel + np.asarray(p["water"], np.float32) * (1 - fresnel)
    streak = light[src[:, None], xs]
    streak = blur(blur(streak, 14, axis=0) * 3.4 + blur(streak, 3, axis=0) * 0.5, 1, axis=1, passes=1)
    water += streak * (1.0 - 0.55 * smoothstep(0, 320, d))[:, None, None]
    shine = np.clip((noise((H - HY, W), 2, 90, rng) - 0.78) * 5.0, 0, 1) * (1.0 - smoothstep(0, 360, d))[:, None]
    water += shine[..., None] * horizon * 0.22
    moon = p.get("moon") and dict(x=p["moon"]["x"], glow=(205, 215, 235))
    for s, k in ((sun, 1.0), (moon, 0.8)):
        if s:
            cone = np.exp(-((np.arange(W)[None, :] - s["x"]) / (14 + d * 0.55)[:, None]) ** 2)
            sparks = (rng.random((H - HY, W)) > 0.982).astype(np.float32) * cone
            water += (blur(sparks, 2, axis=1, passes=1) * 3.0 * k)[..., None] * np.asarray(s["glow"], np.float32)
    img[:HY] = scene[:HY]
    img[HY:] = water

    if p.get("boat"):
        boat, wl = boat_layer()
        bm = boat.mask()
        rows = np.arange(wl, min(H, wl + 50))
        flip = np.zeros_like(bm)
        flip[rows] = bm[2 * wl - 1 - rows]
        flip[rows] = flip[rows[:, None], np.clip(np.arange(W)[None, :] + dx[rows - HY], 0, W - 1)]
        img = over(img, sil * 0.8 + np.asarray(p["water"], np.float32) * 0.2, flip * 0.5)
        img = over(img, sil, bm)
        if p.get("windows"):
            lay, _ = windows(boat.facades, 0.8, rng)
            img += (lay * inside(bm))[..., None] * np.asarray((255, 206, 130), np.float32)

    if p.get("snow"):
        for mask in (fm, mm):
            cap = np.clip(mask - np.roll(mask, 3, 0), 0, 1) * (0.6 + 0.4 * noise((H, W), 3, 9, rng))
            cap[HY:] = 0
            img = over(img, (196, 208, 232), cap * 0.9)
        ice = smoothstep(0.58, 0.66, fbm((H, W), 6, 180, rng)) * (1 - smoothstep(HY + 6, HY + 70, YY))
        ice[:HY] = 0
        img = over(img, (54, 64, 92), ice * 0.7)
        flakes = np.zeros((H, W), np.float32)
        for n, r, k in ((1400, 1, 2.6), (220, 2, 4.5)):   # far flakes, then a few big near ones
            f = np.zeros((H, W), np.float32)
            np.add.at(f, (rng.integers(0, H, n), rng.integers(0, W, n)), 0.5 + 0.5 * rng.random(n))
            flakes += blur(f, r, passes=2) * k
        img += flakes[..., None] * np.asarray((214, 224, 242), np.float32)

    if p.get("flag"):                                       # the flag on the palace, up by day only
        img = over(img, (14, 14, 18), ((XX >= 1772) & (XX < 1774) & (YY >= 548) & (YY < 590)).astype(np.float32))
        flag = ((XX >= 1774) & (XX < 1802) & (YY >= 548) & (YY < 566)).astype(np.float32)
        cross = (((XX >= 1782) & (XX < 1786)) | ((YY >= 555) & (YY < 559))).astype(np.float32)
        img = over(img, (0, 84, 150), flag * (1 - cross) * 0.9)
        img = over(img, (250, 204, 20), flag * cross * 0.9)

    # keep the corners the launcher draws on dark, then a little grain against banding
    img *= (0.58 + 0.42 * smoothstep(0, 340, YY))[..., None]
    img *= (1.0 - 0.32 * np.exp(-np.hypot((XX - 420) / 1.6, YY - 120) / 300))[..., None]
    img *= (1.0 - 0.42 * smoothstep(780, H, YY))[..., None]
    img *= (1.0 - 0.11 * (((XX - W / 2) / (W / 2)) ** 2 + ((YY - H / 2) / (H / 2)) ** 2))[..., None]
    img += rng.normal(0, 1.4, (H, W, 1)).astype(np.float32)
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def save(im, path, limit=300 * 1024):
    for q in (86, 82, 78, 74, 70):
        im.save(path, "JPEG", quality=q, optimize=True, progressive=True)
        if path.stat().st_size <= limit:
            return q
    raise SystemExit(f"{path.name} is still over {limit // 1024} KB")


def main(argv):
    OUT.mkdir(exist_ok=True)
    wanted = [a for a in argv if not a.startswith("-")]
    for name, p in MOODS.items():
        if wanted and not any(w in name for w in wanted):
            continue
        path = OUT / f"{name}.jpg"
        q = save(render(name, p), path)
        print(f"{path.name}: {path.stat().st_size // 1024} KB (quality {q})")
    (OUT / "captions.txt").write_text("".join(f"{n}.jpg\t{p['caption']}\n" for n, p in MOODS.items()),
                                      encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1:])

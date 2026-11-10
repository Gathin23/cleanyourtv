#!/usr/bin/env python3
"""Draws the wallpapers of themes/ridgelines from nothing but code: ranges of mountains in flat layers of
colour, each ridge paler than the one before it, with mist settling in the valleys between them. Eight
lights, from first light to an aurora night. No photos, artwork or fonts are used, so the pictures are
original and anyone can redraw them.

    python3 make_wallpapers.py            # needs Pillow and NumPy; writes wallpapers/*.jpg and captions.txt
    python3 make_wallpapers.py night      # only the pictures whose file name contains "night"

The same seeds give the same pictures. They stay dark where the launcher draws (themes/README.md,
Wallpapers): the clock and the buttons sit on the top corners and the dock on the bottom third, so the
light lives on the far ridges and the horizon.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

W, H = 1920, 1080
OUT = Path(__file__).resolve().parent / "wallpapers"
YY, XX = np.mgrid[0:H, 0:W].astype(np.float32)

# One picture per light. Sky stops run from the top (0) to the bottom (1) of the screen; `near` is the
# colour of the closest ridge and `far` the colour the farthest ridge fades into.
MOODS = {
    "01-first-light": dict(
        caption="First light over the ranges", seed=11,
        sky=[(0.00, (14, 12, 34)), (0.30, (44, 36, 78)), (0.52, (150, 98, 132)), (0.66, (238, 162, 140)),
             (0.80, (252, 206, 160))],
        near=(18, 14, 34), far=(206, 150, 156), mist=(236, 180, 164), mist_amount=0.30,
        sun=dict(x=1380, y=560, disc=0, glow=(255, 196, 150), size=420, strength=0.6),
        layers=6, stars=0.15),
    "02-valley-mist": dict(
        caption="Mist in the valleys", seed=23,
        sky=[(0.00, (14, 22, 36)), (0.35, (52, 72, 96)), (0.60, (132, 152, 168)), (0.80, (176, 186, 190))],
        near=(14, 22, 30), far=(150, 166, 178), mist=(182, 192, 198), mist_amount=0.62,
        sun=dict(x=620, y=420, disc=22, glow=(250, 240, 220), size=300, strength=0.35),
        layers=7, stars=0.0),
    "03-golden-hour": dict(
        caption="Golden hour on the high ridges", seed=37,
        sky=[(0.00, (24, 14, 30)), (0.28, (96, 42, 52)), (0.50, (214, 104, 58)), (0.64, (250, 164, 74)),
             (0.80, (255, 214, 130))],
        near=(34, 10, 16), far=(236, 136, 70), mist=(252, 176, 96), mist_amount=0.34,
        sun=dict(x=560, y=540, disc=34, glow=(255, 200, 120), size=560, strength=0.85),
        layers=6, stars=0.0),
    "04-dusk": dict(
        caption="Dusk, magenta to indigo", seed=41,
        sky=[(0.00, (10, 10, 34)), (0.30, (40, 26, 80)), (0.52, (138, 54, 112)), (0.68, (228, 104, 112)),
             (0.82, (250, 160, 120))],
        near=(14, 8, 30), far=(184, 76, 118), mist=(224, 112, 124), mist_amount=0.30,
        sun=dict(x=1500, y=640, disc=0, glow=(255, 140, 110), size=520, strength=0.55),
        layers=6, stars=0.10),
    "05-smoke-haze": dict(
        caption="Smoke haze in late summer", seed=53,
        sky=[(0.00, (30, 16, 14)), (0.30, (108, 52, 30)), (0.55, (206, 112, 50)), (0.75, (234, 160, 84))],
        near=(30, 12, 10), far=(208, 110, 52), mist=(230, 142, 72), mist_amount=0.48,
        sun=dict(x=1180, y=470, disc=40, glow=(255, 120, 60), size=260, strength=0.55, disc_rgb=(255, 92, 48)),
        layers=7, stars=0.0),
    "06-moonrise": dict(
        caption="Moonrise over the far peaks", seed=67,
        sky=[(0.00, (4, 6, 16)), (0.40, (12, 20, 44)), (0.65, (34, 50, 86)), (0.80, (60, 78, 112))],
        near=(4, 6, 14), far=(52, 70, 106), mist=(74, 92, 128), mist_amount=0.30,
        moon=dict(x=1320, y=330, r=34),
        layers=6, stars=0.55),
    "07-starfield": dict(
        caption="A clear night full of stars", seed=79,
        sky=[(0.00, (2, 3, 10)), (0.45, (6, 10, 28)), (0.75, (18, 28, 56))],
        near=(2, 3, 9), far=(26, 36, 66), mist=(36, 48, 82), mist_amount=0.24,
        milky_way=True, layers=5, stars=1.0),
    "08-aurora-ridge": dict(
        caption="Aurora over the ridge", seed=97,
        sky=[(0.00, (2, 4, 12)), (0.45, (4, 14, 26)), (0.75, (10, 34, 42))],
        near=(2, 5, 10), far=(18, 52, 58), mist=(28, 74, 72), mist_amount=0.26,
        aurora=1.0, layers=6, stars=0.7),
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


def over(img, rgb, alpha):
    """Paint colour rgb over img with alpha (rows x columns)."""
    a = alpha[..., None]
    return img * (1.0 - a) + np.asarray(rgb, np.float32) * a


def lerp(a, b, t):
    return tuple(float(x) + (float(y) - float(x)) * t for x, y in zip(a, b))


def ridge(rng, base, amp, peaks, sharp=False):
    """One ridge line across the screen: summed octaves of smooth 1D noise, y in pixels for every column."""
    x = np.arange(W, dtype=np.float32)
    y = np.zeros(W, np.float32)
    a, n = 1.0, peaks
    for _ in range(5):
        pts = rng.random(n + 2).astype(np.float32)
        xs = np.linspace(-W / n, W + W / n, n + 2)
        # cosine interpolation between the points keeps the slopes soft
        idx = np.clip(np.searchsorted(xs, x) - 1, 0, n)
        t = (x - xs[idx]) / (xs[idx + 1] - xs[idx])
        t = (1 - np.cos(t * np.pi)) / 2
        v = pts[idx] * (1 - t) + pts[idx + 1] * t
        if a == 1.0 and sharp:
            v = 1.0 - np.abs(2.0 * v - 1.0)          # folds the curve into ridged peaks
        y += a * v
        a *= 0.45
        n *= 2
    y = (y - y.min()) / (np.ptp(y) + 1e-6)
    return base - amp * y


def trees(rng, line, rgb_mask, density, scale):
    """Pine silhouettes standing on a ridge line, drawn on a 2x mask for clean edges."""
    img = Image.new("L", (W * 2, H * 2), 0)
    d = ImageDraw.Draw(img)
    x = 0.0
    while x < W:
        x += rng.uniform(4, 40) / density
        if rng.random() < 0.35:
            continue
        xi = int(min(W - 1, x))
        h = rng.uniform(14, 46) * scale
        w = h * rng.uniform(0.26, 0.34)
        top = line[xi] - h
        tiers = 4
        for k in range(tiers):
            ty = top + h * k / (tiers + 1)
            by = top + h * (k + 1.6) / (tiers + 1) if k < tiers - 1 else line[xi] + 3
            hw = w * (k + 1) / tiers
            d.polygon([(2 * x, 2 * ty), (2 * (x - hw), 2 * by), (2 * (x + hw), 2 * by)], fill=255)
    small = img.resize((W, H), Image.Resampling.BOX)
    return np.maximum(rgb_mask, np.asarray(small, np.float32) / 255.0)


def stars(img, rng, amount, horizon):
    if amount <= 0:
        return img
    n = int(1400 * amount)
    xs = rng.integers(0, W, n)
    ys = (rng.random(n) ** 1.6 * horizon).astype(int)
    b = rng.random(n) ** 3 * 200 * amount + 25
    for x, y, v in zip(xs, ys, b):
        img[y, x] = np.maximum(img[y, x], v)
        if v > 150:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < H and 0 <= xx < W:
                    img[yy, xx] = np.maximum(img[yy, xx], v * 0.45)
    return img


def glow(img, x, y, rgb, size, strength):
    d2 = ((XX - x) ** 2 + (YY - y) ** 2) / (size * size)
    return over(img, rgb, strength * np.exp(-d2 * 2.2))


def disc(img, x, y, r, rgb):
    d = np.sqrt((XX - x) ** 2 + (YY - y) ** 2)
    return over(img, rgb, np.clip(r - d + 0.5, 0, 1))


# ----------------------------------------------------------------------------------------------- picture

def draw(name, m):
    rng = np.random.default_rng(m["seed"])
    img = np.repeat(gradient(m["sky"], H)[:, None, :], W, axis=1)

    horizon = 640
    if m.get("milky_way"):
        band = np.abs((YY - 120) - (XX - 300) * 0.32) / 150.0
        n = rng.random((H // 8 + 2, W // 8 + 2)).astype(np.float32)
        n = np.asarray(Image.fromarray(n).resize((W, H), Image.Resampling.BICUBIC), np.float32)
        img = over(img, (120, 120, 170), np.clip(np.exp(-band ** 2) * (0.10 + 0.10 * n), 0, 1))
    img = stars(img, rng, m.get("stars", 0), horizon)
    if m.get("aurora"):
        x = np.arange(W, dtype=np.float32)
        curve = (330 + 80 * np.sin(x / 300.0 + 1.2) + 30 * np.sin(x / 110.0 + 0.4))[None, :]
        # rays: smooth noise along the curtain, fine in x and stretched upwards
        rays = np.zeros(W, np.float32)
        for cells, a in ((24, 0.5), (70, 0.3), (180, 0.2)):
            pts = rng.random(cells + 2).astype(np.float32)
            rays += a * np.interp(x, np.linspace(0, W, cells + 2), pts)
        rays = (0.25 + 0.75 * smoothstep(0.35, 0.75, rays))[None, :]
        lower = np.exp(-np.maximum(YY - curve, 0) / 14.0) * (YY >= curve - 0)
        upper = np.exp(-np.maximum(curve - YY, 0) / 120.0) * (YY < curve)
        body = (lower + upper) * smoothstep(curve + 40, curve - 10, YY)
        img = over(img, (70, 230, 160), np.clip(body * rays * 0.55 * m["aurora"], 0, 1))
        img = over(img, (160, 90, 200), np.clip(np.exp(-((YY - curve + 230) / 70.0) ** 2) * rays * 0.14, 0, 1))
    if "sun" in m:
        s = m["sun"]
        img = glow(img, s["x"], s["y"], s["glow"], s["size"], s["strength"])
        if s.get("disc"):
            img = disc(img, s["x"], s["y"], s["disc"], s.get("disc_rgb", (255, 244, 220)))
    if "moon" in m:
        mo = m["moon"]
        img = glow(img, mo["x"], mo["y"], (150, 170, 210), 260, 0.25)
        img = disc(img, mo["x"], mo["y"], mo["r"], (232, 236, 240))

    n = m["layers"]
    for i in range(n):
        depth = i / (n - 1)                       # 0 farthest, 1 nearest
        base = 560 + 470 * depth ** 1.15          # where the ridge's lowest point sits
        amp = 140 + 120 * depth
        line = ridge(rng, base, amp, peaks=int(rng.integers(3, 6)), sharp=i % 2 == 1 and i < n - 2)
        col = lerp(m["far"], m["near"], depth ** 0.8)
        mask = np.clip(YY - line[None, :] + 0.5, 0.0, 1.0)
        if i >= n - 2:
            mask = trees(rng, line, mask, density=1.0 + depth, scale=0.8 + 1.4 * depth)
        layer = np.empty((H, W, 3), np.float32)
        layer[:] = col
        # mist pooling in the valley below each ridge, so the next ridge stands out against it
        if i < n - 1:
            pool = m["mist_amount"] * (1 - depth * 0.6) * smoothstep(line[None, :] + 30, line[None, :] + 260, YY)
            layer = over(layer, m["mist"], np.clip(pool, 0, 1))
        img = img * (1 - mask[..., None]) + layer * mask[..., None]

    # keep the corners for the clock and the buttons, and the bottom third for the dock, dark
    top = smoothstep(260, 0, YY) * 0.35
    img = over(img, (0, 0, 0), top)
    vign = ((XX - W / 2) / (W * 0.75)) ** 2 + ((YY - H * 0.45) / (H * 0.9)) ** 2
    img = over(img, (0, 0, 0), np.clip(vign - 0.25, 0, 0.45))

    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    path = OUT / f"{name}.jpg"
    out.save(path, "JPEG", quality=85, optimize=True, progressive=True)
    return path


def main():
    OUT.mkdir(exist_ok=True)
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    for name, m in MOODS.items():
        if only in name:
            p = draw(name, m)
            print(f"{p.name}: {p.stat().st_size // 1024} KB")
    (OUT / "captions.txt").write_text("".join(f"{k}.jpg\t{v['caption']}\n" for k, v in MOODS.items()),
                                      encoding="utf-8")


if __name__ == "__main__":
    main()

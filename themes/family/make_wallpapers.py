#!/usr/bin/env python3
"""Makes themes/family/wallpapers/*.jpg from the owner's line drawings (dark lines on cream): aile.jpg, the whole
family, for the day, and aile_V.jpg, the parents alone, for the night while the girls are asleep. The lines are
lifted off the paper and redrawn on black, in a colour that changes with the time of day, under a soft light and
a sun or moon drawn in the same line.

    python3 make_wallpapers.py            # needs Pillow and NumPy

Kept dark on purpose (see themes/README.md, Wallpapers): the drawing is scaled and moved so the clock (top left)
and the buttons (top right) stay clear and the feet stand just above the dock.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

W, H = 1920, 1080
HERE = Path(__file__).resolve().parent
OUT = HERE / "wallpapers"
SCALE, DX, DY = 1.15, 150, -20          # source pixels -> wallpaper pixels: feet land at ~0.69 of the height

BEAT = 0.462                            # about where the heartbeat line runs, as a fraction of the height
# The sun tells the hour: rising out of the heartbeat line on the right in the morning,
# setting red into it on the left in the evening; the light it throws and the line's colour shift with it, and
# night is the darkest of the three. Rising and setting suns sit on the line itself, wherever it runs.
MOODS = {
    "morning":   dict(source="aile.jpg", line="#e8c47c", light="#2e2210", sun=("rise", 0.822, BEAT, 0.022),
                      sun_light=("#3a2610", 0.22)),
    "evening":   dict(source="aile.jpg", line="#e2703f", light="#2e1208", sun=("set", 0.39, BEAT, 0.019),
                      sun_light=("#481a0a", 0.22)),
    "night":     dict(source="aile_V.jpg", line="#bdb7a2", light="#0a1220", moon=(0.80, 0.22, 0.022)),
}


def rgb(hexs):
    return np.array([int(hexs[i:i + 2], 16) for i in (1, 3, 5)], float)


def lines(source):
    """How much line each wallpaper pixel holds, 0..1, from the drawing's darkness against its paper."""
    src = Image.open(HERE / source).convert("L")
    big = src.resize((round(src.width * SCALE), round(src.height * SCALE)), Image.LANCZOS)
    canvas = Image.new("L", (W, H), 243)
    canvas.paste(big, (DX, DY))
    return np.clip((228 - np.asarray(canvas, float)) / 150, 0, 1)


def line_row(line, col, near):
    """The row where the drawing's line crosses this column, looking within 45 px of near."""
    top = int(near) - 45
    return top + int(line[top:top + 90, col].argmax())


def sky_mark(mood, line):
    """The sun or moon in the drawing's line, and where the sun stands (for its light)."""
    m = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(m)
    centre = None
    if mood.get("sun"):
        kind, x, y, r = mood["sun"]
        X, Y, R = x * W, y * H, r * W
        if kind == "full":                         # a whole sun with eight rays
            rays = range(0, 360, 45)
        else:                                      # half out of the heartbeat line, or sinking into it
            Y = line_row(line, int(X), Y) + (0 if kind == "rise" else 0.45 * R)
            rays = range(215, 326, 36) if kind == "rise" else range(235, 306, 35)
        d.ellipse([X - R, Y - R, X + R, Y + R], outline=255, width=3)
        for a in rays:
            c, s = np.cos(np.radians(a)), np.sin(np.radians(a))
            d.line([(X + c * R * 1.35, Y + s * R * 1.35), (X + c * R * 1.75, Y + s * R * 1.75)], fill=255, width=3)
        if kind != "full":                         # nothing below the line, column by column
            for col in range(int(X - 1.2 * R), int(X + 1.2 * R) + 1):
                cut = line_row(line, col, Y - (0 if kind == "rise" else 0.45 * R)) - 1
                d.line([(col, min(cut, Y) if kind == "rise" else cut), (col, Y + 2 * R)], fill=0)   # a rising sun stays a half
        centre = (X, Y)
    if mood.get("moon"):
        x, y, r = mood["moon"]
        X, Y, R = x * W, y * H, r * W
        d.arc([X - R, Y - R, X + R, Y + R], 60, 300, fill=255, width=3)
        X2, R2 = X + 0.42 * R, 0.80 * R
        d.arc([X2 - R2, Y - R2, X2 + R2, Y + R2], 110, 250, fill=255, width=3)
    return np.asarray(m, float) / 255, centre


def draw(name, mood):
    img = np.zeros((H, W, 3))
    yy, xx = np.ogrid[:H, :W]
    d2 = ((xx - 0.62 * W) / (0.45 * W)) ** 2 + ((yy - 0.50 * H) / (0.40 * H)) ** 2
    img += np.exp(-d2)[..., None] * rgb(mood["light"])               # a soft light behind the family
    line = lines(mood["source"])
    mark, sun = sky_mark(mood, line)
    if mood.get("sun_light"):                                        # and the sun's own, from where it stands
        col, radius = mood["sun_light"]
        img += np.exp(-(((xx - sun[0]) ** 2 + (yy - sun[1]) ** 2) / (radius * W) ** 2))[..., None] * rgb(col)
    if mood.get("moon"):
        rng = np.random.default_rng(7)
        for x, y, b in zip(rng.uniform(0, W, 220).astype(int), rng.uniform(0, 0.45 * H, 220).astype(int),
                           rng.uniform(40, 130, 220)):
            img[y:y + 2, x:x + 2] += b
    a = np.maximum(line, mark)[..., None]
    img = img * (1 - a) + rgb(mood["line"]) * a
    OUT.mkdir(exist_ok=True)
    path = OUT / f"{name}.jpg"
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).save(path, quality=86, optimize=True, progressive=True)
    print(f"{path.name}: {path.stat().st_size // 1024} KB")


if __name__ == "__main__":
    for name, mood in MOODS.items():
        draw(name, mood)

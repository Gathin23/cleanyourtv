"""Regenerate three original abstract wallpapers. Requires Pillow; the generated art is CC0-1.0."""
from pathlib import Path
from math import exp, sin, pi
from PIL import Image

PALETTES = [
    ('01-tidal.jpg', (7, 19, 31), (15, 134, 145), (70, 74, 152)),
    ('02-violet.jpg', (17, 13, 32), (107, 57, 151), (34, 115, 145)),
    ('03-amber.jpg', (24, 17, 25), (165, 85, 75), (83, 60, 141)),
]

def make():
    out = Path(__file__).resolve().parent / 'wallpapers'
    out.mkdir(exist_ok=True)
    width, height = 480, 270
    for name, base, first, second in PALETTES:
        image = Image.new('RGB', (width, height))
        pixels = image.load()
        for y in range(height):
            ny = y / (height - 1)
            for x in range(width):
                nx = x / (width - 1)
                glow = exp(-((nx - .76) ** 2 / .16 + (ny - .43) ** 2 / .19))
                ribbon = exp(-((ny - (.54 + .17 * sin(2 * pi * nx - .8))) / .115) ** 2)
                edge = exp(-((nx - .19) ** 2 / .09 + (ny - .68) ** 2 / .14))
                shade = .48 + .52 * (1 - ny)
                pixels[x, y] = tuple(max(0, min(255, round((base[i] + .65 * first[i] * glow + .34 * second[i] * ribbon + .16 * second[i] * edge) * shade))) for i in range(3))
        image.resize((1920, 1080), Image.Resampling.BICUBIC).save(out / name, quality=90, optimize=True)
        print(name)

if __name__ == '__main__':
    make()

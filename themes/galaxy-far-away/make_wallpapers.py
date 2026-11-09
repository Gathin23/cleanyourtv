#!/usr/bin/env python3
"""Build the galaxy-far-away wallpapers: Star Wars filming locations and NASA's Kepler-16b, under a starry sky.

Each picture is a photo from Wikimedia Commons with a dark starry sky drawn over its top third and a long,
soft fade into the photo, so the clock and the caption always sit on a dark, even background. The dark is
kept above video black (16): on a TV that reads the HDMI signal as limited range, anything darker turns
pure black and the fade shows a hard line where it starts. Light dithering keeps the 8-bit gradient free
of steps.

Usage: python3 make_wallpapers.py [--cache DIR]
Downloads the originals (or reads them from --cache, named as on Commons) and writes wallpapers/*.jpg
and wallpapers/captions.txt next to this script. Needs Pillow and NumPy.
"""
import argparse
import os
import re
import urllib.parse
import urllib.request

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "wallpapers")
W, H = 1920, 1080
SKY = np.array((22, 26, 36), np.float32)

# (output name, Commons file, caption)
PICTURES = [
    ("01-tatooine-kepler16.jpg", "Alien sunset.jpg", "Tatooine · Kepler-16b, a planet with two suns · NASA/JPL-Caltech"),
    ("02-tatooine-kepler16-system.jpg", "Kepler-16.jpg", "Tatooine · the Kepler-16 system · NASA/JPL-Caltech"),
    ("03-tatooine-mos-espa.jpg", "Ong Jmel, Mos Espa, Star Wars set, Nefta, Tunisie DSC 2022.jpg", "Tatooine · Mos Espa set, Tunisia · Habib M'henni"),
    ("04-tatooine-mos-espa.jpg", "Ong Jmel, Mos Espa, Star Wars set, Nefta, Tunisie DSC 1974.jpg", "Tatooine · Mos Espa set, Tunisia · Habib M'henni"),
    ("05-ahch-to-skellig.jpg", "Skellig Michael03(js).jpg", "Ahch-To · Skellig Michael, Ireland · Jerzy Strzelecki"),
    ("06-ahch-to-skellig.jpg", "Skellig Michael, Co. Kerry, Ireland.jpg", "Ahch-To · Skellig Michael, Ireland · Notsmallworld"),
    ("07-ahch-to-skellig.jpg", "Skellig Michael Views.jpg", "Ahch-To · Skellig Michael, Ireland · NoNameIsLeft"),
    ("08-hoth-hardangerjokulen.jpg", "\"Der größte Nationalpark Norwegens\" 10.jpg", "Hoth · Hardangerjøkulen glacier, Norway · Holger Uwe Schmitt"),
    ("09-hoth-hardangerjokulen.jpg", "Hardangerjøkulen, Rembesdalen, dam, magasin (NVE VNdias 08392).jpg", "Hoth · Hardangerjøkulen glacier, Norway · Knut Ove Hillestad"),
    ("10-tatooine-death-valley.jpg", "Ridge at Dante's View under a stormy sky, Death Valley National Park, California.jpg", "Tatooine · Death Valley, USA · Christian David"),
    ("11-yavin4-tikal.jpg", "Tikal Temple IV, 2022 01.jpg", "Yavin 4 · Tikal, Guatemala · Simon Burchell"),
    ("12-yavin4-tikal.jpg", "Talud-Tablero Temple (5C-49), Tikal 06.jpg", "Yavin 4 · Tikal, Guatemala · Simon Burchell"),
    ("13-yavin4-tikal.jpg", "Tikal From Temple IV.jpg", "Yavin 4 · Tikal, Guatemala · PStreet"),
    ("14-endor-redwoods.jpg", "Stout Memorial Grove in Jedediah Smith Redwoods State Park in 2011 (22).JPG", "Endor · redwoods, California · Chmee2"),
    ("15-endor-redwoods.jpg", "Redwoods Jedediah Smith Redwoods State Park 1.jpg", "Endor · redwoods, California · Miguel Vieira"),
    ("16-endor-redwoods.jpg", "Redwoods Jedediah Smith Redwoods State Park 2.jpg", "Endor · redwoods, California · Miguel Vieira"),
    ("17-naboo-balbianello.jpg", "The Villa del Balbianello in Italy.jpg", "Naboo · Villa del Balbianello, Lake Como · Sheeba Samuel"),
    ("18-naboo-balbianello.jpg", "The Villa del Balbianello.jpg", "Naboo · Villa del Balbianello, Lake Como · Sheeba Samuel"),
    ("19-jakku-liwa.jpg", "Liwa Desert.JPG", "Jakku · Liwa desert, UAE · Jc8136"),
]


def cache_name(title):
    return re.sub(r'[<>:"/\|?*]', "_", title)


def fetch(title, cache):
    if cache:
        path = os.path.join(cache, cache_name(title))
        if os.path.exists(path):
            return Image.open(path).convert("RGB")
    url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(title) + "?width=2400"
    req = urllib.request.Request(url, headers={"User-Agent": "cleanyourtv-theme-builder"})
    with urllib.request.urlopen(req, timeout=120) as r:
        data = r.read()
    if cache:
        os.makedirs(cache, exist_ok=True)
        with open(os.path.join(cache, cache_name(title)), "wb") as f:
            f.write(data)
    from io import BytesIO
    return Image.open(BytesIO(data)).convert("RGB")


def compose(im, seed, y0f=0.20, y1f=0.76, power=1.7, blur=28):
    """Starry dark top; the photo shows through from y0f to y1f along t^power, blurred at its edge."""
    top = int(H * y0f)
    ph = H - top
    s = max(W / im.width, ph / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    x = (im.width - W) // 2
    y = max(0, int((im.height - ph) * 0.45))
    full = Image.new("RGB", (W, H), tuple(int(v) for v in SKY))
    full.paste(im.crop((x, y, x + W, y + ph)), (0, top))
    sharp = np.asarray(full, np.float32)
    soft = np.asarray(full.filter(ImageFilter.GaussianBlur(blur)), np.float32)
    t = np.clip((np.arange(H) - H * y0f) / (H * (y1f - y0f)), 0, 1)
    a = (t ** power)[:, None, None]
    k = np.clip(t * 1.6, 0, 1)[:, None, None]
    photo = soft * (1 - k) + sharp * k
    yy = np.arange(H, dtype=np.float32)[:, None, None] / H
    sky = np.broadcast_to(SKY * (0.75 + 0.45 * np.clip(yy / (y0f + 0.25), 0, 1)), (H, W, 3)).copy()
    rng = np.random.default_rng(seed)
    n = 700
    xs = rng.integers(0, W, n)
    ys = (rng.random(n) ** 1.6 * H * (y1f - 0.05)).astype(int)
    br = rng.random(n) ** 3 * 150 + 35
    for x_, y_, b_ in zip(xs, ys, br):
        sky[y_, x_] = np.maximum(sky[y_, x_], b_)
        if b_ > 120 and 0 < x_ < W - 1 and 0 < y_ < H - 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                sky[y_ + dy, x_ + dx] = np.maximum(sky[y_ + dy, x_ + dx], b_ * 0.45)
    out = sky * (1 - a) + photo * a
    # dither only the sky and the fade, where 8-bit steps show; on the photo itself it would only add bytes
    out = out + (rng.random(out.shape, np.float32) - rng.random(out.shape, np.float32)) * (1 - a)
    return Image.fromarray(np.rint(out).clip(0, 255).astype(np.uint8))


def save_under(img, path, limit=295 * 1024):
    """The best JPEG quality that keeps the picture under the recipe's 300 KB."""
    from io import BytesIO
    for soften in (0, 0.6, 1.0):                 # dense foliage: a touch of smoothing before giving up quality
        src = img.filter(ImageFilter.GaussianBlur(soften)) if soften else img
        for q in range(88, 59, -2):
            buf = BytesIO()
            src.save(buf, "JPEG", quality=q, optimize=True, progressive=True)
            if buf.tell() <= limit:
                with open(path, "wb") as f:
                    f.write(buf.getvalue())
                return q, soften
    raise SystemExit(f"{path}: cannot get under {limit // 1024} KB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", help="folder with (or for) the downloaded originals")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    captions = []
    for i, (name, title, caption) in enumerate(PICTURES):
        save_under(compose(fetch(title, args.cache), seed=i), os.path.join(OUT, name))
        captions.append(f"{name}\t{caption}")
        print(name)
    with open(os.path.join(OUT, "captions.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(captions) + "\n")


if __name__ == "__main__":
    main()

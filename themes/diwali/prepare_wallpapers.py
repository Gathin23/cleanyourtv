#!/usr/bin/env python3
"""Builds wallpapers/ from eight Diwali photos on Wikimedia Commons (credited in CREDITS.md and in captions.txt).

Each original is fetched from Commons (or read from ORIGINALS if it is already there), checked against the SHA-256
below, cropped to 16:9 with the box given, resized to 1920x1080, darkened toward the bottom (the dock), the top-left
corner (the clock) and a little under the top-right buttons, and saved as a JPEG with no EXIF or other metadata.

Usage: python3 prepare_wallpapers.py ORIGINALS   (needs Pillow; ORIGINALS is a folder for the downloads)
"""
import hashlib
import os
import sys
import time
import urllib.error
import urllib.request

from PIL import Image, ImageChops, ImageDraw, ImageFilter

W, H = 1920, 1080
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wallpapers")
COMMONS = "https://upload.wikimedia.org/wikipedia/commons/"

# (output name, Commons path, SHA-256 of the original, crop box (left, top, right, bottom) on the original)
SOURCES = [
    ("01-row-of-diyas.jpg", "a/a7/Diwali_Diyas_4.jpg",
     "713edcb5666a0c368f481d8c2a518bd15b6597c8a7fc8c1603cb2dcba5760d6f", (857, 987, 4857, 3237)),
    ("02-rangoli-and-diyas.jpg", "0/04/Diya_set_in_a_rangoli.jpg",
     "52bcbcefc37c9399bf8604063e255ea824cfac3d9a5a0c2971e789e10bcef895", (0, 160, 4608, 2752)),
    ("03-diyas-and-marigolds.jpg", "5/51/The_Glow_of_Togetherness%2C_Diwali_in_Common_Households_of_India_02.jpg",
     "0a2ef9c23a07a532774759ff51be8279cbeffb0d8e60c328db083602a4d8fea1", (0, 560, 4080, 2855)),
    ("04-akash-kandils.jpg", "e/e5/Diwali_Lanterns_in_Pun_Maharashtra.jpg",
     "59f4aa4f09dc9e9180d7de56113223107a706ae78ccec50c00a0ec394d09232a", (0, 0, 4080, 2295)),
    ("05-fireworks-over-the-city.jpg", "6/68/Diwali_Fireworks%2C_Melbourne_Australia_2013.jpg",
     "f6e2833aeb6fed2f9f66b4ae70f889180c4b3715c75d796ab687c2410098c5e7", (0, 100, 3580, 2114)),
    ("06-fireworks-over-the-lake.jpg", "2/2f/Fireworks_Diwali_Chennai_India_November_2013_b.jpg",
     "709ba680685f9de785b24ecc8573b377e7145558d9f17faf5fd65bb3732155e3", (0, 0, 5367, 3019)),
    ("07-a-street-in-lights.jpg", "c/c8/DiwaliStreetKarnal.JPG",
     "4f96e01b54a11ae4ed4706bdfd1281c2bcefdc161f1fdd2054cbfed238f990c0", (0, 405, 3888, 2592)),
    ("08-four-flames.jpg", "5/50/Diwali_deepak_%28diya%29.jpg",
     "39ab58f1267105042b6fe651b7356254cbbaf3d3f4b95e4277161724d2c6a97a", (0, 200, 6016, 3584)),
]


def fetch(folder, path, sha):
    name = os.path.join(folder, os.path.basename(path).replace("%2C", ",").replace("%28", "(").replace("%29", ")"))
    if not os.path.exists(name):
        req = urllib.request.Request(COMMONS + path, headers={"User-Agent": "CleanYourTV-themes (github.com/Gathin23/cleanyourtv)"})
        for attempt in range(5):  # Commons answers 429 to quick runs of downloads; wait and try again
            try:
                with urllib.request.urlopen(req, timeout=120) as r:
                    data = r.read()
                break
            except urllib.error.HTTPError as e:
                if e.code != 429 or attempt == 4:
                    raise
                time.sleep(10 * (attempt + 1))
        with open(name, "wb") as f:
            f.write(data)
    data = open(name, "rb").read()
    if data[:3] != b"\xff\xd8\xff":
        sys.exit(f"{name} is not a JPEG")
    if hashlib.sha256(data).hexdigest() != sha:
        sys.exit(f"{name} is not the file this theme was made from (SHA-256 differs)")
    return name


def shade(img):
    """The same darkening as the other wallpaper themes: bottom for the dock, top-left for the clock, top-right buttons."""
    mask = Image.new("L", (W, H), 0)
    md = ImageDraw.Draw(mask)
    for y in range(H):
        t = max(0.0, (y - H * 0.55) / (H * 0.45))
        md.line([(0, y), (W, y)], fill=int(255 * 0.6 * t ** 1.3))
    corner = Image.new("L", (W, H), 0)
    ImageDraw.Draw(corner).ellipse([-520, -420, 780, 440], fill=int(255 * 0.62))
    ImageDraw.Draw(corner).ellipse([1220, 30, 2120, 340], fill=int(255 * 0.45))
    mask = ImageChops.lighter(mask, corner.filter(ImageFilter.GaussianBlur(160)))
    return Image.composite(Image.new("RGB", (W, H), (4, 2, 6)), img, mask)


def main(folder):
    os.makedirs(folder, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for out, path, sha, box in SOURCES:
        x0, y0, x1, y1 = box
        if abs((x1 - x0) * 9 - (y1 - y0) * 16) > 16:
            sys.exit(f"{out}: crop box is not 16:9")
        src = Image.open(fetch(folder, path, sha))
        src.load()
        img = src.convert("RGB").crop(box).resize((W, H), Image.LANCZOS)
        # a new image with only the pixels: no EXIF, ICC or XMP from the original is carried over
        clean = Image.new("RGB", (W, H))
        clean.paste(shade(img))
        for quality in range(86, 69, -2):  # the best quality that stays under 300 KB, as the theme guide asks
            dest = os.path.join(OUT, out)
            clean.save(dest, quality=quality, optimize=True, progressive=True)
            if os.path.getsize(dest) <= 300 * 1024:
                break
        print("wrote", os.path.join("wallpapers", out))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip().splitlines()[-1])
    main(sys.argv[1])

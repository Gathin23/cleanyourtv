#!/usr/bin/env python3
"""Builds wallpapers/ from four New Year photos on Wikimedia Commons (credited in CREDITS.md and in captions.txt).

Each original is fetched from Commons (or read from ORIGINALS if it is already there, under its Commons name),
checked against the SHA-256 below, converted to sRGB if it carries a colour profile, cropped to 16:9 with the box
given, resized to 1920x1080, darkened toward the bottom (the dock), the top-left corner (the clock) and a little
under the top-right buttons, and saved as a JPEG with no EXIF or other metadata.

Usage: python3 prepare_wallpapers.py ORIGINALS   (needs Pillow; ORIGINALS is a folder for the downloads)
"""
import hashlib
import io
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from PIL import Image, ImageChops, ImageCms, ImageDraw, ImageFilter

W, H = 1920, 1080
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "wallpapers")
COMMONS = "https://upload.wikimedia.org/wikipedia/commons/"

# (output name, Commons path, SHA-256 of the original, crop box (left, top, right, bottom) on the original)
SOURCES = [
    ("01-melbourne.jpg", "6/6c/Melbourne_New_Year_2014_%2811726618003%29.jpg",
     "39906752a37aeac24ed93f549a7788c1ef89a0da5110fe59937cd097a6bd92d7", (189, 0, 3892, 2083)),
    ("02-marina-bay.jpg", "c/cc/Marina-Bay_Singapore_Firework-launching-CNY-2015-03.jpg",
     "316a37a59878df5a0a62c09faded886274c275a1c8c054bf6ded98ee4ad263fd", (0, 26, 5174, 2936)),
    ("03-dubai.jpg", "1/14/Burj_Al_Arab_Fireworks_2021_New_Year.jpg",
     "24ea949af2d03e623f87474d133115e4ddfe8ee95d9a364eb68cc20d89269bca", (0, 304, 5836, 3587)),
    ("04-como.jpg", "3/33/Capodanno_2020.jpg",
     "73732cf86d003dd4c9c52c76711617247170f6549e1bd37b925bdabe1fd31af0", (0, 325, 6240, 3835)),
]


def fetch(folder, path, sha):
    # kept under its Commons name, so ORIGINALS can never be mistaken for wallpapers/ and its built files
    name = os.path.join(folder, urllib.parse.unquote(path.rsplit("/", 1)[1]))
    if not os.path.exists(name):
        req = urllib.request.Request(COMMONS + path, headers={"User-Agent": "CleanYourTV-themes (github.com/Gathin23/cleanyourtv)"})
        for attempt in range(5):  # Commons answers 429 to quick runs of downloads; wait and try again
            try:
                with urllib.request.urlopen(req, timeout=180) as r:
                    data = r.read()
                break
            except urllib.error.HTTPError as e:
                if e.code != 429 or attempt == 4:
                    raise
                time.sleep(15 * (attempt + 1))
        with open(name, "wb") as f:
            f.write(data)
    data = open(name, "rb").read()
    if data[:3] != b"\xff\xd8\xff":
        sys.exit(f"{name} is not a JPEG")
    if hashlib.sha256(data).hexdigest() != sha:
        sys.exit(f"{name} is not the file this theme was made from (SHA-256 differs)")
    return name


def icc_profile(name):
    """The colour profile a JPEG carries, or None. Pillow stops looking at the frame header and some cameras write
    the profile after it, so the segments are read here up to the scan."""
    data = open(name, "rb").read()
    chunks, i = {}, 2
    while i + 4 <= len(data) and data[i] == 0xFF and data[i + 1] != 0xDA:
        size = int.from_bytes(data[i + 2:i + 4], "big")
        body = data[i + 4:i + 2 + size]
        if data[i + 1] == 0xE2 and body[:12] == b"ICC_PROFILE\0":
            chunks[body[12]] = body[14:]
        i += 2 + size
    return b"".join(chunks[n] for n in sorted(chunks)) or None


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
        name = fetch(folder, path, sha)
        src = Image.open(name)
        src.load()
        icc = icc_profile(name)
        if icc:  # convert("RGB") alone would keep another profile's values and the TV would read them as sRGB
            src = ImageCms.profileToProfile(src, ImageCms.ImageCmsProfile(io.BytesIO(icc)),
                                            ImageCms.createProfile("sRGB"), outputMode="RGB")
        img = src.convert("RGB").crop(box).resize((W, H), Image.LANCZOS)
        # a new image with only the pixels: no EXIF, ICC or XMP from the original is carried over
        clean = Image.new("RGB", (W, H))
        clean.paste(shade(img))
        for quality in range(86, 49, -2):  # the best quality that stays under 300 KB, as the theme guide asks
            dest = os.path.join(OUT, out)
            clean.save(dest, quality=quality, optimize=True, progressive=True)
            if os.path.getsize(dest) <= 300 * 1024:
                break
        else:
            sys.exit(f"{out}: still over 300 KB at quality 50")
        print("wrote", os.path.join("wallpapers", out), os.path.getsize(dest))


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip().splitlines()[-1])
    main(sys.argv[1])

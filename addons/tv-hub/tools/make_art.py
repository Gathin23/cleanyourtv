"""Draws TV Hub's banner and launcher icon: a rounded screen with two antennas and a live dot.
Usage: python3 tools/make_art.py <font.ttf>  (any sans-serif TTF; the repo's PNGs used Outfit)."""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
BG, ACCENT, INK, LIVE = (17, 22, 29), (79, 163, 224), (242, 244, 247), (229, 72, 77)
S = 4  # supersampling


def glyph(d, cx, cy, w):
    h = w * 0.62
    x0, y0 = cx - w / 2, cy - h / 2 + w * 0.08
    lw = max(2, int(w * 0.07))
    d.line([(cx, y0), (cx - w * 0.22, y0 - w * 0.24)], fill=ACCENT, width=lw)
    d.line([(cx, y0), (cx + w * 0.22, y0 - w * 0.24)], fill=ACCENT, width=lw)
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=w * 0.14, outline=ACCENT, width=lw, fill=(24, 34, 46))
    r = w * 0.07
    d.ellipse([cx - r, cy + w * 0.08 - r, cx + r, cy + w * 0.08 + r], fill=LIVE)


def banner(font):
    W, H = 320 * S, 180 * S
    im = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(im)
    glyph(d, 78 * S, 92 * S, 92 * S)
    f = ImageFont.truetype(font, 46 * S)
    d.text((140 * S, 90 * S), "TV Hub", font=f, fill=INK, anchor="lm")
    out = RES / "drawable-xhdpi" / "banner.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    im.resize((320, 180), Image.LANCZOS).save(out, optimize=True)


def icon():
    for name, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        im = Image.new("RGBA", (px * S, px * S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.rounded_rectangle([0, 0, px * S - 1, px * S - 1], radius=px * S * 0.22, fill=BG)
        glyph(d, px * S / 2, px * S / 2, px * S * 0.62)
        out = RES / f"mipmap-{name}" / "ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        im.resize((px, px), Image.LANCZOS).save(out, optimize=True)


banner(sys.argv[1])
icon()

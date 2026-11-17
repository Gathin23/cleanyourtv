"""Draws the tile banners: banner_v0..5 for a hoàng đạo day, banner_v6..11 for a hắc đạo day, hour set v % 6,
and banner_start, the neutral one shown until the app first runs.

Usage: python3 tools/make_banners.py <NotoSerif-Regular.ttf> <Roboto-Regular.ttf>
(both are on the TV: adb pull /system/fonts/NotoSerif-Regular.ttf, /system/fonts/Roboto-Regular.ttf)
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H = 640, 360
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable-nodpi"
SETS = [
    "Tý Sửu Mão Ngọ Thân Dậu", "Dần Mão Tỵ Thân Tuất Hợi", "Tý Sửu Thìn Tỵ Mùi Tuất",
    "Tý Dần Mão Ngọ Mùi Dậu", "Dần Thìn Tỵ Thân Dậu Hợi", "Sửu Thìn Ngọ Mùi Tuất Hợi",
]


def gradient(good):
    # Radial glow from the top right over a dark base, as in the approved mockup.
    glow, mid, base = ((74, 58, 28), (29, 26, 20), (18, 19, 24)) if good else ((46, 50, 61), (24, 26, 32), (17, 18, 22))
    img = Image.new("RGB", (W, H), base)
    px = img.load()
    for y in range(H):
        for x in range(W):
            d = (((x - W * 0.85) / (W * 1.3)) ** 2 + ((y - H * 0.1) / (H * 1.2)) ** 2) ** 0.5
            if d < 0.55:
                t = d / 0.55
                c = tuple(int(glow[i] + (mid[i] - glow[i]) * t) for i in range(3))
            else:
                t = min(1, (d - 0.55) / 0.45)
                c = tuple(int(mid[i] + (base[i] - mid[i]) * t) for i in range(3))
            px[x, y] = c
    return img


def spaced(draw, xy, text, font, fill, spacing):
    x, y = xy
    for ch in text:
        draw.text((x, y), ch, font=font, fill=fill)
        x += draw.textlength(ch, font=font) + spacing


def main(serif_path, sans_path):
    serif = ImageFont.truetype(serif_path, 92)
    cap = ImageFont.truetype(sans_path, 30)
    hours_font = ImageFont.truetype(sans_path, 33)
    OUT.mkdir(parents=True, exist_ok=True)
    for v in range(12):
        good = v < 6
        img = gradient(good)
        d = ImageDraw.Draw(img)
        left = 52
        spaced(d, (left, 46), "HÔM NAY", cap, (170, 160, 140) if good else (130, 134, 145), 9)
        word = "Hoàng đạo" if good else "Hắc đạo"
        d.text((left - 4, 92), word, font=serif, fill=(236, 208, 142) if good else (194, 199, 210))
        d.line([(left, 262), (W - 40, 262)], fill=(78, 70, 56) if good else (58, 61, 70), width=2)
        hours = " · ".join(SETS[v % 6].split())
        # Shrink the hour line if needed so it fits on one line.
        f = hours_font
        size = 33
        while d.textlength(hours, font=f) > W - left - 36 and size > 22:
            size -= 1
            f = ImageFont.truetype(sans_path, size)
        d.text((left, 282), hours, font=f, fill=(244, 241, 234) if good else (200, 203, 212))
        img.save(OUT / f"banner_v{v}.png", optimize=True)
    # Before the app's first run: no verdict, just the name and how to open it.
    img = gradient(True)
    d = ImageDraw.Draw(img)
    spaced(d, (52, 46), "LỊCH VẠN NIÊN", cap, (170, 160, 140), 9)
    d.text((48, 92), "Hôm nay", font=serif, fill=(236, 208, 142))
    d.line([(52, 262), (W - 40, 262)], fill=(78, 70, 56), width=2)
    d.text((52, 282), "Bấm OK để xem ngày tốt, giờ tốt", font=hours_font, fill=(244, 241, 234))
    img.save(OUT / "banner_start.png", optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

#!/usr/bin/env python3
"""Makes themes/istanbul_v2/wallpapers/*.jpg from the owner's three pictures in originals/: each is scaled to the
TV's 1920 px width and centred on black, so its own black frame bands grow by a few pixels instead of anything
being cropped, then saved under the mood name config.json uses.

    python3 make_wallpapers.py            # needs Pillow
"""
from pathlib import Path

from PIL import Image

W, H = 1920, 1080
HERE = Path(__file__).resolve().parent
NAMES = {"morning": "ist_Morning.jpg", "evening": "ist_evening.jpg",
         "night": "ist_night.jpg"}

if __name__ == "__main__":
    out = HERE / "wallpapers"
    out.mkdir(exist_ok=True)
    for mood, name in NAMES.items():
        src = Image.open(HERE / "originals" / name).convert("RGB")
        h = round(src.height * W / src.width)
        canvas = Image.new("RGB", (W, H), (0, 0, 0))
        canvas.paste(src.resize((W, h), Image.LANCZOS), (0, (H - h) // 2))
        path = out / f"{mood}.jpg"
        canvas.save(path, quality=85, optimize=True, progressive=True)
        print(f"{path.name}: {path.stat().st_size // 1024} KB")

"""Stacks the README stat strip, the Featured in panel and the before/after TVs into one card (docs/card.jpg),
the file the front page displays.

Run tools/readme_stats.py and tools/readme_featured.py first, then:
python3 tools/readme_card.py Fredoka.ttf docs/card.jpg
"""
import sys

# See readme_stats.py: Pillow lives in the optional requirements file, so name it rather than crash.
try:
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("readme_card.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")

FONT, OUT = sys.argv[1], sys.argv[2]
BG, LINE, DIM, GREEN = "#15181e", "#2c313a", "#aab3bd", "#cfffcf"
W = 1600
def f(size, wt):
    x = ImageFont.truetype(FONT, size); x.set_variation_by_axes([wt]); return x
def spaced(d, cx, y, t, font, sp, fill):
    w = sum(d.textlength(c, font=font) for c in t) + sp * (len(t) - 1); x = cx - w / 2
    for c in t: d.text((x, y), c, font=font, fill=fill); x += d.textlength(c, font=font) + sp

# the TV screens, cropped to the bezel; the Wi-Fi name on the after shot is blurred
before = Image.open("docs/stock-home.jpg").convert("RGB").crop((130, 216, 1317, 864))
after = Image.open("docs/new-home.jpg").convert("RGB")
box = (1150, 236, 1240, 280); after.paste(after.crop(box).filter(ImageFilter.GaussianBlur(9)), box)
after = after.crop((203, 138, 1467, 792))
PAD, ARROW = 70, 90
tw = (W - 2 * PAD - ARROW) // 2
shots = [im.resize((tw, round(im.height * tw / im.width)), Image.LANCZOS) for im in (before, after)]
th = max(s.height for s in shots)
tv = Image.new("RGB", (W, 90 + th + 80), BG); d = ImageDraw.Draw(tv)
lab = f(30, 500)
for i, (s, t, c) in enumerate(zip(shots, ("BEFORE", "AFTER"), (DIM, GREEN))):
    x = PAD + i * (tw + ARROW); y = 90 + (th - s.height) // 2
    spaced(d, x + tw / 2, 34, t, lab, 8, c)
    tv.paste(s, (x, y)); d.rectangle([x - 1, y - 1, x + s.width, y + s.height], outline=LINE, width=2)
ax, ay = W / 2, 90 + th / 2
d.line([(ax - 26, ay), (ax + 26, ay)], fill=GREEN, width=5)
d.line([(ax + 8, ay - 16), (ax + 27, ay), (ax + 8, ay + 16)], fill=GREEN, width=5, joint="curve")

parts = [Image.open("docs/stats.png").convert("RGB"), Image.open("docs/featured.png").convert("RGB"), tv]
SEP = 24  # extra air on each side of a divider
H = sum(p.height for p in parts) + 2 * SEP * (len(parts) - 1)
card = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(card); y = 0
for i, p in enumerate(parts):
    if i: y += SEP; d.line([(PAD, y), (W - PAD, y)], fill=LINE, width=2); y += SEP
    card.paste(p, (0, y)); y += p.height
card.save(OUT, quality=88, optimize=True)

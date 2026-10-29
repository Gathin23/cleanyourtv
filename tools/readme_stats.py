"""Renders the stat strip (docs/stats.png), the top part of the README card.

Update STATS, then: python3 tools/readme_stats.py Fredoka.ttf docs/stats.png
and rebuild the card with tools/readme_card.py. Fredoka is the variable TTF from Google Fonts."""
import sys

# Pillow is not in tools/requirements.txt: it is only needed to redraw the README card, so point at the
# optional file the way tools/flamechart.py does, instead of ending in a bare traceback.
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("readme_stats.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")

FONT, OUT = sys.argv[1], sys.argv[2]
STATS = [("260", "TV MODELS"), ("77", "BRANDS"), ("25", "LANGUAGES"), ("497 KB", "LAUNCHER")]
W, H = 1600, 240
img = Image.new("RGB", (W, H), "#15181e"); d = ImageDraw.Draw(img)
def f(size, wt):
    x = ImageFont.truetype(FONT, size); x.set_variation_by_axes([wt]); return x
num, lab = f(92, 600), f(30, 500)
PAD = 70
col = (W - 2 * PAD) / len(STATS)
for i, (n, l) in enumerate(STATS):
    cx = PAD + col * i + col / 2
    w = d.textlength(n, font=num); d.text((cx - w / 2, 40), n, font=num, fill="#cfffcf")
    sp = 5; w = sum(d.textlength(c, font=lab) for c in l) + sp * (len(l) - 1); x = cx - w / 2
    for c in l: d.text((x, 160), c, font=lab, fill="#aab3bd"); x += d.textlength(c, font=lab) + sp
    if i: d.line([(PAD + col * i, 56), (PAD + col * i, H - 56)], fill="#2c313a", width=3)
img.save(OUT, optimize=True)

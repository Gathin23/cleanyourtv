"""Renders the Featured in panel (docs/featured.png), the middle part of the README card.

Update LOGOS / MORE, then: python3 tools/readme_featured.py Fredoka.ttf docs/featured.png
and rebuild the card with tools/readme_card.py. Logos come from docs/press/."""
import sys

# See readme_stats.py: Pillow lives in the optional requirements file, so name it rather than crash.
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("readme_featured.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")

FONT, OUT = sys.argv[1], sys.argv[2]
LOGOS = ["techcrunch", "techradar", "tomsguide", "mashable", "androidauthority", "xda", "flatpanelshd", "itsfoss",
         "phandroid", "gigazine", "xataka", "winfuture", "frandroid", "macitynet", "techaeris", "techpp"]
MORE = "+171 more  ·  187 articles from 31 countries"
W, LH, GAP, ROWGAP, PAD = 1600, 54, 40, 36, 80
def f(size, wt):
    x = ImageFont.truetype(FONT, size); x.set_variation_by_axes([wt]); return x
logos = []
for n in LOGOS:
    im = Image.open(f"docs/press/{n}.png").convert("RGBA")
    logos.append(im.resize((round(im.width * LH / im.height), LH), Image.LANCZOS))
half = (len(logos) + 1) // 2
rows = [logos[:half], logos[half:]]
widest = max(sum(i.width for i in r) + GAP * (len(r) - 1) for r in rows)
if widest > W - 2 * PAD:  # shrink every logo so the widest row fits
    k = (W - 2 * PAD - GAP * (half - 1)) / (widest - GAP * (half - 1))
    rows = [[i.resize((round(i.width * k), round(LH * k)), Image.LANCZOS) for i in r] for r in rows]
LH = rows[0][0].height
top = 120; H = top + len(rows) * LH + (len(rows) - 1) * ROWGAP + 150
img = Image.new("RGBA", (W, H), "#15181e"); d = ImageDraw.Draw(img)
lab = f(30, 500); t = "FEATURED IN"; sp = 8
w = sum(d.textlength(c, font=lab) for c in t) + sp * (len(t) - 1); x = (W - w) / 2
for c in t: d.text((x, 48), c, font=lab, fill="#e6e9ee"); x += d.textlength(c, font=lab) + sp
y = top
for r in rows:
    x = (W - (sum(i.width for i in r) + GAP * (len(r) - 1))) / 2
    for im in r: img.alpha_composite(im, (round(x), y)); x += im.width + GAP
    y += LH + ROWGAP
m = f(32, 500); w = d.textlength(MORE, font=m) + 56; x0 = (W - w) / 2; ty = y + 26
d.text((x0, ty), MORE, font=m, fill="#aab3bd")  # grey; the title is the soft white one, green stays for numbers, AFTER and arrows
ax, ay = x0 + w - 26, ty + 22  # arrow: Fredoka has no U+2192
d.line([(ax - 12, ay), (ax + 12, ay)], fill="#cfffcf", width=4)
d.line([(ax + 2, ay - 10), (ax + 13, ay), (ax + 2, ay + 10)], fill="#cfffcf", width=4, joint="curve")
img.convert("RGB").save(OUT, optimize=True)

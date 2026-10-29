"""Stacks the README stat strip, the Featured in panel and the before/after TVs into one card (docs/card.jpg),
the file the front page displays.

Run tools/readme_stats.py and tools/readme_featured.py first, then:
python3 tools/readme_card.py Fredoka.ttf docs/card.jpg
python3 tools/readme_card.py --halloween Fredoka.ttf docs/card-halloween.jpg   # the October variant
The Halloween variant keeps the layout and the generated numbers: it shows docs/stats-halloween.png
(python3 tools/readme_stats.py --halloween Fredoka.ttf docs/stats-halloween.png: the same numbers in pumpkin
orange), recolours the headings to orange, adds a thin dripping stripe and a few bats, and
shows docs/new-home-halloween.jpg as AFTER. docs/card.jpg is not touched, so the README goes back with one line.
Fredoka is the variable TTF from Google Fonts, and its file is not in this repo: pass the path of the
copy you downloaded. The four pictures it stacks are this checkout's own - docs/stock-home.jpg,
docs/new-home.jpg and the two its siblings draw - so run it from the repository root.
"""
import os
import sys

USAGE = ("usage: readme_card.py [--halloween] <font.ttf> <out.jpg>\n"
         "  Fredoka, the variable TTF from Google Fonts, is not in this repo: download it and pass the\n"
         "  path of the file you saved.")


def font_and_out(argv):
    """The font and the picture, or a usage line that says where the font comes from.

    The font is the one argument a reader brings themselves: it is not in this repo, so a wrong path is
    the first thing that fails here, and Pillow's `OSError: cannot open resource` names neither the file
    it wanted nor where to get it.
    """
    argv = [a for a in argv if a != "--halloween"]
    if len(argv) != 3:
        sys.exit(USAGE)
    font, out = argv[1], argv[2]
    if not os.path.isfile(font):
        sys.exit(f"cannot open the font at {font}: Fredoka, the variable TTF from Google Fonts, is not in\n"
                 "this repo, so pass the path of the copy you downloaded")
    return font, out


def out_problem(path):
    """Why the picture at `path` cannot be written, or None.

    The second argument the reader types, and the one Pillow only judges at the end of the draw:
    `img.save()` answers a folder with IsADirectoryError, a path through a file with NotADirectoryError,
    a folder that is not there with FileNotFoundError and a name with no extension with `unknown file
    extension` - each a traceback that names neither the argument nor whether a picture was written.
    Judged here, before Pillow is imported, so a machine without Pillow gives the same answer.
    """
    if os.path.isdir(path):
        return (f"{path} is a folder, and this writes one picture: pass a file name such as "
                f"docs/card.jpg")
    folder = os.path.dirname(path) or "."
    if not os.path.isdir(folder):
        return (f"there is no folder {folder} for {path}: create it first, or pass a name inside one "
                f"that is there")
    if not os.path.splitext(path)[1]:
        return (f"{path} has no extension, so the format to write is unknown: pass a name ending in "
                f".jpg")
    return None


HALLOWEEN = "--halloween" in sys.argv
FONT, OUT = font_and_out(sys.argv)

# The picture is the other argument the reader types, and Pillow only refuses it after the whole thing
# has been drawn: a folder, a folder that is not there, a path through a file and a suffixless name were
# each a 12-line traceback with exit 1, the code a run that wrote nothing returns, saying nothing about
# what was written. Judged before the Pillow import, so a machine without it answers the same way.
OUT_PROBLEM = out_problem(OUT)
if OUT_PROBLEM:
    sys.exit(f"{OUT_PROBLEM}. Nothing was written.")

# The pictures the card stacks are the input this script does not take as arguments: two screenshots the
# repository ships, and the two pictures its siblings draw. They are read at names relative to the
# repository root, so a run from another directory, or a sibling's picture not drawn yet, reached
# `Image.open()` in the middle of the draw and came back as `FileNotFoundError: [Errno 2] ...
# 'docs/stats.png'` - a traceback naming neither the script that draws it nor whether a picture was
# written. Judged before the Pillow import, so a machine without it answers the same way.
INPUTS = (
    ("docs/stock-home.jpg", None),
    ("docs/new-home.jpg", None),
    ("docs/stats.png", "tools/readme_stats.py draws it"),
    ("docs/featured.png", "tools/readme_featured.py draws it"),
)
HALLOWEEN_INPUTS = (("docs/new-home-halloween.jpg", None),
                    ("docs/stats-halloween.png", "tools/readme_stats.py --halloween draws it"))


def inputs_problem():
    """Why a picture the card stacks cannot be read, or None."""
    for path, drawn_by in INPUTS + (HALLOWEEN_INPUTS if HALLOWEEN else ()):
        if os.path.isdir(path):
            return (f"{path} is a folder, and the card reads one picture from it: pass this checkout's "
                    f"file, run from the repository root")
        if not os.path.isfile(path):
            if drawn_by:
                return (f"cannot read {path}: there is no such file. {drawn_by}, and the card reads it "
                        f"from the repository root, so run that first")
            return (f"cannot read {path}: there is no such file. The card stacks this checkout's own "
                    f"pictures, so run this from the repository root")
    return None


INPUTS_PROBLEM = inputs_problem()
if INPUTS_PROBLEM:
    sys.exit(f"{INPUTS_PROBLEM}. Nothing was written.")

# See readme_stats.py: Pillow lives in the optional requirements file, so name it rather than crash.
try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont
except ImportError:
    sys.exit("readme_card.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")


def load(path):
    """One picture the card stacks, or a sentence naming the file Pillow could not read.

    `inputs_problem()` has already answered whether the file is there; a file that is there and is not a
    picture - a truncated PNG from an interrupted pull, an HTML error page saved under the name - is
    Pillow's `UnidentifiedImageError` out of the open, in the middle of the draw.
    """
    try:
        return Image.open(path).convert("RGB")
    except (OSError, ValueError) as error:
        sys.exit(f"cannot read {path}: {error}. Nothing was written.")


BG, LINE, DIM, GREEN = "#15181e", "#2c313a", "#aab3bd", "#cfffcf"
ORANGE = "#ff9a3c"  # the Halloween variant's mint
W = 1600
def f(size, wt):
    # A path that exists is not yet a font: Google Fonts serves Fredoka as a zip holding the variable
    # `Fredoka[wdth,wght].ttf` and a folder of static single-weight files beside it. `truetype` answers a
    # zip with `OSError: unknown file format` and `set_variation_by_axes` answers a static file with
    # `OSError: invalid argument` - neither names the argument that was wrong, and both arrive as a
    # Pillow traceback in the middle of a draw. Name the file and what it has to be instead.
    try:
        x = ImageFont.truetype(FONT, size); x.set_variation_by_axes([wt])
    except (OSError, ValueError) as error:
        sys.exit(f"cannot use the font at {FONT}: {error}. The card is drawn with Fredoka's variable TTF\n"
                 "(Fredoka[wdth,wght].ttf from Google Fonts), not the zip it is downloaded in and not one of\n"
                 "the static single-weight files beside it, which have no weight to set. Nothing was written.")
    return x
f(12, 400)  # the font is the argument the reader brings, so a wrong one is named before any drawing

def spaced(d, cx, y, t, font, sp, fill):
    w = sum(d.textlength(c, font=font) for c in t) + sp * (len(t) - 1); x = cx - w / 2
    for c in t: d.text((x, y), c, font=font, fill=fill); x += d.textlength(c, font=font) + sp

# the TV screens, cropped to the bezel; the Wi-Fi name on the after shot is blurred
before = load("docs/stock-home.jpg").crop((130, 216, 1317, 864))
if HALLOWEEN:
    # the 1920x1080 capture of the Halloween Home; its Wi-Fi name is blurred the same way
    after = load("docs/new-home-halloween.jpg")
    box = (1390, 128, 1525, 192); after.paste(after.crop(box).filter(ImageFilter.GaussianBlur(9)), box)
    after = after.crop((60, 30, 1860, 1000))
    GREEN = ORANGE
else:
    after = load("docs/new-home.jpg")
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

def tint(img, box, src="#cfffcf", dst=ORANGE):
    """Moves the pixels in `box` that sit between the dark background and `src` the same way to `dst`.

    Anti-aliased edges keep their weight: a pixel's share of `src` is read from how far green sits above
    blue, and that share is moved to `dst`. The grey labels and the logos have blue above green, so they
    are left as they are."""
    box = tuple(box); part = img.crop(box)
    r, g, b = part.split()
    share = ImageChops.subtract(g, b).point(lambda v: min(255, v * 255 // 48))
    s_rgb, d_rgb = Image.new("RGB", (1, 1), src).getpixel((0, 0)), Image.new("RGB", (1, 1), dst).getpixel((0, 0))
    moved = Image.merge("RGB", [c.point(lambda v, k=k: max(0, min(255, v + d_rgb[k] - s_rgb[k])))
                                for k, c in enumerate((r, g, b))])
    img.paste(Image.composite(moved, part, share), box)


def heading(img, box):
    """The grey 'Featured in' heading in `box`, in orange: its brightness decides how much orange."""
    box = tuple(box); part = img.crop(box)
    light = part.convert("L").point(lambda v: max(0, min(255, (v - 24) * 255 // 206)))
    img.paste(Image.composite(Image.new("RGB", part.size, ORANGE), part, light), box)


def decorate(card):
    """A dripping stripe along the top, and a few bats at low opacity."""
    import math
    layer = Image.new("RGBA", card.size, (0, 0, 0, 0)); dl = ImageDraw.Draw(layer)
    # as on the website header and the README banner: a thin stripe, a few thin drips ending in a drop
    for x, drop in ((95, 34), (155, 20), (380, 42), (520, 24), (800, 40), (1100, 28), (1170, 36), (1335, 22),
                    (1560, 30)):
        dl.rectangle([x - 2, 0, x + 1, drop], fill=ORANGE)
        dl.ellipse([x - 4, drop - 2, x + 3, drop + 5], fill=ORANGE)
    dl.rectangle([0, 0, W, 5], fill=ORANGE)
    card.paste(Image.composite(Image.new("RGB", card.size, ORANGE), card, layer.split()[3].point(lambda v: v * 9 // 10)))
    deco = Image.new("RGBA", card.size, (0, 0, 0, 0)); dd = ImageDraw.Draw(deco)

    def bat(cx, cy, s):
        pts = [(0, -4), (3, -8), (5, -3), (16, -9), (30, -2), (22, 0), (26, 9), (14, 4), (6, 12), (0, 5)]
        pts = pts + [(-x, y) for x, y in reversed(pts)]
        dd.polygon([(cx + x * s, cy + y * s) for x, y in pts], fill=(255, 154, 60, 45))

    for cx, cy, s in ((92, 70, 1.3), (1548, 150, 1.3), (565, 352, 1.1), (1090, 478, 1.0), (140, 585, 1.2),
                      (1480, 650, 1.1), (800, 760, 1.0)):
        bat(cx, cy, s)
    card.alpha_composite(deco) if hasattr(card, "alpha_composite") and card.mode == "RGBA" else card.paste(deco, (0, 0), deco)


stats, featured = load("docs/stats-halloween.png" if HALLOWEEN else "docs/stats.png"), load("docs/featured.png")
if HALLOWEEN:
    heading(featured, (650, 50, 950, 85))     # "FEATURED IN"
    tint(featured, (1090, 315, 1135, 352))    # the arrow after "314 articles"
parts = [stats, featured, tv]
SEP = 24  # extra air on each side of a divider
H = sum(p.height for p in parts) + 2 * SEP * (len(parts) - 1)
card = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(card); y = 0
for i, p in enumerate(parts):
    if i: y += SEP; d.line([(PAD, y), (W - PAD, y)], fill=LINE, width=2); y += SEP
    card.paste(p, (0, y)); y += p.height
if HALLOWEEN:
    decorate(card)
try:
    card.save(OUT, quality=88, optimize=True)
except (OSError, ValueError) as error:
    # The read-only folder or the full disk, or an extension Pillow does not know (`.txt`): found at the
    # write, after the drawing. Name the file and say that no picture came of the run.
    sys.exit(f"cannot write {OUT}: {error}. Nothing was written.")

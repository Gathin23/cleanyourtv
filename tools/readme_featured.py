"""Renders the Featured in panel (docs/featured.png), the middle part of the README card.

Update LOGOS / MORE, then: python3 tools/readme_featured.py Fredoka.ttf docs/featured.png
and rebuild the card with tools/readme_card.py. Logos come from docs/press/, at names relative to the
repository root, so run it from there. Fredoka is the variable TTF from Google Fonts, and its file is not
in this repo: pass the path of the copy you downloaded.
"""
import os
import sys

USAGE = ("usage: readme_featured.py <font.ttf> <out.png>\n"
         "  Fredoka, the variable TTF from Google Fonts, is not in this repo: download it and pass the\n"
         "  path of the file you saved.")


def font_and_out(argv):
    """The font and the picture, or a usage line that says where the font comes from.

    The font is the one argument a reader brings themselves: it is not in this repo, so a wrong path is
    the first thing that fails here, and Pillow's `OSError: cannot open resource` names neither the file
    it wanted nor where to get it.
    """
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
                f"docs/featured.png")
    folder = os.path.dirname(path) or "."
    if not os.path.isdir(folder):
        return (f"there is no folder {folder} for {path}: create it first, or pass a name inside one "
                f"that is there")
    if not os.path.splitext(path)[1]:
        return (f"{path} has no extension, so the format to write is unknown: pass a name ending in "
                f".png")
    return None


FONT, OUT = font_and_out(sys.argv)

# The picture is the other argument the reader types, and Pillow only refuses it after the whole thing
# has been drawn: a folder, a folder that is not there, a path through a file and a suffixless name were
# each a 12-line traceback with exit 1, the code a run that wrote nothing returns, saying nothing about
# what was written. Judged before the Pillow import, so a machine without it answers the same way.
OUT_PROBLEM = out_problem(OUT)
if OUT_PROBLEM:
    sys.exit(f"{OUT_PROBLEM}. Nothing was written.")

LOGOS = ["techcrunch", "techradar", "tomsguide", "mashable", "androidauthority", "xda", "flatpanelshd", "itsfoss",
         "phandroid", "gigazine", "xataka", "winfuture", "frandroid", "macitynet", "techaeris", "techpp"]
MORE = "+297 more  ·  313 articles from 44 countries"


def logo_problem():
    """Why a logo this panel draws cannot be read, or None.

    The pictures are the input this script does not take as arguments: `docs/press/<name>.png` for each
    name in LOGOS, read from wherever the reader stands, so a run from another directory, or a logo named
    in LOGOS whose picture was renamed, reached `Image.open()` in the middle of the draw and came back as
    `FileNotFoundError: [Errno 2] ... 'docs/press/xda.png'` - a traceback naming neither LOGOS nor this
    checkout, and saying nothing about whether the panel was written. Judged before the Pillow import, so
    a machine without it answers the same way.
    """
    for name in LOGOS:
        path = f"docs/press/{name}.png"
        if os.path.isdir(path):
            return (f"{path} is a folder, and the panel draws one picture from it: pass this checkout's "
                    f"file, run from the repository root")
        if not os.path.isfile(path):
            return (f"cannot read {path}: there is no such file. The logos the panel draws are the names "
                    f"in LOGOS here, read from docs/press/ in this checkout, so run this from the "
                    f"repository root and give a renamed logo its picture")
    return None


LOGO_PROBLEM = logo_problem()
if LOGO_PROBLEM:
    sys.exit(f"{LOGO_PROBLEM}. Nothing was written.")

# See readme_stats.py: Pillow lives in the optional requirements file, so name it rather than crash.
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("readme_featured.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")
W, LH, GAP, ROWGAP, PAD = 1600, 54, 40, 36, 80
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
logos = []
for n in LOGOS:
    try:
        im = Image.open(f"docs/press/{n}.png").convert("RGBA")
    except (OSError, ValueError) as error:
        # A picture that is there and is not one: a truncated PNG, a file an interrupted copy left, an
        # HTML error page saved under the logo's name. Found at the open, after the font was loaded.
        sys.exit(f"cannot read docs/press/{n}.png: {error}. Nothing was written.")
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
try:
    img.convert("RGB").save(OUT, optimize=True)
except (OSError, ValueError) as error:
    # The read-only folder or the full disk, or an extension Pillow does not know (`.txt`): found at the
    # write, after the drawing. Name the file and say that no picture came of the run.
    sys.exit(f"cannot write {OUT}: {error}. Nothing was written.")

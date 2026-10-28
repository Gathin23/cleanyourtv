"""Renders the stat strip (docs/stats.png), the top part of the README card.

Update STATS, then: python3 tools/readme_stats.py Fredoka.ttf docs/stats.png
and rebuild the card with tools/readme_card.py. Fredoka is the variable TTF from Google Fonts, and its
file is not in this repo: download it and pass the path of the copy you saved.
"""
import os
import sys

USAGE = ("usage: readme_stats.py <font.ttf> <out.png>\n"
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
                f"docs/stats.png")
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

# Pillow is not in tools/requirements.txt: it is only needed to redraw the README card, so point at the
# optional file the way tools/flamechart.py does, instead of ending in a bare traceback.
try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    sys.exit("readme_stats.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")

STATS = [("341", "TV MODELS"), ("93", "BRANDS"), ("29", "LANGUAGES"), ("532 KB", "LAUNCHER")]
W, H = 1600, 240
img = Image.new("RGB", (W, H), "#15181e"); d = ImageDraw.Draw(img)
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
num, lab = f(92, 600), f(30, 500)
PAD = 70
col = (W - 2 * PAD) / len(STATS)
for i, (n, l) in enumerate(STATS):
    cx = PAD + col * i + col / 2
    w = d.textlength(n, font=num); d.text((cx - w / 2, 40), n, font=num, fill="#cfffcf")
    sp = 5; w = sum(d.textlength(c, font=lab) for c in l) + sp * (len(l) - 1); x = cx - w / 2
    for c in l: d.text((x, 160), c, font=lab, fill="#aab3bd"); x += d.textlength(c, font=lab) + sp
    if i: d.line([(PAD + col * i, 56), (PAD + col * i, H - 56)], fill="#2c313a", width=3)
try:
    img.save(OUT, optimize=True)
except (OSError, ValueError) as error:
    # The read-only folder or the full disk, or an extension Pillow does not know (`.txt`): found at the
    # write, after the drawing. Name the file and say that no picture came of the run.
    sys.exit(f"cannot write {OUT}: {error}. Nothing was written.")

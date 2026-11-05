#!/usr/bin/env python3
"""Compare a before and an after screenshot, and draw the pair for a pull request.

A performance change should look exactly the same. This says whether it does: it compares the two
screenshots pixel by pixel, leaving out the regions you --mask (the clock moves on), and reports how
many pixels differ and where. It also writes the pair side by side, with the regions you --blur
pixelated (your Wi-Fi name, an account picture), ready to attach.

    tools/screendiff.py shots/before.png shots/after.png --mask 90,40,900,220 \\
        --blur 1265,125,1525,197 --out shots/before-after.png

Boxes are x0,y0,x1,y1 in the screenshots' pixels. Exit status: 0 identical, 1 different, 2 an error.
Needs Pillow (tools/requirements-perf.txt). Read tools/screendiff.md first.
"""
import argparse
from pathlib import Path
import re
import sys

BOX = re.compile(r"^\d+,\d+,\d+,\d+$")


def box(text):
    """'x0,y0,x1,y1' as a tuple, x0 < x1 and y0 < y1."""
    if not BOX.match(text):
        raise argparse.ArgumentTypeError(f"a box is x0,y0,x1,y1 in pixels, not {text!r}")
    x0, y0, x1, y1 = map(int, text.split(","))
    if x0 >= x1 or y0 >= y1:
        raise argparse.ArgumentTypeError(f"a box needs x0 < x1 and y0 < y1: {text!r}")
    return x0, y0, x1, y1


def output_path(option, path):
    """The file a picture will be written to, checked before the screenshots are read.

    Exit status 1 is 'the two screenshots differ', a finding the reader acts on, so a folder typed wrong
    must not come back as one: --out and --diff are judged here, before the images are opened and before
    Pillow is imported, and a path that cannot be written is named rather than attempted.
    """
    if not path:
        return None
    target = Path(path)
    if target.is_dir():
        raise ValueError(f"{option} {path} is a folder, so nothing could be written there: "
                         f"name the file, e.g. --out shots/before-after.png")
    if not target.parent.is_dir():
        if target.parent.is_file():
            raise ValueError(f"{option} {path}: {target.parent} is a file, not a folder")
        raise ValueError(f"{option} {path}: there is no folder {target.parent} - create it first")
    if not target.suffix:
        raise ValueError(f"{option} {path} has no extension, so its format is unknown: name it {path}.png")
    return target


def write(image, path):
    """Save one picture, naming the path and the reason if it cannot be written.

    Pillow answers an unwritable destination with OSError and an extension it does not know with
    ValueError; either left alone ends the run in a traceback with exit 1, the status the page reads as
    'the screenshots differ'.
    """
    try:
        image.save(path)
    except (OSError, ValueError) as e:
        raise ValueError(f"cannot write {path}: {getattr(e, 'strerror', None) or e}") from e


def screenshot(which, path):
    """The picture at `path`, decoded now, named as the argument it was given as.

    Image.open() reads only the header, so a screenshot whose copy was cut short - an interrupted
    `tools/tv.py --screenshot`, a file pulled off the TV before the write finished, a download that
    stopped early - opens here without complaint and fails on the first pixel read, inside the
    comparison. Left alone that is an OSError traceback with exit 1, the status the page reads as
    "different: N pixels differ ... find out why before posting": a finding about the launcher from a
    file nothing could be read out of. Decoding it here answers it as the unreadable file it is, with
    the before or after argument named.
    """
    from PIL import Image
    image = None
    try:
        image = Image.open(path)
        image.load()
    except OSError as e:
        if image is not None:
            image.close()   # the file is open; a refusal should not leave the handle for the GC
        raise ValueError(f"cannot read the {which} screenshot {path}: {e}. Nothing was compared.")
    return image


def compare(a, b, masks, threshold):
    """(pixels that differ by more than threshold in any channel, their bounding box or None, the largest difference)."""
    return _compare(a, b, masks, threshold)[:3]


def _compare(a, b, masks, threshold):
    """The comparison numbers and the binary pixel mask they describe."""
    from PIL import ImageChops, ImageDraw
    a, b = a.convert("RGB").copy(), b.convert("RGB").copy()
    for im in (a, b):
        draw = ImageDraw.Draw(im)
        for m in masks:
            draw.rectangle([m[0], m[1], m[2] - 1, m[3] - 1], fill="black")
    diff = ImageChops.difference(a, b)
    # The largest channel difference per pixel, then a 0/255 mask of pixels over the threshold.
    r, g, bl = diff.split()
    worst = ImageChops.lighter(ImageChops.lighter(r, g), bl)
    over = worst.point(lambda v: 255 if v > threshold else 0)
    count = over.histogram()[255]
    return count, over.getbbox(), max(worst.getdata()), over


def redact(im, region):
    """Pixelate then blur a region, so text in it can't be read back."""
    from PIL import Image, ImageFilter
    part = im.crop(region)
    small = part.resize((max(1, part.size[0] // 14), max(1, part.size[1] // 14)), Image.BILINEAR)
    im.paste(small.resize(part.size, Image.NEAREST).filter(ImageFilter.GaussianBlur(4)), region)
    return im


def pair(a, b, blurs, labels, scale):
    """The two screenshots side by side, blurred where asked, scaled, with a label over each."""
    from PIL import Image, ImageDraw, ImageFont
    a, b = a.convert("RGB").copy(), b.convert("RGB").copy()
    for region in blurs:
        redact(a, region)
        redact(b, region)
    w, h = int(a.size[0] * scale), int(a.size[1] * scale)
    a, b = a.resize((w, h), Image.LANCZOS), b.resize((w, h), Image.LANCZOS)
    out = Image.new("RGB", (2 * w + 30, h + 60), "white")
    out.paste(a, (0, 60))
    out.paste(b, (w + 30, 60))
    draw, font = ImageDraw.Draw(out), ImageFont.load_default(size=30)
    draw.text((10, 14), labels[0], fill="black", font=font)
    draw.text((w + 40, 14), labels[1], fill="black", font=font)
    return out


def parse(argv):
    p = argparse.ArgumentParser(description="Compare two screenshots and draw the pair. See tools/screendiff.md.")
    p.add_argument("before")
    p.add_argument("after")
    p.add_argument("--mask", type=box, action="append", default=[], metavar="BOX",
                   help="leave a region out of the comparison (repeatable), e.g. the clock")
    p.add_argument("--blur", type=box, action="append", default=[], metavar="BOX",
                   help="pixelate a region in the picture (repeatable), e.g. the Wi-Fi name")
    p.add_argument("--out", help="write the side-by-side pair here")
    p.add_argument("--diff", metavar="PNG", help="write the differing pixels here, white on black")
    p.add_argument("--labels", default="Before,After", help="labels over the pair, comma-separated")
    p.add_argument("--scale", type=float, default=0.5, help="size of each screenshot in the pair (default 0.5)")
    p.add_argument("--threshold", type=int, default=8,
                   help="ignore channel differences up to this (0-255, default 8: compression noise)")
    args = p.parse_args(argv)
    if not 0 < args.scale <= 1:
        p.error("--scale is between 0 and 1")
    if not 0 <= args.threshold <= 255:
        p.error("--threshold is between 0 and 255")
    labels = [l.strip() for l in args.labels.split(",")]
    if len(labels) != 2:
        p.error("--labels takes two labels")
    args.label_pair = labels
    return args


def main(argv=None):
    args = parse(sys.argv[1:] if argv is None else argv)
    for option, path in (("--out", args.out), ("--diff", args.diff)):
        try:
            output_path(option, path)
        except ValueError as e:
            print(f"{e}. Nothing was read or written.", file=sys.stderr)
            return 2
    try:
        import PIL  # noqa: F401 - the check; screenshot() reads Image when it is about to decode
    except ImportError:
        print("screendiff.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt", file=sys.stderr)
        return 2
    try:
        a, b = screenshot("before", args.before), screenshot("after", args.after)
    except ValueError as e:
        print(e, file=sys.stderr)
        return 2
    if a.size != b.size:
        print(f"the screenshots differ in size: {a.size[0]}x{a.size[1]} and {b.size[0]}x{b.size[1]}", file=sys.stderr)
        return 2
    outside = [r for r in args.mask + args.blur if r[2] > a.size[0] or r[3] > a.size[1]]
    if outside:
        print(f"a box is outside the {a.size[0]}x{a.size[1]} screenshot: {outside[0]}", file=sys.stderr)
        return 2
    count, bbox, worst, diff = _compare(a, b, args.mask, args.threshold)
    if args.out:
        try:
            write(pair(a, b, args.blur, args.label_pair, args.scale), args.out)
        except ValueError as e:
            print(f"{e}. The comparison was not reported.", file=sys.stderr)
            return 2
        print(f"{args.out}: written")
    if args.diff:
        try:
            write(diff, args.diff)
        except ValueError as e:
            print(f"{e}. The comparison was not reported.", file=sys.stderr)
            return 2
    masked = f" ({len(args.mask)} region{'s' if len(args.mask) != 1 else ''} masked)" if args.mask else ""
    if count == 0:
        print(f"identical{masked}: no pixel differs by more than {args.threshold}")
        return 0
    print(f"different{masked}: {count} pixels differ by more than {args.threshold}, inside "
          f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]} (largest difference {worst})")
    return 1


if __name__ == "__main__":
    sys.exit(main())

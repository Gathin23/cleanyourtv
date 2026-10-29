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
    try:
        from PIL import Image
    except ImportError:
        print("screendiff.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt", file=sys.stderr)
        return 2
    try:
        a, b = Image.open(args.before), Image.open(args.after)
    except OSError as e:
        print(f"cannot read a screenshot: {e}", file=sys.stderr)
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
        pair(a, b, args.blur, args.label_pair, args.scale).save(args.out)
        print(f"{args.out}: written")
    if args.diff:
        diff.save(args.diff)
    masked = f" ({len(args.mask)} region{'s' if len(args.mask) != 1 else ''} masked)" if args.mask else ""
    if count == 0:
        print(f"identical{masked}: no pixel differs by more than {args.threshold}")
        return 0
    print(f"different{masked}: {count} pixels differ by more than {args.threshold}, inside "
          f"{bbox[0]},{bbox[1]},{bbox[2]},{bbox[3]} (largest difference {worst})")
    return 1


if __name__ == "__main__":
    sys.exit(main())

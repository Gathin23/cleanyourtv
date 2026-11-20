"""Turns a folder of your own photos into wallpapers the launcher can show on a small TV.

    tools/venv/bin/python -I tools/wallpaper_prep.py ~/Pictures/trip /tmp/my-theme/wallpapers

Every JPEG, PNG, WebP and (with pillow-heif) HEIC file in the first folder is centre-cropped to 16:9, shrunk to
1280x720, darkened so the clock, the grid and the dock stay readable, and saved as 01.jpg, 02.jpg, ... in the
second folder. The originals are never touched: a target that is the source folder (by any spelling or
link), or an 01.jpg there that is a symlink or hard link to one of your photos, stops the run before
anything is written. Each picture is kept under --max-kb (300 by default, what
themes/README.md asks for) by lowering the JPEG quality a step at a time, and a picture that cannot be
brought under the limit at the quality floor is printed as over it, at the size the file really is. A
limit no picture can meet - 0 or less - is refused before anything is read.
Each output is replaced only after a complete JPEG is ready; a failed conversion leaves any earlier
wallpaper at that name intact.
Embedded ICC colour profiles are converted to sRGB before darkening, so Display P3 and Adobe RGB
photos are interpreted in their own colour space instead of assuming sRGB samples.
Without Pillow's LittleCMS support, a warning says the profile was not applied and conversion uses the
original RGB path; malformed profiles still skip that photo when colour management is available.

The darkening is painted into the picture, so wallpaper_dim can stay at 0 and the launcher draws no shade
layer. The numbers that worked on a Xiaomi Mi Box S (2 GB) are the defaults: brightness 0.25 and a gradient
that adds up to 60% black toward the bottom, where the dock sits.

Needs Pillow; HEIC (iPhone and many Android cameras) also needs pillow-heif. Both go into tools/venv, the
Python the line above runs the tool with - a system python3 has neither, and on Ubuntu 24.04 its pip is not
installed (PEP 668 leaves it externally managed):

    tools/venv/bin/pip install -r tools/requirements-photos.txt

Use pictures you have the right to show.
"""
import argparse
import io
import os
from pathlib import Path
import stat
import sys
import tempfile

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"}


def parse_size(text):
    try:
        w, h = text.lower().split("x")
        w, h = int(w), int(h)
    except ValueError:
        raise argparse.ArgumentTypeError("size must look like 1280x720")
    if w < 16 or h < 9 or w > 7680 or h > 4320:
        raise argparse.ArgumentTypeError("size must be between 16x9 and 7680x4320")
    return w, h


def source_files(folder):
    """Picture files directly inside folder, in name order (so the numbering is stable)."""
    names = sorted(n for n in os.listdir(folder) if os.path.splitext(n)[1].lower() in EXTENSIONS)
    return [os.path.join(folder, n) for n in names if os.path.isfile(os.path.join(folder, n))]


def overlapping_outputs(files, target):
    """Names of the numbered outputs in target that already are, or link to, one of the source files.

    A symlink or hardlink called 01.jpg in the target would make the save overwrite an original, and
    one pointing at a later photo would do it before that photo's turn, so every planned name is
    checked against every source file up front, by (device, inode), before anything is written.
    """
    identities = {(info.st_dev, info.st_ino) for info in map(os.stat, files)}
    clashes = []
    for number in range(1, len(files) + 1):
        name = "%02d.jpg" % number
        try:
            info = os.stat(os.path.join(target, name))
        except OSError:
            continue
        if (info.st_dev, info.st_ino) in identities:
            clashes.append(name)
    return clashes


def darken(image, brightness, gradient):
    """Scale brightness, then blend toward black from top (0) to bottom (gradient)."""
    from PIL import Image, ImageEnhance
    image = ImageEnhance.Brightness(image).enhance(brightness)
    if gradient > 0:
        mask = Image.linear_gradient("L").resize(image.size).point(lambda v: int(v * gradient))
        image = Image.composite(Image.new("RGB", image.size, (0, 0, 0)), image, mask)
    return image


def to_srgb(image):
    """Read profiled photo samples in their own colour space before writing an sRGB JPEG."""
    profile = image.info.get("icc_profile")
    if not profile:
        return image.convert("RGB")
    try:
        from PIL import ImageCms
        source = ImageCms.ImageCmsProfile(io.BytesIO(profile))
        # A palette or grayscale/alpha PNG needs its colour channels expanded for LittleCMS;
        # a CMYK JPEG must keep its CMYK samples until its own profile has been applied.
        mode = {"RGB": "RGB", "GRAY": "L", "CMYK": "CMYK"}.get(source.profile.xcolor_space.strip())
        if mode:
            image = image.convert(mode)
        return ImageCms.profileToProfile(image, source, ImageCms.createProfile("sRGB"), outputMode="RGB")
    except ImportError:
        # ImageCms can defer a missing native core's ImportError until profile parsing.
        # Keep the original conversion available, but do not claim its profile was applied.
        print("warning: ICC colour conversion needs Pillow with LittleCMS; using RGB conversion "
              "without applying the profile.", file=sys.stderr)
        return image.convert("RGB")


def prepare(path, out_path, size, brightness, gradient, quality, max_kb):
    """Writes one wallpaper and returns (size in KB, JPEG quality used)."""
    from PIL import Image, ImageOps
    with Image.open(path) as opened:
        image = to_srgb(ImageOps.exif_transpose(opened))
    image = ImageOps.fit(image, size, Image.LANCZOS, centering=(0.5, 0.5))
    image = darken(image, brightness, gradient)
    # Save candidates beside the destination, so an encoder/disk failure leaves
    # an earlier wallpaper intact. Resolve links as Image.save did, but reject loops.
    target = Path(out_path)
    try:
        target = target.resolve(strict=True)
    except FileNotFoundError:
        target = target.resolve()
    mode = stat.S_IMODE(target.stat().st_mode) if target.exists() else None
    if mode is not None and not os.access(target, os.W_OK):
        raise PermissionError(f"cannot write {out_path}")
    committed = False
    try:
        with tempfile.TemporaryDirectory(prefix=".wallpaper-", dir=target.parent) as folder:
            stage = Path(folder) / "wallpaper.jpg"
            q = quality
            while True:
                image.save(stage, "JPEG", quality=q, optimize=True)
                kb = stage.stat().st_size / 1024
                if kb <= max_kb or q <= 50:
                    break
                q -= 6
            if mode is not None:
                stage.chmod(mode)
            os.replace(stage, target)
            committed = True
    except OSError as error:
        if not committed:
            raise
        # Replacement succeeded; failing to remove the staging directory must
        # not make main() report that this picture was skipped.
        print(f"warning: {out_path} was written, but staging cleanup failed: {error}", file=sys.stderr)
    return round(kb), q


def main(argv=None):
    # A console code page such as cp1252 cannot print every name; print a replacement character instead of stopping.
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(errors="replace")
        except (AttributeError, ValueError):
            pass
    parser = argparse.ArgumentParser(description="Resize and darken photos for the launcher's wallpaper folder.")
    parser.add_argument("source", help="folder with your photos")
    parser.add_argument("target", help="folder to write 01.jpg, 02.jpg, ... into (created if missing)")
    parser.add_argument("--size", type=parse_size, default=(1280, 720), help="default 1280x720")
    parser.add_argument("--brightness", type=float, default=0.25, help="0.1 to 1.0, default 0.25")
    parser.add_argument("--gradient", type=float, default=0.6, help="extra black toward the bottom, 0 to 0.9, default 0.6")
    parser.add_argument("--quality", type=int, default=84, help="starting JPEG quality, default 84")
    parser.add_argument("--max-kb", type=int, default=300, help="size limit per picture, default 300")
    args = parser.parse_args(argv)

    if not 0.1 <= args.brightness <= 1.0:
        parser.error("--brightness must be between 0.1 and 1.0")
    if not 0 <= args.gradient <= 0.9:
        parser.error("--gradient must be between 0 and 0.9")
    if not 30 <= args.quality <= 95:
        parser.error("--quality must be between 30 and 95")
    # A limit of 0 or less is the one value the size search can never reach: every picture is written
    # over it and the run still exits 0, so `--max-kb 0` - what a script meaning "no limit" passes -
    # read as a clean run whose pictures are all over the budget it stated.
    if args.max_kb < 1:
        parser.error(f"--max-kb must be at least 1 KB, and {args.max_kb} is a limit no picture can meet: "
                     "every picture would be written over it")
    if not os.path.isdir(args.source):
        parser.error(f"{args.source} is not a folder")
    source_real = os.path.realpath(args.source)
    target_real = os.path.realpath(args.target)
    # A case alias can spell an ancestor differently while naming the source directory itself.
    if (target_real == source_real or target_real.startswith(source_real.rstrip(os.sep) + os.sep)
            or any(parent.is_dir() and os.path.samefile(source_real, parent)
                   for parent in Path(target_real).parents)):
        parser.error("the target must be a different folder, and not inside the source: the originals are never overwritten")
    if os.path.exists(args.target) and os.path.samefile(args.source, args.target):
        parser.error("the target must be a different folder, and not inside the source: the originals are never overwritten")
    if os.path.exists(args.target) and not os.path.isdir(args.target):
        parser.error(f"{args.target} is a file, not a folder")

    try:
        import PIL  # noqa: F401
    except ImportError:
        sys.exit("Pillow is missing: tools/venv/bin/pip install -r tools/requirements-photos.txt")
    try:
        import pillow_heif
        pillow_heif.register_heif_opener()
    except ImportError:
        pillow_heif = None

    files = source_files(args.source)
    if not files:
        sys.exit(f"no JPEG, PNG, WebP or HEIC pictures in {args.source}")
    clashes = overlapping_outputs(files, args.target)
    if clashes:
        sys.exit(f"{', '.join(clashes)} in {args.target} would overwrite your original photos "
                 "(a link or the same file); remove or rename them, or pick another target. Nothing was written.")
    try:
        os.makedirs(args.target, exist_ok=True)
    except OSError as e:
        sys.exit(f"cannot create the folder {args.target}: {e.strerror}. Nothing was written.")

    written = 0
    for number, path in enumerate(files, 1):
        name = os.path.basename(path)
        if os.path.splitext(name)[1].lower() in {".heic", ".heif"} and pillow_heif is None:
            print(f"skipped {name}: HEIC needs pillow-heif (tools/venv/bin/pip install -r tools/requirements-photos.txt)")
            continue
        out_path = os.path.join(args.target, "%02d.jpg" % number)
        try:
            _, quality = prepare(path, out_path, args.size, args.brightness, args.gradient, args.quality, args.max_kb)
        except Exception as error:  # a corrupt picture must not stop the rest
            print(f"skipped {name}: {error}")
            continue
        written += 1
        # Judged on the file's own size, not the rounded number prepare() returns: rounding 40.23 KB to
        # 40 puts it exactly on a 40 KB limit, so a picture over its budget read as "40 KB" with no note
        # - the one thing themes/README.md's "under 300 KB" is about. An over-limit picture is printed to
        # a tenth of a KB, so the line and its note cannot contradict each other.
        size_kb = os.path.getsize(out_path) / 1024
        over = size_kb > args.max_kb
        note = f"  (over {args.max_kb} KB even at quality {quality})" if over else ""
        shown = f"{size_kb:.1f}" if over else f"{round(size_kb)}"
        print(f"{os.path.basename(out_path)}  <- {name}  {shown} KB, quality {quality}{note}")
    print(f"{written} of {len(files)} pictures written to {args.target}")
    return 0 if written else 1


if __name__ == "__main__":
    sys.exit(main())

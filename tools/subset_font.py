#!/usr/bin/env python3
"""Rewrites the launcher's typeface as the smallest face that can still draw the launcher.

    subset_font.py             rewrite launcher/app/src/main/res/font/outfit.ttf and report the bytes
    subset_font.py --check     write nothing; exit 1 when the committed font cannot draw what the app draws
    subset_font.py --from X    trim a different original, e.g. a newer Outfit release

Always trimmed from `launcher/fonts/outfit-original.ttf`, never from the trimmed file itself: a re-run that
started from the current face could only ever remove more, and a character dropped once could never be
brought back. That original is committed for exactly this reason (tests/test_no_prebuilt_binaries.py allows
a font: "a resource the tree means to ship"), and it sits outside any Gradle resource folder, so it is
never packaged into the APK.

Why the launcher does not ship Outfit as downloaded: it is one variable font carrying every weight from
100 to 900 and 360 codepoints, while the launcher asks for four of those weights (in res/font/outfit*.xml)
and cannot produce 61 of the codepoints - the system font already backs those, so nothing on screen changes.

Clipping the variation axis is where the saving is: charset alone is worth about 6.8 KB, because gvar and
GPOS dominate a variable font. The axis therefore keeps wght 100 as well as the declared 200-500, because
`font_body` in res/values/fonts.xml points at "@font/outfit" - the bare font, which draws the DEFAULT
instance - and Outfit's default is 100. Dropping 100 renumbers the default to 200 and quietly thickens
every tile subtitle, card row and dialog that uses font_body. Keeping it costs 316 bytes.

What is kept on purpose, because a smaller font that renders differently is not a saving: kerning (GPOS),
every OpenType feature, hinting and the gasp table, and the licence notices inside the file
(--name-IDs). Outfit carries no Reserved Font Name, so a modified copy stays inside the OFL that
launcher/FONT-LICENSE-OFL.txt already ships.

Needs fontTools (`pip install fonttools`), which the repo does not ship and the test suite does not need:
this script is only run when the font or a locale's characters change. After running it, build the
launcher and look at the clock, the date line and the tile labels on a real set before committing.
"""
from __future__ import annotations

import argparse
import glob
import hashlib
import io
import os
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "launcher/app/src/main/res/font/outfit.ttf")
SOURCE = os.path.join(ROOT, "launcher/fonts/outfit-original.ttf")
FONT_DIR = os.path.join(ROOT, "launcher/app/src/main/res/font")
RES_DIR = os.path.join(ROOT, "launcher/app/src/main/res")

# The characters the launcher can ask this typeface to draw: everything a keyboard, a number, a date format
# or the UI's own punctuation can produce, plus U+FE0E (the text-presentation selector the weather line puts
# after a sky glyph) and U+FF05 (a fullwidth percent). Anything outside this set already falls back today.
ALWAYS = [chr(c) for c in range(0x20, 0x7F)]                      # printable ASCII
ALWAYS += [chr(c) for c in range(0xA0, 0x180)]                    # Latin-1 Supplement, Latin Extended-A
ALWAYS += list("\u2013\u2014\u2018\u2019\u201C\u201D\u2026\u00B7\u00B0\u20AC\u00A3\u00A5\uFE0E\uFF05")
# U+00AD, the soft hyphen, draws nothing at all: an invisible character is not a glyph the font has to carry.
BLANK = {"\u00AD"}

# res/values/fonts.xml points `font_body` at "@font/outfit" - the bare font, no family - which draws the
# *default instance*. Outfit's default is wght 100 (Thin), not 400. Clipping the axis to the declared weights
# alone renumbers the default to 200, which quietly thickens every surface that uses font_body: tile
# subtitles, card rows and dialogs. So the axis keeps 100, and this constant is what makes that checkable.
DEFAULT_WEIGHT = 100


def declared_weights() -> tuple[int, int]:
    """The weight range res/font/outfit*.xml actually asks for, so the axis can be clipped to it."""
    weights = set()
    for path in glob.glob(os.path.join(FONT_DIR, "outfit*.xml")):
        with open(path, encoding="utf-8") as handle:
            weights |= {int(w) for w in re.findall(r'android:fontWeight="(\d+)"', handle.read())}
    if not weights:
        raise SystemExit("no android:fontWeight in res/font/outfit*.xml - refusing to guess a range")
    return min(weights), max(weights)


def locale_characters() -> set[str]:
    """Every character any shipped locale's strings.xml can put on screen."""
    found: set[str] = set()
    for path in glob.glob(os.path.join(RES_DIR, "values*/strings.xml")):
        with open(path, encoding="utf-8") as handle:
            for body in re.findall(r">([^<]*)<", handle.read()):
                found |= set(body)
    return found


def wanted_characters() -> list[str]:
    # Strings may carry a newline or a tab inside a formatted date; those are layout, not glyphs.
    return sorted(c for c in set(ALWAYS) | locale_characters()
                  if ord(c) >= 0x20 and ord(c) != 0x7F and c not in BLANK)


def in_apk(path: str) -> int:
    """What the entry costs inside the APK: AGP deflates a .ttf, so the raw size is not the number that matters."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        archive.write(path, "res/font/outfit.ttf")
    return zipfile.ZipFile(buffer).infolist()[0].compress_size


def digest(path: str) -> str:
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()[:16]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Rewrite the launcher font as the smallest face that still draws the launcher")
    parser.add_argument("--check", action="store_true", help="verify the committed font; write nothing")
    parser.add_argument("--from", dest="source", default=SOURCE,
                        help="the untrimmed face to trim (default: launcher/fonts/outfit-original.ttf)")
    args = parser.parse_args()

    try:
        from fontTools.ttLib import TTFont
        from fontTools.varLib import instancer
    except ImportError:
        raise SystemExit("fontTools is not installed here: python3 -m pip install fonttools")

    if not os.path.exists(args.source):
        raise SystemExit(f"no untrimmed face at {os.path.relpath(args.source, ROOT)} - pass --from with an "
                         f"unmodified Outfit (github.com/Outfitio/Outfit-Fonts); the committed font is "
                         f"already trimmed, and trimming it again can only lose characters")

    low, high = declared_weights()
    low = min(low, DEFAULT_WEIGHT)      # keep the default instance inside the axis, or every font_body view changes weight
    wanted = wanted_characters()
    original = TTFont(args.source)
    original_cmap = set(original.getBestCmap())
    before_raw, before_apk = os.path.getsize(FONT), in_apk(FONT)
    face = TTFont(FONT)

    if args.check:
        have = set(face.getBestCmap())          # keyed by codepoint, not by character
        # The invariant is coverage the shipped face actually had. A character the untrimmed Outfit never
        # carried (Cyrillic in values-uk) belongs to the system font before and after, so it cannot fail.
        # A character it DID carry, and the app can draw, must still be there - at any codepoint, not only
        # below U+0100, which is how an earlier revision of this check let a missing letter through.
        expected = [c for c in wanted if ord(c) in original_cmap]
        missing = [c for c in expected if ord(c) not in have]
        never = [c for c in wanted if ord(c) not in original_cmap]
        if "fvar" not in face:
            print("FAIL: the committed face has no variation axis, so the families cannot pick a weight")
            return 1
        fvar = face["fvar"].axes[0]
        print(f"{os.path.relpath(FONT, ROOT)}: {len(have)} codepoints, "
              f"axis {fvar.minValue:.0f}..{fvar.maxValue:.0f} default {fvar.defaultValue:.0f}, "
              f"{before_raw} B ({before_apk} in-APK)")
        print(f"declared weights in res/font: {min(declared_weights())}-{max(declared_weights())}, "
              f"default instance must stay {DEFAULT_WEIGHT}")
        if missing:
            print(f"FAIL: {len(missing)} characters the app can draw and the untrimmed face carried are gone: "
                  + ", ".join(f"U+{ord(c):04X}" for c in missing[:24]))
            return 1
        if fvar.defaultValue != DEFAULT_WEIGHT:
            print(f"FAIL: the default instance is {fvar.defaultValue:.0f}, and font_body draws it - "
                  f"every tile subtitle and card row would render at the wrong weight")
            return 1
        if fvar.minValue > min(declared_weights()) or fvar.maxValue < max(declared_weights()):
            print(f"FAIL: the axis {fvar.minValue:.0f}-{fvar.maxValue:.0f} does not cover the weights the "
                  f"families ask for ({min(declared_weights())}-{max(declared_weights())})")
            return 1
        if never:
            print(f"backed by the system font, as before ({len(never)}): "
                  + "".join(never[:40]).encode("unicode_escape").decode())
        print(f"OK: {len(expected)} characters kept of the {len(expected) + len(never)} the app can draw, "
              f"at the weights it asks for, with the default still at {DEFAULT_WEIGHT}")
        return 0

    instancer.instantiateVariableFont(original, {"wght": (low, high)}, inplace=True)
    clipped = io.BytesIO()
    original.save(clipped)

    with tempfile.TemporaryDirectory() as scratch:
        clipped_path = os.path.join(scratch, "clipped.ttf")
        with open(clipped_path, "wb") as handle:
            handle.write(clipped.getvalue())
        keep = [c for c in wanted if ord(c) in original_cmap]
        run = subprocess.run(
            [sys.executable, "-m", "fontTools.subset", clipped_path,
             "--unicodes=" + ",".join(f"U+{ord(c):04X}" for c in keep),
             "--layout-features=*", "--glyph-names", "--notdef-outline",
             "--name-IDs=*", "--name-languages=*",
             "--output-file=" + FONT],
            capture_output=True, text=True)
        if run.returncode:
            print(run.stdout + run.stderr)
            return run.returncode

    after_raw, after_apk = os.path.getsize(FONT), in_apk(FONT)
    print(f"from {os.path.relpath(args.source, ROOT)} (sha256 {digest(args.source)}), axis {low}-{high}")
    print(f"{os.path.relpath(FONT, ROOT)}: {before_raw} -> {after_raw} B "
          f"({before_apk} -> {after_apk} in-APK, {before_apk - after_apk} saved)")
    print("Now build the launcher and look at the clock, the date line and the tile labels on a real set.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

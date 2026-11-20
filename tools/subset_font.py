#!/usr/bin/env python3
"""Rewrites the launcher's typeface at fewer weights, never with fewer characters.

    subset_font.py                   rewrite launcher/app/src/main/res/font/outfit.ttf and report the bytes
    subset_font.py --check           write nothing; exit 1 unless the committed font is a faithful trim
    subset_font.py --verify-outlines also compare every outline against the original, weight by weight
    subset_font.py --from X          trim a different original, e.g. a newer Outfit release

Always trimmed from `launcher/fonts/outfit-original.ttf`, never from the trimmed file itself: a re-run that
started from the current face could only ever remove more, and a character dropped once could never be
brought back. That original is committed for exactly this reason (tests/test_no_prebuilt_binaries.py allows
a font: "a resource the tree means to ship"), and it sits outside any Gradle resource folder, so it is
never packaged into the APK.

**The characters are not trimmed.** An earlier revision kept only the characters it could find in
res/values*/strings.xml plus a guess at Latin ranges, and dropped 61 codepoints the face carried: Latin
Extended-B (Ș ș Ț ț), the combining accents U+0300/U+0301, arrows, U+2122, U+2713. The reasoning was that
"no locale uses them" - but the launcher draws text nobody can enumerate at build time. AppAdapter draws
each installed app's label, cards carry captions, and themes may supply greeting names; all of it arrives
at runtime from other packages. Those characters did not fall back before, so dropping them changed the
weight and width of real labels on upgrade. midhatn proved it with a native raster comparison on a real
TV: 75 of 150 renderings differed, Țara mea 204 -> 209 px wide at weight 400. Keeping the whole cmap costs
5,426 B in-APK and is the only version that can honestly say nothing on screen changes.

The saving therefore comes from the variation axis alone. Outfit ships wght 100..900 in one variable font
while the launcher asks for 100..500: res/font/outfit*.xml declares 200-500, and 100 stays because
`font_body` in res/values/fonts.xml points at "@font/outfit" - the bare font, which draws the DEFAULT
instance, and Outfit's default is 100 (Thin), not 400. Dropping 100 renumbers the default to 200 and
quietly thickens every tile subtitle, card row and dialog that uses font_body. Keeping it costs 316 bytes.

What is kept on purpose, because a smaller font that renders differently is not a saving: every codepoint
the original carried, kerning (GPOS), every OpenType feature, hinting and the gasp table, and the licence
notices inside the file (--name-IDs). Outfit carries no Reserved Font Name, so a modified copy stays
inside the OFL that launcher/FONT-LICENSE-OFL.txt already ships.

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

# res/values/fonts.xml points `font_body` at "@font/outfit" - the bare font, no family - which draws the
# *default instance*. Outfit's default is wght 100 (Thin), not 400. Clipping the axis to the declared weights
# alone renumbers the default to 200, which quietly thickens every surface that uses font_body: tile
# subtitles, card rows and dialogs. So the axis keeps 100, and this constant is what makes that checkable.
DEFAULT_WEIGHT = 100

# A glyph that moved by more than half a font unit is a visible change; at or below it, float rounding in
# the instancer. Outfit's em is 1000 units, so half a unit is half a pixel at 1000 px.
TOLERANCE = 0.5


def declared_weights() -> tuple[int, int]:
    """The weight range res/font/outfit*.xml actually asks for, so the axis can be clipped to it."""
    weights = set()
    for path in glob.glob(os.path.join(FONT_DIR, "outfit*.xml")):
        with open(path, encoding="utf-8") as handle:
            weights |= {int(w) for w in re.findall(r'android:fontWeight="(\d+)"', handle.read())}
    if not weights:
        raise SystemExit("no android:fontWeight in res/font/outfit*.xml - refusing to guess a range")
    return min(weights), max(weights)


def in_apk(path: str) -> int:
    """What the entry costs inside the APK: AGP deflates a .ttf, so the raw size is not the number that matters."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        archive.write(path, "res/font/outfit.ttf")
    return zipfile.ZipFile(buffer).infolist()[0].compress_size


def digest(path: str) -> str:
    with open(path, "rb") as handle:
        return hashlib.sha256(handle.read()).hexdigest()[:16]


def axis(face) -> tuple[float, float, float]:
    if "fvar" not in face:
        raise SystemExit("FAIL: the face has no variation axis, so the families cannot pick a weight")
    wght = face["fvar"].axes[0]
    if (wght.axisTag or "").strip() != "wght":
        raise SystemExit(f"FAIL: the variation axis is {wght.axisTag!r}, not wght")
    return wght.minValue, wght.maxValue, wght.defaultValue


def check(verify_outlines: bool) -> int:
    from fontTools.pens.recordingPen import RecordingPen
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont

    if not os.path.exists(SOURCE):
        raise SystemExit(f"no untrimmed face at {os.path.relpath(SOURCE, ROOT)} - the coverage check has "
                         f"nothing to compare against")
    original, face = TTFont(SOURCE), TTFont(FONT)
    want_cmap, have_cmap = set(original.getBestCmap()), set(face.getBestCmap())
    missing = sorted(want_cmap - have_cmap)
    low, high = declared_weights()
    minimum, maximum, default = axis(face)
    before_raw, before_apk = os.path.getsize(SOURCE), in_apk(SOURCE)
    now_raw, now_apk = os.path.getsize(FONT), in_apk(FONT)

    print(f"{os.path.relpath(FONT, ROOT)}: {len(have_cmap)} codepoints, "
          f"{len(face.getGlyphOrder())} glyphs, axis {minimum:.0f}..{maximum:.0f} default {default:.0f}, "
          f"{now_raw} B ({now_apk} in-APK)")
    print(f"{os.path.relpath(SOURCE, ROOT)}: {len(want_cmap)} codepoints, "
          f"{len(original.getGlyphOrder())} glyphs, {before_raw} B ({before_apk} in-APK)")
    print(f"declared weights in res/font: {low}-{high}, default instance must stay {DEFAULT_WEIGHT}")

    failed = False
    if missing:
        print(f"FAIL: {len(missing)} codepoints the original carried are gone: "
              + ", ".join(f"U+{cp:04X}" for cp in missing[:24])
              + " - the launcher draws labels it cannot enumerate at build time (AppAdapter package "
                "labels, card captions, theme greetings), so every codepoint has to survive")
        failed = True
    if default != DEFAULT_WEIGHT:
        print(f"FAIL: the default instance is {default:.0f}, and font_body draws it - "
              f"every tile subtitle and card row would render at the wrong weight")
        failed = True
    if minimum > low or maximum < high:
        print(f"FAIL: the axis {minimum:.0f}-{maximum:.0f} does not cover the weights the families ask "
              f"for ({low}-{high})")
        failed = True
    if now_apk >= before_apk:
        print(f"FAIL: the committed font costs {now_apk} B in-APK, no less than the original's "
              f"{before_apk} B - there is no saving to justify the swap")
        failed = True

    if verify_outlines and not failed:
        weights = sorted({int(DEFAULT_WEIGHT), int(minimum), int(maximum)} | {low, high})
        print(f"outlines, every codepoint, at weights {', '.join(str(w) for w in weights)}:")
        for weight in weights:
            a = instantiateVariableFont(TTFont(SOURCE), {"wght": weight}, inplace=False)
            b = instantiateVariableFont(TTFont(FONT), {"wght": weight}, inplace=False)
            set_a, set_b = a.getGlyphSet(), b.getGlyphSet()
            map_a, map_b = a.getBestCmap(), b.getBestCmap()
            identical = moved = 0
            worst = 0.0
            for codepoint in have_cmap:
                name_a, name_b = map_a.get(codepoint), map_b.get(codepoint)
                if name_a is None or name_b is None:
                    moved += 1
                    worst = float("inf")
                    continue
                pen_a, pen_b = RecordingPen(), RecordingPen()
                set_a[name_a].draw(pen_a)
                set_b[name_b].draw(pen_b)
                same = pen_a.value == pen_b.value and set_a[name_a].width == set_b[name_b].width
                if same:
                    identical += 1
                    continue
                moved += 1
                for (op_a, points_a), (op_b, points_b) in zip(pen_a.value, pen_b.value):
                    if op_a != op_b:
                        worst = float("inf")
                        break
                    for p, q in zip(points_a, points_b):
                        if isinstance(p, tuple) and isinstance(q, tuple):
                            worst = max(worst, abs(p[0] - q[0]), abs(p[1] - q[1]))
                worst = max(worst, 0.0 if set_a[name_a].width == set_b[name_b].width
                            else abs(set_a[name_a].width - set_b[name_b].width))
            verdict = "ok" if worst <= TOLERANCE else "FAIL"
            print(f"  wght {weight}: {identical}/{len(have_cmap)} identical, {moved} moved, "
                  f"worst {worst:.3f} units - {verdict}")
            if worst > TOLERANCE:
                failed = True

    if failed:
        return 1
    print(f"OK: all {len(want_cmap)} codepoints kept, at {low}-{high} with the default at {DEFAULT_WEIGHT}, "
          f"{before_apk - now_apk} B cheaper in-APK")
    return 0


def rewrite(source: str) -> int:
    from fontTools.ttLib import TTFont
    from fontTools.varLib import instancer

    low, high = declared_weights()
    low = min(low, DEFAULT_WEIGHT)      # keep the default instance inside the axis, or every font_body view changes weight
    before_raw, before_apk = os.path.getsize(FONT), in_apk(FONT)
    # recalcTimestamp=False keeps the original's head.modified through the save below; otherwise every
    # run stamps the current minute and two regenerations of the same original differ byte for byte.
    original = TTFont(source, recalcTimestamp=False)
    instancer.instantiateVariableFont(original, {"wght": (low, high)}, inplace=True)
    clipped = io.BytesIO()
    original.save(clipped)

    with tempfile.TemporaryDirectory() as scratch:
        clipped_path = os.path.join(scratch, "clipped.ttf")
        with open(clipped_path, "wb") as handle:
            handle.write(clipped.getvalue())
        run = subprocess.run(
            [sys.executable, "-m", "fontTools.subset", clipped_path,
             # every codepoint the face has, not a guess at which ones the app might ask for
             "--unicodes=*", "--layout-features=*", "--glyph-names", "--notdef-outline",
             "--name-IDs=*", "--name-languages=*",
             # without this the head table records the moment of the run, so two regenerations of the
             # same original minutes apart differ by a few bytes and "run it yourself and compare" fails
             "--no-recalc-timestamp",
             "--output-file=" + FONT],
            capture_output=True, text=True)
        if run.returncode:
            print(run.stdout + run.stderr)
            return run.returncode

    after_raw, after_apk = os.path.getsize(FONT), in_apk(FONT)
    print(f"from {os.path.relpath(source, ROOT)} (sha256 {digest(source)}), axis {low}-{high}")
    print(f"{os.path.relpath(FONT, ROOT)}: {before_raw} -> {after_raw} B "
          f"({before_apk} -> {after_apk} in-APK, {before_apk - after_apk} saved)")
    print("Now build the launcher and look at the clock, the date line and the tile labels on a real set.")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Rewrite the launcher font at the weights it asks for, keeping every character")
    parser.add_argument("--check", action="store_true", help="verify the committed font; write nothing")
    parser.add_argument("--verify-outlines", action="store_true",
                        help="with --check: compare every outline and advance against the original")
    parser.add_argument("--from", dest="source", default=SOURCE,
                        help="the untrimmed face to trim (default: launcher/fonts/outfit-original.ttf)")
    args = parser.parse_args()

    try:
        import fontTools  # noqa: F401
    except ImportError:
        raise SystemExit("fontTools is not installed here: python3 -m pip install fonttools")

    if args.check:
        return check(args.verify_outlines)

    if not os.path.exists(args.source):
        raise SystemExit(f"no untrimmed face at {os.path.relpath(args.source, ROOT)} - pass --from with an "
                         f"unmodified Outfit (github.com/Outfitio/Outfit-Fonts); the committed font is "
                         f"already trimmed, and trimming it again can only lose characters")
    return rewrite(args.source)


if __name__ == "__main__":
    raise SystemExit(main())

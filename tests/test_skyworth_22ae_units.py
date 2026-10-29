"""Checks that the Skyworth SWTV-22AE-FHD page names the build the folder's list carries.

This folder holds two units of one model in two files. The list's header names the Android 14
(SDK 34) unit it was written on, firmware UKNV.260211.001; the README's device info is a second unit
on Android 11 (SDK 30), firmware RTM7.230903.136, whose owner applied that same list. For a while
neither file said so, so the page read as if the list came from the Android 11 build and an owner of
the build it actually came from could not tell the two apart.

Both builds are read out of the files rather than copied in, and one test pins the shapes a build can
take in these headers, since a model name has the same shape.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "examples" / "skyworth-swtv-22ae-fhd"

# A vendor build: RTM7.230903.136 (Skyworth), UKNV.260211.001 (Google TV), V8-0012T01-LF1V667 (TCL).
# It starts with a capital and is broken into at least two dotted or dashed parts. A model name has
# the same shape (SWTV-22AE-FHD, KD-55X80J) and a build carries a date or a serial number, so a build
# is one of those tokens with a run of four digits and a model name is not.
BUILD = re.compile(r"(?<![A-Za-z0-9])[A-Z][A-Za-z0-9]{1,}(?:[.\-_][A-Za-z0-9]+){1,}")
BUILT = re.compile(r"\d{4,}")
FIRMWARE = re.compile(r"^[^\n]*\*\*Firmware:\*\*([^\n]*)", re.M)
# The list header's own unit: its first lines name the model, Android version and build
# (docs/workflows/tv-list.md, step 2).
HEADER_LINES = 6


def builds(text):
    """The vendor builds in a piece of text, in the order they appear, without duplicates."""
    out = []
    for token in BUILD.findall(text):
        if BUILT.search(token) and token not in out:
            out.append(token)
    return out


def read():
    readme = (FOLDER / "README.md").read_text(encoding="utf-8", errors="replace")
    listing = (FOLDER / "debloat.txt").read_text(encoding="utf-8-sig", errors="replace")
    device = [b for line in FIRMWARE.findall(readme) for b in builds(line)]
    header = builds("\n".join(listing.splitlines()[:HEADER_LINES]))
    return readme, device, header


class Skyworth22aeUnitsTest(unittest.TestCase):
    def test_the_page_names_the_build_its_list_carries(self):
        readme, device, header = read()
        self.assertTrue(header, "the list's header names no build")
        self.assertNotEqual(sorted(header), sorted(device),
                            "the two files name the same unit, so this test no longer has a job")
        for build in header:
            self.assertIn(build, readme,
                          f"the README's device info is the {device[0]} unit, but the page never names "
                          f"{build}, the build the folder's list was written on")

    def test_the_page_keeps_its_own_unit(self):
        readme, device, header = read()
        self.assertEqual(["RTM7.230903.136"], device,
                         "the README's Firmware line no longer records the Android 11 unit")
        self.assertEqual(["UKNV.260211.001"], header,
                         "the list header no longer records the Android 14 unit")
        self.assertIn("Android TV 11", readme)
        for build in device:
            self.assertIn(build, readme)

    def test_build_shapes(self):
        # The three vendor builds the tree carries, so a narrower pattern cannot quietly stop
        # matching the files it is meant to read...
        for build in ("RTM7.230903.136", "UKNV.260211.001", "V8-0012T01-LF1V667"):
            self.assertEqual([build], builds(build))
        # ...and the shapes around them that are not builds: a model name, a script's variable and a
        # product name from the same headers.
        for other in ("SWTV-22AE-FHD", "KD-55X80J", "STOCK_LAUNCHER", "G08_4K_GB"):
            self.assertEqual([], builds(other), other)

    def test_a_build_named_twice_is_read_once(self):
        self.assertEqual(["UKNV.260211.001"], builds("UKNV.260211.001 on UKNV.260211.001"))


if __name__ == "__main__":
    unittest.main()

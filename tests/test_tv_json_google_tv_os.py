"""A list whose header says Google TV has a tv.json "os" that says so too.

tools/gen_tables.py writes the OS cell of both TV tables from tv.json, not from the list, so a row can drift
from the header it stands for. The first line of examples/dcolor-gd2/debloat.txt names "Google TV 14 (SDK 34)"
and its tv.json notes say it "came with Google TV", while its "os" read "Android TV 14 (SDK 34)": both tables
told a reader looking for a Google TV list that this box was Android TV.

Only the plain case is checked: the header's first three lines name "Google TV <version>", so "os" (and
"readme_os" when there is one) must name Google TV. A header that only mentions the Google TV launcher, a
"Google TV stick" or a product called "Google TV 4K" is not read as the OS.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
HEADER_LINES = 3
GOOGLE_TV_OS = re.compile(r"\bGoogle TV \d+\b")


def mismatches(header, data):
    """The tv.json keys whose OS cell drops Google TV although the header names Google TV <version>."""
    if not GOOGLE_TV_OS.search("\n".join(header.split("\n")[:HEADER_LINES])):
        return []
    return [key for key in ("os", "readme_os") if key in data and "Google TV" not in data[key]]


class GoogleTvOsTest(unittest.TestCase):
    def test_every_google_tv_header_has_a_google_tv_row(self):
        wrong = []
        for folder in sorted(p for p in EXAMPLES.iterdir() if p.is_dir()):
            listing, row = folder / "debloat.txt", folder / "tv.json"
            if not (listing.is_file() and row.is_file()):
                continue  # test_examples.py refuses a folder without them
            data = json.loads(row.read_text(encoding="utf-8"))
            header = listing.read_text(encoding="utf-8", errors="replace")
            wrong += [f"examples/{folder.name}/tv.json \"{key}\": {data[key]!r}"
                      for key in mismatches(header, data)]
        self.assertEqual(wrong, [], "the list header says Google TV <version>; say Google TV in the row too")

    def test_the_dcolor_case_is_caught(self):
        header = "# Dcolor GD2 (SDMC, device YHT, Amlogic), Google TV 14 (SDK 34)\n# Build: x\n"
        self.assertEqual(mismatches(header, {"os": "Android TV 14 (SDK 34)"}), ["os"])
        self.assertEqual(mismatches(header, {"os": "Google TV 14 (SDK 34)"}), [])

    def test_readme_os_is_checked_too(self):
        header = "# Some box, Google TV 12\n"
        data = {"os": "Google TV 12", "readme_os": "Android TV 12"}
        self.assertEqual(mismatches(header, data), ["readme_os"])

    def test_a_launcher_or_stick_name_is_not_the_os(self):
        header = ("# Projector, built-in Google TV stick: ro.product.model=\"4K Google TV Stick\"\n"
                  "# Android TV 14 (Google TV), firmware x\n# Home it shipped with: Google TV (launcherx)\n")
        self.assertEqual(mismatches(header, {"os": "Android TV 14"}), [])
        header = "# ACME Google TV 4K\n# Android TV 11 with the Google TV home\n"
        self.assertEqual(mismatches(header, {"os": "Android TV 11"}), [])

    def test_only_the_first_lines_are_read(self):
        header = "# Some set, Android TV 11\n#\n#\n# Compare with the Google TV 12 list in another folder\n"
        self.assertEqual(mismatches(header, {"os": "Android TV 11"}), [])


if __name__ == "__main__":
    unittest.main()

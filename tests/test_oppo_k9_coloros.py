"""OPPO K9 55 (OPPOCNM632) list and device note.

ColorOS accepts a HOME install only for three package names. This list leaves the stock home
`com.heytap.tv.launcher` enabled. The device note names that allowlist and warns not to run
`launcher/build-and-install.sh`. Launcher manifest and troubleshooting checks live with the
launcher change.

Run from the repository root: python3 -m unittest tests.test_oppo_k9_coloros -v
"""
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "oppo-k9-55-oppocnm632.md"
LIST = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "debloat.txt"
EXTRA = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "debloat-extra.txt"
TV_JSON = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "tv.json"
INDEX = ROOT / "examples" / "README.md"

STOCK = "com.heytap.tv.launcher"
ALLOWLIST = (STOCK, "com.oneplus.tv.launcher2", "com.oppo.tv.launcher2")
NOTE_LINK = "../docs/devices/oppo-k9-55-oppocnm632.md"


def active_packages(path):
    packages = []
    for line in path.read_text(encoding="utf-8").splitlines():
        body = line.split("#", 1)[0].strip()
        if body:
            packages.append(body)
    return packages


class OppoK9ColorOsTest(unittest.TestCase):
    def test_the_note_names_the_allowlist(self):
        text = NOTE.read_text(encoding="utf-8")
        for package in ALLOWLIST:
            self.assertIn(package, text)
        self.assertIn("examples/oppo-k9-55-oppocnm632/debloat.txt", text)
        self.assertIn("do not build over it", text.lower())
        self.assertIn("do not run `launcher/build-and-install.sh`", text.lower())

    def test_the_stock_home_is_not_disabled(self):
        self.assertNotIn(STOCK, active_packages(LIST))
        self.assertNotIn(STOCK, active_packages(EXTRA))

    def test_the_row_links_the_note(self):
        notes = json.loads(TV_JSON.read_text(encoding="utf-8"))["notes"]
        self.assertIn(NOTE_LINK, notes)

    def test_the_examples_index_links_the_note(self):
        # tv.json is not a markdown page. test_docs_pages_reachable only counts a markdown link.
        self.assertIn(f"]({NOTE_LINK})", INDEX.read_text(encoding="utf-8"))

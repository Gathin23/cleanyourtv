"""OPPO K9 55 (OPPOCNM632) ColorOS list and launcher.

The examples half names the three allowlisted package ids, leaves the stock home enabled, and
links the device note from tv.json and examples/README.md. The launcher half declares
com.heytap.tv.launcher.category.MODERN_HOME on the HOME filter, keeps com.heytap.tv.launcher out
of the install scripts' stock-launcher lists, and names that category and the device-note path
in docs/TROUBLESHOOTING.md.

Run from the repository root: python3 -m unittest tests.test_oppo_k9_coloros -v
"""
import json
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "oppo-k9-55-oppocnm632.md"
LIST = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "debloat.txt"
EXTRA = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "debloat-extra.txt"
TV_JSON = ROOT / "examples" / "oppo-k9-55-oppocnm632" / "tv.json"
INDEX = ROOT / "examples" / "README.md"
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
INSTALL_SH = ROOT / "launcher" / "build-and-install.sh"
INSTALL_PS1 = ROOT / "launcher" / "build-and-install.ps1"
TROUBLESHOOTING = ROOT / "docs" / "TROUBLESHOOTING.md"

MODERN_HOME = "com.heytap.tv.launcher.category.MODERN_HOME"
HOME = "android.intent.category.HOME"
STOCK = "com.heytap.tv.launcher"
ALLOWLIST = (STOCK, "com.oneplus.tv.launcher2", "com.oppo.tv.launcher2")
NOTE_LINK = "../docs/devices/oppo-k9-55-oppocnm632.md"
NOTE_PATH = "docs/devices/oppo-k9-55-oppocnm632.md"
FILTER = re.compile(r"<intent-filter\b[^>]*>(.*?)</intent-filter>", re.S)
CATEGORY = re.compile(r'<category\s+android:name="([^"]+)"')


def active_packages(path):
    packages = []
    for line in path.read_text(encoding="utf-8").splitlines():
        body = line.split("#", 1)[0].strip()
        if body:
            packages.append(body)
    return packages


def known_line(text, marker):
    return text.split(marker, 1)[1].split("\n", 1)[0]


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

    def test_home_filter_declares_modern_home(self):
        blocks = FILTER.findall(MANIFEST.read_text(encoding="utf-8"))
        homes = [CATEGORY.findall(block) for block in blocks if HOME in CATEGORY.findall(block)]
        self.assertTrue(homes, "MainActivity's HOME intent-filter was not found")
        self.assertIn(MODERN_HOME, homes[0])

    def test_install_scripts_leave_the_stock_home_out(self):
        sh = known_line(INSTALL_SH.read_text(encoding="utf-8"), 'KNOWN_STOCK_LAUNCHERS="')
        ps1 = known_line(INSTALL_PS1.read_text(encoding="utf-8"), "$knownStock = @(")
        self.assertNotIn(STOCK, sh)
        self.assertNotIn(STOCK, ps1)

    def test_troubleshooting_names_the_category_and_the_note(self):
        text = TROUBLESHOOTING.read_text(encoding="utf-8")
        self.assertIn(MODERN_HOME, text)
        for package in ALLOWLIST:
            self.assertIn(package, text)
        self.assertIn(NOTE_PATH, text)

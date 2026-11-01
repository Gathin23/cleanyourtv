"""The Permissions column of addons/README.md, held to each add-on's own AndroidManifest.xml.

The column is one line and the add-on's own page is a table, and neither is generated from the
manifest the app really ships, so the two drift. Frigate Glance's manifest asks for
`RECEIVE_BOOT_COMPLETED` - the app comes back after a reboot, as its own README's "Start at boot" row
says and as `install.sh` arranges on TCL sets - while its row here listed its overlay, accessibility,
network and foreground service and stopped there. It was the only add-on out of the nine whose
manifest asks for that permission whose row left it out: brightness, quiet-hours and tv-static all
say "start at boot", so a reader scanning the table would have taken the row at its word.

The direction that matters more than a missing word is a row saying "None" for an app whose manifest
asks for something, and that is checked for every add-on here. Each add-on's own Permissions section
is held to its manifest for the rule addons/README.md states ("The add-on's README lists every
permission it asks for and why"): every `uses-permission` constant has to be named there, which is an
exact match and costs a new add-on nothing, since naming the constant is how all 25 pages do it.

The table is prose ("overlay" for SYSTEM_ALERT_WINDOW, "start at boot" for RECEIVE_BOOT_COMPLETED),
so WORDING below is the wording this page uses today. A permission it has no words for is skipped
rather than failing, so a new add-on whose manifest asks for something nobody has written a row for
yet is not blocked by this file; the constant is still required in that add-on's own README above.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADDONS = ROOT / "addons"
INDEX = ADDONS / "README.md"

# The words addons/README.md's Permissions column uses for each constant, lowercased.
WORDING = {
    "INTERNET": ("internet", "network"),
    "ACCESS_NETWORK_STATE": ("network state", "network"),
    "SYSTEM_ALERT_WINDOW": ("overlay", "display over other apps", "system_alert_window"),
    "FOREGROUND_SERVICE": ("foreground service",),
    "FOREGROUND_SERVICE_DATA_SYNC": ("foreground service",),
    "FOREGROUND_SERVICE_SPECIAL_USE": ("foreground service",),
    "RECEIVE_BOOT_COMPLETED": ("start at boot", "receive_boot_completed"),
    "BLUETOOTH": ("bluetooth",),
    "BLUETOOTH_ADMIN": ("bluetooth",),
    "BLUETOOTH_CONNECT": ("bluetooth",),
    "ACCESS_COARSE_LOCATION": ("location",),
    "SCHEDULE_EXACT_ALARM": ("exact alarm",),
    "WRITE_SECURE_SETTINGS": ("write_secure_settings", "secure settings"),
}


def addon_dirs():
    return sorted(p for p in ADDONS.iterdir() if p.is_dir()) if ADDONS.is_dir() else []


def manifest_permissions(folder):
    """The `uses-permission` names an add-on's manifest asks for, without the android.permission. prefix."""
    manifest = folder / "app" / "src" / "main" / "AndroidManifest.xml"
    if not manifest.is_file():
        return []
    text = manifest.read_text(encoding="utf-8", errors="ignore")
    return [name.replace("android.permission.", "")
            for name in re.findall(r'<uses-permission[^>]*android:name="([^"]+)"', text)]


def index_rows():
    """{folder: cells} from the add-ons table, keyed by the folder the Add-on cell links to."""
    rows = {}
    text = INDEX.read_text(encoding="utf-8") if INDEX.is_file() else ""
    if "| Add-on |" not in text:
        return rows  # the read guards in the tests below answer an empty walk
    for line in text.splitlines():
        if not line.startswith("| ["):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split(" | ")]
        link = re.match(r"\[[^\]]*\]\(([^)/]+)/?\)", cells[0])
        if link and len(cells) >= 6:
            rows[link[1].rstrip("/")] = cells
    return rows


def permissions_cell(cells):
    """The Permissions column: fifth of Add-on | What it does | Author | Tested on | Permissions | Screenshot."""
    return cells[4]


def permissions_section(readme):
    """The add-on's own Permissions heading and what follows it, up to the next heading."""
    found = re.search(r"(?ms)^#+\s*Permissions\s*$.*?(?=^#+ |\Z)", readme)
    return found.group(0) if found else ""


def says_none(cell):
    return "none" in cell.lower()


class IndexPermissionsColumnTest(unittest.TestCase):
    def test_every_permission_a_manifest_asks_for_is_in_its_row(self):
        rows = index_rows()
        self.assertGreaterEqual(len(rows), 20, f"read {len(rows)} rows of the add-ons table")
        checked = 0
        for folder in addon_dirs():
            with self.subTest(addon=folder.name):
                self.assertIn(folder.name, rows, "addons/README.md needs a row linking this add-on")
                cell = permissions_cell(rows[folder.name])
                asked = manifest_permissions(folder)
                words = cell.lower()
                if asked:
                    self.assertFalse(says_none(cell),
                                     f"{folder.name} asks for {', '.join(asked)} and its row says {cell!r}")
                for permission in asked:
                    if permission not in WORDING:
                        continue  # no wording to look for yet; the add-on's own README is still held below
                    self.assertTrue(any(word in words for word in WORDING[permission]),
                                    f"{folder.name} asks for {permission} and its row says {cell!r}, "
                                    f"which does not name it ({', '.join(WORDING[permission])})")
                    checked += 1
        self.assertGreaterEqual(checked, 15, f"the walk read {checked} permissions in the add-ons")

    def test_a_row_that_says_none_belongs_to_an_app_that_asks_for_nothing(self):
        rows = index_rows()
        self.assertGreaterEqual(len(rows), 20, f"read {len(rows)} rows of the add-ons table")
        none_rows = 0
        for folder in addon_dirs():
            with self.subTest(addon=folder.name):
                cell = permissions_cell(rows[folder.name])
                if manifest_permissions(folder):
                    continue  # the test above answers the other direction
                self.assertTrue(says_none(cell),
                                f"{folder.name}'s manifest asks for nothing, so its row should say None, "
                                f"not {cell!r}")
                none_rows += 1
        self.assertGreaterEqual(none_rows, 5, f"{none_rows} rows say None; the walk read no add-on")

    def test_every_addon_folder_has_exactly_one_row(self):
        rows = index_rows()
        folders = [folder.name for folder in addon_dirs()]
        self.assertGreaterEqual(len(folders), 20, f"read {len(folders)} add-on folders")
        self.assertEqual([name for name in rows if name not in folders], [],
                         "a table row links a folder that is not there")
        self.assertEqual(sorted(folders), sorted(rows),
                         "every add-on folder has one row in the add-ons table")


class AddonReadmePermissionsTest(unittest.TestCase):
    def test_each_readme_names_every_permission_its_manifest_asks_for(self):
        checked = 0
        for folder in addon_dirs():
            readme = folder / "README.md"
            if not readme.is_file():
                continue
            section = permissions_section(readme.read_text(encoding="utf-8", errors="ignore"))
            with self.subTest(addon=folder.name):
                if manifest_permissions(folder):
                    self.assertTrue(section,
                                    "an add-on's README lists its permissions under a Permissions heading")
                for permission in manifest_permissions(folder):
                    self.assertIn(permission, section,
                                  f"{folder.name}'s Permissions section does not name {permission}")
                    checked += 1
        self.assertGreaterEqual(checked, 15, f"the walk read {checked} permissions in the add-ons")


class ThePageSaysSoTest(unittest.TestCase):
    def test_the_rules_say_the_column_carries_the_list(self):
        text = INDEX.read_text(encoding="utf-8")
        self.assertTrue("Permissions column of the table above" in text,
                        "the Rules say the column carries the list each add-on's manifest asks for")

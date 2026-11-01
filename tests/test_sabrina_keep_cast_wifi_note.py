"""The Chromecast with Google TV 4K folder: setupwraith's saved Wi-Fi network, and the keep-cast list.

On `sabrina` (UTTC.250917.004) setupwraith can own a saved Wi-Fi network, as on the HD (`boreal`) on the
same build: on the folder's second unit `dumpsys wifi | grep -o 'cname=[^ ]*'` named
com.google.android.tungsten.setupwraith as the creator of one of its saved networks. Android deletes an
app's saved networks when the app is disabled, and debloat.txt disables setupwraith, but only the HD list
said so. These tests pin the warning in debloat.txt and the half-sentence where debloat-keep-cast.txt
says why it keeps setupwraith.

debloat-keep-cast.txt says it is "the same cleanup as debloat.txt, minus the three packages that cost
something on this device": setupwraith, dreamx and backdrop. A package added to one list and not the
other makes that sentence wrong, so the two lists are compared here. The keep-cast list was linked only
from debloat.txt's header; the folder's tv.json notes (its examples/README.md row) and the "Untrusted
device" bullet in docs/TROUBLESHOOTING.md point to it now.

Run from the repository root:
python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import json
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "examples" / "google-chromecast-gtv-4k"
DEBLOAT = FOLDER / "debloat.txt"
KEEP_CAST = FOLDER / "debloat-keep-cast.txt"
DOC = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8")
SETUPWRAITH = "com.google.android.tungsten.setupwraith"
# What debloat-keep-cast.txt leaves enabled: casting's credentials and the ambient screensaver pair.
KEPT = {SETUPWRAITH, "com.google.android.apps.tv.dreamx", "com.google.android.backdrop"}
PACKAGE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$")


def packages(path):
    """The list's active lines, comments cut off, read the way tests/test_examples.py reads a list."""
    out = []
    for line in path.read_text(encoding="utf-8").split("\n"):
        body = line.split("#", 1)[0].strip()
        if body:
            out.append(body)
    return out


def header_bullets(path):
    """A list header's `# - ` bullets, with their `#   ` continuation lines joined and spaces squeezed."""
    found, current = [], None
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("# - "):
            if current is not None:
                found.append(current)
            current = line[4:]
        elif current is not None and line.startswith("#   "):
            current += " " + line[4:]
        else:
            if current is not None:
                found.append(current)
            current = None
    if current is not None:
        found.append(current)
    return [re.sub(r"\s+", " ", b) for b in found]


def bullets(text):
    """Split a markdown doc into its `- **Title**` bullets, wrapping lines included."""
    found, current = [], None
    for line in text.splitlines():
        if line.startswith("- **"):
            if current is not None:
                found.append(current)
            current = line
        elif current is not None and (line.startswith("  ") or not line.strip()):
            current += "\n" + line
        else:
            if current is not None:
                found.append(current)
            current = None
    if current is not None:
        found.append(current)
    return found


def one(items, needle, where):
    matches = [b for b in items if needle in b]
    assert len(matches) == 1, f"expected one bullet with {needle!r} in {where}, found {len(matches)}"
    return re.sub(r"\s+", " ", matches[0])


class KeepCastListTest(unittest.TestCase):
    def test_keep_cast_is_debloat_minus_the_three_kept_packages(self):
        full, keep = packages(DEBLOAT), packages(KEEP_CAST)
        for line in keep:
            self.assertRegex(line, PACKAGE, f"debloat-keep-cast.txt: not a package name: {line!r}")
        self.assertEqual(len(keep), len(set(keep)), "debloat-keep-cast.txt lists a package twice")
        self.assertEqual(sorted(set(keep) - set(full)), [],
                         "in debloat-keep-cast.txt only: add it to debloat.txt as well, or take it out")
        self.assertEqual(sorted(set(full) - set(keep)), sorted(KEPT),
                         "debloat-keep-cast.txt should be debloat.txt minus setupwraith, dreamx and "
                         "backdrop; add a package to both lists, or say in its header why not")

    def test_keep_cast_header_names_what_it_keeps(self):
        header = "\n".join(l for l in KEEP_CAST.read_text(encoding="utf-8").splitlines() if l.startswith("#"))
        self.assertIn("minus the three packages", header)
        for package in sorted(KEPT):
            with self.subTest(package=package):
                self.assertIn(package, header)


class SetupwraithWifiNoteTest(unittest.TestCase):
    def test_debloat_warns_that_setupwraith_can_own_the_saved_network(self):
        note = one(header_bullets(DEBLOAT), "cname=", "debloat.txt")
        self.assertIn("dumpsys wifi | grep -o 'cname=[^ ]*'", note)
        self.assertIn(SETUPWRAITH, note)
        self.assertIn("deletes that network", note)
        self.assertIn("android.uid.system:1000", note, "what the owner reads once it is safe")
        self.assertIn("tools/disable.sh skips setupwraith", note)
        self.assertIn("FORCE_SETUPWRAITH=1", note)

    def test_the_troubleshooting_entry_it_names_exists(self):
        note = one(header_bullets(DEBLOAT), "cname=", "debloat.txt")
        title = re.search(r'docs/TROUBLESHOOTING\.md, "([^"]+)"', note)
        self.assertIsNotNone(title, "the note should name its docs/TROUBLESHOOTING.md entry in quotes")
        self.assertTrue(any(b.startswith("- **" + title.group(1)) for b in bullets(DOC)),
                        f"no docs/TROUBLESHOOTING.md bullet starts with {title.group(1)!r}")

    def test_keep_cast_says_setupwraith_can_own_a_network(self):
        kept = one(header_bullets(KEEP_CAST), SETUPWRAITH, "debloat-keep-cast.txt")
        self.assertIn("credentials", kept, "the casting reason stays")
        self.assertIn("saved Wi-Fi network", kept)


class KeepCastPointersTest(unittest.TestCase):
    def test_the_folder_row_links_the_keep_cast_list(self):
        notes = json.loads((FOLDER / "tv.json").read_text(encoding="utf-8"))["notes"]
        self.assertIn("](google-chromecast-gtv-4k/debloat-keep-cast.txt)", notes)

    def test_the_untrusted_device_bullet_links_the_keep_cast_list(self):
        casting = one(bullets(DOC), "Casting from a phone fails", "docs/TROUBLESHOOTING.md")
        target = "../examples/google-chromecast-gtv-4k/debloat-keep-cast.txt"
        self.assertIn(f"]({target})", casting)
        self.assertTrue((ROOT / "docs" / target).resolve().is_file())


if __name__ == "__main__":
    unittest.main()

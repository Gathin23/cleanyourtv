"""examples/hy320 describes a manifest change and an undo that are not this repository's.

The folder records a projector whose Home key arrives as android.intent.category.HOME.JM, and it said
the launcher's AndroidManifest.xml had been updated to accept that category. The manifest here declares
android.intent.category.HOME on MainActivity and no HOME.JM, so that sentence described a build of the
owner's own: an owner who follows the folder from a clean checkout does not get the filter at all, and
the folder cannot be reproduced from this repository (#1799).

Two smaller statements in the same folder are checked here too, because they are the other two places
an owner is sent to a command that stops: the header said build-and-install.sh disabled the projector's
stock launcher, which the script cannot do by itself - com.htc.htclauncherhighenglishd08 is not one of
the launchers it recognises, so it stops at "not a stock launcher this script knows" unless
STOCK_LAUNCHER is given - and the Undo line named no stock launcher, so restore.sh fell back to
com.google.android.tvlauncher, the Android TV default this projector does not ship, leaving the stock
Home off after an undo.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "examples" / "hy320"
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
INSTALL = ROOT / "launcher" / "build-and-install.sh"

HOME_JM = "android.intent.category.HOME.JM"
HOME = "android.intent.category.HOME"

# An <intent-filter> is what makes an activity answer an intent; the categories of a <queries> block
# (the one naming HOME there is stock_home_redirect's lookup) declare nothing about the launcher.
FILTER = re.compile(r"<intent-filter\b[^>]*>(.*?)</intent-filter>", re.S)
CATEGORY = re.compile(r'<category\s+android:name="([^"]+)"')
KNOWN_LAUNCHERS = re.compile(r'^KNOWN_STOCK_LAUNCHERS="([^"]+)"', re.M)
STOCK_BULLET = re.compile(r"^# - (\S+): stock launcher", re.M)


def filter_categories(manifest_text):
    """The categories the manifest's intent filters declare."""
    names = set()
    for block in FILTER.findall(manifest_text):
        names.update(CATEGORY.findall(block))
    return names


def known_stock_launchers(script_text):
    """The packages build-and-install.sh accepts as a stock launcher on its own."""
    found = KNOWN_LAUNCHERS.search(script_text)
    if not found:
        raise ValueError("build-and-install.sh no longer declares KNOWN_STOCK_LAUNCHERS")
    return found.group(1).split()


def header_lines(text):
    return [line for line in text.splitlines() if line.startswith("#")]


def undo_arguments(text):
    """The words after tools/restore.sh on the list's '# Undo:' line."""
    for line in header_lines(text):
        if line.startswith("# Undo:"):
            words = line.split("# Undo:", 1)[1].split()
            if "tools/restore.sh" not in words:
                raise ValueError(f"the Undo line does not run tools/restore.sh: {line.strip()}")
            return words[words.index("tools/restore.sh") + 1:]
    raise ValueError("the list has no '# Undo:' line")


def stock_launcher(text):
    """The package the header's 'Kept on purpose' bullet calls the stock launcher."""
    found = STOCK_BULLET.search("\n".join(header_lines(text)))
    if not found:
        raise ValueError("the header has no '# - <package>: stock launcher' bullet")
    return found.group(1)


class Hy320FolderTest(unittest.TestCase):
    def setUp(self):
        self.readme = (FOLDER / "README.md").read_text(encoding="utf-8")
        self.list = (FOLDER / "debloat.txt").read_text(encoding="utf-8")
        self.categories = filter_categories(MANIFEST.read_text(encoding="utf-8"))
        # A reader that found nothing would let the comparisons below pass on any page.
        self.assertIn(HOME, self.categories,
                      "MainActivity's HOME filter is the one this folder's Home key goes through")

    def test_the_firmware_category_note_is_true_of_this_manifest(self):
        declared = HOME_JM in self.categories
        text = self.readme + "\n" + self.list
        if declared:
            # The filter is in the repository now, so the note that says it is missing has to go.
            self.assertNotIn("#1799", text,
                             "the launcher declares HOME.JM now: drop the note that says it does not")
        else:
            self.assertIn("#1799", text,
                          "the folder has to say the launcher here does not declare HOME.JM (#1799)")
            self.assertNotIn("manifest was updated", text,
                             "no HOME.JM filter was ever committed to launcher/AndroidManifest.xml")

    def test_the_undo_line_passes_the_stock_launcher(self):
        # restore.sh's default is com.google.android.tvlauncher; a set that does not ship it keeps its
        # stock Home disabled after an undo unless the package is named (#1308 did this for five lists).
        self.assertEqual(undo_arguments(self.list),
                         ["examples/hy320/debloat.txt", stock_launcher(self.list)])

    def test_the_folder_says_the_install_script_does_not_know_its_stock_launcher(self):
        stock = stock_launcher(self.list)
        known = known_stock_launchers(INSTALL.read_text(encoding="utf-8"))
        self.assertNotIn(stock, known,
                         f"{stock} is a launcher build-and-install.sh recognises now: the folder can "
                         f"stop telling the owner to pass STOCK_LAUNCHER")
        for name, text in (("README.md", self.readme), ("debloat.txt", self.list)):
            self.assertIn("STOCK_LAUNCHER=none", text,
                          f"{name} sends the owner to build-and-install.sh without saying that it "
                          f"refuses this set's stock launcher")


class ReaderTest(unittest.TestCase):
    def test_a_queries_entry_is_not_an_intent_filter(self):
        manifest = ('<manifest><queries><intent>'
                    f'<category android:name="{HOME}" />'
                    '</intent></queries>'
                    '<application><activity><intent-filter>'
                    '<category android:name="android.intent.category.DEFAULT" />'
                    '</intent-filter></activity></application></manifest>')
        self.assertEqual(filter_categories(manifest), {"android.intent.category.DEFAULT"})

    def test_every_filter_category_is_read(self):
        manifest = ('<intent-filter><category android:name="a.ONE" />'
                    '<category android:name="a.TWO" /></intent-filter>'
                    '<intent-filter><category android:name="a.TWO" />'
                    '<category android:name="a.THREE" /></intent-filter>')
        self.assertEqual(filter_categories(manifest), {"a.ONE", "a.TWO", "a.THREE"})

    def test_the_known_launchers_come_from_the_script(self):
        self.assertEqual(known_stock_launchers('KNOWN_STOCK_LAUNCHERS="com.a com.b"\n'), ["com.a", "com.b"])
        with self.assertRaises(ValueError):
            known_stock_launchers("no such line\n")

    def test_the_undo_arguments_skip_the_host_prefix(self):
        # TV_HOST=<tv ip> holds a space, so the words before the script name cannot be counted.
        text = ("# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/x/debloat.txt\n"
                "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt com.a.launcher\n")
        self.assertEqual(undo_arguments(text), ["examples/x/debloat.txt", "com.a.launcher"])

    def test_an_undo_line_without_a_launcher_reads_as_one_argument(self):
        text = "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
        self.assertEqual(undo_arguments(text), ["examples/x/debloat.txt"])

    def test_a_list_with_no_undo_line_is_refused(self):
        with self.assertRaises(ValueError):
            undo_arguments("# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/x/debloat.txt\n")

    def test_the_stock_launcher_is_the_one_the_bullet_names(self):
        header = ("# Kept on purpose:\n"
                  "# - com.htc.htcsettings: projector's own settings\n"
                  "# - com.htc.htclauncher: stock launcher, disabled during setup\n"
                  "# - com.softwinner.tcorrection: projection/keystone settings\n")
        self.assertEqual(stock_launcher(header), "com.htc.htclauncher")
        with self.assertRaises(ValueError):
            stock_launcher("# - com.htc.htcsettings: projector's own settings\n")


if __name__ == "__main__":
    unittest.main()

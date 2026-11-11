"""The FPD CG50-C3 note: a one-day list stays a note, and its remote lesson lands in the docs.

#475 supplied a working list plus two facts the repo was missing: a setupwraith check that has to be
two commands because the wizard owned a saved network that was not the one in use, and a remote whose
gear and notifications buttons arrive as scancodes 240/362 with `KEY_UNKNOWN`/`KEY_PROGRAM` - codes
that no `settings_key_keycodes` entry can ever match. The note itself is only worth having if it keeps
the list out of `examples/` while the two facts reach the docs a user actually reads, so the tests
parse sections and bullets rather than grepping the whole file: a sentence that drifts into a
neighbouring section would leave both just as useless.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    """Text of a repo file, or empty if it does not exist yet (each test then fails with its own message)."""
    path = ROOT / relative
    return path.read_text(encoding="utf-8") if path.is_file() else ""


NOTE = read("docs/devices/fpd-cg50-c3.md")
TROUBLE = read("docs/TROUBLESHOOTING.md")
PROMPT = read("PROMPT.md")
EXAMPLES_README = read("examples/README.md")

PACKAGES = [
    "com.mediatek.android.leanbacklauncher.partnercustomizer",
    "android.autoinstalls.config.google.gtvpai",
    "com.google.android.feedback",
    "com.google.android.apps.tv.dreamx",
    "fusion.android.tv.demo",
    "com.mstar.android.tv.disclaimercustomization",
    "com.electronic.manual",
    "com.mediatek.androidbox",
    "com.google.android.videos",
    "com.google.android.play.games",
    "com.google.android.youtube.tvmusic",
    "com.google.android.tungsten.setupwraith",
    "com.google.android.apps.tv.launcherx",
]


def section(title_needle):
    """Body of the `## ...` section whose heading contains the needle."""
    parts = re.split(r"(?m)^## ", NOTE)
    matches = [p for p in parts[1:] if title_needle.lower() in p.split("\n", 1)[0].lower()]
    assert len(matches) == 1, f"expected one section about {title_needle!r}, found {len(matches)}"
    return matches[0]


def table_rows(block):
    """Rows of the first markdown table in a block, as cell lists."""
    lines = [l for l in block.splitlines() if l.startswith("|")]
    assert lines, "no table in this section"
    rows = []
    for line in lines[2:]:
        rows.append([c.strip() for c in line.strip("|").split("|")])
    return rows


def fenced_text(block, info="text"):
    m = re.search(r"```" + info + r"\n(.*?)\n```", block, re.S)
    assert m, f"no ```{info} block"
    return m.group(1)


def bullets(text):
    """`- **...` bullets with their wrapped lines, as one flattened string each."""
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
    return [re.sub(r"\s+", " ", b) for b in found]


def bullet_containing(text, needle):
    matches = [b for b in bullets(text) if needle in b]
    assert len(matches) == 1, f"expected one bullet with {needle!r}, found {len(matches)}"
    return matches[0]


def prompt_bullet(needle):
    """PROMPT.md bullets start with a plain `- `, not `- **`, so they need their own reader."""
    found, current = [], None
    for line in PROMPT.splitlines():
        if line.startswith("- "):
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
    matches = [b for b in found if needle in b]
    assert len(matches) == 1, f"expected one prompt bullet with {needle!r}, found {len(matches)}"
    return re.sub(r"\s+", " ", matches[0])


class NoteKeepsTheListOutOfExamplesTest(unittest.TestCase):
    def test_no_examples_folder_for_a_one_day_list(self):
        self.assertFalse((ROOT / "examples" / "fpd-cg50-c3").exists(),
                         "a list applied the same day belongs in docs/devices/, not examples/")

    def test_the_note_says_why(self):
        self.assertIn("not an `examples/` list", NOTE)
        self.assertIn("CONTRIBUTING.md", NOTE)
        self.assertIn("one day", NOTE.lower())

    def test_limits_section_is_honest(self):
        limits = section("Limits")
        for phrase in ("One session", "no second unit", "reboot"):
            self.assertIn(phrase, limits)
        self.assertIn("Promote this to `examples/fpd-cg50-c3/`", limits)


class IdentityTest(unittest.TestCase):
    def test_firmware_fields(self):
        rows = dict((r[0], r[1]) for r in table_rows(section("Identify the firmware")))
        self.assertIn("`EcoTV Pro A`", rows["`ro.product.model`"])
        self.assertIn("`hongkong`", rows["`ro.product.device`"])
        self.assertIn("`FPD_4KHA0`", rows["`ro.product.name` (product)"])
        self.assertIn("`RTM9.241015.016`", rows["`ro.build.display.id` / firmware"])
        self.assertIn("11 / 30", rows["Android / SDK"])
        self.assertIn("`1`", rows["`ro.adb.secure`"])

    def test_adb_is_recorded_as_usb_toggle_then_closed(self):
        adb = section("Reaching ADB")
        self.assertIn("USB debugging alone", adb)
        self.assertIn("5555", adb)
        self.assertIn("refused after a reboot", adb)


class TranscribedListTest(unittest.TestCase):
    def setUp(self):
        self.block = fenced_text(section("The list as applied"))

    def test_every_listed_package_is_a_bare_name(self):
        packages = []
        for n, line in enumerate(self.block.splitlines(), 1):
            body = line.split("#", 1)[0].strip()
            if body:
                self.assertRegex(body, r"^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$",
                                 f"line {n} is not a package name: {line!r}")
                packages.append(body)

    def test_the_thirteen_packages_survived(self):
        packages = [l.split("#", 1)[0].strip() for l in self.block.splitlines()
                    if l.split("#", 1)[0].strip()]
        self.assertEqual(sorted(packages), sorted(PACKAGES))
        self.assertEqual(len(packages), len(set(packages)), "a package is listed twice")

    def test_the_list_is_attributed_to_the_owner(self):
        body = section("The list as applied")
        self.assertIn("#475", body)
        self.assertIn("Transcribed", body)

    def test_live_tv_and_inputs_are_answered(self):
        body = section("The list as applied")
        self.assertIn("com.mediatek.wwtv.tvcenter", body)
        self.assertIn("live_tv_package", body)
        self.assertIn("HDMI 1-3", body)


class SetupwraithCheckTest(unittest.TestCase):
    def setUp(self):
        self.body = section("setupwraith")

    def test_the_check_is_two_commands(self):
        # `dumpsys wifi` alone names every creator; only `cmd wifi status` names the one in use,
        # and that difference is what makes FORCE safe here.
        self.assertIn("dumpsys wifi | grep -o 'cname=[^ ]*'", self.body)
        self.assertIn("cmd wifi status", self.body)
        self.assertIn("not the one in use", self.body)

    def test_forcing_is_scoped_to_this_situation(self):
        self.assertIn("FORCE_SETUPWRAITH=1", self.body)
        self.assertIn("forget and rejoin", self.body)

    def test_crash_loop_was_checked_not_assumed(self):
        self.assertIn("logcat -b crash", self.body)


class RemoteScancodeTest(unittest.TestCase):
    def setUp(self):
        self.body = section("remote")
        self.rows = dict((r[0], (r[1], r[2])) for r in table_rows(self.body))

    def test_the_two_dead_buttons_are_scancode_and_unmapped_keycode(self):
        gear = next(v for k, v in self.rows.items() if "gear" in k)
        notifications = next(v for k, v in self.rows.items() if "notification" in k)
        self.assertEqual(gear[0], "240")
        self.assertIn("`KEY_UNKNOWN`", gear[1])
        self.assertEqual(notifications[0], "362")
        self.assertIn("`KEY_PROGRAM`", notifications[1])

    def test_no_keycode_can_match_them(self):
        self.assertIn("not a code you can list", self.body)
        self.assertIn("scancode", self.body)

    def test_the_fix_is_scancodes_with_the_home_helper(self):
        self.assertIn("settings_key_scancodes", self.body)
        self.assertIn("240, 362", self.body)
        self.assertIn("HOME_KEY_SERVICE=1", self.body)
        self.assertIn("only from", self.body)  # config.xml, not the theme

    def test_testing_needs_the_real_remote(self):
        # -t, not -lt: -l prints the key's name in place of the number the list takes.
        self.assertIn("getevent -t", self.body)
        self.assertIn("never\nreaches an accessibility service", self.body.replace("  ", " "))

    def test_working_buttons_come_from_the_firmware_routing(self):
        self.assertIn("dumpsys window policy", self.body)
        self.assertIn("com.mediatek.tv.service", self.body)


class KeptPackagesTest(unittest.TestCase):
    def test_the_factory_and_pairing_apps_are_explained(self):
        kept = section("What was kept")
        for package in ("com.ktc.tv.factory", "ktc.factorymenu.ui",
                        "com.mediatek.autopair", "com.ktc.serialport"):
            self.assertIn(package, kept)
        self.assertIn("purpose the owner could not establish", kept)


class DocsPointersTest(unittest.TestCase):
    def test_troubleshooting_bullet_carries_the_scancode_path(self):
        bullet = bullet_containing(TROUBLE, "Which keycode")
        self.assertIn("`KEY_UNKNOWN`", bullet)
        self.assertIn("`KEY_PROGRAM`", bullet)
        self.assertIn("settings_key_scancodes", bullet)
        self.assertIn("getevent -t", bullet)
        self.assertIn("HOME_KEY_SERVICE=1", bullet)
        self.assertIn("devices/fpd-cg50-c3.md", bullet)

    def test_prompt_tells_the_assistant_the_same(self):
        bullet = prompt_bullet("stock launcher also answers the remote's app buttons")
        self.assertIn("`KEY_UNKNOWN`", bullet)
        self.assertIn("`KEY_PROGRAM`", bullet)
        self.assertIn("settings_key_scancodes", bullet)
        self.assertIn("only `config.xml`", bullet)

    def test_examples_index_links_the_note_once(self):
        pointers = [l for l in EXAMPLES_README.splitlines() if "../docs/devices/fpd-cg50-c3.md" in l]
        self.assertEqual(len(pointers), 1, "the note should be indexed once in examples/README.md")
        self.assertIn("one-day", pointers[0])
        self.assertIn("240/362", pointers[0])


if __name__ == "__main__":
    unittest.main()

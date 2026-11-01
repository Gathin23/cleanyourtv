"""The Durabrand (#470) device note, and the factory-menu trap it reports.

The note is documentation only, so the tests pin the facts that make it useful (the packages, the
two that the firmware restores, the recovery sequence) and that its links still resolve.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "durabrand-durat32isdb.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
EXAMPLES_README = ROOT / "examples" / "README.md"
PROMPT = ROOT / "PROMPT.md"
FACTORY = "com.cvte.tv.hkcfac"

DISABLED = [
    "fusion.android.tv.demo",
    "com.haier.apps",
    "com.haier.apps.evoSmart",
    "com.mstar.android.tv.disclaimercustomization",
    "com.google.android.feedback",
    "com.google.android.videos",
    "com.google.android.play.games",
    "com.google.android.youtube.tvmusic",
    "com.android.printspooler",
    "com.google.android.syncadapters.calendar",
]

LINK = re.compile(r"\]\(([^)\s]+)\)")


def bullet(text, start):
    """The markdown bullet that begins with `start`, up to the next top-level bullet."""
    lines = text.split("\n")
    first = next(i for i, line in enumerate(lines) if line.startswith(start))
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def broken_links(path):
    broken = []
    for target in LINK.findall(path.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        local = target.split("#", 1)[0]
        if local and not (path.parent / local).exists():
            broken.append(target)
    return broken


class DurabrandNoteTest(unittest.TestCase):
    def test_note_exists_and_names_the_firmware(self):
        self.assertTrue(NOTE.is_file(), "docs/devices entry missing")
        text = NOTE.read_text(encoding="utf-8")
        for token in ("DURAT32ISDB", "dupont", "m7332", "RTM6.230109.232", "Android TV 11",
                      "2023-08-01", "issues/470"):
            with self.subTest(token=token):
                self.assertIn(token, text)

    def test_every_disabled_package_is_recorded(self):
        text = NOTE.read_text(encoding="utf-8")
        for package in DISABLED:
            with self.subTest(package=package):
                self.assertIn(package, text)

    def test_the_two_boot_restored_packages_are_called_out(self):
        # Disabling them looks successful until the next boot, which is the whole point of the note.
        text = NOTE.read_text(encoding="utf-8")
        self.assertIn("com.google.android.tvrecommendations", text)
        self.assertIn("com.haier.launcher.haiermatrix", text)
        self.assertIn("com.at.factory.factorymanager", text)
        self.assertRegex(text, r"(?i)re-?enabled? (?:on|at) every\s+boot|come back on every\s+boot")

    def test_note_admits_it_is_not_a_tested_list(self):
        text = NOTE.read_text(encoding="utf-8")
        self.assertRegex(text, r"(?i)not\*\* a tested list|not a tested list")
        self.assertIn("several days", text)
        # A one-day report must not be indexed as an examples/ list.
        self.assertFalse(list((ROOT / "examples").glob("durabrand*")),
                         "a same-day report does not belong in examples/")

    def test_note_keeps_the_input_panel_and_setupwraith_apart(self):
        text = NOTE.read_text(encoding="utf-8")
        self.assertIn("com.mediatek.wwtv.tvcenter", text)
        self.assertIn("INPUT_KEY_SERVICE", text)
        self.assertIn("com.google.android.tungsten.setupwraith", text)

    def test_recovery_sequence_is_in_order(self):
        text = NOTE.read_text(encoding="utf-8")
        block = text.split("```sh", 1)[1].split("```", 1)[0]
        steps = ["am start -n %s/.MainActivity" % FACTORY, "input keyevent 218",
                 "tools/tv.py --reboot", "hdmi_control_enabled 1"]
        positions = [block.find(step) for step in steps]
        self.assertNotIn(-1, positions, f"missing recovery step in {steps}")
        self.assertEqual(positions, sorted(positions), "recovery steps out of order")
        self.assertRegex(text, r"(?i)in front")

    def test_note_links_resolve(self):
        self.assertEqual(broken_links(NOTE), [])


class TroubleshootingTrapTest(unittest.TestCase):
    def setUp(self):
        self.text = TROUBLE.read_text(encoding="utf-8")
        self.trap = bullet(self.text, "- **The Input key and CEC die")

    def test_trap_names_the_cause_and_the_damage(self):
        for token in (FACTORY, "customer_factory_mode", "vendor.mtk.factory.disable.input",
                      "fdata.ini", "HDMI-CEC", "issues/470",
                      "com.google.android.katniss", "com.google.android.apps.mediashell"):
            with self.subTest(token=token):
                self.assertIn(token, self.trap)

    def test_trap_explains_the_reboot_failure_and_the_shell_limit(self):
        self.assertRegex(self.trap, r"(?i)refused")
        self.assertIn("factorymanager", self.trap)

    def test_trap_has_the_way_out(self):
        for command in (f"com.cvte.tv.hkcfac/.MainActivity", "input keyevent 218",
                        "tools/tv.py --reboot", "hdmi_control_enabled 1"):
            with self.subTest(command=command):
                self.assertIn(command, self.trap)
        self.assertLess(self.trap.find("am start"), self.trap.find("keyevent 218"))

    def test_trap_points_at_the_device_note_and_that_link_works(self):
        self.assertIn("devices/durabrand-durat32isdb.md", self.trap)
        self.assertEqual(broken_links(TROUBLE), [])

    def test_trap_does_not_look_like_the_setupwraith_credentials_case(self):
        # The reader must not go re-enable the setup wizard for a stuck Input key.
        self.assertNotIn("CastInfoProvider", self.trap)


class IndexAndPromptTest(unittest.TestCase):
    def test_examples_readme_lists_the_note(self):
        text = EXAMPLES_README.read_text(encoding="utf-8")
        section = text.split("Notes for models that have no tested list yet", 1)[1]
        self.assertIn("docs/devices/durabrand-durat32isdb.md", section)
        self.assertIn("DURAT32ISDB", section)
        self.assertEqual(broken_links(EXAMPLES_README), [])

    def test_prompt_tells_the_assistant_not_to_open_factory_menus(self):
        text = PROMPT.read_text(encoding="utf-8")
        lines = text.split("\n")
        starts = [i for i, line in enumerate(lines)
                  if line.startswith("- ") and "factory" in line.lower()]
        self.assertTrue(starts, "PROMPT.md has no line about factory menus")
        warn = []
        for start in starts:
            for line in lines[start:]:
                if warn and (line.startswith("- ") or line.startswith("## ") or not line.strip()):
                    break
                warn.append(line)
        joined = " ".join(warn)
        self.assertIn("dumpsys package", joined)
        self.assertIn("TROUBLESHOOTING", joined)
        self.assertIn(FACTORY, joined)


if __name__ == "__main__":
    unittest.main()

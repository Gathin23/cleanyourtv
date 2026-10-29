"""The Sony K-85XR70 (#590) density note, and the diagnostic it feeds into TROUBLESHOOTING.

Documentation only, so the tests pin the numbers a reader has to be able to trust (the three boots,
the empty saved density, the two-command workaround and its undo) and the links that carry them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
NOTE = ROOT / "docs" / "devices" / "sony-k85xr70-ae2.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
EXAMPLES_README = ROOT / "examples" / "README.md"
PROMPT = ROOT / "PROMPT.md"

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


def table_rows(text, header_start):
    """The pipe rows of the first table whose header line starts with `header_start`."""
    rows = []
    for line in text.split("\n"):
        if not line.startswith("|"):
            if rows:
                break
            continue
        if line.startswith("|---") or line.startswith("| ---"):
            continue
        if not rows and not line.startswith(header_start):
            continue
        rows.append([cell.strip() for cell in line.strip().strip("|").split("|")])
    return rows


def broken_links(path):
    broken = []
    for target in LINK.findall(path.read_text(encoding="utf-8")):
        if target.startswith(("http://", "https://", "mailto:", "#")):
            continue
        local = target.split("#", 1)[0]
        if local and not (path.parent / local).exists():
            broken.append(target)
    return broken


class SonyDensityNoteTest(unittest.TestCase):
    def setUp(self):
        self.text = NOTE.read_text(encoding="utf-8")

    def test_note_exists_and_names_the_firmware(self):
        self.assertTrue(NOTE.is_file(), "docs/devices entry missing")
        for token in ("K-85XR70", "BRAVIA 4K AE2", "114.602.170.1", "Android 14",
                      "issues/29", "issues/590"):
            with self.subTest(token=token):
                self.assertIn(token, self.text)

    def test_note_says_the_1080p_ui_is_the_firmware(self):
        # The whole point of keeping #29 and #590 together: 1080p is normal here, 640 is not.
        for token in ("ro.surface_flinger.max_graphics_width", "ro.surface_flinger.max_graphics_height",
                      "ro.vendor.mtk.ui.resolution", "1920×1080"):
            with self.subTest(token=token):
                self.assertIn(token, self.text)
        self.assertRegex(self.text, r"(?i)not evidence of damage|is the normal state")

    def test_boot_table_carries_the_three_sessions(self):
        rows = table_rows(self.text, "| Boot |")
        self.assertEqual(len(rows), 4, f"expected header + 3 boots, got {rows}")
        failing = [row for row in rows[1:] if "640" in " ".join(row)]
        self.assertEqual(len(failing), 1, "the 640 boot must be one row")
        self.assertIn("empty", failing[0][-1], "the failing boot had no saved density")
        recovered = [row for row in rows[1:] if "320" in row[-1]]
        self.assertEqual(len(recovered), 1, "one row must show the saved density after the fix")

    def test_table_records_the_properties_the_number_comes_from(self):
        rows = table_rows(self.text, "| Field |")
        table = {row[0]: row[1] for row in rows[1:]}
        self.assertIn("320", table["Panel"])
        self.assertEqual(table["UI resolution"], "1920×1080")

    def test_workaround_is_two_commands_in_order_then_a_reboot(self):
        block = self.text.split("```sh", 1)[1].split("```", 1)[0]
        steps = ["wm density 320",
                 "settings --user 0 put secure display_density_forced 320",
                 "tv.py --reboot"]
        positions = [block.find(step) for step in steps]
        self.assertNotIn(-1, positions, f"missing step in {steps}")
        self.assertEqual(positions, sorted(positions), "the saved density must come after wm density")
        self.assertRegex(self.text, r"(?i)wm density 320` alone did (?:the same|not)")

    def test_note_carries_the_undo_and_the_warning(self):
        self.assertIn("settings delete secure display_density_forced", self.text)
        self.assertIn("wm density reset", self.text)
        self.assertRegex(self.text, r"(?i)do not copy|don't copy 320")
        self.assertRegex(self.text, r"(?i)empty (?:saved density )?is a valid")

    def test_note_admits_what_is_not_established(self):
        self.assertIn("## What is not established", self.text)
        for token in ("factory-default", "vendor framework", "no long standby"):
            with self.subTest(token=token):
                self.assertIn(token, self.text)
        # A density report does not make the set an examples/ entry.
        self.assertFalse(list((ROOT / "examples").glob("sony-k85xr70*")),
                         "a report without a debloat list does not belong in examples/")

    def test_note_keeps_the_launcher_findings_separate(self):
        self.assertIn("## Launcher compatibility (from #29)", self.text)
        self.assertIn("com.dangbei.TVHomeLauncher", self.text)
        self.assertIn("com.sony.dtv.tvlin", self.text)
        self.assertRegex(self.text, r"(?i)not a claim\s+about Android 11\+|not a claim about Android 11\+")

    def test_note_links_resolve(self):
        self.assertEqual(broken_links(NOTE), [])


class TroubleshootingBulletTest(unittest.TestCase):
    def setUp(self):
        self.text = TROUBLE.read_text(encoding="utf-8")
        self.trap = bullet(self.text, "- **The whole UI is huge and clipped")

    def test_bullet_has_the_three_readings(self):
        for command in ("tools/tv.py 'wm size'", "tools/tv.py 'wm density'",
                        "settings --user 0 get secure display_density_forced"):
            with self.subTest(command=command):
                self.assertIn(command, self.trap)

    def test_bullet_separates_the_override_from_the_launcher(self):
        self.assertIn("issues/590", self.trap)
        self.assertIn("issues/29", self.trap)
        self.assertRegex(self.trap, r"(?i)override size")
        self.assertRegex(self.trap, r"(?i)empty saved density is a normal Android default")

    def test_bullet_has_the_reboot_trap_and_the_way_out(self):
        self.assertIn("wm density 320", self.trap)
        self.assertIn("display_density_forced 320", self.trap)
        self.assertIn("wm density reset", self.trap)
        self.assertIn("settings delete secure display_density_forced", self.trap)
        self.assertLess(self.trap.find("wm density 320"),
                        self.trap.find("display_density_forced 320"),
                        "writing the saved density before the runtime one is backwards")

    def test_bullet_does_not_send_the_reader_to_the_other_sony_cases(self):
        # The empty-screenshot and HOME-handover bullets mention other Sony sets; this one must not
        # read like either of them.
        self.assertNotIn("screencap", self.trap)
        self.assertNotIn("leanbacklauncher", self.trap)

    def test_bullet_points_at_the_note_and_that_link_works(self):
        self.assertIn("devices/sony-k85xr70-ae2.md", self.trap)
        self.assertEqual(broken_links(TROUBLE), [])


class IndexAndPromptTest(unittest.TestCase):
    def test_examples_readme_lists_the_note(self):
        text = EXAMPLES_README.read_text(encoding="utf-8")
        section = text.split("Notes for models that have no tested list yet", 1)[1]
        self.assertIn("docs/devices/sony-k85xr70-ae2.md", section)
        self.assertIn("BRAVIA 4K AE2", section)
        self.assertEqual(broken_links(EXAMPLES_README), [])

    def test_prompt_checks_density_before_the_grid(self):
        text = PROMPT.read_text(encoding="utf-8")
        self.assertIn("`wm density`", text)
        self.assertIn("display_density_forced", text)
        self.assertIn("TROUBLESHOOTING", text)
        self.assertRegex(text, r"(?i)not a launcher bug")


if __name__ == "__main__":
    unittest.main()

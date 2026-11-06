"""The read-only state block at the top of the accessibility helper section.

The block is what a reader runs before changing anything, and what a bug report is built from. It has
to read both secure settings that decide whether a helper filters keys (the enabled list and the global
`accessibility_enabled` flag, which can disagree after a reboot), the dump that shows the binding, and
the launcher's app-ops, where firmware auto-start denials show up. None of it may write anything.

The sentence after the block tells a reporter what to hand over and points at the auto-start note
further down the same section: the pointer is only honest while that note exists there, after the
block, and asks for the same details.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
SECTION = "## Accessibility helper enabled but the remote button does nothing"
INTRO = "Read the state without changing anything:"
REPORT = "For a bug report, give these outputs as"
AUTO_START_REPORT = "When reporting, give the"
COMMANDS = (
    "tools/tv.py 'am get-current-user'",
    "tools/tv.py 'settings --user current get secure enabled_accessibility_services'",
    "tools/tv.py 'settings --user current get secure accessibility_enabled'",
    "tools/tv.py 'dumpsys accessibility'",
    "tools/tv.py 'appops get com.gathin.tvhome'",
)
WRITES = (" put ", " set ", "write-settings", "force-stop", " delete ", " reset ", "pm ", "install")


def section(text, heading):
    """The body under a `## ` heading, up to the next `## `."""
    lines = text.split("\n")
    start = next(i for i, line in enumerate(lines) if line.startswith(heading))
    out = []
    for line in lines[start + 1:]:
        if line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def block_after(text, marker):
    """The lines of the first fenced code block that follows `marker`."""
    lines = text.split("\n")
    start = next(i for i, line in enumerate(lines) if marker in line)
    opening = next(i for i in range(start + 1, len(lines)) if lines[i].strip().startswith("```"))
    closing = next(i for i in range(opening + 1, len(lines)) if lines[i].strip().startswith("```"))
    return [line.strip() for line in lines[opening + 1:closing] if line.strip()]


def paragraph(text, start):
    """The prose paragraph that contains `start`, joined onto one line."""
    for para in text.split("\n\n"):
        if start in para:
            return " ".join(line.strip() for line in para.split("\n"))
    raise ValueError(start)


class AccessibilityReadStateBlockTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.trouble_text = TROUBLE.read_text(encoding="utf-8")
        cls.section_text = section(cls.trouble_text, SECTION)
        cls.block = block_after(cls.section_text, INTRO)
        cls.report = paragraph(cls.section_text, REPORT)
        cls.auto_start = paragraph(cls.section_text, AUTO_START_REPORT)

    def test_block_gives_every_command_in_order(self):
        self.assertEqual(self.block, list(COMMANDS))

    def test_block_reads_the_global_flag(self):
        # The enabled list can name the helper while the flag reads 0: only this line tells them apart.
        self.assertIn("tools/tv.py 'settings --user current get secure accessibility_enabled'",
                      self.block)

    def test_block_reads_the_launcher_app_ops(self):
        # An auto-start denial is the other way a listed helper stays unbound.
        self.assertIn("tools/tv.py 'appops get com.gathin.tvhome'", self.block)

    def test_both_settings_are_read_for_the_current_user(self):
        settings = [command for command in self.block if "settings " in command]
        self.assertEqual(len(settings), 2)
        for command in settings:
            with self.subTest(command=command):
                self.assertIn("settings --user current get secure ", command)

    def test_block_changes_nothing(self):
        for command in self.block:
            for word in WRITES:
                with self.subTest(command=command, word=word):
                    self.assertNotIn(word, command)

    def test_every_command_goes_through_tv_py(self):
        for command in self.block:
            with self.subTest(command=command):
                self.assertTrue(command.startswith("tools/tv.py '") and command.endswith("'"))

    def test_report_sentence_follows_the_block_before_the_table(self):
        text = self.section_text
        block_end = text.index(COMMANDS[-1])
        report = text.index(REPORT)
        table = text.index("| Helper |")
        self.assertLess(block_end, report)
        self.assertLess(report, table)
        self.assertEqual(text.count(REPORT), 1)

    def test_report_sentence_names_what_to_give(self):
        for field in ("model", "Android version", "build", "current user", "redacted lines"):
            with self.subTest(field=field):
                self.assertIn(field, self.report)

    def test_report_sentence_asks_against_the_raw_dump(self):
        self.assertIn("not the raw dump", self.report)

    def test_report_sentence_points_at_the_auto_start_note(self):
        self.assertIn("auto-start note", self.report)
        self.assertIn("this section", self.report)

    def test_auto_start_note_exists_later_in_the_same_section(self):
        self.assertIn("auto-start", self.auto_start)
        self.assertIn("APP_AUTO_START", self.auto_start)
        self.assertLess(self.section_text.index(REPORT), self.section_text.index(AUTO_START_REPORT))

    def test_auto_start_note_asks_for_what_the_pointer_says(self):
        for field in ("model", "build", "current user", "redacted lines", "not the raw dump"):
            with self.subTest(field=field):
                self.assertIn(field, self.auto_start)
                self.assertIn(field, self.report)

    def test_auto_start_note_uses_the_same_appops_read(self):
        self.assertIn("`appops get com.gathin.tvhome`", self.auto_start)

    def test_block_extraction_reads_only_the_first_fence_after_the_marker(self):
        sample = "\n".join(["```sh", "before", "```", INTRO, "", "```sh", "a", "", "b", "```",
                            "```sh", "after", "```"])
        self.assertEqual(block_after(sample, INTRO), ["a", "b"])

    def test_paragraph_extraction_joins_wrapped_lines(self):
        sample = "first\n\n" + REPORT + " one\nand two.\n\nnext"
        self.assertEqual(paragraph(sample, REPORT), REPORT + " one and two.")

    def test_paragraph_extraction_names_a_missing_paragraph(self):
        with self.assertRaises(ValueError):
            paragraph("nothing here", REPORT)

    def test_section_extraction_stops_at_the_next_heading(self):
        sample = "\n".join(["## One", "body", "## Two", "other"])
        self.assertEqual(section(sample, "## One"), "body")


if __name__ == "__main__":
    unittest.main()

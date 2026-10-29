"""Pins the second data point for the key-helper/auto-repeat trade (#944).

docs/devices/tcl-55p71b-g09-key-repeat.md was written on one TCL build, and its own Limits said the
claim belongs to that build: whether other firmwares do the same was open. #944 answers part of that
with a Xiaomi MiTV-MOSR4 (Google TV 11, MediaTek) where the launcher's own helpers cost the same repeat,
which is worth recording next to the bisect - but it is a weaker report than the first one: the owner did
not run the empty-list control (clearing the list unhooks the buttons he installed the helpers for), and
the build string he gives is not the one docs/devices/xiaomi-mitv-mosr4.md carries. These tests keep the
section from growing into a claim the report does not support, and keep the two notes and the two
entry points pointing at each other.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
G09 = ROOT / "docs" / "devices" / "tcl-55p71b-g09-key-repeat.md"
MOSR4 = ROOT / "docs" / "devices" / "xiaomi-mitv-mosr4.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
INDEX = ROOT / "examples" / "README.md"
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java"

HEADING = "## The same trade on a Xiaomi MediaTek set"
BUILD_IN_ISSUE = "RTM5.220609.003.1265"
BUILD_IN_NOTE = "RTM5.220609.003/1585"
RECEIVER = "com.xiaomi.android.tvsetup.partnercustomizer/.GlobalKeyReceiver"


def flat(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


def section_of(text, heading):
    """The flattened body under `heading`, up to the next `##` heading; None when it is not there."""
    flat_text = re.sub(r"\s+", " ", text)
    if heading not in flat_text:
        return None
    rest = flat_text.split(heading, 1)[1]
    following = re.search(r"\s##\s", rest)
    return rest if following is None else rest[:following.start()]


def section(path):
    return section_of(path.read_text(encoding="utf-8"), HEADING)


def links(path):
    text = path.read_text(encoding="utf-8")
    return [str((path.parent / target).resolve()) for target in re.findall(r"\]\(([^)\s]+)\)", text)
            if not target.startswith(("http", "#"))]


class SecondUnitTest(unittest.TestCase):
    def setUp(self):
        body = section(G09)
        self.assertIsNotNone(body, f"{HEADING} is gone from the G09 note")
        self.section = body or ""

    def test_says_which_set_and_which_build(self):
        for needle in ("#944", "MiTV-MOSR4", "`irobot`", BUILD_IN_ISSUE, "Android 11", "MediaTek"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.section)

    def test_keeps_both_rows_of_the_bisect(self):
        self.assertIn("with `InputKeyService` and `HomeKeyService` bound moves it one step", self.section)
        self.assertIn("`InputKeyService` alone is still one step", self.section)

    def test_names_the_missing_control_instead_of_borrowing_it(self):
        self.assertIn("empty-list control was not run", self.section)
        self.assertIn("still measured on the G09 alone", self.section,
                      "the control was not repeated on this set, so it must not be reported as if it was")

    def test_names_the_capability_and_how_it_was_read(self):
        for needle in ("capabilities=8", "CAPABILITY_CAN_FILTER_KEY_EVENTS", "dumpsys accessibility"):
            with self.subTest(needle=needle):
                self.assertIn(needle, self.section)

    def test_the_two_build_strings_are_kept_apart(self):
        self.assertIn(BUILD_IN_NOTE, self.section,
                      "the MOSR4 note's own build has to be named, or the two units read as one")
        self.assertIn("separate units", self.section)
        self.assertIn(BUILD_IN_NOTE, flat(MOSR4), "the note this section defers to no longer carries that build")

    def test_the_receiver_claim_matches_the_mosr4_note(self):
        self.assertIn(RECEIVER, self.section)
        note = flat(MOSR4)
        self.assertIn(RECEIVER, note, "the receiver is the MOSR4 note's finding, not something to invent here")
        for button in ("Input", "Settings", "YouTube", "Netflix"):
            with self.subTest(button=button):
                self.assertIn(button, self.section)

    def test_no_fix_is_promised_and_the_suggestion_is_left_to_the_launcher(self):
        self.assertIn("adjustSuggestedStreamVolume", self.section)
        self.assertIn("is not in this note", self.section)
        self.assertNotIn("solves", self.section.lower(),
                         "the suggestion is untested here; it does not belong in a note as an answer")
        if JAVA.is_dir():
            sources = "\n".join(p.read_text(encoding="utf-8") for p in JAVA.rglob("*.java"))
            self.assertNotIn("adjustSuggestedStreamVolume", sources,
                             "the launcher now synthesises the repeat: the note's wording has to change with it")
        self.assertIn("does the same is untested", flat(G09),
                      "the G09 Limits sentence this section defers to was reworded away")


class EntryPointsTest(unittest.TestCase):
    def test_the_mosr4_note_points_back(self):
        text = flat(MOSR4)
        self.assertIn("#944", text)
        self.assertIn("capabilities=8", text)
        self.assertIn(str(G09.resolve()), links(MOSR4))
        self.assertIn("unhooks the buttons", text, "the reason the control was skipped belongs there too")

    def test_troubleshooting_mentions_the_second_set(self):
        text = flat(TROUBLE)
        heading = "## A key helper is bound and a held remote button stopped repeating"
        self.assertEqual(text.count(heading), 1)
        body = text[text.index(heading):]
        body = body[:body.find(" ## ")] if " ## " in body else body
        self.assertIn("MiTV-MOSR4", body)
        self.assertIn(BUILD_IN_ISSUE, body)
        self.assertIn("#944", body)
        self.assertIn("empty list was not tried", body)

    def test_the_index_row_warns_before_the_helpers_are_installed(self):
        rows = [line for line in INDEX.read_text(encoding="utf-8").split("\n")
                if line.startswith("| `xiaomi-mitv-mosr4/`")]
        self.assertEqual(len(rows), 1)
        self.assertIn("cost the volume's live repeat", rows[0])
        self.assertIn(str(MOSR4.resolve()), links(INDEX))

    def test_links_resolve(self):
        for page in (G09, MOSR4):
            for target in links(page):
                with self.subTest(page=page.name, target=target):
                    self.assertTrue(Path(target).exists(), "relative link target does not exist")


class SectionReaderTest(unittest.TestCase):
    def test_missing_heading_is_none(self):
        self.assertIsNone(section_of("# A page\n\nSome text.\n", HEADING))

    def test_stops_at_the_next_heading(self):
        text = f"# Page\n\n{HEADING}\n\nBody.\n\n## Choices\n\nLater.\n"
        self.assertIn("Body.", section_of(text, HEADING))
        self.assertNotIn("Later.", section_of(text, HEADING))

    def test_a_deeper_heading_does_not_end_it(self):
        text = f"# Page\n\n{HEADING}\n\nBody.\n\n### Detail\n\nStill here.\n\n## Choices\n\nGone.\n"
        body = section_of(text, HEADING)
        self.assertIn("Still here.", body)
        self.assertNotIn("Gone.", body)

    def test_last_section_runs_to_the_end(self):
        text = f"# Page\n\n{HEADING}\n\nBody.\n"
        self.assertIn("Body.", section_of(text, HEADING))


if __name__ == "__main__":
    unittest.main()

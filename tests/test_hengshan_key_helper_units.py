"""Pins the third unit of the `hengshan` board's key-helper/volume-ramp trade (#1326).

The key-helper section of docs/TROUBLESHOOTING.md described the Hisense case as one unit *with a CEC
soundbar*, and explained the loss through the CEC step. #1326 is a 55U7N on the same board and build
(`hengshan`, Google TV 12, `STT2.230310.002`) that steps once per press with `HomeKeyService` alone and
no soundbar in the report, so the soundbar is what that unit's log accounted for, not what the loss
needs. These tests keep the three places that describe the board - the list header, the folder notes and
the troubleshooting page - naming the units the list records and the build and keycodes the notes carry,
so the page cannot describe one unit as if it were the whole board, and a fourth unit cannot be added to
the list without the page that an owner searches before installing a helper following.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "examples" / "hisense-u75-hengshan"
LIST = FOLDER / "debloat.txt"
NOTE = FOLDER / "README.md"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"

HEADING = "## A key helper is bound and a held remote button stopped repeating"
BOARD = "hengshan"
# A model token as the list header and the notes write them: 75U75N, 55U7N.
MODEL = re.compile(r"\b(\d{2}[A-Z]\d[A-Z0-9]{0,2})\b")
UNIT_LINE = re.compile(r"^#\s*(?:Second|Third|Fourth|Fifth|Sixth)\s+unit:\s*(.+)$")
BUILD = re.compile(r"build\s+([A-Z0-9]+(?:\.[A-Z0-9]+)*)")


def flat(path):
    return re.sub(r"\s+", " ", path.read_text(encoding="utf-8"))


def header(text):
    """The comment block before the list's own commands: what the file says about the units it came from."""
    return text.split("# Apply:", 1)[0]


def units(text):
    """Every model the header records as a further unit of the board, in the order it records them."""
    found = []
    for line in text.splitlines():
        match = UNIT_LINE.match(line.strip())
        if not match:
            continue
        model = MODEL.search(match.group(1))
        if model:
            found.append(model.group(1))
    return found


def build(text):
    """The firmware build the header states, or '' when it states none."""
    match = BUILD.search(header(text))
    return match.group(1) if match else ""


def section_of(text, heading):
    """The flattened body under `heading` up to the next `##` heading; None when the heading is gone."""
    flat_text = re.sub(r"\s+", " ", text)
    if heading not in flat_text:
        return None
    rest = flat_text.split(heading, 1)[1]
    following = re.search(r"\s##\s", rest)
    return rest if following is None else rest[:following.start()]


def board_sentences(text, heading):
    """The sentences of that section that talk about this board, flattened."""
    body = section_of(text, heading)
    if body is None:
        return []
    return [sentence for sentence in re.split(r"(?<=[.:]) ", body)
            if "Hisense" in sentence or f"`{BOARD}`" in sentence or BOARD in sentence]


def remote_codes(text):
    """{button: keycode} from the folder notes' remote table, read the way the table lays it out."""
    codes = {}
    for line in text.splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 2 or not line.strip().startswith("|"):
            continue
        code = re.match(r"(\d{2,4})\b", cells[1])
        if code and cells[0] and not set(cells[0]) <= set("-: "):
            codes[cells[0]] = code.group(1)
    return codes


class BoardTest(unittest.TestCase):
    def setUp(self):
        self.list_text = LIST.read_text(encoding="utf-8")
        self.note = NOTE.read_text(encoding="utf-8")
        self.trouble = TROUBLE.read_text(encoding="utf-8")
        self.body = section_of(self.trouble, HEADING)
        self.assertIsNotNone(self.body, f"{HEADING} is gone from docs/TROUBLESHOOTING.md")
        self.units = units(self.list_text)

    def test_the_list_still_records_more_than_one_unit(self):
        self.assertGreaterEqual(len(self.units), 2,
                                "the list header records the units this board was tested on; it now names "
                                f"{self.units}, so the checks below would read nothing")

    def test_the_page_quotes_the_build_the_list_states(self):
        recorded = build(self.list_text)
        self.assertTrue(recorded, "the list header no longer states its firmware build")
        self.assertIn(recorded, self.body,
                      f"docs/TROUBLESHOOTING.md quotes another build for this board than {recorded}")

    def test_the_last_unit_recorded_in_the_list_is_named_on_the_page(self):
        newest = self.units[-1]
        self.assertIn(newest, self.body,
                      f"{newest} is recorded as a unit of this board but the key-helper section does not name "
                      "it, so its owner cannot tell the trade applies to their set")

    def test_the_page_invents_no_unit_this_board_never_shipped(self):
        recorded = set(self.units) | {"U75", "U7N"}
        named = set()
        for sentence in board_sentences(self.trouble, HEADING):
            named.update(MODEL.findall(sentence))
        self.assertTrue(named, "the page no longer names a model of this board")
        self.assertLessEqual(named, recorded,
                             f"the page names {sorted(named - recorded)} for this board; the list records "
                             f"{sorted(recorded)}")

    def test_the_app_button_codes_on_the_page_are_the_notes_codes(self):
        table = remote_codes(self.note)
        self.assertGreaterEqual(len(table), 5, f"the remote table stopped parsing: {table}")
        codes = set()
        for sentence in board_sentences(self.trouble, HEADING):
            # a build string carries its own digits (STT2.230310.002), so it is not read as a keycode
            codes.update(re.findall(r"\b(\d{3})\b", re.sub(r"[A-Z0-9]+\.[A-Z0-9.]+", " ", sentence)))
        self.assertTrue(codes, "the page no longer quotes this board's keycodes")
        self.assertLessEqual(codes, set(table.values()),
                             f"the page quotes {sorted(codes - set(table.values()))}, which the remote table "
                             f"does not carry ({sorted(table.items())})")

    def test_every_unit_the_list_records_is_named_in_the_folder_notes(self):
        for unit in self.units:
            with self.subTest(unit=unit):
                self.assertIn(unit, self.note,
                              f"{unit} is recorded in the list header but the folder notes do not mention it")

    def test_the_new_link_resolves(self):
        for target in re.findall(r"\]\(([^)\s]+)\)", self.trouble):
            if target.startswith(("http", "#")):
                continue
            path = target.split("#", 1)[0]
            with self.subTest(target=target):
                self.assertTrue((TROUBLE.parent / path).resolve().exists(), "relative link target is gone")


class ReaderTest(unittest.TestCase):
    def test_units_reads_each_further_unit_in_order(self):
        text = "# Board, build X1.2.\n# Second unit: Hisense 75U75N, US market.\n# Third unit: Hisense 55U7N.\n# Apply:\n"
        self.assertEqual(units(text), ["75U75N", "55U7N"])

    def test_a_unit_line_without_a_model_is_not_a_unit(self):
        text = "# Board.\n# Second unit: same one, no model given.\n"
        self.assertEqual(units(text), [])

    def test_build_is_read_before_the_commands_only(self):
        text = "# Board, build STT2.230310.002.\n# Apply: run something that mentions build elsewhere\n"
        self.assertEqual(build(text), "STT2.230310.002")
        self.assertEqual(build("# Board with no build line.\n# Apply: x\n"), "")

    def test_a_missing_heading_reads_as_none(self):
        self.assertIsNone(section_of("# A page\n\nSome text.\n", HEADING))

    def test_the_section_stops_at_the_next_heading(self):
        text = f"# Page\n\n{HEADING}\n\nHisense 75U75N.\n\n## Later\n\nGone.\n"
        self.assertIn("Hisense 75U75N.", section_of(text, HEADING))
        self.assertNotIn("Gone.", section_of(text, HEADING))

    def test_remote_codes_reads_the_table_and_skips_its_header(self):
        text = ("| Button | Keycode | Fate |\n|---|---|---|\n"
                "| Prime Video | 295 (`KEY_FN_F3`) | nothing |\n| Gear | 83 (`KEY_NOTIFICATION`) | nothing |\n")
        self.assertEqual(remote_codes(text), {"Prime Video": "295", "Gear": "83"})


if __name__ == "__main__":
    unittest.main()

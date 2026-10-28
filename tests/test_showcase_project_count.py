"""SHOWCASE.md opens with "N projects so far.", and nothing was counting the rows.

The line was right at 70 rows. Then four projects landed in three separate merges - the HAAM HDMI
visibility fork, the Bluetooth pill fork and Tellyscope - and one of the three bumped the number
(70 -> 71) while the others did not, so the page offered a reader four projects fewer than it holds.
README.md's "Built something on top?" row spells the same number and had drifted on its own: it said
seventy while the page said 71.

These tests read the number out of the page rather than copying it into the test, count the project
rows the way the page lays them out (a table row whose first cell is a project link), and fail when
the two disagree, naming both numbers. The README's spelled form is read through a word parser, so a
row reworded around the number still counts. No other prose is matched.

Adding a project therefore means bumping both numbers, which is what the failure message says.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHOWCASE = ROOT / "SHOWCASE.md"
README = ROOT / "README.md"

# The page held 50+ rows when this test landed. A parser that silently matches nothing (a changed
# row layout, a reordered page) must not read as "0 == 0, all good".
MIN_ROWS = 50

ONES = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8,
    "nine": 9, "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
    "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18, "nineteen": 19,
}
TENS = {
    "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50, "sixty": 60, "seventy": 70,
    "eighty": 80, "ninety": 90,
}


def project_rows(lines):
    """[(line number, line)] for every project row: a "|" row whose first cell is a link."""
    found = []
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if re.match(r"^\|\s*\[", stripped):
            found.append((number, stripped))
    return found


def stated_number(lines):
    """The number in "N projects so far.", or None when the page does not carry the line."""
    for line in lines:
        match = re.match(r"^(\d+)\s+projects so far\.$", line.strip())
        if match:
            return int(match.group(1))
    return None


def spelled_number(word):
    """"seventy-five" -> 75, "seventy" -> 70, "twenty one" -> 21, "a hundred and four" -> 104,
    anything else -> None."""
    text = word.lower().replace("-", " ").strip()
    parts = [p for p in text.split() if p != "and"]
    # The page passed a hundred rows in Round W: "a hundred", "one hundred and four", "a hundred and twenty".
    if len(parts) >= 2 and parts[0] in ("a", "one") and parts[1] == "hundred":
        if len(parts) == 2:
            return 100
        rest = spelled_number(" ".join(parts[2:]))
        return None if rest is None else 100 + rest
    if not parts or len(parts) > 2:
        return None
    if len(parts) == 1:
        return TENS.get(parts[0]) or ONES.get(parts[0])
    if parts[0] in TENS and parts[1] in ONES and ONES[parts[1]] < 10:
        return TENS[parts[0]] + ONES[parts[1]]
    return None


def readme_spelled(lines):
    """(number, the words it was read from) for README's "<words> projects" phrase, or (None, None).

    The words directly before "projects" are tried two at a time and then one at a time ("twenty
    one" needs both, "seventy-five" is one), so the sentence around the number can be reworded.
    """
    for line in lines:
        for match in re.finditer(r"projects\b", line):
            words = re.findall(r"[A-Za-z-]+", line[: match.start()].rstrip())
            for take in (5, 4, 3, 2, 1):
                if len(words) >= take:
                    candidate = " ".join(words[-take:])
                    number = spelled_number(candidate)
                    if number is not None:
                        return number, candidate
    return None, None


def audit(showcase_lines):
    """(stated, counted, error) - error is None when the page and its rows agree."""
    counted = len(project_rows(showcase_lines))
    stated = stated_number(showcase_lines)
    if stated is None:
        return None, counted, "SHOWCASE.md carries no 'N projects so far.' line"
    if counted < MIN_ROWS:
        return stated, counted, (
            f"only {counted} project rows were read, fewer than the {MIN_ROWS} the page is known to "
            "hold - the row layout changed and this check is reading nothing"
        )
    if stated != counted:
        return stated, counted, (
            f"SHOWCASE.md says {stated} projects and its tables hold {counted} rows: bump the number "
            "(and README.md's spelled one) when you add a project"
        )
    return stated, counted, None


class ShowcaseProjectCount(unittest.TestCase):
    def setUp(self):
        self.showcase = SHOWCASE.read_text(encoding="utf-8").splitlines()
        self.readme = README.read_text(encoding="utf-8").splitlines()

    def test_the_page_states_the_number_of_rows_it_holds(self):
        stated, counted, error = audit(self.showcase)
        self.assertIsNone(error, error)
        self.assertEqual(stated, counted)

    def test_the_count_line_sits_in_the_intro_before_the_first_section(self):
        headings = [n for n, line in enumerate(self.showcase) if line.startswith("## ")]
        counted_at = [
            n for n, line in enumerate(self.showcase) if stated_number([line]) is not None
        ]
        self.assertEqual(len(counted_at), 1, "expected exactly one 'N projects so far.' line")
        self.assertLess(counted_at[0], headings[0], "the count line belongs above the first section")

    def test_readme_spells_the_same_number(self):
        number, word = readme_spelled(self.readme)
        self.assertIsNotNone(number, "README.md's 'Built something on top?' row lost its spelled count")
        counted = len(project_rows(self.showcase))
        self.assertEqual(
            number,
            counted,
            f"README.md says '{word} projects' and SHOWCASE.md holds {counted} rows",
        )


class TheCheckerItself(unittest.TestCase):
    """The repo-wide tests above are only worth their failures if these hold."""

    PAGE = [
        "71 projects so far.",
        "",
        "## Forks of the launcher",
        "",
        "| Project | What it is | Built on | By |",
        "|---|---|---|---|",
        "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
        "| [Two](https://example.com/two) | A fork | TCL G08 | [me](https://example.com) |",
    ]

    def test_a_short_count_is_reported_with_both_numbers(self):
        _, counted, error = audit(self.PAGE + ["| [Three](https://example.com/3) | x | y | z |"] * 48)
        self.assertEqual(counted, 50)
        self.assertIn("says 71", error)
        self.assertIn("hold 50", error)

    def test_an_agreeing_page_is_quiet(self):
        page = ["50 projects so far."] + self.PAGE[2:] + [
            f"| [P{i}](https://example.com/{i}) | x | y | z |" for i in range(48)
        ]
        stated, counted, error = audit(page)
        self.assertIsNone(error)
        self.assertEqual((stated, counted), (50, 50))

    def test_a_page_without_the_line_is_reported_rather_than_read_as_zero(self):
        _, _, error = audit(self.PAGE[2:])
        self.assertIn("no 'N projects so far.' line", error)

    def test_a_header_row_and_the_brand_index_are_not_project_rows(self):
        page = self.PAGE + ["", "- **TCL:** [One](https://example.com/one)"]
        self.assertEqual(len(project_rows(page)), 2)

    def test_a_number_in_prose_is_not_the_count_line(self):
        self.assertIsNone(stated_number(["About 71 projects so far."]))

    def test_spelled_numbers_are_read(self):
        self.assertEqual(spelled_number("seventy"), 70)
        self.assertEqual(spelled_number("seventy-five"), 75)
        self.assertEqual(spelled_number("Seventy Five"), 75)
        self.assertEqual(spelled_number("twenty one"), 21)
        self.assertIsNone(spelled_number("many"))
        self.assertIsNone(spelled_number("seventy five three"))

    def test_readme_words_are_read_and_an_unreadable_one_is_reported(self):
        self.assertEqual(readme_spelled(["where seventy-five projects already live"]), (75, "seventy-five"))
        self.assertEqual(readme_spelled(["where seventy projects already live"]), (70, "seventy"))
        self.assertEqual(readme_spelled(["where a hundred and four projects already live"]), (104, "a hundred and four"))
        self.assertEqual(readme_spelled(["where one hundred and twenty-one projects live"]), (121, "one hundred and twenty-one"))
        self.assertEqual(readme_spelled(["a row with no count at all"]), (None, None))


if __name__ == "__main__":
    unittest.main()

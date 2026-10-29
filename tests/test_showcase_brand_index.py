"""SHOWCASE.md's brand index has one line per brand, and the Bluetooth pill fork's row went in twice.

"Find a project by TV brand" is the index a reader uses to answer "which of these runs on my set", and
docs/workflows/showcase.md asks for a new row to be added under its TV brand. When the Bluetooth pill
fork landed (row 47, nikhiltalreja's tree) a second `- **Xiaomi:**` line was written below XGIMI
instead of the fork being added to the Xiaomi line already there, so the page carried Xiaomi twice:
the first line listed 14 projects without the fork, the second listed all 15. A reader who stopped at
the first Xiaomi line never saw the fork, and the page looked like it had two Xiaomi sections.

These tests read the index into brand -> links. A brand named twice fails, and the failure prints the
links only one of the two lines carries, so the fix is to fold them into one line. The brand labels
are not compared in any order: the list is grouped by hand and is not alphabetical today.

No prose is matched, so rewording a line or renaming a project stays free.

Run from the repository root: python3 -m unittest discover -s tests -v
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "SHOWCASE.md"
SECTION = "## Find a project by TV brand"
END = "## Add yours"

# `- **Xiaomi:** [One](url), [Two](url)`: the bullet docs/workflows/showcase.md asks a row to be added to.
ENTRY = re.compile(r"^- \*\*([^*]+?):\*\*(.*)$")
LINK = re.compile(r"\[([^\]]+)\]\((https?://[^)\s]+)\)")


def index(lines):
    """[(line number, brand, [(name, url)])] for every bullet in the brand index."""
    try:
        start = next(n for n, line in enumerate(lines) if line.startswith(SECTION))
    except StopIteration:
        return []
    found = []
    for number in range(start + 1, len(lines)):
        line = lines[number]
        if line.startswith(END):
            break
        match = ENTRY.match(line)
        if match:
            found.append((number + 1, match.group(1).strip(), LINK.findall(match.group(2))))
    return found


def duplicates(entries):
    """{brand: [entries]} for every brand that is named more than once."""
    seen = {}
    for entry in entries:
        seen.setdefault(entry[1], []).append(entry)
    return {brand: group for brand, group in seen.items() if len(group) > 1}


class BrandIndex(unittest.TestCase):
    def setUp(self):
        self.lines = PAGE.read_text(encoding="utf-8").splitlines()
        self.entries = index(self.lines)

    def test_the_index_is_found(self):
        # A parser that reads nothing would make the test below pass for any page.
        self.assertGreaterEqual(
            len(self.entries), 20, "the brand index should name the brands the page lists; the parser found fewer"
        )
        self.assertIn("Xiaomi", [brand for _, brand, _ in self.entries])

    def test_no_brand_is_named_twice(self):
        repeated = duplicates(self.entries)
        report = []
        for brand, group in repeated.items():
            first, second = group[0], group[-1]
            only_first = {url for _, url in first[2]} - {url for _, url in second[2]}
            only_second = {url for _, url in second[2]} - {url for _, url in first[2]}
            report.append(
                f"  {brand} is named on lines {', '.join(str(n) for n, _, _ in group)}: "
                f"only line {first[0]} carries {sorted(only_first) or 'nothing'}, "
                f"only line {second[0]} carries {sorted(only_second) or 'nothing'}"
            )
        self.assertEqual(
            [],
            list(repeated),
            "a brand is listed twice in 'Find a project by TV brand', so a project on one of the lines is\n"
            "hard to find: fold the links into the one line, under the brand. Add the new project to the\n"
            "line that is already there instead of writing a second one.\n" + "\n".join(report),
        )

    def test_every_entry_links_a_project(self):
        empty = [f"line {number}: {brand}" for number, brand, links in self.entries if not links]
        self.assertEqual([], empty, "these brand lines name no project: " + "; ".join(empty))


class TheReaderItself(unittest.TestCase):
    """The repo-wide test above is only worth its failures if the reader sees these shapes."""

    def test_a_brand_named_twice_is_reported(self):
        lines = [
            SECTION,
            "",
            "- **TCL:** [One](https://example.com/one)",
            "- **XGIMI:** [Two](https://example.com/two)",
            "- **TCL:** [One](https://example.com/one), [Three](https://example.com/three)",
            "",
            END,
        ]
        repeated = duplicates(index(lines))
        self.assertEqual(["TCL"], list(repeated))
        self.assertEqual([3, 5], [number for number, _, _ in repeated["TCL"]])

    def test_one_line_per_brand_is_quiet(self):
        lines = [SECTION, "- **TCL:** [One](https://example.com/one)", "- **Sony:** [Two](https://example.com/two)", END]
        self.assertEqual({}, duplicates(index(lines)))

    def test_a_comma_separated_line_is_read_into_every_link(self):
        lines = [SECTION, "- **TCL:** [One](https://example.com/1), [Two](https://example.com/2)", END]
        self.assertEqual(
            [("One", "https://example.com/1"), ("Two", "https://example.com/2")], index(lines)[0][2]
        )

    def test_a_brand_named_in_another_bullet_or_in_prose_is_not_an_entry(self):
        lines = [
            SECTION,
            "- [TCL](https://example.com/tcl) is mentioned in prose.",
            "- **TCL:** [One](https://example.com/one)",
            END,
        ]
        self.assertEqual(["TCL"], [brand for _, brand, _ in index(lines)])

    def test_the_section_stops_at_the_next_heading(self):
        lines = [SECTION, "- **TCL:** [One](https://example.com/one)", END, "- **TCL:** [Two](https://example.com/2)"]
        self.assertEqual(1, len(index(lines)))

    def test_a_page_without_the_section_reads_as_empty(self):
        self.assertEqual([], index(["# Made with cleanyourtv", "- **TCL:** [One](https://example.com/one)"]))


if __name__ == "__main__":
    unittest.main()

"""SHOWCASE.md is read as tables, and a row appended at the end of the page is not in one.

Two projects - johnny1K's weather card and Pagamase's CleanYourTV Panel - were added to the end of
SHOWCASE.md, after the brand index, instead of into the table each belongs to. GitHub renders a "|"
line outside a table as plain text, so both projects were unreadable as projects, neither was named
in "Find a project by TV brand", and "53 projects so far." was two short of the rows on the page.

These tests read the page the way GitHub does: a table starts at its header row, then the `|---`
dashes, and every following "|" line is a row until a line that is not one. A "|" line anywhere else
is stranded. The project sections are also checked for the four cells a row needs, since a row with
the wrong cell count renders broken too. No prose is matched, so rewording a row is free.
"""

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "SHOWCASE.md"


def cells(line):
    """The cells of a markdown table line, outer pipes stripped."""
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def is_dash_row(line):
    """True for the `|---|:--:|` row that turns the line above it into a table header."""
    if not line.startswith("|"):
        return False
    body = cells(line)
    return bool(body) and all(cell and set(cell) <= set("-: ") for cell in body)


def stray_rows(lines):
    """[(line number, line)] for every "|" line that is not inside a table."""
    inside = set()
    index = 0
    while index < len(lines) - 1:
        line = lines[index]
        if line.startswith("|") and not is_dash_row(line) and is_dash_row(lines[index + 1]):
            inside.add(index)
            inside.add(index + 1)
            index += 2
            while index < len(lines) and lines[index].startswith("|"):
                inside.add(index)
                index += 1
            continue
        index += 1
    return [
        (number + 1, line)
        for number, line in enumerate(lines)
        if line.startswith("|") and number not in inside
    ]


def tables(lines):
    """[(first row index, [row lines])] for every table on the page."""
    found = []
    index = 0
    while index < len(lines) - 1:
        line = lines[index]
        if line.startswith("|") and not is_dash_row(line) and is_dash_row(lines[index + 1]):
            rows = []
            index += 2
            while index < len(lines) and lines[index].startswith("|"):
                rows.append(lines[index])
                index += 1
            found.append((index, rows))
            continue
        index += 1
    return found


def heading_above(lines, stop):
    """The last `## ` heading before `stop`, or '' when the table sits above every heading."""
    for number in range(stop, -1, -1):
        if lines[number].startswith("## "):
            return lines[number][3:].strip()
    return ""


class ShowcaseTables(unittest.TestCase):
    def setUp(self):
        self.lines = PAGE.read_text(encoding="utf-8").splitlines()

    def test_no_row_is_stranded_outside_a_table(self):
        stranded = stray_rows(self.lines)
        self.assertEqual(
            stranded,
            [],
            "these lines start with '|' but are not in a table, so GitHub renders them as text:\n"
            + "\n".join(f"  line {number}: {line[:90]}" for number, line in stranded),
        )

    def test_every_table_has_rows_under_a_heading(self):
        found = tables(self.lines)
        self.assertGreaterEqual(
            len(found), 5, "the page should carry the project tables; the parser found fewer"
        )
        for _, rows in found:
            self.assertTrue(rows, "a table with a header and no rows")
        for end, _ in found:
            self.assertTrue(
                heading_above(self.lines, end),
                f"the table ending on line {end} is not under any '## ' heading",
            )

    def test_every_row_has_the_four_cells_the_header_names(self):
        for _, rows in tables(self.lines):
            for row in rows:
                self.assertEqual(
                    len(cells(row)), 4, f"expected 4 cells in: {row[:90]}"
                )

    def test_every_row_names_a_project_a_tv_and_an_author(self):
        for _, rows in tables(self.lines):
            for row in rows:
                project, what, built_on, by = cells(row)
                self.assertTrue(project.startswith("["), f"no project link in: {row[:90]}")
                self.assertTrue(what, f"an empty 'what it is' cell in: {row[:90]}")
                self.assertTrue(built_on, f"no TV or platform named in: {row[:90]}")
                self.assertIn("/", by, f"no author link in: {row[:90]}")


class TheCheckerItself(unittest.TestCase):
    """The repo-wide test above is only worth its failures if these hold."""

    def test_a_row_appended_after_the_brand_list_is_reported(self):
        lines = [
            "## Forks of the launcher",
            "",
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
            "",
            "- **TCL:** [One](https://example.com/one)",
            "| [Two](https://example.com/two) | A fork | TCL G08 | [me](https://example.com) |",
        ]
        self.assertEqual([line for _, line in stray_rows(lines)], [lines[7]])

    def test_a_row_inside_a_table_is_not_reported(self):
        lines = [
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
            "",
        ]
        self.assertEqual(stray_rows(lines), [])

    def test_a_header_without_its_dash_row_is_not_a_table(self):
        lines = [
            "| Project | What it is | Built on | By |",
            "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
        ]
        self.assertEqual(len(stray_rows(lines)), 2)
        self.assertEqual(tables(lines), [])

    def test_a_table_stops_at_the_first_line_that_is_not_a_row(self):
        lines = [
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
            "",
            "| [Loose](https://example.com/loose) | Not a row | TCL G08 | [me](https://example.com) |",
        ]
        self.assertEqual(len(tables(lines)[0][1]), 1)
        self.assertEqual([line for _, line in stray_rows(lines)], [lines[4]])

    def test_a_page_with_no_tables_reports_no_table_and_no_rows(self):
        lines = ["# Made with cleanyourtv", "", "Just prose, no pipes."]
        self.assertEqual(tables(lines), [])
        self.assertEqual(stray_rows(lines), [])

    def test_a_narrow_table_is_still_read_as_a_table(self):
        lines = [
            "| Project | What it is |",
            "|---|---|",
            "| [One](https://example.com/one) | A fork |",
        ]
        self.assertEqual(len(tables(lines)), 1)
        self.assertEqual(len(tables(lines)[0][1]), 1)


if __name__ == "__main__":
    unittest.main()

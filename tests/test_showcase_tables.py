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


def author_problems(lines):
    """[(row, why)] for every table row that does not read as a project, a description, a TV and an author.

    A row of the wrong width is named here with its count rather than unpacked: a row missing its
    author cell, or one whose description holds a `|` that ends the cell, is a mistake a contributor
    makes by hand, and `ValueError: not enough values to unpack` points at this file instead of at the
    row. The count is judged on its own by test_every_row_has_the_four_cells_the_header_names, so the
    two say the same thing twice on purpose - neither case should go quiet when the other is not run.
    """
    problems = []
    for _, rows in tables(lines):
        for row in rows:
            cells_of = cells(row)
            if len(cells_of) != 4:
                problems.append((row, f"{len(cells_of)} cells, not the four the header names"))
                continue
            project, what, built_on, by = cells_of
            if not project.startswith("["):
                problems.append((row, "no project link"))
            elif not what:
                problems.append((row, "an empty 'what it is' cell"))
            elif not built_on:
                problems.append((row, "no TV or platform named"))
            elif "/" not in by:
                problems.append((row, "no author link"))
    return problems


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
        problems = [f"{why}: {row[:90]}" for row, why in author_problems(self.lines)]
        self.assertEqual(
            problems, [], "a row has to read as a project, a description, a TV and an author"
        )


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

    def test_a_row_with_three_cells_is_named_with_its_count(self):
        """The row a contributor forgets the author cell in; unpacking it raised ValueError instead."""
        lines = [
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 |",
        ]
        problems = author_problems(lines)
        self.assertEqual([row for row, _ in problems], [lines[2]])
        self.assertIn("3 cells", problems[0][1])

    def test_a_row_with_a_pipe_in_a_cell_is_named_with_its_count(self):
        """A `|` typed inside a description ends the cell, the mistake the markdown sweep was written for."""
        lines = [
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 | music | [me](https://example.com) |",
        ]
        problems = author_problems(lines)
        self.assertEqual([row for row, _ in problems], [lines[2]])
        self.assertIn("5 cells", problems[0][1])

    def test_a_row_with_four_cells_but_no_author_is_named(self):
        lines = [
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 | Julien |",
        ]
        problems = author_problems(lines)
        self.assertEqual([row for row, _ in problems], [lines[2]])
        self.assertIn("author", problems[0][1])

    def test_a_page_of_good_rows_has_no_problems(self):
        lines = [
            "## Forks of the launcher",
            "",
            "| Project | What it is | Built on | By |",
            "|---|---|---|---|",
            "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
        ]
        self.assertEqual([], author_problems(lines))


if __name__ == "__main__":
    unittest.main()

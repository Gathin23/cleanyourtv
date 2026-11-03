"""A markdown table's rows have the cells its header declares.

A `|` in a cell's text ends that cell, whatever surrounds it: GitHub splits the row before it reads
any inline markup, so backticks do not protect it and only `\\|` does. The Insignia note's "Settings on
Fire OS 7" table shipped `a signature|privileged permission` inside its second cell, which gave that
one row three cells under a two-cell header: the page renders with an extra empty column down the
table, and the sentence breaks after "signature", with `privileged permission); android.settings.
DISPLAY_SETTINGS ...` pushed into that phantom column. Nothing failed - tests/test_markdown_links.py
reads what a page links to, not the shape of its tables - so the first reader to notice was the one on
the page.

What is read: every `.md` page, the rows of every table whose rows start with a pipe (all 498 tables
in this tree do), and the number of unescaped pipes in each row against its header's. `\\|` is the
escape GFM gives for a pipe meant as text, so it is not counted. The separator line and the fenced
code blocks are skipped: a table drawn inside a fence is an example, not a table.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import unittest
from pathlib import Path

# The page walk is the markdown sweep's, used here rather than copied: a folder that stops being
# documentation stops being read by both at once.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_markdown_links import markdown_files

ROOT = Path(__file__).resolve().parents[1]

# The characters a table's second line may hold: pipes, colons for the alignment and the dashes.
SEPARATOR = set("|:- ")


def pipes(line):
    """How many pipes in LINE are cell separators, i.e. are not escaped with a backslash."""
    count = 0
    i = 0
    while i < len(line):
        if line[i] == "\\":
            i += 2           # the escaped character, pipe or not, is text
            continue
        if line[i] == "|":
            count += 1
        i += 1
    return count


def lines_outside_fences(text):
    """(line number, line) for TEXT, with fenced code blocks left out and their numbers kept."""
    in_fence = False
    for number, line in enumerate(text.splitlines(), 1):
        stripped = line.strip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
            continue
        if not in_fence:
            yield number, line


def cells(line):
    """How many cells LINE has: its unescaped pipes, plus one between them and minus the outer two."""
    stripped = line.strip()
    if stripped.startswith("|"):
        stripped = stripped[1:]
    if stripped.endswith("|") and not stripped.endswith("\\|"):
        stripped = stripped[:-1]
    return pipes(stripped) + 1


def is_separator(line):
    """Whether LINE is a table's `|---|---|` line."""
    stripped = line.strip()
    return bool(stripped) and set(stripped) <= SEPARATOR and "-" in stripped and "|" in stripped


def tables(text):
    """(header line number, header cells, [(line number, cells)]) for each table in TEXT."""
    lines = list(lines_outside_fences(text))
    i = 0
    while i < len(lines) - 1:
        number, line = lines[i]
        if line.strip().startswith("|") and is_separator(lines[i + 1][1]):
            header = cells(line)
            rows = []
            j = i + 2
            while j < len(lines) and lines[j][1].strip().startswith("|"):
                rows.append((lines[j][0], cells(lines[j][1])))
                j += 1
            yield number, header, rows
            i = j
        else:
            i += 1


def rows_that_break_their_header(text):
    """(line, its cells, the header's line, the header's cells) for each row of the wrong width."""
    broken = []
    for header_line, header, rows in tables(text):
        for number, count in rows:
            if count != header:
                broken.append((number, count, header_line, header))
    return broken


class ShippedPages(unittest.TestCase):
    def test_every_table_row_has_the_cells_its_header_declares(self):
        broken = []
        for page in markdown_files(ROOT):
            for number, count, header_line, header in rows_that_break_their_header(
                    page.read_text(errors="replace")):
                broken.append(f"{page.relative_to(ROOT)}:{number} has {count} cells, "
                              f"its header at line {header_line} has {header}")
        self.assertEqual([], broken, "a pipe in a cell's text ends the cell:\n" + "\n".join(broken))

    def test_the_reader_read_the_shipped_tree(self):
        """A walk that stopped finding tables would pass the test above without reading anything."""
        found = rows = 0
        for page in markdown_files(ROOT):
            for _, _, table_rows in tables(page.read_text(errors="replace")):
                found += 1
                rows += len(table_rows)
        self.assertGreaterEqual(found, 400, f"only {found} tables found")
        self.assertGreaterEqual(rows, 3000, f"only {rows} table rows found")


class Rows(unittest.TestCase):
    """The reader, over tables written here rather than over the tree."""

    def test_a_pipe_in_a_cell_is_named(self):
        page = ("| Opens | Does not open |\n"
                "|---|---|\n"
                "| a | b (a signature|privileged permission); c |\n")
        self.assertEqual([(3, 3, 1, 2)], rows_that_break_their_header(page))

    def test_an_escaped_pipe_is_text(self):
        page = ("| Opens | Does not open |\n"
                "|---|---|\n"
                "| a | b (a signature\\|privileged permission); c |\n")
        self.assertEqual([], rows_that_break_their_header(page))

    def test_an_escaped_backslash_does_not_escape_the_pipe(self):
        # `\\` is one backslash of text, so the pipe after it still ends the cell.
        page = ("| a | b |\n"
                "|---|---|\n"
                "| one\\\\ | two | three |\n")
        self.assertEqual([(3, 3, 1, 2)], rows_that_break_their_header(page))

    def test_a_fenced_table_is_an_example(self):
        page = ("```markdown\n"
                "| a | b |\n"
                "|---|---|\n"
                "| one | two | three |\n"
                "```\n")
        self.assertEqual([], rows_that_break_their_header(page))

    def test_a_table_indented_under_a_list_item_is_read(self):
        page = ("1. Steps:\n"
                "   | Key | What |\n"
                "   |---|---|\n"
                "   | `a` | b | c |\n")
        self.assertEqual([(4, 3, 2, 2)], rows_that_break_their_header(page))

    def test_a_row_without_outer_pipes_still_matches(self):
        page = ("Key | What\n"
                "---|---\n"
                "`a` | b\n")
        self.assertEqual([], rows_that_break_their_header(page))

    def test_a_line_of_dashes_without_a_header_is_not_a_table(self):
        page = "---|---\n"
        self.assertEqual([], rows_that_break_their_header(page))

    def test_a_header_alone_is_a_table(self):
        page = ("| a | b |\n"
                "|---|---|\n")
        self.assertEqual([], rows_that_break_their_header(page))

    def test_a_row_with_two_extra_cells_is_named_once(self):
        page = ("| a | b |\n"
                "|---|---|\n"
                "| one | two | three | four |\n")
        self.assertEqual([(3, 4, 1, 2)], rows_that_break_their_header(page))


if __name__ == "__main__":
    unittest.main()

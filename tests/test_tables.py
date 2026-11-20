"""Unit tests for tools/tables.py: the canonical Markdown-table reader.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import tables  # noqa: E402


class TableRowsTest(unittest.TestCase):
    def test_first_table_by_header(self):
        """Only the first table with the named header is returned."""
        text = """| A | B |\n|---|---|\n| one | two |\n\nSome prose\n\n| C | D |\n|---|---|---|\n| three | four |\n"""
        self.assertEqual([["one", "two"]], tables.table_rows(text, "| A |"))

    def test_multiple_tables_skips_to_first_match(self):
        text = """| X | Y |\n|---|---|\n| skip | this |\n\n| Target | Data |\n|---|---|---|\n| match | here |\n"""
        self.assertEqual([["match", "here"]], tables.table_rows(text, "| Target |"))

    def test_separator_skipped(self):
        self.assertEqual([["a", "b"]], tables.table_rows("| H |\n|---|---|\n| a | b |\n", "| H |"))

    def test_separator_with_alignment_colons_skipped(self):
        """GFM alignment marks (:---, ---:) in the separator line do not become a row."""
        text = "| Key | Value |\n|:----|------:|\n| a | b |\n"
        self.assertEqual([["a", "b"]], tables.table_rows(text, "| Key |"))

    def test_trailing_prose_stops_table(self):
        text = "| A |\n|---|\n| one |\n\nProse starts here\n| more |\n"
        self.assertEqual([["one"]], tables.table_rows(text, "| A |"))

    def test_blank_line_before_first_row_keeps_table_open(self):
        """A non-| line before any rows is skipped, not the table's end."""
        text = "| A | B |\n|---|---|\n\n| one | two |\n"
        self.assertEqual([["one", "two"]], tables.table_rows(text, "| A |"))

    def test_multiple_rows_in_order(self):
        text = "| A | B |\n|---|---|\n| one | two |\n| three | four |\n| five | six |\n"
        self.assertEqual(
            [["one", "two"], ["three", "four"], ["five", "six"]],
            tables.table_rows(text, "| A |"),
        )

    def test_cells_are_stripped(self):
        """Padding spaces around a cell do not reach the caller."""
        text = "| A | B |\n|---|---|\n| one |  two  |\n"
        self.assertEqual([["one", "two"]], tables.table_rows(text, "| A |"))

    def test_header_start_matches_longer_header_line(self):
        """Real callers name a table by the start of its header (| Add-on | ...)."""
        text = "| Add-on | What it does | Screenshot |\n|---|---|---|\n| x | y | z |\n"
        self.assertEqual([["x", "y", "z"]], tables.table_rows(text, "| Add-on |"))

    def test_row_without_space_separators_is_one_cell(self):
        """Cells split on " | " only: `|a|b|` is one cell, which is why manage.py
        indexes later columns behind len() guards."""
        text = "| A | B |\n|---|---|\n|a|b|\n"
        self.assertEqual([["a|b"]], tables.table_rows(text, "| A |"))

    def test_row_shorter_than_header_is_read_as_is(self):
        """A row with fewer cells than the header is returned as-is; callers guard
        with len() (manage.py reads the screenshot only when len(c) > 2)."""
        text = "| A | B | C |\n|---|---|---|\n| only-one |\n"
        self.assertEqual([["only-one"]], tables.table_rows(text, "| A |"))

    def test_table_without_separator_line_still_reads_rows(self):
        text = "| H |\n| a |\n"
        self.assertEqual([["a"]], tables.table_rows(text, "| H |"))

    def test_crlf_line_endings(self):
        """A page saved with Windows line endings parses the same."""
        text = "| A | B |\r\n|---|---|\r\n| one | two |\r\n"
        self.assertEqual([["one", "two"]], tables.table_rows(text, "| A |"))

    def test_last_line_without_trailing_newline(self):
        self.assertEqual([["one"]], tables.table_rows("| A |\n|---|\n| one |", "| A |"))

    def test_no_matching_header_returns_empty(self):
        text = "| A |\n|---|\n| one |\n"
        self.assertEqual([], tables.table_rows(text, "| Z |"))

    def test_multiline_cell_stays_intact(self):
        """Cell content with pipes is not split further."""
        text = "| A |\n|---|\n| `key_value` |\n"
        rows = tables.table_rows(text, "| A |")
        # split on " | " gives single cell
        self.assertEqual([["`key_value`"]], rows)


class PlainTest(unittest.TestCase):
    def test_links_extract_text(self):
        """plain() extracts the link text, not the URL."""
        self.assertEqual("go", tables.plain("[go](https://cleanyour.tv)"))

    def test_code_mark_stripped(self):
        self.assertEqual("key", tables.plain("`key`"))

    def test_combined(self):
        self.assertEqual("link code rest", tables.plain("[link](http://x) `code` rest"))

    def test_real_tools_table_cell(self):
        """The README Tools table: a code link, prose, and a second link with a
        trailing period - the form `set` and `check` compare config keys against."""
        cell = (
            "[`tools/tv.py`](tools/tv.py) | Pure-Python ADB client: shell, push, "
            "screenshots. [Guide](tools/tv.md)."
        )
        self.assertEqual(
            "tools/tv.py | Pure-Python ADB client: shell, push, screenshots. Guide.",
            tables.plain(cell),
        )

    def test_multiple_links(self):
        self.assertEqual("a and b", tables.plain("[a](x) and [b](y)"))

    def test_empty_cell(self):
        self.assertEqual("", tables.plain(""))

    def test_none_cell(self):
        self.assertEqual("", tables.plain(None))

    def test_strips_surrounding_whitespace(self):
        self.assertEqual("text", tables.plain("  text  "))


class ImgSrcTest(unittest.TestCase):
    def test_single_img_src(self):
        cell = '<img src="switches/vpn_pill.jpg">'
        self.assertEqual("switches/vpn_pill.jpg", tables.img_src(cell))

    def test_real_addon_screenshot_cell(self):
        """addons/README.md writes src first, then width and alt."""
        cell = '<img src="tatami/screenshot.jpg" width="240" alt="Tatami on a TV">'
        self.assertEqual("tatami/screenshot.jpg", tables.img_src(cell))

    def test_empty_src_is_skipped_for_a_later_one(self):
        """img_src wants a path: an empty src does not stop the search (img_srcs
        keeps the empty string in its place instead)."""
        cell = '<img src=""> <img src="b.jpg">'
        self.assertEqual("b.jpg", tables.img_src(cell))

    def test_src_not_first_attribute(self):
        """The first-screenshot reader matches src right after <img, as every page
        in this repo writes it; img_srcs is the order-independent one."""
        cell = '<img alt="x" src="y.jpg">'
        self.assertIsNone(tables.img_src(cell))

    def test_no_img_returns_none(self):
        self.assertIsNone(tables.img_src("no image here"))

    def test_empty_string_cell(self):
        self.assertIsNone(tables.img_src(""))

    def test_none_cell(self):
        self.assertIsNone(tables.img_src(None))


class ImgSrcsTest(unittest.TestCase):
    def test_multiple_imgs_in_order(self):
        cell = '<img src="a.jpg"> <img src="b.jpg">'
        self.assertEqual(["a.jpg", "b.jpg"], tables.img_srcs(cell))

    def test_real_switches_row(self):
        """docs/switches.md's vpn_pill row draws two pictures; src is first."""
        cell = (
            '<img src="switches/vpn_pill.jpg" width="320" alt="vpn_pill on a TV"> '
            '<img src="switches/vpn_pill_rest.jpg" width="320" alt="vpn_pill at rest">'
        )
        self.assertEqual(
            ["switches/vpn_pill.jpg", "switches/vpn_pill_rest.jpg"],
            tables.img_srcs(cell),
        )

    def test_img_with_other_attrs(self):
        cell = '<img src="shot.jpg" width="320" height="180">'
        self.assertEqual(["shot.jpg"], tables.img_srcs(cell))

    def test_src_not_first_attribute(self):
        """img_srcs finds the src wherever it sits in the tag."""
        self.assertEqual(["y.jpg"], tables.img_srcs('<img alt="x" src="y.jpg">'))

    def test_missing_src_keeps_empty(self):
        """Page draws something with no src for styling."""
        cell = '<img src="real.jpg"> <img alt="no source">'
        self.assertEqual(["real.jpg", ""], tables.img_srcs(cell))

    def test_no_images(self):
        self.assertEqual([], tables.img_srcs("none here"))

    def test_empty_string(self):
        self.assertEqual([], tables.img_srcs(""))

    def test_none_cell(self):
        self.assertEqual([], tables.img_srcs(None))


class SchemaTest(unittest.TestCase):
    """The reader chain callers use: table_rows for the page, then the cell
    readers per column (manage.py reads every table this way)."""

    def test_switch_row_through_the_full_chain(self):
        text = (
            "| Key | Screenshot |\n"
            "|:----|------------|\n"
            "| `vpn_pill` | <img src=\"switches/vpn_pill.jpg\" width=\"320\"> "
            "<img src=\"switches/vpn_pill_rest.jpg\" width=\"320\"> |\n"
        )
        rows = tables.table_rows(text, "| Key |")
        self.assertEqual(["vpn_pill"], [tables.plain(c[0]) for c in rows])
        self.assertEqual(
            ["switches/vpn_pill.jpg", "switches/vpn_pill_rest.jpg"],
            tables.img_srcs(rows[0][1]),
        )

    def test_theme_row_with_missing_screenshot_column(self):
        """A theme row missing its screenshot column yields one cell for the
        folder, and img_src on a missing column stays None (len guard)."""
        text = (
            "| Theme | What it does | Screenshot |\n"
            "|---|---|---|\n"
            "| [zen/](themes/zen/) | Calm colours |\n"
        )
        rows = tables.table_rows(text, "| Theme |")
        self.assertEqual(["zen/"], [tables.plain(c[0]) for c in rows])
        shot = tables.img_src(rows[0][2]) if len(rows[0]) > 2 else None
        self.assertIsNone(shot)


if __name__ == "__main__":
    unittest.main()
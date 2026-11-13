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

    def test_trailing_prose_stops_table(self):
        text = "| A |\n|---|\n| one |\n\nProse starts here\n| more |\n"
        self.assertEqual([["one"]], tables.table_rows(text, "| A |"))

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

    def test_no_img_returns_none(self):
        self.assertIsNone(tables.img_src("no image here"))

    def test_none_cell(self):
        self.assertIsNone(tables.img_src(None))


class ImgSrcsTest(unittest.TestCase):
    def test_multiple_imgs_in_order(self):
        cell = '<img src="a.jpg"> <img src="b.jpg">'
        self.assertEqual(["a.jpg", "b.jpg"], tables.img_srcs(cell))

    def test_img_with_other_attrs(self):
        cell = '<img src="shot.jpg" width="320" height="180">'
        self.assertEqual(["shot.jpg"], tables.img_srcs(cell))

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


if __name__ == "__main__":
    unittest.main()
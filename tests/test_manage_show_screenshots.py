"""`manage.py show` names every picture a switch's row carries, not only the first.

docs/switches.md lets one row show more than one picture - "A row may show more than one picture - `vpn_pill`
carries the pill up and the pill at rest - and every picture in a row is held to those rules" - and
tests/test_switches.py reads them all, which is why the page can make that promise. tools/manage.py's
switches() kept one path per key (`re.search` stops at the first `<img src>`), so `show vpn_pill` printed
`docs/switches/vpn_pill.jpg` and never `vpn_pill_rest.jpg`, the picture the page shows beside it: a reader
who opened the one path the tool gave them saw half of what the page documents for the key they were
looking up. Nothing read what show printed, so the suite stayed green.

The reader here is the tool's own `img_srcs()`, held to the page's rule in both directions: an empty src
keeps its place as an empty string (a picture the page draws but names nothing for), and a picture whose src
is there is never dropped. The tree test then holds every picture the shipped page gives a key to the item
`show` prints, so a key that gains a second picture - or a reader that stops reading one - fails here.

No TV: this is the offline half of tools/manage.py, over the checkout's own docs/switches.md.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import io
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


def pictures(item):
    """The files `show` prints for one switch, in the order it prints them."""
    return [Path(p).name for p in item.get("screenshots") or []]


class ImgSrcsTest(unittest.TestCase):
    def test_it_reads_every_picture_of_a_row_in_the_order_the_page_draws_them(self):
        cell = ('<img src="switches/vpn_pill.jpg" width="320"> '
                '<img src="switches/vpn_pill_rest.jpg" width="320">')
        self.assertEqual(["switches/vpn_pill.jpg", "switches/vpn_pill_rest.jpg"], manage.img_srcs(cell))

    def test_one_picture_reads_as_one(self):
        self.assertEqual(["switches/dock_band.jpg"], manage.img_srcs('<img src="switches/dock_band.jpg">'))

    def test_an_img_without_a_src_keeps_its_place(self):
        cell = '<img src="switches/dock_band.jpg"> <img alt="no source">'
        self.assertEqual(["switches/dock_band.jpg", ""], manage.img_srcs(cell))

    def test_a_cell_with_no_picture_reads_as_none(self):
        self.assertEqual([], manage.img_srcs("no picture here"))
        self.assertEqual([], manage.img_srcs(""))


class StandInTreeTest(unittest.TestCase):
    """A tree of its own, so a page rewritten in another shape cannot pass by reading nothing."""

    KEYS = (
        "# Themes\n\n## Keys\n\n| Key | Type | Meaning |\n|---|---|---|\n"
        "| `two_pictures` | bool | A switch the page shows on both sides (false by default) |\n"
        "| `one_picture` | bool | A switch with a single picture (false by default) |\n"
        "| `no_picture` | bool | A key from before the screenshot rule (false by default) |\n"
    )
    PAGE = (
        "# Switches on a TV\n\n## Screenshots\n\n| Key | Screenshot |\n|---|---|\n"
        '| `two_pictures` | <img src="switches/two.jpg" width="320"> <img src="switches/two_rest.jpg" width="320"> |\n'
        '| `one_picture` | <img src="switches/one.jpg" width="320"> |\n'
    )

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "themes").mkdir()
        (self.root / "themes" / "README.md").write_text(self.KEYS, encoding="utf-8")
        (self.root / "docs").mkdir()
        (self.root / "docs" / "switches.md").write_text(self.PAGE, encoding="utf-8")
        self.items = {s["id"]: s for s in manage.switches(self.root)}

    def tearDown(self):
        self.tmp.cleanup()

    def test_the_reader_finds_the_tree(self):
        self.assertEqual({"two_pictures", "one_picture", "no_picture"}, set(self.items),
                         "the stand-in tree no longer reads as three switches")

    def test_a_row_with_two_pictures_keeps_both(self):
        self.assertEqual(["two.jpg", "two_rest.jpg"], pictures(self.items["two_pictures"]))

    def test_the_first_picture_is_still_the_single_screenshot(self):
        # Everything else that reads an item still expects one path there.
        self.assertEqual("two.jpg", Path(self.items["two_pictures"]["screenshot"]).name)
        self.assertEqual(["one.jpg"], pictures(self.items["one_picture"]))

    def test_a_key_with_no_row_carries_no_picture(self):
        self.assertEqual([], pictures(self.items["no_picture"]))
        self.assertIsNone(self.items["no_picture"]["screenshot"])

    def test_show_prints_both_pictures_of_a_switch(self):
        from unittest import mock
        out = io.StringIO()
        with mock.patch.object(manage, "find", return_value=self.items["two_pictures"]), \
                contextlib.redirect_stdout(out):
            self.assertEqual(0, manage.cmd_show("two_pictures"))
        printed = [line.split(":", 1)[1].strip() for line in out.getvalue().splitlines()
                   if line.strip().startswith("screenshot")]
        self.assertEqual(["two.jpg", "two_rest.jpg"], [Path(p).name for p in printed],
                         "show printed one picture of a row that carries two")


class ShippedPageTest(unittest.TestCase):
    """The page the checkout ships: every picture it gives a key reaches that key's item, and show prints it."""

    def setUp(self):
        self.items = {s["id"]: s for s in manage.switches()}
        self.page = (ROOT / "docs" / "switches.md").read_text(encoding="utf-8")

    def test_every_picture_in_the_page_reaches_a_key_and_is_a_file(self):
        pairs = []
        for line in self.page.splitlines():
            if not line.startswith("|"):
                continue
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cells) < 2:
                continue
            keys = re.findall(r"`([a-z_]+)`", cells[0])
            srcs = [s for s in manage.img_srcs(cells[1]) if s]
            for key in keys:
                for src in srcs:
                    pairs.append((key, src))
        self.assertGreaterEqual(len(pairs), 45, "the page's screenshot table moved: is the reader still right?")
        for key, src in pairs:
            with self.subTest(key=key, picture=src):
                self.assertIn(key, self.items, f"docs/switches.md shows a picture for `{key}`, which the Keys table does not name")
                self.assertIn((ROOT / "docs" / src).resolve(), [Path(p) for p in self.items[key]["screenshots"]],
                              f"`{key}` carries a picture in docs/switches.md that show does not report")

    def test_the_keys_with_two_pictures_are_read_with_both(self):
        for key in ("vpn_pill", "icon_only_vpn"):
            with self.subTest(key=key):
                self.assertEqual(2, len(self.items[key]["screenshots"]),
                                 f"`{key}` has two pictures in docs/switches.md; the reader lost one")

    def test_show_prints_the_picture_the_page_shows_at_rest(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, manage.cmd_show("vpn_pill"))
        self.assertIn("vpn_pill_rest.jpg", out.getvalue(),
                      "show printed one of the two pictures docs/switches.md gives vpn_pill")

    def test_a_one_picture_key_prints_one_line(self):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            self.assertEqual(0, manage.cmd_show("columns"))
        lines = [line for line in out.getvalue().splitlines() if line.strip().startswith("screenshot")]
        self.assertEqual(1, len(lines), "a key with one picture printed more than one screenshot line")


if __name__ == "__main__":
    unittest.main()

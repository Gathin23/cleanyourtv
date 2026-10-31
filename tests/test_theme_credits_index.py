"""A theme's CREDITS.md is named by the row that lists the theme.

docs/DOS-AND-DONTS.md asks a theme author for pictures "with a stated free licence (CC0, CC BY),
credited in `CREDITS.md`", and themes/CREATE_A_THEME.md step 2 sends the next author to
`themes/sweden/CREDITS.md` for the format. themes/README.md is the page that lists the themes, and
the file that answers "may I reuse this picture, and who made it?" was named by five of its six rows:
`cinematic/` shipped a CC0 wallpaper whose credit nothing outside the folder mentioned, so a reader
choosing a theme from the index could not tell its picture is not MIT, and an author copying the shape
of a row never learned the file exists.

The rule this holds is the one themes/README.md states below its table: a theme that did not draw its
own pictures says where they came from in a `CREDITS.md`, and its row names that file. The check is
per row, not per page: a mention in another theme's row, or in the prose around the table, is not the
row for this theme, and a row that says `CREDITS` without the file being there is reported on the
other side - tests/test_theme_picture_credits.py reads what the file itself says.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
INDEX = "themes/README.md"

# A row of the theme table: the first cell is the folder, in backticks and with a trailing slash.
ROW = re.compile(r"^\|\s*`([^`/]+)/`\s*\|(.*)$")


def rows(text):
    """{theme name: its row of the table}, in page order."""
    found = {}
    for line in text.split("\n"):
        m = ROW.match(line)
        if m:
            found[m.group(1)] = line
    return found


def credits_files(root):
    """The theme folders that ship a CREDITS.md, by name, in a stable order."""
    root = Path(root) / "themes"
    if not root.is_dir():
        return []
    return sorted(p.parent.name for p in root.glob("*/CREDITS.md") if p.is_file())


def unnamed_credits(root):
    """[(theme, problem)] for every CREDITS.md its row does not name."""
    root = Path(root)
    index = root / INDEX
    text = index.read_text(encoding="utf-8") if index.is_file() else ""
    listed = rows(text)
    out = []
    for theme in credits_files(root):
        row = listed.get(theme)
        if row is None:
            out.append((theme, f"themes/{theme}/ ships a CREDITS.md and {INDEX} has no row for it"))
        elif "CREDITS.md" not in row:
            out.append((theme, f"themes/{theme}/CREDITS.md is not named in its row of {INDEX}"))
    return out


def unlisted_themes(root):
    """The theme folders with a config.json that no row of the table lists."""
    root = Path(root)
    index = root / INDEX
    listed = rows(index.read_text(encoding="utf-8")) if index.is_file() else {}
    return sorted(p.parent.name for p in (root / "themes").glob("*/config.json")
                  if p.parent.name not in listed)


class CreditsIndexTest(unittest.TestCase):
    def test_every_credits_file_is_named_in_its_row(self):
        unnamed = unnamed_credits(ROOT)
        self.assertEqual([], [(theme, why) for theme, why in unnamed],
                         "\n".join(why for _, why in unnamed) +
                         "\nA theme's credit file is where a reader finds out a picture is not MIT, so the"
                         "\nrow that lists the theme names it (the sentence below the table says so).")

    def test_the_tree_is_read(self):
        # A walk that read nothing passes the test above: six themes ship a CREDITS.md today.
        themes = credits_files(ROOT)
        self.assertGreaterEqual(len(themes), 6, f"expected six theme credit files, found {themes}")
        for theme in ("cinematic", "halloween", "nature-calm", "retro", "storm", "sweden"):
            self.assertIn(theme, themes)
        self.assertGreaterEqual(len(rows((ROOT / INDEX).read_text(encoding="utf-8"))), 14,
                               f"{INDEX} states the themes in a table of rows")

    def test_every_theme_folder_has_a_row(self):
        self.assertEqual([], unlisted_themes(ROOT),
                         "theme folders with a config.json and no row in " + INDEX)

    def test_the_page_states_the_rule(self):
        # The check is the page's own sentence rather than a rule kept only here: the paragraph below
        # the table says a theme that did not draw its own pictures names the file, and that the row
        # names it.
        text = (ROOT / INDEX).read_text(encoding="utf-8")
        above_keys = text.partition("\n## Keys")[0]
        self.assertIn("| <img src=", above_keys, f"{INDEX} lost its table of themes")
        below_table = above_keys.split("| <img src=")[-1]
        self.assertTrue("CREDITS.md" in below_table,
                        f"the paragraph below {INDEX}'s table no longer says a theme's pictures are "
                        "credited in its own CREDITS.md, which is the rule this check holds")


class CheckerTest(unittest.TestCase):
    """The checker has to be able to fail, and to read only the row for that theme."""

    def tree(self, files):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        for name, text in files.items():
            (root / name).parent.mkdir(parents=True, exist_ok=True)
            (root / name).write_text(text, encoding="utf-8")
        return root

    def page(self, *rows_):
        return ("# Themes\n\n| Theme | What it does | Screenshot |\n|---|---|---|\n" +
                "\n".join(rows_) + "\n")

    def test_a_credits_file_its_row_names_is_quiet(self):
        root = self.tree({
            "themes/README.md": self.page(
                "| `storm/` | Lightning, CC BY: see `storm/CREDITS.md` | <img src=\"storm/screenshot.jpg\"> |"),
            "themes/storm/config.json": "{}",
            "themes/storm/CREDITS.md": "# Wallpaper Credits\n",
        })
        self.assertEqual([], unnamed_credits(root))

    def test_a_credits_file_its_row_does_not_name_is_reported(self):
        root = self.tree({
            "themes/README.md": self.page(
                "| `cinematic/` | OLED look | <img src=\"cinematic/screenshot.jpg\"> |"),
            "themes/cinematic/config.json": "{}",
            "themes/cinematic/CREDITS.md": "# Wallpaper Credits\n",
        })
        self.assertEqual(
            [("cinematic", "themes/cinematic/CREDITS.md is not named in its row of themes/README.md")],
            unnamed_credits(root))

    def test_a_mention_in_another_themes_row_is_not_the_row(self):
        # The failure the page has to catch: the table names the file somewhere, just not in the row a
        # reader of this theme is on.
        root = self.tree({
            "themes/README.md": self.page(
                "| `cinematic/` | OLED look | <img src=\"cinematic/screenshot.jpg\"> |",
                "| `storm/` | Lightning, see `storm/CREDITS.md` | <img src=\"storm/screenshot.jpg\"> |"),
            "themes/cinematic/config.json": "{}",
            "themes/cinematic/CREDITS.md": "# Wallpaper Credits\n",
            "themes/storm/config.json": "{}",
            "themes/storm/CREDITS.md": "# Wallpaper Credits\n",
        })
        self.assertEqual(["cinematic"], [theme for theme, _ in unnamed_credits(root)])

    def test_a_mention_in_the_prose_is_not_a_row(self):
        root = self.tree({
            "themes/README.md": "# Themes\n\nSee `cinematic/CREDITS.md`.\n\n" + self.page(
                "| `cinematic/` | OLED look | <img src=\"cinematic/screenshot.jpg\"> |"),
            "themes/cinematic/config.json": "{}",
            "themes/cinematic/CREDITS.md": "# Wallpaper Credits\n",
        })
        self.assertEqual(["cinematic"], [theme for theme, _ in unnamed_credits(root)])

    def test_a_theme_with_no_credits_file_is_not_judged(self):
        # Three themes draw their pictures from code and three ship one drawn for them, with no
        # CREDITS.md at all; this check has nothing to say about them.
        root = self.tree({
            "themes/README.md": self.page(
                "| `aurora/` | A dark aurora wallpaper | <img src=\"aurora/screenshot.jpg\"> |"),
            "themes/aurora/config.json": "{}",
            "themes/aurora/wallpaper.jpg": "not a picture, but not a CREDITS.md either\n",
        })
        self.assertEqual([], unnamed_credits(root))

    def test_a_theme_folder_with_no_row_is_reported(self):
        root = self.tree({
            "themes/README.md": self.page(
                "| `storm/` | Lightning, see `storm/CREDITS.md` | <img src=\"storm/screenshot.jpg\"> |"),
            "themes/storm/config.json": "{}",
            "themes/storm/CREDITS.md": "# Wallpaper Credits\n",
            "themes/lost/config.json": "{}",
            "themes/lost/CREDITS.md": "# Wallpaper Credits\n",
        })
        self.assertEqual([("lost", "themes/lost/ ships a CREDITS.md and themes/README.md has no row for it")],
                         unnamed_credits(root))
        self.assertEqual(["lost"], unlisted_themes(root))

    def test_a_theme_folder_named_without_the_trailing_slash_is_not_a_row(self):
        # The Keys table below the theme table has rows of its own (`| `dock_apps` | ... |`); a row is
        # only a theme when its first cell is a folder.
        root = self.tree({
            "themes/README.md": self.page(
                "| `cinematic/` | OLED look | <img src=\"cinematic/screenshot.jpg\"> |") +
                                  "\n| `cinematic` | not a row of the theme table |\n",
            "themes/cinematic/config.json": "{}",
            "themes/cinematic/CREDITS.md": "# Wallpaper Credits\n",
        })
        self.assertEqual(["cinematic"], [theme for theme, _ in unnamed_credits(root)])


if __name__ == "__main__":
    unittest.main()

"""The two claims a theme row makes about its own folder: how often the pictures change, and their licence.

`themes/README.md`'s theme table is what a reader sees before pushing a theme: how often its pictures
change ("a new one every 20 minutes", "a new one every hour"), and the licence they were published
under ("The photos are CC BY / CC BY-SA, not MIT"). Both are prose beside the folder and neither was
read by a test. `tests/test_themes.py` holds the row and its screenshot, `tests/test_theme_wallpapers.py`
every picture's shape and weight, and `tests/test_theme_picture_credits.py` each picture's line in
`CREDITS.md` against the caption the TV shows - the row's own two sentences were read by nothing.

So a theme that gains pictures and moves `wallpaper_rotation_minutes` in its `config.json`, or a
collection whose licence changes in `CREDITS.md`, leaves the row promising a rotation the launcher does
not run or a licence the repo does not redistribute under, on the page a reader meets first, with the
suite green. `themes/CREATE_A_THEME.md` sends an author to this table to copy a row's shape, so a wrong
sentence is copied rather than seen.

What is judged, for every row of that table:

- an interval the row states, in digits or in a word ("every 20 minutes", "every five minutes",
  "every hour"), is held to `wallpaper_rotation_minutes` in that theme's `config.json`. A theme that
  states an interval and sets no key is reported: `Config.java` reads the key as
  `integer(json, "wallpaper_rotation_minutes", 0, 0, 1440)`, so no key means no rotation, and the row
  would be promising one;
- a licence the row states (CC0, CC BY, CC BY-SA, public domain, Unsplash) is held to the licences
  stated in that theme's `CREDITS.md`. The row may name fewer than the file - a theme can credit a
  picture and not repeat it in the row's summary - but never one the file does not state, which is the
  condition CC BY and CC BY-SA grant;
- a row that states neither is quiet, and the walk fails when it reads nothing: a table rewritten in
  another shape, or a reader that stops matching, would otherwise pass by finding nothing.

Read one way only: the config and the file are the truth, the row is held to them, and a theme that
sets an interval or ships pictures without saying so in its row is not this check's business.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"

# The theme table's header, the only table in the page whose rows are theme folders.
TABLE = "| Theme | What it does | Screenshot |"

# "a new one every 20 minutes", "shuffled every 15 minutes", "a new one every hour in order".
INTERVAL = re.compile(r"every[ \t]+(\d{1,3}|[a-z]+)[ \t]+(minutes?|hours?)", re.I)
EVERY_HOUR = re.compile(r"every[ \t]+(hour|minute)\b", re.I)
WORDS = {name: number for number, name in enumerate(
    ("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve"), 1)}

# Longest first, so "CC BY-SA" is read as one licence rather than as "CC BY" beside it.
LICENCE = re.compile(r"CC BY-SA|CC BY|CC0|public domain|Unsplash", re.I)
# The same licence spelled two ways, as CREDITS.md and a row spell it ("CC0 1.0" is still CC0).
NAMED = {"cc0": "cc0", "cc by": "cc by", "cc by-sa": "cc by-sa", "public domain": "public domain",
         "unsplash": "unsplash license"}

# What Config.java answers for a theme that sets no key: no rotation.
UNSET_ROTATION = 0


def rows(text):
    """[(folder, its row's description)] for the theme table in themes/README.md, in its order."""
    if TABLE not in text:
        return []
    section = text.split(TABLE, 1)[1]
    out = []
    for line in section.split("\n")[1:]:
        if not line.startswith("|"):
            break
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) < 2:
            continue
        name = cells[0].strip("`").rstrip("/")
        if not name or set(name) <= {"-"}:
            continue  # the |---| separator under the header
        out.append((name, cells[1]))
    return out


def intervals(body):
    """[(minutes, the words that state it)] for every rotation interval a row's description states."""
    out = []
    for match in INTERVAL.finditer(body):
        number, unit = match.group(1), match.group(2).casefold()
        value = int(number) if number.isdigit() else WORDS.get(number.casefold())
        if value is None:
            continue
        out.append((value * 60 if unit.startswith("hour") else value, match.group(0)))
    for match in EVERY_HOUR.finditer(body):
        out.append((60 if match.group(1).casefold() == "hour" else 1, match.group(0)))
    return out


def licences(text):
    """The licences a passage states, each under the one name the two spellings share."""
    return {NAMED[match.group(0).casefold()] for match in LICENCE.finditer(text)}


def read_config(folder, root):
    """The theme's config.json, or None when the folder has none."""
    path = root / folder / "config.json"
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def read_credits(folder, root):
    """The theme's CREDITS.md, or None when a theme with a licence claim ships none."""
    path = root / folder / "CREDITS.md"
    return path.read_text(encoding="utf-8") if path.exists() else None


def problems(readme, root=THEMES):
    """Everything a row promises that the theme folder beside it does not do."""
    out = []
    for folder, body in rows(readme):
        config = read_config(folder, root)
        if config is None:
            out.append(f"themes/README.md: the theme table names {folder}/, and {root / folder} has no "
                       f"config.json: a row without a theme is a link into nothing")
            continue
        rotation = config.get("wallpaper_rotation_minutes", UNSET_ROTATION)
        for minutes, phrase in intervals(body):
            if minutes == rotation:
                continue
            if rotation == UNSET_ROTATION:
                out.append(f"themes/README.md's {folder}/ row says {phrase!r} and its config.json sets no "
                           f"wallpaper_rotation_minutes: the launcher does not rotate at all, so the row "
                           f"promises a rotation that does not run")
            else:
                out.append(f"themes/README.md's {folder}/ row says {phrase!r} and its config.json sets "
                           f"wallpaper_rotation_minutes to {rotation}: the row and the key have to be one "
                           f"interval")
        stated = licences(body)
        if not stated:
            continue
        credits = read_credits(folder, root)
        if credits is None:
            out.append(f"themes/README.md's {folder}/ row states {', '.join(sorted(stated))} and "
                       f"{folder}/CREDITS.md is not there: a licence the repo redistributes under has to "
                       f"be stated in the theme itself")
            continue
        missing = stated - licences(credits)
        if missing:
            out.append(f"themes/README.md's {folder}/ row states {', '.join(sorted(missing))} and its "
                       f"CREDITS.md does not: the file is what the repo redistributes under")
    return out


class TreeTest(unittest.TestCase):
    """The theme table as it ships, read against every theme folder."""

    def test_every_row_keeps_its_promise(self):
        readme = (THEMES / "README.md").read_text(encoding="utf-8")
        self.assertEqual([], problems(readme))

    def test_the_walk_reads_the_claims(self):
        readme = (THEMES / "README.md").read_text(encoding="utf-8")
        intervals_read = sum(len(intervals(body)) for _, body in rows(readme))
        licences_read = sum(1 for _, body in rows(readme) if licences(body))
        self.assertGreaterEqual(intervals_read, 8, "the rotation claims a row states are not being read")
        self.assertGreaterEqual(licences_read, 8, "the licence claims a row states are not being read")


class ReaderTest(unittest.TestCase):
    """The same readers over a theme table written here, one shape per test."""

    def tree(self, row, config=None, credits=None):
        """A one-theme tree: README.md with this row, and the folder's two hand-kept files."""
        folder = tempfile.mkdtemp()
        self.addCleanup(__import__("shutil").rmtree, folder, True)
        root = Path(folder)
        (root / "example").mkdir()
        if config is not None:
            (root / "example" / "config.json").write_text(json.dumps(config), encoding="utf-8")
        if credits is not None:
            (root / "example" / "CREDITS.md").write_text(credits, encoding="utf-8")
        readme = f"{TABLE}\n|---|:---|:---|\n| `example/` | {row} | <img src=\"example/screenshot.jpg\"> |\n"
        return readme, root

    def test_the_interval_shapes_a_row_uses_are_read(self):
        for phrase, minutes in (("a new one every 20 minutes", 20), ("every five minutes", 5),
                                ("a new one every hour in order", 60), ("shuffled every 15 minutes", 15)):
            readme, root = self.tree(phrase, {"wallpaper_rotation_minutes": minutes})
            self.assertEqual([], problems(readme, root), phrase)

    def test_a_row_a_theme_moved_on_from_is_reported(self):
        readme, root = self.tree("Eight photos, a new one every 20 minutes.",
                                 {"wallpaper_rotation_minutes": 30})
        found = problems(readme, root)
        self.assertEqual(1, len(found), found)
        self.assertIn("every 20 minutes", found[0])
        self.assertIn("to 30", found[0])

    def test_a_row_that_promises_a_rotation_no_key_sets_is_reported(self):
        readme, root = self.tree("Eight photos, a new one every 20 minutes.", {"wallpaper": True})
        found = problems(readme, root)
        self.assertEqual(1, len(found), found)
        self.assertIn("sets no wallpaper_rotation_minutes", found[0])

    def test_a_licence_the_credits_state_beside_another_is_quiet(self):
        readme, root = self.tree("The photos are CC BY / CC BY-SA, not MIT.",
                                 {"wallpaper": True}, "Each picture: CC BY or CC BY-SA, see the list.\n")
        self.assertEqual([], problems(readme, root))

    def test_a_licence_read_as_one_is_not_read_as_two(self):
        readme, root = self.tree("The picture is CC0, not MIT.", {"wallpaper": True},
                                 "The picture is CC BY-SA 4.0.\n")
        found = problems(readme, root)
        self.assertEqual(1, len(found), found)
        self.assertIn("cc0", found[0])
        self.assertNotIn("cc by-sa", found[0])

    def test_a_licence_claimed_with_no_credits_file_is_reported(self):
        readme, root = self.tree("The photos are CC0.", {"wallpaper": True})
        found = problems(readme, root)
        self.assertEqual(1, len(found), found)
        self.assertIn("CREDITS.md is not there", found[0])

    def test_a_row_that_states_neither_is_quiet(self):
        readme, root = self.tree("A quiet flat look, five columns and no wallpaper.", {"wallpaper": False})
        self.assertEqual([], problems(readme, root))

    def test_a_row_for_a_folder_that_is_not_there_is_reported(self):
        readme, root = self.tree("The photos are CC0.")
        found = problems(readme, root)
        self.assertEqual(1, len(found), found)
        self.assertIn("no config.json", found[0])

    def test_a_table_the_walk_cannot_find_reads_nothing(self):
        self.assertEqual([], rows("# Themes\n\nNo table here.\n"))
        self.assertEqual([], problems("# Themes\n\nNo table here.\n"))


if __name__ == "__main__":
    unittest.main()

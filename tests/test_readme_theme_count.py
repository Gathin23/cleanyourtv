"""The front page's theme count matches the themes/ folder.

README.md's feature table answers "what can I get without a rebuild?" with a themes row, and that
row carries a number: "Five ship in themes/". The number is a claim about the tree, and nothing read
it: it was written when themes/ held five folders (aurora, default, dussehra, scenes, six-columns)
and stayed at five while glass and ruska were added, so the page offered a reader two fewer looks
than the repo ships.

A theme folder is one with a config.json - the same rule tests/test_themes.py uses, and the same one
the launcher pushes (tools/theme.sh reads a folder's config.json). The number in the row is read
from the page, never copied here, so a third theme cannot land without the sentence following.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
THEMES = ROOT / "themes"
FRONT = ROOT / "README.md"

# The row is `| **Themes** | ... N ship in [`themes/`](themes/)`. A count written as a word or as
# digits is accepted, so rewording the cell stays free as long as the number is still there.
ROW_START = "| **Themes** |"
COUNT = re.compile(r"\b(\d+|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\s+ship\b",
                   re.IGNORECASE)
WORDS = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6,
    "seven": 7, "eight": 8, "nine": 9, "ten": 10, "eleven": 11, "twelve": 12,
}


def themes_row(text):
    """The README feature row for themes, or None when the page has no such row."""
    for line in text.split("\n"):
        if line.startswith(ROW_START):
            return line
    return None


def count_in(row):
    """The number the row claims, or None when it claims none."""
    if row is None:
        return None
    match = COUNT.search(row)
    if match is None:
        return None
    token = match.group(1).lower()
    return int(token) if token.isdigit() else WORDS[token]


def theme_folders(root=THEMES):
    """Every folder in themes/ that carries a config.json, i.e. every pushable theme."""
    if not root.is_dir():
        return []
    return sorted(p.name for p in root.iterdir() if p.is_dir() and (p / "config.json").is_file())


class ReadmeThemeCountTest(unittest.TestCase):
    def test_the_count_names_every_theme(self):
        row = themes_row(FRONT.read_text(encoding="utf-8"))
        self.assertIsNotNone(row, "README.md has no `| **Themes** |` feature row any more")
        claimed = count_in(row)
        self.assertIsNotNone(claimed, f"the Themes row states no count: {row}")
        shipped = theme_folders()
        # Named in the failure so whoever added a theme knows which line to change.
        self.assertEqual(
            claimed, len(shipped),
            f"the Themes row says {claimed} and themes/ holds {len(shipped)}: {', '.join(shipped)}")


class CountTest(unittest.TestCase):
    """The readers above, so the repo-wide test cannot pass by finding nothing."""

    def test_a_word_is_read(self):
        self.assertEqual(5, count_in("| **Themes** | ... Five ship in [`themes/`](themes/) |"))

    def test_digits_are_read(self):
        self.assertEqual(7, count_in("| **Themes** | ... 7 ship in [`themes/`](themes/) |"))

    def test_a_row_without_a_count_reads_as_none(self):
        self.assertEqual(None, count_in("| **Themes** | Layout from a pushed `config.json` |"))

    def test_a_missing_row_reads_as_none(self):
        self.assertEqual(None, count_in(themes_row("| **Configure** | Dock order |\n")))
        self.assertEqual(None, count_in(themes_row("")))

    def test_the_row_is_found_among_other_rows(self):
        text = ("| **Configure** | Dock order |\n"
                "| **Themes** | ... Six ship in [`themes/`](themes/) |\n"
                "| **25 languages** | Follows the TV's language |\n")
        self.assertEqual(6, count_in(themes_row(text)))

    def test_only_folders_with_a_config_count(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "aurora").mkdir()
            (root / "aurora" / "config.json").write_text("{}", encoding="utf-8")
            (root / "wallpapers").mkdir()  # a picture dump, not a theme
            (root / "README.md").write_text("not a folder", encoding="utf-8")
            self.assertEqual(["aurora"], theme_folders(root))


if __name__ == "__main__":
    unittest.main()

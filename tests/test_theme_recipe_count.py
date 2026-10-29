"""docs/workflows/theme.md is the recipe a theme author is sent to (AGENTS.md's workflow list), and its
steps stopped at "add a row to themes/README.md" - while the suite also holds the front page's Themes
row to the number of theme folders.

A theme built exactly from the recipe as it read - a `config.json` of keys the table already carries, a
wallpaper, a `screenshot.jpg` off the TV and one row in themes/README.md - already fails the suite:

    AssertionError: 8 != 9 : the Themes row says 8 and themes/ holds 9: aurora, default, dussehra,
    exampletheme, glass, ruska, scenes, six-columns, sweden

so the step an author is missing is the one the failure message asks for (`the Themes row says N`), and
the recipe never mentioned it.

The push step had the same problem in miniature: it gave `tools/theme.sh themes/<name>` without the
`TV_HOST` every other page passes, and the script stops before it reaches the TV:

    tools/theme.sh: line 21: TV_HOST: set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50
    tools/theme.sh themes/aurora

These tests read both requirements out of what enforces them - `tests/test_readme_theme_count.py` and
`tools/theme.sh`'s own `:?` guard - and not out of a copy pasted here, so an assertion about the recipe
can only pass while the checker really enforces what it asks for: if either stops, the pair fails and the
recipe has to be re-read rather than silently kept in step.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs/workflows/theme.md"
COUNT_CHECK = ROOT / "tests/test_readme_theme_count.py"
THEME_SH = ROOT / "tools/theme.sh"


def steps(text):
    """[(number, text)] for the numbered steps of a recipe, wrapped continuation lines joined in."""
    found = []
    for line in text.split("\n"):
        m = re.match(r"^(\d+)\.\s+(.*)$", line)
        if m:
            found.append([int(m.group(1)), m.group(2)])
        elif found and line.startswith("   ") and line.strip():
            found[-1][1] = found[-1][1] + " " + line.strip()
    return [(number, body) for number, body in found]


def steps_naming(text, needle):
    """The numbered steps whose text names `needle`."""
    return [body for _, body in steps(text) if needle in body]


class RecipeTest(unittest.TestCase):
    def setUp(self):
        self.text = PAGE.read_text(encoding="utf-8")

    def test_the_recipe_has_steps(self):
        self.assertGreaterEqual(len(steps(self.text)), 5, "docs/workflows/theme.md lost its numbered steps")

    def test_the_recipe_bumps_the_front_page_count(self):
        front = steps_naming(self.text, "ship in")
        self.assertEqual(len(front), 1,
                         "expected one step telling the author to bump the count in README.md's Themes row\n"
                         f"(the row the checker reads with its `N ship in` sentence), found {len(front)}:\n"
                         "tests/test_readme_theme_count.py fails a new theme folder until that sentence says\n"
                         "the same number, so without the step the recipe sends an author into a red suite")
        step = front[0]
        self.assertIn("README.md", step,
                      "the step that changes the count does not say which page carries it: the Themes row is\n"
                      "in the front page's feature table, not themes/README.md")
        self.assertIn("test_readme_theme_count", step,
                      "the step does not name tests/test_readme_theme_count.py, the check that refuses the\n"
                      "page until the count matches the folders")

    def test_the_push_step_passes_the_tv_host(self):
        push = steps_naming(self.text, "tools/theme.sh")
        self.assertEqual(len(push), 1, f"expected one step pushing the theme, found {len(push)}")
        self.assertIn("TV_HOST", push[0],
                      "the push step runs tools/theme.sh without TV_HOST, which the script requires:\n"
                      "tools/theme.sh: line 21: TV_HOST: set TV_HOST to the TV IP address, e.g.\n"
                      "TV_HOST=192.168.1.50 tools/theme.sh themes/aurora")


class CheckerTest(unittest.TestCase):
    """The requirements the recipe is held to are the ones the checker and the script still enforce."""

    def test_the_count_checker_still_reads_the_themes_row(self):
        text = COUNT_CHECK.read_text(encoding="utf-8")
        self.assertIn('"| **Themes** |"', text,
                      "tests/test_readme_theme_count.py no longer looks for the Themes row; the recipe's\n"
                      "count step can go")
        self.assertIn(r"\s+ship\b", text,
                      "tests/test_readme_theme_count.py no longer reads the count out of an `N ship in`\n"
                      "sentence; the recipe's wording is free again")
        self.assertIn('(p / "config.json").is_file()', text,
                      "tests/test_readme_theme_count.py no longer counts the folders that carry a\n"
                      "config.json; the recipe's count step can go")

    def test_theme_sh_still_requires_a_host(self):
        text = THEME_SH.read_text(encoding="utf-8")
        self.assertIn("${TV_HOST:?", text,
                      "tools/theme.sh no longer requires TV_HOST, so the push step need not name it")


class StepReaderTest(unittest.TestCase):
    def test_a_wrapped_step_is_read_as_one_step(self):
        text = "1. Push it\n   with TV_HOST.\n2. Add a row.\n"
        self.assertEqual(steps(text), [(1, "Push it with TV_HOST."), (2, "Add a row.")])

    def test_a_mention_outside_the_steps_is_not_a_step(self):
        text = "An intro that names tools/theme.sh.\n\n1. Create a folder.\n"
        self.assertEqual(steps_naming(text, "tools/theme.sh"), [])

    def test_a_step_names_only_what_it_carries(self):
        text = "1. Push with TV_HOST and tools/theme.sh.\n2. Bump the count.\n"
        self.assertEqual(len(steps_naming(text, "tools/theme.sh")), 1)
        self.assertEqual(steps_naming(text, "ship in"), [])


if __name__ == "__main__":
    unittest.main()

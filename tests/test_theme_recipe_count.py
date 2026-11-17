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

The step that lists the front pages had the same failure a third time. It carried seven pages and, on two
of the three pages that carry it, called the last two of them the ones that write the count in digits;
README.he.md writes "28 ערכות" and README.uz.md "28 ta tema", and the Uzbek page joined the pin in #3573
the day before - so an author following `themes/README.md` or `themes/CREATE_A_THEME.md`, both of which
still said seven, bumped six pages and left README.uz.md at the old number, which is the red suite the
step exists to prevent. The pages that state the count, and which of them write it as a number, are read
here out of the pages themselves through the count check's own reader rather than pinned again, so a
translation that starts stating the count fails the three recipes until they name it too.
"""
import re
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_readme_theme_count as front_counts  # noqa: E402  (a module, so its tests do not run twice)

PAGE = ROOT / "docs/workflows/theme.md"
COUNT_CHECK = ROOT / "tests/test_readme_theme_count.py"
THEME_SH = ROOT / "tools/theme.sh"
# The three pages that carry the count step: the workflow AGENTS.md sends a theme author to, the table's
# own "Adding a theme", and the longer recipe beside it.
RECIPES = (PAGE, ROOT / "themes/README.md", ROOT / "themes/CREATE_A_THEME.md")


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


# The step or paragraph that lists the front pages: from "bump the count" to the sentence that says the
# list is today's pages rather than the rule. `themes/README.md` and `themes/CREATE_A_THEME.md` write it
# as a paragraph and `docs/workflows/theme.md` as a numbered step, and all three carry both phrases.
CLAIM_BLOCK = re.compile(r"(?is)bump the count.*?rather than the rule")
# `README.md`, `README.pt-BR.md`, `README.he.md`: a front page, as the sentence names it.
FRONT_PAGE = re.compile(r"README[A-Za-z-]*(?:\.[A-Za-z-]+)*\.md")
# `(the last four in digits)`, wrapped anywhere: how many of the pages named last write the count as a
# number instead of a word.
DIGITS_GROUP = re.compile(r"(?is)the last ([\w-]+)\s+in digits")


def claim_block(text):
    """The step that lists the front pages, its wrapped lines joined into one string.

    The pages wrap by hand at about 100 characters, so `rather than the rule` is split across two lines in
    `docs/workflows/theme.md`; the block is read out of the whitespace-joined page for that reason.
    """
    match = CLAIM_BLOCK.search(" ".join(text.split()))
    return match.group(0) if match else ""


def named_pages(text):
    """(the pages the step names, in its order, and how many of them it says write the count in digits).

    The number is None for a step that says nothing about digits, which is a step an author has to read
    the pages for rather than a wrong one.
    """
    block = claim_block(text)
    names = []
    for name in FRONT_PAGE.findall(block):
        if name not in names:
            names.append(name)
    group = DIGITS_GROUP.search(block)
    in_digits = None
    if group:
        word = group.group(1).lower()
        in_digits = int(word) if word.isdigit() else front_counts.COUNT_WORDS.get(word)
    return names, in_digits


def stating_pages(root=ROOT):
    """The front pages that state the theme count, read the way the count check reads them."""
    return {page.name for page in front_counts.front_pages(root)
            if front_counts.claims(page.read_text(encoding="utf-8"))}


def digit_pages(root=ROOT):
    """The pages among those that write the count as a number rather than a word."""
    out = set()
    for page in front_counts.front_pages(root):
        for values, line in front_counts.claims(page.read_text(encoding="utf-8")):
            if any(re.search(rf"(?<!\d){number}(?!\d)", line) for number in values):
                out.add(page.name)
    return out


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


class RecipePageListTest(unittest.TestCase):
    """The count step names the pages that state the count, and groups them by how they write it."""

    def test_every_recipe_names_the_pages_that_state_the_count(self):
        stating = stating_pages()
        self.assertGreaterEqual(len(stating), 7, "no front page states the theme count any more")
        for recipe in RECIPES:
            names, _ = named_pages(recipe.read_text(encoding="utf-8"))
            with self.subTest(recipe=recipe.name):
                self.assertEqual(
                    sorted(names), sorted(stating),
                    f"{recipe.relative_to(ROOT)} names {len(names)} front pages where {len(stating)} "
                    f"state the count ({', '.join(sorted(stating))}): a page the step does not name keeps "
                    f"the old number, and CI then fails on one the recipe never mentioned")

    def test_the_digits_group_is_the_pages_that_write_digits(self):
        digits = digit_pages()
        for recipe in RECIPES:
            names, stated = named_pages(recipe.read_text(encoding="utf-8"))
            with self.subTest(recipe=recipe.name):
                if stated is None:
                    self.fail(
                        f"{recipe.relative_to(ROOT)}'s count step does not say which of the pages write "
                        f"the count in digits ({', '.join(sorted(digits))} do)")
                self.assertEqual(
                    set(names[-stated:]), digits,
                    f"{recipe.relative_to(ROOT)} says the last {stated} of its {len(names)} pages write "
                    f"the count in digits, where {', '.join(sorted(digits))} do")


class PageListReaderTest(unittest.TestCase):
    def test_a_wrapped_step_is_read_as_one_claim(self):
        text = ("Then bump the count in the Themes row: `README.md`, `README.pt-BR.md` and\n"
                "`README.he.md` (the last two\nin digits), each in its own words; the three are today's\n"
                "pages rather than the rule.")
        self.assertEqual(named_pages(text),
                         (["README.md", "README.pt-BR.md", "README.he.md"], 2))

    def test_a_step_with_no_digits_group_reports_none(self):
        text = "Then bump the count on `README.md`, rather than the rule."
        self.assertEqual(named_pages(text), (["README.md"], None))

    def test_a_page_named_outside_the_step_is_not_part_of_it(self):
        text = ("`README.he.md` is the page the row is written in.\n\n"
                "Then bump the count on `README.md`, rather than the rule.")
        self.assertEqual(named_pages(text)[0], ["README.md"])

    def test_a_page_named_twice_is_listed_once(self):
        text = ("1. Bump the count on `README.md` and `README.he.md`; `README.md` again, rather than the\n"
                "   rule.")
        self.assertEqual(named_pages(text)[0], ["README.md", "README.he.md"])


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

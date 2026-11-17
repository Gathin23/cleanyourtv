"""The launcher's language count is read on every front page that states it, not only the generated ones.

The card's alt text and the `| **31 languages** |` feature row both carry the number of languages the
launcher ships, and `tools/gen_tables.py` writes it for the pages in its TRANSLATIONS map (README.md,
README.nl.md, README.de.md, README.uk.md, README.tr.md) and for `tools/readme_stats.py`'s STATS, which
the pictures are drawn from. Two more front pages state the same number in that row and carry neither
the card nor the marker the generator looks for: README.he.md and README.pt-BR.md. Nothing writes them
and nothing read them, so `docs/workflows/translation.md` step 9 - the step a translator adds a
language with - named the five generated pages and stopped there.

The gap that leaves: add a language folder, run `tools/gen_tables.py` as the step says, and the five
generated pages and the pictures move to 32 while the Hebrew and Portuguese pages keep 31 - one fewer
language than the launcher ships, on two pages a reader picks their language from, with the suite and
`tools/gen_tables.py --check` green. Both pages set to 30 on this checkout of main: 2987 tests OK
(skipped=27) and `--check` exit 0, so neither number is read anywhere.

A third page stays outside the table for a second reason, and it is the one this file could not see:
README.uz.md states the row as `| **31 til** |`, and `til` is not `dil` - the Turkish word the table
already carries - so the coverage test at the end of this file, which reads the rows that use one of
the words above, found nothing to report. The page landed (0102a9a9) the day before this check was
written and has stated the count since its first version, so "all seven" - what
`docs/workflows/translation.md` step 9 said the check read - has been eight pages the whole time.

The number is never copied here. It is `tools/gen_tables.py`'s own `languages()`, the count the script
writes, and each page's row is read out of the page. The row carries the number in digits on all eight
pages today, which is what `docs/workflows/translation.md` step 9 asks for; a page that writes it as a
word is refused by name rather than read as no claim at all.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (needs tools/ on the path)

RECIPE = ROOT / "docs" / "workflows" / "translation.md"

# The pages that state how many languages the launcher ships, and the word each one states it in. The
# five above the last three are the pages tools/gen_tables.py writes (its TRANSLATIONS map, plus
# README.md); README.he.md and README.pt-BR.md are the hand-kept pair this file was written for, and
# README.uz.md is the page it could not see: its word is `til`, not the Turkish `dil` the table carries,
# so the cover test below - which reads a row written in one of these words - passed over it.
LANG_ROW = {
    "README.md": "languages",
    "README.de.md": "Sprachen",
    "README.es.md": "idiomas",
    "README.fr.md": "langues",
    "README.he.md": "שפות",
    "README.nl.md": "talen",
    "README.pt-BR.md": "idiomas",
    "README.tr.md": "dil",
    "README.uk.md": "мова",
    "README.uz.md": "til",
}

# `| **31 languages** | Follows the TV's language ...`. A step row such as `| **1. On the TV** |` is not
# one: the number there is followed by a period, not by a space and the word.
ROW = re.compile(r"^\| \*\*(\d+) ([^*|\n]+?)\*\* \|", re.M)


def front_pages(root=ROOT):
    """Every front page: README.md and the translations beside it."""
    return sorted(p for p in root.glob("README*.md") if p.is_file())


def rows(text):
    """[(number, word)] for every `| **N word** |` row on a page, in page order."""
    return [(int(m.group(1)), m.group(2).strip()) for m in ROW.finditer(text)]


def claimed(text, word):
    """The number the page states in its `<word>` row, or None when it states none."""
    for number, label in rows(text):
        if label == word:
            return number
    return None


def languages(root=ROOT):
    """English plus every translated strings.xml, as tools/gen_tables.py counts them."""
    return gen_tables.languages(root)


class LanguageCountTest(unittest.TestCase):
    def test_every_page_in_the_table_states_the_count_the_launcher_ships(self):
        shipped = languages()
        for name, word in sorted(LANG_ROW.items()):
            page = ROOT / name
            with self.subTest(page=name):
                self.assertTrue(page.is_file(), f"{name} is gone from the front pages")
                text = page.read_text(encoding="utf-8")
                stated = claimed(text, word)
                # Named in the failure so whoever adds a language knows which line to change.
                self.assertIsNotNone(
                    stated,
                    f"{name} states no `| **N {word}** |` row any more: the language count is a claim "
                    f"about the launcher and this file reads it on every page that makes it")
                self.assertEqual(
                    stated, shipped,
                    f"{name} says {stated} languages and the launcher ships {shipped}: "
                    f"`tools/gen_tables.py` writes the five pages in its TRANSLATIONS map and nothing "
                    f"writes this one, so its row moves by hand")

    def test_the_table_covers_every_page_that_states_it_in_one_of_these_words(self):
        """A page that copies a label this file knows is read, not left outside it.

        README.uk.md, README.tr.md and README.pt-BR.md each joined the table after stating their count
        where nothing looked. A page that copies one of these eight words is the same case; one that
        writes the count in a word this file does not carry (a new translation's own, as README.uz.md's
        `til` was beside the Turkish `dil`) is out of its reach, and that word is the line to add here.
        """
        words = set(LANG_ROW.values())
        for page in front_pages():
            for number, label in rows(page.read_text(encoding="utf-8")):
                if label not in words:
                    continue
                with self.subTest(page=page.name, word=label):
                    self.assertEqual(
                        LANG_ROW.get(page.name), label,
                        f"{page.name} states the language count as `{label}` and is not in this "
                        f"file's table, so nothing reads its {number}")

    def test_the_recipe_names_every_page_that_states_the_count(self):
        """docs/workflows/translation.md step 9 is where a translator is told which pages move.

        Read as a whole page rather than as one step, so rewording or moving the step stays free: what
        the step is for is that a language added by that page does not leave a front page behind.
        """
        recipe = RECIPE.read_text(encoding="utf-8")
        for name in sorted(LANG_ROW):
            with self.subTest(page=name):
                # assertTrue, not assertIn: the page is four thousand words and the failure should
                # name the page it is about, not print the recipe back.
                self.assertTrue(
                    name in recipe,
                    f"docs/workflows/translation.md does not name {name}, which states the language "
                    f"count, so the step a translator follows leaves it claiming the old number")


class TheReader(unittest.TestCase):
    """claimed() over rows written here, so the rule is pinned rather than the pages' wording."""

    def test_the_english_row_is_read(self):
        text = "| **31 languages** | Follows the TV's language, with a fully mirrored layout |"
        self.assertEqual(31, claimed(text, "languages"))

    def test_the_hebrew_row_is_read(self):
        text = "| **31 שפות** | השפה נקבעת לפי הטלוויזיה. בעברית הפריסה מוצגת מימין לשמאל. |"
        self.assertEqual(31, claimed(text, "שפות"))

    def test_the_portuguese_row_is_read(self):
        text = ("| **31 idiomas** | Segue o idioma da TV, com layout totalmente espelhado da direita "
                "para a esquerda em hebraico. |")
        self.assertEqual(31, claimed(text, "idiomas"))

    def test_the_uzbek_row_is_read(self):
        """`til`, the word the cover test could not match against the Turkish `dil`."""
        text = ("| **31 til** | Televizor tiliga ergashadi; ivrit uchun toʻliq oynalashtirilgan, "
                "oʻngdan chapga joylashuv |")
        self.assertEqual(31, claimed(text, "til"))
        self.assertIsNone(claimed(text, "dil"))

    def test_a_step_row_is_not_a_language_row(self):
        text = "| **1. On the TV** | Settings → About → press OK on **Build** seven times |"
        self.assertEqual([], rows(text))
        self.assertIsNone(claimed(text, "On the TV"))

    def test_a_row_written_as_a_word_is_refused_by_name(self):
        text = "| **thirty-one languages** | Follows the TV's language |"
        self.assertIsNone(claimed(text, "languages"))

    def test_a_row_for_another_number_is_not_read_as_this_one(self):
        text = ("| **122 brands** | Every brand with a tested list |\n"
                "| **31 languages** | Follows the TV's language |")
        self.assertEqual(31, claimed(text, "languages"))
        self.assertEqual(122, claimed(text, "brands"))

    def test_a_page_stating_one_too_few_is_refused(self):
        """The failure the two hand-kept pages carry when a language is added without them."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            res = root / "launcher" / "app" / "src" / "main" / "res"
            for folder in ("values", "values-de", "values-fr"):
                (res / folder).mkdir(parents=True)
                (res / folder / "strings.xml").write_text("<resources/>", encoding="utf-8")
            shipped = languages(root)
            self.assertEqual(3, shipped)
            row = "| **2 languages** | Follows the TV's language |"
            self.assertNotEqual(shipped, claimed(row, "languages"))

    def test_a_stand_in_page_stating_the_count_is_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            res = root / "launcher" / "app" / "src" / "main" / "res"
            for folder in ("values", "values-he"):
                (res / folder).mkdir(parents=True)
                (res / folder / "strings.xml").write_text("<resources/>", encoding="utf-8")
            shipped = languages(root)
            self.assertEqual(2, shipped)
            self.assertEqual(shipped, claimed("| **2 שפות** | השפה נקבעת לפי הטלוויזיה. |", "שפות"))

    def test_the_front_pages_are_walked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "README.md").write_text("| **1 languages** | x |", encoding="utf-8")
            (root / "README.he.md").write_text("| **1 שפות** | x |", encoding="utf-8")
            (root / "docs").mkdir()
            (root / "docs" / "README.other.md").write_text("nothing", encoding="utf-8")
            self.assertEqual(["README.he.md", "README.md"],
                             [p.name for p in front_pages(root)])


if __name__ == "__main__":
    unittest.main()

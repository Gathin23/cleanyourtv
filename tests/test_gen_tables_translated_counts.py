"""tools/gen_tables.py writes README.nl.md's brand block, so it has to write that page's counts too.

The script gained a translated README: README.nl.md carries the same generated brand block and short TV
table as README.md, and the count line the block sits under stayed where it was - "316 tv-modellen · 89
merken · 28 talen" over a grid built from 345 lists in 93 brands - because counts() only knew the
English words ("N TV models · B brands · L languages · " and "| **N languages** |"). The page
contradicted itself, one line apart, and nothing read either number: the English card's alt text is held
by test_gen_tables.py, the Dutch one by nothing.

TRANSLATIONS is now {page: the three words that page states its counts in}, so a translated README gets
its own numbers moved with its grid, and this file reads the mapping: each page it names must state those
three words in its card's alt text, one triple, and must be what render() gives back for it.

The mapping is also the whole list of pages the script writes, so a translation cannot copy the brand block
by hand and be left out of it: README.de.md did exactly that when the German page landed, and its grid said
"Xiaomi · 32" and "+49 more brands" over 97 brands while README.md said 34 and +53 - the block's own header
says it is generated, and no run touched it. One test here walks the checkout for the markers and holds
every page that carries them to being written.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (needs tools/ on the path first)
from test_gen_tables import START, END, add, section, tree, tv  # noqa: E402

# The Dutch page's card, as README.nl.md has it: the three counts, then the launcher's size (not one of
# them, so it does not move), then the press counts test_press_counts.py reads.
NL = f"""# Titel

<a href="docs/PRESS.md"><img src="docs/card.jpg" alt="316 tv-modellen · 89 merken · 28 talen · launcher
van 523 KB. Genoemd in TechCrunch." width="800"></a>

Een gewone regel Nederlandse tekst die niet van deze generator is.

| **28 talen** | Volgt de taal van de tv. |
| **Thema's** | Achtergrond en indeling uit een `config.json`. |

{START.format("tv-brands")}

oude merkregel

{END.format("tv-brands")}

<details>

{START.format("tv-table")}

| TV | OS |
|---|---|
| oude rij | x |

{END.format("tv-table")}

</details>
"""

LISTS = [("acme-a", tv("Acme A", "Acme")), ("acme-b", tv("Acme B", "Acme")), ("bolt-c", tv("Bolt C", "Bolt"))]

# The German page's card, as README.de.md has it: the same three counts in German, then the launcher's size,
# which is not one of them and does not move. No short TV table - that page has the brand block only.
DE = f"""# Titel

<a href="docs/PRESS.md"><img src="docs/card.jpg" alt="316 TV-Modelle · 89 Marken · 28 Sprachen · Launcher
mit 523 KB. Erwähnt in TechCrunch." width="800"></a>

Eine gewöhnliche deutsche Zeile, die nicht von diesem Generator stammt.

| **28 Sprachen** | Folgt der Sprache des Fernsehers. |
| **Themes** | Hintergrund und Aufteilung aus einer `config.json`. |

{START.format("tv-brands")}

alte Markenzeile

{END.format("tv-brands")}
"""


def checkout(tmp, dutch: str | None = NL, german: str | None = DE):
    """The fake checkout test_gen_tables.py builds, with the translated READMEs beside its README.md."""
    root = tree(tmp, LISTS)
    for page, text in (("README.nl.md", dutch), ("README.de.md", german)):
        if text is not None:
            (root / page).write_text(text, encoding="utf-8")
    return root


def numbers(text, words):
    """(models, brands, languages) as `text` states them in `words`, or None."""
    found = re.search(rf"(\d+) {re.escape(words[0])} · (\d+) {re.escape(words[1])} · "
                      rf"(\d+) {re.escape(words[2])} · ", text)
    return tuple(int(n) for n in found.groups()) if found else None


def alt_triple(text, words):
    """The card's alt text up to and including the three counts, so a stray second triple cannot match."""
    found = re.search(rf'alt="(\d+ {re.escape(words[0])} · \d+ {re.escape(words[1])} · '
                      rf'\d+ {re.escape(words[2])} · )', text)
    return found.group(1) if found else None


def outside(text):
    """The lines of a page that are not inside a generated block, so prose can be compared."""
    keeping, inside = [], False
    for line in text.split("\n"):
        if line.startswith(("<!-- tv-brands:start", "<!-- tv-table:start")):
            inside = True
        if not inside:
            keeping.append(line)
        if line.strip() in ("<!-- tv-brands:end -->", "<!-- tv-table:end -->"):
            inside = False
    return keeping


class TranslatedCountsTest(unittest.TestCase):
    """A translated page in TRANSLATIONS gets its own numbers; a page that is not is not touched."""

    def render(self, root):
        return gen_tables.render(root)

    def test_the_translated_page_states_this_checkout_s_numbers_in_its_own_words(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            dutch = self.render(root)["README.nl.md"]
            entries = gen_tables.load(root)
            self.assertEqual((3, 2, 3), (len(entries), len(gen_tables.brands(entries)),
                                         gen_tables.languages(root)))
        self.assertIn("3 tv-modellen · 2 merken · 3 talen · ", dutch)
        self.assertIn("| **3 talen** |", dutch)
        self.assertNotIn("316", dutch)
        self.assertNotIn("28 talen", dutch)

    def test_the_numbers_the_generator_does_not_own_stay_where_they_are(self):
        # The launcher's size and the Dutch prose are not counts of the examples: a run that rewrote
        # them would be editing the translation, which is not what this script is for.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            dutch = self.render(root)["README.nl.md"]
        self.assertIn("launcher\nvan 523 KB", dutch)
        self.assertIn("Een gewone regel Nederlandse tekst die niet van deze generator is.", dutch)
        self.assertIn("| **Thema's** | Achtergrond en indeling uit een `config.json`. |", dutch)

    def test_only_the_two_count_lines_move_outside_the_generated_blocks(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            dutch = self.render(root)["README.nl.md"]
        before, after = outside(NL), outside(dutch)
        self.assertEqual(len(before), len(after), "the block padding changed the page's shape")
        moved = [(a, b) for a, b in zip(before, after) if a != b]
        self.assertEqual(2, len(moved), f"more than the two count lines changed: {moved}")
        self.assertIn("2 merken · 3 talen", moved[0][1])
        self.assertTrue(moved[1][1].startswith("| **3 talen** |"))

    def test_the_english_page_keeps_the_english_words(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            readme = self.render(root)["README.md"]
        self.assertEqual((3, 2, 3), numbers(readme, ("TV models", "brands", "languages")))
        self.assertIn("| **3 languages** |", readme)
        self.assertNotIn("tv-modellen", readme)

    def test_a_README_nl_that_is_not_there_is_not_written(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp, dutch=None)
            self.assertNotIn("README.nl.md", self.render(root))

    def test_each_translated_page_states_the_numbers_in_its_own_words(self):
        # Two pages in the mapping, two sets of words: the Dutch one must not take the German words or the
        # other way round, which is what one shared triple would do.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            rendered = self.render(root)
        self.assertIn("3 tv-modellen · 2 merken · 3 talen · ", rendered["README.nl.md"])
        self.assertIn("| **3 talen** |", rendered["README.nl.md"])
        self.assertIn("3 TV-Modelle · 2 Marken · 3 Sprachen · ", rendered["README.de.md"])
        self.assertIn("| **3 Sprachen** |", rendered["README.de.md"])
        self.assertNotIn("Marken", rendered["README.nl.md"])
        self.assertNotIn("merken", rendered["README.de.md"])

    def test_the_german_page_keeps_what_the_generator_does_not_own(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            german = self.render(root)["README.de.md"]
        self.assertIn("Launcher\nmit 523 KB", german)
        self.assertIn("Eine gewöhnliche deutsche Zeile, die nicht von diesem Generator stammt.", german)
        self.assertIn("| **Themes** | Hintergrund und Aufteilung aus einer `config.json`. |", german)

    def test_a_README_de_that_is_not_there_is_not_written(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp, german=None)
            rendered = self.render(root)
        self.assertNotIn("README.de.md", rendered)
        self.assertIn("README.nl.md", rendered)

    def test_both_translated_pages_get_the_brand_block_beside_their_counts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            rendered = self.render(root)
        for page in ("README.nl.md", "README.de.md"):
            with self.subTest(page=page):
                block = section(rendered[page], "tv-brands")
                self.assertIn("[Acme](https://cleanyour.tv/tvs?q=Acme) · 2", " ".join(block))
                self.assertNotIn("alte Markenzeile", block)

    def test_the_brand_block_is_written_beside_the_counts(self):
        # The point of moving the numbers: the grid under them is generated from every tv.json.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            dutch = self.render(root)["README.nl.md"]
        block = section(dutch, "tv-brands")
        self.assertIn("[Acme](https://cleanyour.tv/tvs?q=Acme) · 2", " ".join(block))
        self.assertIn("| Acme A | Android TV 11 |", dutch)


class ThisRepositoryTest(unittest.TestCase):
    """README.nl.md on this checkout: the mapping names its words, and the page is what render() gives."""

    def test_every_translated_page_the_generator_writes_names_its_three_words(self):
        self.assertTrue(gen_tables.TRANSLATIONS, "no translated page is written at all")
        for page, words in gen_tables.TRANSLATIONS.items():
            with self.subTest(page=page):
                self.assertEqual(3, len(words), f"{page}'s counts are stated in three words, not {words}")
                self.assertTrue(all(word.strip() for word in words), words)
                self.assertEqual(3, len(set(words)), f"{page} uses one word twice: {words}")
                text = (ROOT / page).read_text(encoding="utf-8")
                self.assertIsNotNone(alt_triple(text, words),
                                     f"{page}'s card alt text does not state {words[0]} · {words[1]} · "
                                     f"{words[2]} in the words TRANSLATIONS gives")

    def test_the_translated_card_and_row_carry_the_numbers_the_examples_give(self):
        entries = gen_tables.load(ROOT)
        counts = (len(entries), len(gen_tables.brands(entries)), gen_tables.languages(ROOT))
        for page, words in gen_tables.TRANSLATIONS.items():
            with self.subTest(page=page):
                text = (ROOT / page).read_text(encoding="utf-8")
                self.assertEqual(f"{counts[0]} {words[0]} · {counts[1]} {words[1]} · "
                                 f"{counts[2]} {words[2]} · ", alt_triple(text, words))
                self.assertIn(f"| **{counts[2]} {words[2]}** |", text)

    def test_the_page_on_disk_is_what_the_script_writes(self):
        # What --check reads: if the counts moved without the page, this is the failure a maintainer sees.
        rendered = gen_tables.render(ROOT)
        for page in gen_tables.TRANSLATIONS:
            with self.subTest(page=page):
                self.assertEqual((ROOT / page).read_text(encoding="utf-8"), rendered[page])

    def test_every_page_that_carries_the_brand_block_is_written(self):
        # The block's header says it is generated, so a page that carries it has to be in the list this
        # script writes: README.de.md copied it by hand and was left out, and its grid said "Xiaomi · 32"
        # and "+49 more brands" over 97 brands. A translation that copies the block next is held here.
        pages, marker = [], "<!-- tv-brands:start"
        for path in sorted(ROOT.glob("README*.md")):
            if marker in path.read_text(encoding="utf-8"):
                pages.append(path.name)
        self.assertLessEqual({"README.md", "README.nl.md", "README.de.md"}, set(pages),
                             f"the brand block's marker was not found where this test expects it: {pages}")
        written = set(gen_tables.OWNED) | set(gen_tables.TRANSLATIONS)
        for page in pages:
            with self.subTest(page=page):
                self.assertIn(page, written,
                              f"{page} carries the brand block but tools/gen_tables.py never rewrites it: "
                              f"add it to TRANSLATIONS with the three words its counts are stated in")

    def test_the_walk_reads_this_checkout(self):
        entries = gen_tables.load(ROOT)
        self.assertGreaterEqual(len(entries), 300)
        self.assertGreaterEqual(len(gen_tables.brands(entries)), 80)


if __name__ == "__main__":
    unittest.main()

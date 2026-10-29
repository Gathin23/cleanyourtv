"""Keeps the press numbers in docs/PRESS.md, the README cards and the card source in step.

The article total is written in every place that states it: the PRESS.md intro, its per-country index
line, the alt text of the card in README.md, the alt text of the same card in README.nl.md (in Dutch)
and in README.de.md (in German), and tools/readme_featured.py (which draws the line onto
docs/featured.png). Adding one article by hand means editing all of them; this checks they agree, so a
PR that adds a row and forgets the counts fails here instead of shipping a card that says 73 over a
list of 74.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
PRESS = ROOT / "docs" / "PRESS.md"
README = ROOT / "README.md"
FEATURED = ROOT / "tools" / "readme_featured.py"
PRESS_RECIPE = ROOT / "docs" / "workflows" / "press.md"

LATAM = "Latin America (Spanish-language)"
ONLINE = "Online (newsletters, blogs, AI news)"
ROW = re.compile(r"^\| (?!Outlet\b)(?!--)")
LINK = re.compile(r"\[[^\]]+\]\((https?://[^)]+)\)")
TOC_ENTRY = re.compile(r"\[([^\]]+)\]\(#[^)]+\)&nbsp;(\d+)")
# The card image the numbers are drawn on, wherever a README shows it: README.md in English, README.nl.md
# in Dutch and README.de.md in German, and any translation that copies the picture later. The words
# around the numbers are translated; the numbers are not.
CARD_ALT = re.compile(r'src="docs/card\.jpg"[^>]*?alt="([^"]*)"')


def card_alt_texts():
    """{file name: alt text} for every README whose card image carries an alt text."""
    out = {}
    for path in sorted(ROOT.glob("README*.md")):
        match = CARD_ALT.search(path.read_text(encoding="utf-8"))
        if match:
            out[path.name] = match.group(1)
    return out


def sections():
    """(heading, rows) for every article section, in file order. The Videos section at the end is not articles."""
    out, current = [], None
    for line in PRESS.read_text(encoding="utf-8").split("\n"):
        if line.strip() == "## Videos":
            break
        if line.startswith("## "):
            current = (line[3:].strip(), [])
            out.append(current)
        elif current is not None and ROW.match(line):
            current[1].append(line)
    return out


def intro():
    """The four numbers the PRESS.md intro states, as ints."""
    text = PRESS.read_text(encoding="utf-8").split("\n")[2]
    total = re.search(r"\*\*(\d+) articles\*\*", text)
    countries = re.search(r"(\d+) of them from outlets in (\d+) countries", text)
    latam = re.search(r"(\d+) from Spanish-language sites", text)
    online = re.search(r"(\d+) from newsletters", text)
    for name, match in (("total", total), ("countries", countries), ("Latin America", latam), ("online", online)):
        if match is None:
            raise AssertionError(f"docs/PRESS.md line 3 no longer states the {name} number")
    return {
        "total": int(total.group(1)),
        "country_articles": int(countries.group(1)),
        "countries": int(countries.group(2)),
        "latam": int(latam.group(1)),
        "online": int(online.group(1)),
    }


def toc():
    """{heading: number} from the one-line index under the intro."""
    line = next(l for l in PRESS.read_text(encoding="utf-8").split("\n") if l.startswith("[United States]"))
    return dict((name, int(n)) for name, n in TOC_ENTRY.findall(line))


def logos():
    source = FEATURED.read_text(encoding="utf-8")
    block = re.search(r"^LOGOS = \[(.*?)\]$", source, re.S | re.M).group(1)
    return re.findall(r'"([^"]+)"', block)


class PressCountsTest(unittest.TestCase):
    def setUp(self):
        self.sections = sections()
        self.counts = dict((name, len(rows)) for name, rows in self.sections)
        self.numbers = intro()
        self.index = toc()

    def test_index_matches_the_sections(self):
        self.assertEqual(list(self.index), [name for name, _ in self.sections])
        for name, rows in self.sections:
            with self.subTest(section=name):
                self.assertEqual(self.index[name], len(rows), f"{name}: index says {self.index[name]}, table has {len(rows)} rows")

    def test_total_matches_the_rows(self):
        self.assertEqual(sum(self.counts.values()), self.numbers["total"])

    def test_the_split_adds_up(self):
        countries = [n for n in self.counts if n not in (LATAM, ONLINE)]
        country_rows = sum(self.counts[n] for n in countries)
        with self.subTest(part="country sections"):
            self.assertEqual(len(countries), self.numbers["countries"])
        with self.subTest(part="country rows"):
            self.assertEqual(country_rows, self.numbers["country_articles"])
        with self.subTest(part="Latin America"):
            self.assertEqual(self.counts[LATAM], self.numbers["latam"])
        with self.subTest(part="online"):
            self.assertEqual(self.counts[ONLINE], self.numbers["online"])
        with self.subTest(part="sum"):
            self.assertEqual(country_rows + self.counts[LATAM] + self.counts[ONLINE], self.numbers["total"])

    def test_readme_alt_text_matches_the_list(self):
        alt = re.search(r'and (\d+) more: (\d+) articles from (\d+) countries', README.read_text(encoding="utf-8"))
        self.assertIsNotNone(alt, "the README card alt text no longer states 'and N more: N articles from N countries'")
        more, articles, countries = (int(g) for g in alt.groups())
        self.assertEqual(articles, self.numbers["total"], "README alt text and docs/PRESS.md disagree on the article count")
        self.assertEqual(countries, self.numbers["countries"], "README alt text and docs/PRESS.md disagree on the country count")
        self.assertEqual(more, self.numbers["total"] - len(logos()), "README alt text and the logos in tools/readme_featured.py disagree on the '+N more'")

    def test_every_readme_card_states_the_same_numbers(self):
        # The card is in README.md, README.nl.md and README.de.md, and all three alt texts state the
        # total, the country count and the "+N more" - in English, Dutch and German. Only the English
        # one was checked, so the press commit that took the list to 296 articles had to change the
        # Dutch alt text by hand as well (6cc2fdc9), and a PR written from docs/workflows/press.md left
        # the Dutch front page claiming an older count. The German page is the case the recipe still
        # missed: it carries the same picture, so the next article had to be added there by hand too.
        alts = card_alt_texts()
        self.assertLessEqual({"README.md", "README.nl.md", "README.de.md"}, set(alts),
                             "README.md, README.nl.md and README.de.md no longer show docs/card.jpg with an alt text")
        numbers = (("article total", self.numbers["total"]),
                   ("country count", self.numbers["countries"]),
                   ("'+N more'", self.numbers["total"] - len(logos())))
        for name, alt in alts.items():
            for what, number in numbers:
                with self.subTest(readme=name, number=what):
                    self.assertRegex(alt, rf"(?<!\d){number}(?!\d)",
                                     f"{name}'s card alt text does not state the {what} ({number}) "
                                     f"that docs/PRESS.md and tools/readme_featured.py carry")

    def test_the_press_recipe_names_every_place_the_counts_live(self):
        # The recipe is what a press PR follows, and the numbers live in every page that shows the card:
        # naming the English and Dutch pages only left the German one at the previous count, which this
        # file then failed on with nothing in the recipe to point at - the same gap #2132 closed for the
        # Dutch page. The list comes from the pages, so a translation that copies the card is covered
        # without this test being edited first.
        step = PRESS_RECIPE.read_text(encoding="utf-8").split("\n2. ")[1].split("\n3. ")[0]
        for name in sorted(card_alt_texts()):
            with self.subTest(readme=name):
                self.assertIn(name, step, f"docs/workflows/press.md step 2 does not name {name}")

    def test_the_card_source_draws_the_same_numbers(self):
        more = re.search(r'^MORE = "(.*)"$', FEATURED.read_text(encoding="utf-8"), re.M)
        self.assertIsNotNone(more, "tools/readme_featured.py no longer has a MORE line")
        expected = "+%d more  ·  %d articles from %d countries" % (
            self.numbers["total"] - len(logos()), self.numbers["total"], self.numbers["countries"])
        self.assertEqual(more.group(1), expected,
                         "tools/readme_featured.py is out of step with docs/PRESS.md; "
                         "run it and tools/readme_card.py to redraw docs/featured.png and docs/card.jpg")

    def test_every_row_is_a_linked_article(self):
        for name, rows in self.sections:
            for row in rows:
                with self.subTest(section=name, row=row[:60]):
                    self.assertEqual(len(LINK.findall(row)), 1, "row needs exactly one article link")
                    cells = [c.strip() for c in row.strip().strip("|").split("|")]
                    self.assertEqual(len(cells), 3, "row needs outlet, article and date")
                    self.assertTrue(cells[0] and cells[2], "outlet and date must not be empty")

    def test_no_article_is_listed_twice(self):
        seen = {}
        for name, rows in self.sections:
            for url in LINK.findall("\n".join(rows)):
                self.assertNotIn(url, seen, f"{url} is in both {seen.get(url)} and {name}")
                seen[url] = name


if __name__ == "__main__":
    unittest.main()

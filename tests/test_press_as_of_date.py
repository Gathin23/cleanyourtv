"""The day the story section says its press numbers were read, against the newest article they count.

Every front page that carries the story paragraph closes its coverage sentence with a parenthetical that
dates the three numbers: "(numbers as of 30 October 2026)" in README.md, "(Stand: 30. Oktober 2026)" in the
German page, "(stand 30 oktober 2026)" in the Dutch one, "(станом на 30 жовтня 2026 року)" in the Ukrainian
one, "(rakamlar 30 Ekim 2026 itibarıyla)" in the Turkish one and "(dados de 30 de outubro de 2026)" in the
Portuguese one. Nothing read any of them: tests/test_press_counts.py reads the three numbers and the totals
beside them, so the date could say anything.

It said a day too early on all six. docs/PRESS.md's intro states 412 articles from 54 countries and 229
videos, and 31 October is when those numbers were counted: 31092430 (30 October, 21:48) left the page at
"351 from 46 countries" and 188 videos, fe9ad320 (31 October, 00:01) took the videos to 229, and 8d5b54dc
(31 October, 13:52) and 57c00a40 (31 October, 14:08) brought the articles to 412 from 49 and then 54
countries. The newest article row in the same page is dated 31 October. A count read on 30 October cannot
hold an article published on the 31st, so each of the six pages offered a reader numbers dated a day older
than the newest article behind them.

The rule is the one the same page already applies to its video table: "views as of 30 Oct" may not be
earlier than the newest video row (tests/test_press_counts.py holds the two together, docs/workflows/press.md
step 3 says so). A page's date is held here the same way, down to 0 >= newest article row, and the clause is
read off every page whose story paragraph carries one - found by the words around it, not by a list of
names, so a translation that copies the sentence is read the day it lands.

A page that carries no such parenthetical is not judged: README.he.md writes its own "נכון ל־30 באוקטובר
2026" about the stars in the paragraph before, and its coverage sentence ends at the press link. Reading
only the parenthetical that follows the link is what keeps that sentence - a statement about a star count,
not about the press numbers - out of this check.

The recipe that a press PR follows said nothing about this day: docs/workflows/press.md step 2 listed the
places the counts live and step 3 stated the video table's "views as of" rule, so a contributor
bumping the counts left the date on the previous sweep and met this file with no line in the recipe to
point at. Step 2 states it now, and RecipeTest holds the recipe to naming this check, the rule, and every
page that dates its numbers today.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
PRESS = ROOT / "docs" / "PRESS.md"
PRESS_RECIPE = ROOT / "docs" / "workflows" / "press.md"

# The link every front page uses to point at the press list, in the story paragraph.
PRESS_LINK = re.compile(r"\[docs/PRESS\.md\]\([^)]*\)")
# The parenthetical that follows it, which dates the numbers: "(numbers as of 31 October 2026)".
CLAUSE = re.compile(r"\(([^()]{5,90})\)")
# A row's date cell in docs/PRESS.md is "31&nbsp;Oct"; a month name is spelled out in each translation.
DATE_CELL = re.compile(r"\|\s*(\d{1,2})&nbsp;([A-Za-z]{3})\s*\|")
ARTICLE_MONTHS = {name: number for number, name in enumerate(
    ("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"), 1)}

# The month names a page in each language can date the numbers with, spelled the way a date spells them:
# German "31. Oktober", Ukrainian "31 жовтня" (genitive), Turkish "31 Ekim", Portuguese "31 de outubro".
MONTHS = (
    ("January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
     "November", "December"),
    ("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober",
     "November", "Dezember"),
    ("januari", "februari", "maart", "april", "mei", "juni", "juli", "augustus", "september", "oktober",
     "november", "december"),
    ("січня", "лютого", "березня", "квітня", "травня", "червня", "липня", "серпня", "вересня", "жовтня",
     "листопада", "грудня"),
    ("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım",
     "Aralık"),
    ("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro",
     "novembro", "dezembro"),
)


def month_numbers():
    """{month name, casefolded: its number}, the six languages the front pages are written in."""
    out = {}
    for names in MONTHS:
        for number, name in enumerate(names, 1):
            out.setdefault(name.casefold(), number)
    return out


MONTH_NAMES = month_numbers()


def paragraphs(text):
    """The page's paragraphs, blank line by blank line."""
    return [block.strip() for block in re.split(r"\n\s*\n", text) if block.strip()]


def story_paragraph(text):
    """The paragraph that repeats the press numbers: it links docs/PRESS.md and names YouTube."""
    for paragraph in paragraphs(text):
        if PRESS_LINK.search(paragraph) and "YouTube" in paragraph:
            return paragraph
    return None


def date_clause(text):
    """The parenthetical that follows the press link in the story paragraph, or None.

    Only the text just after the link is read. A page may write "as of" elsewhere in the story - the
    Hebrew one dates the stars it had on 30 October in the sentence before - and that is not this date.
    """
    paragraph = story_paragraph(text)
    if paragraph is None:
        return None
    link = PRESS_LINK.search(paragraph)
    if link is None:  # story_paragraph() only answers a paragraph that holds the link
        return None
    after = paragraph[link.end():]
    found = CLAUSE.search(after[:120])
    return found.group(1) if found else None


def clause_date(clause):
    """(year, month, day) the clause names, or None when it names no date this check can read.

    Read around a month name: the day is the last number before it and the year the first four-digit
    number after it, which covers "31 October 2026", "31. Oktober 2026", "станом на 31 жовтня 2026 року",
    "rakamlar 31 Ekim 2026 itibarıyla" and "dados de 31 de outubro de 2026" without a rule per language.
    """
    if not clause:
        return None
    low = clause.casefold()
    found = []
    for word, month in MONTH_NAMES.items():
        match = re.search(rf"(?<!\w){re.escape(word)}(?!\w)", low)
        if match:
            found.append((match.start(), match.end(), month))
    for start, end, month in sorted(found):
        day = re.search(r"(\d{1,2})\D{0,8}$", low[:start])
        year = re.search(r"\d{4}", low[end:])
        if day and year:
            return (int(year.group()), month, int(day.group(1)))
    return None


def dated_pages(root=ROOT):
    """[(name, date)] for every front page whose story dates the numbers, in name order."""
    out = []
    for path in sorted(Path(root).glob("README*.md")):
        date = clause_date(date_clause(path.read_text(encoding="utf-8")))
        if date is not None:
            out.append((path.name, date))
    return out


def article_text(text):
    """docs/PRESS.md up to the Videos section, which is the articles' half of the page."""
    lines = text.splitlines()
    for number, line in enumerate(lines):
        if line.startswith("## Videos"):
            return "\n".join(lines[:number])
    return text


def newest_article(text):
    """(month, day) of the newest article row, or None when the page has no dated row."""
    rows = []
    for day, month in DATE_CELL.findall(article_text(text)):
        if month in ARTICLE_MONTHS:
            rows.append((ARTICLE_MONTHS[month], int(day)))
    return max(rows) if rows else None


def stale_pages(root=ROOT, press=None):
    """[(name, its date, the newest article row)] for every dated page behind that row.

    The rows in docs/PRESS.md carry a day and a month, no year, so the comparison is the month and the
    day: a page dated in another year is a typo a reader sees, not one this check can read.
    """
    press = Path(press) if press is not None else Path(root) / "docs" / "PRESS.md"
    newest = newest_article(press.read_text(encoding="utf-8"))
    if newest is None:
        return []
    out = []
    for name, (year, month, day) in dated_pages(root):
        if (month, day) < newest:
            out.append((name, (year, month, day), newest))
    return out


class TreeTest(unittest.TestCase):
    """The six pages on main today, and the day their numbers were counted."""

    def test_no_page_dates_the_press_numbers_before_the_newest_article(self):
        behind = stale_pages()
        named = "; ".join(f"{name} says {day}/{month}/{year}, the newest article row is {newest[1]}/{newest[0]}"
                          for name, (year, month, day), newest in behind)
        self.assertEqual([], behind,
                         f"{named}: a count read that day cannot hold an article published later, so the "
                         f"page's date has to move with the sweep")

    def test_the_pages_that_date_the_numbers_today(self):
        self.assertEqual(
            {"README.md", "README.de.md", "README.nl.md", "README.pt-BR.md", "README.tr.md", "README.uk.md"},
            {name for name, _ in dated_pages()},
            "the front pages that close the coverage sentence with a date; a translation that copies the "
            "clause is read without being added here, and one that stops dating its numbers is a change")

    def test_the_dated_pages_name_a_month_the_press_page_uses(self):
        for name, (year, month, day) in dated_pages():
            self.assertIn(month, set(ARTICLE_MONTHS.values()), f"{name}'s month is one the press page uses")
            self.assertEqual(2026, year,
                             f"{name} dates the press numbers in a year the lists do not cover")


class RecipeTest(unittest.TestCase):
    """docs/workflows/press.md tells a press PR to move the date, as it tells it to move the counts."""

    def recipe_step(self):
        """Step 2 of the recipe, the one that lists the places the numbers live, on one line.

        The step wraps at about 100 columns, so a phrase it states can straddle two lines; the words are
        what a reader takes from it, not where the break fell.
        """
        text = PRESS_RECIPE.read_text(encoding="utf-8")
        return " ".join(text.split("\n2. ")[1].split("\n3. ")[0].split())

    def test_the_recipe_names_the_check_that_reads_the_date(self):
        # The date landed with this file and the recipe was not told: step 2 sent a contributor to the
        # places the counts live and said nothing about the parenthetical, so following it to the letter
        # left the day on the previous sweep and this file red with no line in the recipe to point at.
        step = self.recipe_step()
        self.assertTrue("tests/test_press_as_of_date.py" in step,
                        "docs/workflows/press.md step 2 does not name the check that reads the dated clause")

    def test_the_recipe_states_the_rule_the_day_is_held_to(self):
        step = self.recipe_step()
        self.assertTrue("newest article row" in step,
                        "docs/workflows/press.md step 2 does not say the day may not be earlier than the "
                        "newest article row in docs/PRESS.md")

    def test_the_recipe_names_every_page_that_dates_its_numbers(self):
        # Same rule as the counts beside it: the pages come from the tree, so a translation that copies
        # the dated clause is named here without this test being edited first.
        step = self.recipe_step()
        for name, _ in dated_pages():
            with self.subTest(readme=name):
                self.assertTrue(name in step,
                                f"docs/workflows/press.md step 2 does not name {name}, which dates its numbers")


class ReaderTest(unittest.TestCase):
    """The clause is read in every language the pages write it in, and only where it dates the numbers."""

    STORY = "The coverage: 412 articles from 54 countries and 229 videos, all in [docs/PRESS.md](docs/PRESS.md)"

    def test_each_page_s_clause_parses_to_its_own_day(self):
        expected = {
            "README.md": (2026, 10, 31),
            "README.de.md": (2026, 10, 31),
            "README.nl.md": (2026, 10, 31),
            "README.uk.md": (2026, 10, 31),
            "README.tr.md": (2026, 10, 31),
            "README.pt-BR.md": (2026, 10, 31),
        }
        for name, date in expected.items():
            text = (ROOT / name).read_text(encoding="utf-8")
            self.assertEqual(date, clause_date(date_clause(text)), f"{name}'s dated clause")

    def test_a_clause_in_each_language_reads_the_same_way(self):
        for clause, date in (
            ("numbers as of 31 October 2026", (2026, 10, 31)),
            ("Stand: 31. Oktober 2026", (2026, 10, 31)),
            ("stand 31 oktober 2026", (2026, 10, 31)),
            ("станом на 31 жовтня 2026 року", (2026, 10, 31)),
            ("rakamlar 31 Ekim 2026 itibarıyla", (2026, 10, 31)),
            ("dados de 31 de outubro de 2026", (2026, 10, 31)),
        ):
            self.assertEqual(date, clause_date(clause), clause)

    def test_a_page_that_dates_something_else_is_not_read(self):
        # README.he.md's story opens "נכון ל־30 באוקטובר 2026" about its star count and closes on the
        # press link with no parenthetical: no date of the press numbers, so nothing to hold.
        text = ("# CleanYourTV\n\nנכון ל־30 באוקטובר 2026 הוא צבר יותר מ־46 אלף כוכבים.\n"
                "סיקור בתקשורת: 412 כתבות ו־229 סרטונים ב־YouTube. [docs/PRESS.md](docs/PRESS.md)\n\n## Contributing\n")
        self.assertIsNone(date_clause(text))
        self.assertIsNone(clause_date(date_clause(text)))

    def test_an_unknown_month_is_not_read_as_a_date(self):
        self.assertIsNone(clause_date("numbers as of 31 Octember 2026"))

    def test_a_page_a_day_behind_the_newest_row_is_reported(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            (root / "docs").mkdir()
            (root / "docs" / "PRESS.md").write_text(
                "# Press\n\n412 articles.\n\n| Outlet | [Headline](https://example.com/a) | 31&nbsp;Oct |\n"
                "\n## Videos\n\n| Channel | [Title](https://example.com/v) | 28&nbsp;Oct | Views |\n",
                encoding="utf-8")
            (root / "README.md").write_text(
                "# CleanYourTV\n\nThe lists now cover 450 TV models from 122 brands. The coverage: 412 articles "
                "from 54 countries and 229 videos on YouTube and Bilibili, all in [docs/PRESS.md](docs/PRESS.md) "
                "(numbers as of 30 October 2026).\n", encoding="utf-8")
            self.assertEqual([("README.md", (2026, 10, 30), (10, 31))], stale_pages(root))

    def test_the_same_page_a_day_later_is_not_reported(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            (root / "docs").mkdir()
            (root / "docs" / "PRESS.md").write_text(
                "# Press\n\n| Outlet | [Headline](https://example.com/a) | 31&nbsp;Oct |\n"
                "\n## Videos\n\n| Channel | [Title](https://example.com/v) | 28&nbsp;Oct | Views |\n",
                encoding="utf-8")
            (root / "README.md").write_text(
                "# CleanYourTV\n\nThe coverage: 412 articles from 54 countries and 229 videos on YouTube and "
                "Bilibili, all in [docs/PRESS.md](docs/PRESS.md) (numbers as of 31 October 2026).\n",
                encoding="utf-8")
            self.assertEqual([], stale_pages(root))

    def test_the_video_rows_are_not_the_articles_half(self):
        text = ("# Press\n\n| Outlet | [Headline](https://example.com/a) | 29&nbsp;Oct |\n"
                "\n## Videos\n\n| Channel | [Title](https://example.com/v) | 31&nbsp;Oct | Views |\n")
        self.assertEqual((10, 29), newest_article(text))

"""SHOWCASE.md opens with "N projects so far.", and nothing was counting the rows.

The line was right at 70 rows. Then four projects landed in three separate merges - the HAAM HDMI
visibility fork, the Bluetooth pill fork and Tellyscope - and one of the three bumped the number
(70 -> 71) while the others did not, so the page offered a reader four projects fewer than it holds.
README.md's "Built something on top?" row spells the same number and had drifted on its own: it said
seventy while the page said 71.

These tests read the number out of the page rather than copying it into the test, count the project
rows the way the page lays them out (a table row whose first cell is a project link), and fail when
the two disagree, naming both numbers. The spelled forms are read through word parsers, so a row
reworded around the number still counts. No other prose is matched.

README.pt-BR.md carries the same row in Portuguese ("que já reúne cento e noventa e três projetos"),
and nothing read it: it was translated when SHOWCASE.md held 193 rows, stood at 193 while the page
moved to 218, and the suite stayed green - so a reader of the Portuguese front page was offered 25
projects fewer than the showcase holds. Every page in COUNT_PAGES is read now, with the parser for
its own language, so a translation that states the number is a page this test judges rather than one
it silently skips.

Four more translated front pages carried the row and were read by nothing, because they write the
number another way: README.nl.md in digits ("al 121 projecten"), README.de.md in one German compound
("hundertvierundvierzig Projekte"), README.uk.md in Ukrainian words ("сто шістдесят три проєкти") and
README.uz.md in Uzbek words with the -ta classifier ("ikki yuz oʻn ikkita loyiha"). The showcase held
228 by then, so those readers were offered 121, 144, 163 and 212 projects. German compounds and the
Uzbek classifier are not words a table like this one can hold (the same reason README.uz.md's theme
count moved to digits), so those four pages write the number in digits and are read with
digits_number() - language-neutral, and the classifier may sit between the number and its noun.

README.fr.md is the seventh, and it landed the day after this table was written: the French front
page arrived with the row already in digits ("234 projets"), the shape the four pages above write, so
it needed no parser - only a line in COUNT_PAGES, which it did not get. The fold-in that followed
bumped its row from 230 to 234 by hand while nothing was reading it, so the page agrees today and is
free to drift on the next project: the six pages in the table bumped, this one left at 234, and a
French reader offered the old number with the suite green. The row is read now, and the floor below
counts seven pages rather than six.

Adding a project therefore means bumping every page in COUNT_PAGES, which is what the failure
message says.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SHOWCASE = ROOT / "SHOWCASE.md"
README = ROOT / "README.md"

# The page held 50+ rows when this test landed. A parser that silently matches nothing (a changed
# row layout, a reordered page) must not read as "0 == 0, all good".
MIN_ROWS = 50

ONES = {
    "one": 1, "two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8,
    "nine": 9, "ten": 10, "eleven": 11, "twelve": 12, "thirteen": 13, "fourteen": 14,
    "fifteen": 15, "sixteen": 16, "seventeen": 17, "eighteen": 18, "nineteen": 19,
}
TENS = {
    "twenty": 20, "thirty": 30, "forty": 40, "fifty": 50, "sixty": 60, "seventy": 70,
    "eighty": 80, "ninety": 90,
}


def project_rows(lines):
    """[(line number, line)] for every project row: a "|" row whose first cell is a link."""
    found = []
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if re.match(r"^\|\s*\[", stripped):
            found.append((number, stripped))
    return found


def stated_number(lines):
    """The number in "N projects so far.", or None when the page does not carry the line."""
    for line in lines:
        match = re.match(r"^(\d+)\s+projects so far\.$", line.strip())
        if match:
            return int(match.group(1))
    return None


def spelled_number(word):
    """"seventy-five" -> 75, "seventy" -> 70, "twenty one" -> 21, "a hundred and four" -> 104,
    anything else -> None."""
    text = word.lower().replace("-", " ").strip()
    parts = [p for p in text.split() if p != "and"]
    # The page passed a hundred rows in Round W: "a hundred", "one hundred and four", "a hundred and twenty".
    if len(parts) >= 2 and parts[0] in ("a", "one", "two") and parts[1] == "hundred":
        base = 200 if parts[0] == "two" else 100
        if len(parts) == 2:
            return base
        rest = spelled_number(" ".join(parts[2:]))
        return None if rest is None else base + rest
    if not parts or len(parts) > 2:
        return None
    if len(parts) == 1:
        return TENS.get(parts[0]) or ONES.get(parts[0])
    if parts[0] in TENS and parts[1] in ONES and ONES[parts[1]] < 10:
        return TENS[parts[0]] + ONES[parts[1]]
    return None


# Portuguese number words, for the front page that spells the count in Portuguese. English needs
# "two hundred and four"; Portuguese is additive ("duzentos e dezoito") and "cento" is the hundred
# that takes a tail ("cento e noventa e três"), so a sum is the whole grammar these pages use.
PT_ONES = {
    "um": 1, "uma": 1, "dois": 2, "duas": 2, "três": 3, "quatro": 4, "cinco": 5, "seis": 6,
    "sete": 7, "oito": 8, "nove": 9, "dez": 10, "onze": 11, "doze": 12, "treze": 13, "catorze": 14,
    "quatorze": 14, "quinze": 15, "dezesseis": 16, "dezasseis": 16, "dezessete": 17,
    "dezassete": 17, "dezoito": 18, "dezenove": 19,
}
PT_TENS = {
    "vinte": 20, "trinta": 30, "quarenta": 40, "cinquenta": 50, "sessenta": 60, "setenta": 70,
    "oitenta": 80, "noventa": 90,
}
PT_HUNDREDS = {
    "cem": 100, "cento": 100, "duzentos": 200, "duzentas": 200, "trezentos": 300,
    "trezentas": 300, "quatrocentos": 400, "quatrocentas": 400, "quinhentos": 500,
    "quinhentas": 500, "seiscentos": 600, "seiscentas": 600, "setecentos": 700,
    "setecentas": 700, "oitocentos": 800, "oitocentas": 800, "novecentos": 900,
    "novecentas": 900,
}


def spelled_number_pt(text):
    """"cento e noventa e três" -> 193, "duzentos e dezoito" -> 218, anything else -> None.

    No "mil": a count past 999 is a parser to extend, and an unreadable number fails loudly where a
    guessed one would silently agree with nothing.
    """
    total = 0
    for word in text.lower().replace("-", " ").split():
        if word == "e":
            continue
        if word in PT_HUNDREDS:
            total += PT_HUNDREDS[word]
        elif word in PT_TENS:
            total += PT_TENS[word]
        elif word in PT_ONES:
            total += PT_ONES[word]
        else:
            return None
    return total or None


def digits_number(text):
    """"228" -> 228, "228 ta" -> 228 (the Uzbek classifier sits between the number and its noun),
    anything else -> None.

    The pages that write the count in digits are read with this: a German compound
    ("hundertvierundvierzig") or an Uzbek classified numeral is not a word a table can hold, and a
    number in digits is the same in every language. `\\d{1,4}` keeps a thousands separator out, so
    "1.4" or "1,228" is not read as a count; a count past 9999 is a parser to widen, and an
    unreadable one fails at the page's own test rather than agreeing with nothing.
    """
    match = re.fullmatch(r"(\d{1,4})(?:\s+[^\W\d_]+)?", text.strip())
    return int(match.group(1)) if match else None


def spelled_count(lines, word, reader, most=6):
    """(number, the phrase it was read from) for a page's "<number> <word>" phrase, or (None, None).

    The words directly before the word are tried longest first and then shorter, so "twenty one"
    needs both, "seventy-five" is one, "cento e noventa e três" is five, and the sentence around the
    number can be reworded. `[^\\W_]+` is every letter or digit, accented ones and numerals included,
    and a hyphen keeps the words it joins together, so each page can spell the number in its own
    language and a page that writes it in digits hands "228" to its reader as one word.
    """
    for line in lines:
        for match in re.finditer(rf"{word}\b", line):
            words = re.findall(r"[^\W_]+(?:-[^\W_]+)*", line[: match.start()].rstrip())
            for take in range(most, 0, -1):
                if len(words) >= take:
                    candidate = " ".join(words[-take:])
                    number = reader(candidate)
                    if number is not None:
                        return number, candidate
    return None, None


def readme_spelled(lines):
    """README.md's "<words> projects", read with the English parser."""
    return spelled_count(lines, "projects", spelled_number)


def pt_spelled(lines):
    """README.pt-BR.md's "<words> projetos", read with the Portuguese parser."""
    return spelled_count(lines, "projetos", spelled_number_pt)


# The pages that state the count, and the word each states it before. SHOWCASE.md's own
# "N projects so far." line is the number the rows are counted from; every page here must agree with
# it, and adding a project means bumping all of them. A page that spells the number is read with its
# language's parser; a page that writes it in digits (which is what a classifier or a compound would
# otherwise sit in the way of) is read with digits_number().
COUNT_PAGES = {
    "README.md": ("projects", spelled_number),
    "README.fr.md": ("projets", digits_number),
    "README.pt-BR.md": ("projetos", spelled_number_pt),
    "README.de.md": ("Projekte", digits_number),
    "README.nl.md": ("projecten", digits_number),
    "README.es.md": ("proyectos", digits_number),
    "README.uk.md": ("проєктів", digits_number),
    "README.uz.md": ("loyiha", digits_number),
}


def audit(showcase_lines):
    """(stated, counted, error) - error is None when the page and its rows agree."""
    counted = len(project_rows(showcase_lines))
    stated = stated_number(showcase_lines)
    if stated is None:
        return None, counted, "SHOWCASE.md carries no 'N projects so far.' line"
    if counted < MIN_ROWS:
        return stated, counted, (
            f"only {counted} project rows were read, fewer than the {MIN_ROWS} the page is known to "
            "hold - the row layout changed and this check is reading nothing"
        )
    if stated != counted:
        return stated, counted, (
            f"SHOWCASE.md says {stated} projects and its tables hold {counted} rows: bump the number "
            "(and README.md's spelled one) when you add a project"
        )
    return stated, counted, None


class ShowcaseProjectCount(unittest.TestCase):
    def setUp(self):
        self.showcase = SHOWCASE.read_text(encoding="utf-8").splitlines()
        self.readme = README.read_text(encoding="utf-8").splitlines()

    def test_the_page_states_the_number_of_rows_it_holds(self):
        stated, counted, error = audit(self.showcase)
        self.assertIsNone(error, error)
        self.assertEqual(stated, counted)

    def test_the_count_line_sits_in_the_intro_before_the_first_section(self):
        headings = [n for n, line in enumerate(self.showcase) if line.startswith("## ")]
        counted_at = [
            n for n, line in enumerate(self.showcase) if stated_number([line]) is not None
        ]
        self.assertEqual(len(counted_at), 1, "expected exactly one 'N projects so far.' line")
        self.assertLess(counted_at[0], headings[0], "the count line belongs above the first section")

    def test_every_page_that_states_the_count_agrees_with_the_rows(self):
        counted = len(project_rows(self.showcase))
        self.assertGreaterEqual(
            len(COUNT_PAGES), 7,
            "fewer pages are read than the seven front pages that state the count: a page dropped from "
            "COUNT_PAGES stops being judged, and its number is free to drift again",
        )
        for page, (word, reader) in COUNT_PAGES.items():
            with self.subTest(page=page):
                lines = (ROOT / page).read_text(encoding="utf-8").splitlines()
                number, phrase = spelled_count(lines, word, reader)
                self.assertIsNotNone(
                    number, f"{page} states the project count and none was read"
                )
                self.assertEqual(
                    number,
                    counted,
                    f"{page} says '{phrase} {word}' and SHOWCASE.md holds {counted} rows",
                )


class TheCheckerItself(unittest.TestCase):
    """The repo-wide tests above are only worth their failures if these hold."""

    PAGE = [
        "71 projects so far.",
        "",
        "## Forks of the launcher",
        "",
        "| Project | What it is | Built on | By |",
        "|---|---|---|---|",
        "| [One](https://example.com/one) | A fork | TCL G08 | [me](https://example.com) |",
        "| [Two](https://example.com/two) | A fork | TCL G08 | [me](https://example.com) |",
    ]

    def test_a_short_count_is_reported_with_both_numbers(self):
        _, counted, error = audit(self.PAGE + ["| [Three](https://example.com/3) | x | y | z |"] * 48)
        self.assertEqual(counted, 50)
        self.assertIn("says 71", error)
        self.assertIn("hold 50", error)

    def test_an_agreeing_page_is_quiet(self):
        page = ["50 projects so far."] + self.PAGE[2:] + [
            f"| [P{i}](https://example.com/{i}) | x | y | z |" for i in range(48)
        ]
        stated, counted, error = audit(page)
        self.assertIsNone(error)
        self.assertEqual((stated, counted), (50, 50))

    def test_a_page_without_the_line_is_reported_rather_than_read_as_zero(self):
        _, _, error = audit(self.PAGE[2:])
        self.assertIn("no 'N projects so far.' line", error)

    def test_a_header_row_and_the_brand_index_are_not_project_rows(self):
        page = self.PAGE + ["", "- **TCL:** [One](https://example.com/one)"]
        self.assertEqual(len(project_rows(page)), 2)

    def test_a_number_in_prose_is_not_the_count_line(self):
        self.assertIsNone(stated_number(["About 71 projects so far."]))

    def test_spelled_numbers_are_read(self):
        self.assertEqual(spelled_number("seventy"), 70)
        self.assertEqual(spelled_number("seventy-five"), 75)
        self.assertEqual(spelled_number("Seventy Five"), 75)
        self.assertEqual(spelled_number("twenty one"), 21)
        self.assertIsNone(spelled_number("many"))
        self.assertIsNone(spelled_number("seventy five three"))

    def test_readme_words_are_read_and_an_unreadable_one_is_reported(self):
        self.assertEqual(readme_spelled(["where seventy-five projects already live"]), (75, "seventy-five"))
        self.assertEqual(readme_spelled(["where seventy projects already live"]), (70, "seventy"))
        self.assertEqual(readme_spelled(["where a hundred and four projects already live"]), (104, "a hundred and four"))
        self.assertEqual(readme_spelled(["where one hundred and twenty-one projects live"]), (121, "one hundred and twenty-one"))
        self.assertEqual(readme_spelled(["a row with no count at all"]), (None, None))

    def test_portuguese_words_are_read(self):
        self.assertEqual(spelled_number_pt("cento e noventa e três"), 193)
        self.assertEqual(spelled_number_pt("Duzentos e dezoito"), 218)
        self.assertEqual(spelled_number_pt("duzentos e trinta e um"), 231)
        self.assertEqual(spelled_number_pt("cem"), 100)
        self.assertIsNone(spelled_number_pt("reúne"))
        self.assertIsNone(spelled_number_pt("cento e many"))

    def test_the_portuguese_row_is_read_and_a_number_before_the_word_is_not(self):
        row = "que já reúne cento e noventa e três projetos organizados em telas iniciais"
        self.assertEqual(pt_spelled([row]), (193, "cento e noventa e três"))
        self.assertEqual(pt_spelled(["um tema com três projetos"]), (3, "três"))
        self.assertEqual(pt_spelled(["Esses aplicativos são projetos independentes"]), (None, None))
        # A number that does not touch "projetos" is not the count.
        self.assertEqual(pt_spelled(["193 telas, e os projetos vêm depois"]), (None, None))

    def test_a_count_in_digits_is_read(self):
        self.assertEqual(digits_number("228"), 228)
        self.assertEqual(digits_number(" 121 "), 121)
        # The Uzbek -ta classifier sits between the number and its noun: "228 ta loyiha".
        self.assertEqual(digits_number("228 ta"), 228)
        self.assertIsNone(digits_number("hundertvierundvierzig"),
                          "a spelled number read as a digit count")
        self.assertIsNone(digits_number("1.4"), "a number with a separator read as a count")
        self.assertIsNone(digits_number(""))

    def test_a_row_in_digits_is_read_and_the_same_row_in_words_is_not(self):
        nl = "| **Iets gebouwd?** | Daar staan al 228 projecten, ingedeeld in startschermen |"
        self.assertEqual(spelled_count([nl], "projecten", digits_number), (228, "228"))
        uz = "| **Ustiga?** | TV brendi boʻyicha indekslangan 228 ta loyiha allaqachon yashaydi. |"
        self.assertEqual(spelled_count([uz], "loyiha", digits_number), (228, "228 ta"))
        # Why the four pages write digits: the Ukrainian row the digits reader cannot see is the
        # shape that offered a reader 163 projects while SHOWCASE.md held 228.
        self.assertEqual(
            spelled_count(["де вже живуть сто шістдесят три проєктів"], "проєктів", digits_number),
            (None, None),
        )

    def test_the_french_row_is_read(self):
        """README.fr.md's row as the page writes it: the count in digits, before "projets"."""
        row = ("| **Vous avez construit quelque chose par-dessus ?** | Ajoutez-le à "
               "[SHOWCASE.md](SHOWCASE.md), qui rassemble déjà 234 projets, classés en écrans |")
        self.assertEqual(spelled_count([row], "projets", digits_number), (234, "234"))
        # The other sentence on that page that names "projets" states no number, so it is not a claim.
        self.assertEqual(
            spelled_count(["Ces applications sont des projets indépendants"], "projets", digits_number),
            (None, None),
        )


if __name__ == "__main__":
    unittest.main()

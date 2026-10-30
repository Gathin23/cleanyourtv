"""The front page's theme count matches the themes/ folder, on every front page.

README.md's feature table answers "what can I get without a rebuild?" with a themes row, and that
row carries a number: "eleven ship in themes/". The number is a claim about the tree, and nothing read
it: it was written when themes/ held five folders (aurora, default, dussehra, scenes, six-columns)
and stayed at five while glass and ruska were added, so the page offered a reader two fewer looks
than the repo ships.

The count is on more than one front page. README.de.md and README.nl.md carry the same feature row
in their own words ("Zehn liegen in themes/", "staan tien thema's"), and nothing read those: on
main both still say ten while themes/ holds eleven, so the German and Dutch pages offer a reader one
fewer look than the English one does - the gap #2132 and #2461 closed for the press counts, on the
number a theme PR moves.

README.uk.md is the fourth such page, and it is the case the table below missed: the Ukrainian page
landed with the row in its own words ("У themes/ постачається чотирнадцять"), while the table carried
English, German and Dutch only, so claims() read nothing on it and the pinned set of pages that state
a count stayed the three it named. A page left at the previous count - the failure the German and
Dutch pages were - would have passed here without a word.

The number is never copied here. A page's count is read out of the sentence it writes it in, through
one table of the words these four languages state a number in, so a theme folder added by a PR moves
this test with that page, and a page that starts stating the count is held the same way. A word the
table does not carry names the page that wrote it rather than reading as no claim at all; one is left
out of the table on purpose, since "een" and "ein" are also the articles these languages put before a
noun, and a single-theme repo is not a thing. A fifth language means a fifth line in that table: the
last page added did not fail here, it simply became a page this file could not see.

That one table is what README.md's row is read through too. The row had a twelve-word table of its own
while the sentences were read through one that carries twenty, and the two agreed only while themes/
held twelve: the thirteenth theme, "thirteen ship in themes/", is what docs/workflows/theme.md step 5
tells its author to write, and the row's reader answered "the Themes row states no count" for it - a
page that stated its count correctly, refused by the check that was meant to hold it. The row keeps one
word the shared table leaves out, "one", because its number is anchored to "ship".

A theme folder is one with a config.json - the same rule tests/test_themes.py uses, and the same one
the launcher pushes (tools/theme.sh reads a folder's config.json).

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

# Every feature row that names themes/ and states how many ship there, whatever language it is in:
# English "eleven ship in", German "Zehn liegen in", Dutch "staan tien thema's", Ukrainian
# "постачається чотирнадцять". The words come from the four languages the front pages are written in
# today, two to twenty each; one is left out because "een" and "ein" are also the articles those
# languages put before a noun, so an English "one" would read a Dutch sentence as a claim of one theme.
NUMBER_WORDS = {"two": 2, "three": 3, "four": 4, "five": 5, "six": 6, "seven": 7, "eight": 8,
                "nine": 9, "ten": 10, "eleven": 11, "twelve": 12,
                "thirteen": 13, "fourteen": 14, "fifteen": 15, "sixteen": 16,
                "seventeen": 17, "eighteen": 18, "nineteen": 19, "twenty": 20,
                "zwei": 2, "drei": 3, "vier": 4, "fünf": 5, "sechs": 6, "sieben": 7, "acht": 8,
                "neun": 9, "zehn": 10, "elf": 11, "zwölf": 12, "dreizehn": 13, "vierzehn": 14,
                "fünfzehn": 15, "sechzehn": 16, "siebzehn": 17, "achtzehn": 18, "neunzehn": 19,
                "zwanzig": 20,
                "twee": 2, "drie": 3, "vijf": 5, "zes": 6, "zeven": 7, "negen": 9, "tien": 10,
                "twaalf": 12, "dertien": 13, "veertien": 14, "vijftien": 15, "zestien": 16,
                "zeventien": 17, "achttien": 18, "negentien": 19, "twintig": 20,
                # Ukrainian, README.uk.md's row as it is written today. The apostrophe is the page's
                # own: п'ять, дев'ять and п'ятнадцять carry a straight one there.
                "два": 2, "три": 3, "чотири": 4, "п'ять": 5, "шість": 6, "сім": 7, "вісім": 8,
                "дев'ять": 9, "десять": 10, "одинадцять": 11, "дванадцять": 12, "тринадцять": 13,
                "чотирнадцять": 14, "п'ятнадцять": 15, "шістнадцять": 16, "сімнадцять": 17,
                "вісімнадцять": 18, "дев'ятнадцять": 19, "двадцять": 20}
# The row is read through that same table, so a thirteenth theme is "thirteen ship in themes/" and not
# a row that states nothing. A second table for the row - one that stopped at twelve while this one
# carried twenty - is what made the thirteenth theme fail as "the Themes row states no count".
# "one" is the one word the table leaves out and the row can carry, because COUNT anchors the number
# to "ship": "one ship in themes/" is a count where a bare "een"/"ein" on a page line is an article.
COUNT_WORDS = {"one": 1, **NUMBER_WORDS}
THEMES_LINK = "[`themes/`](themes/)"


def word_alternation(words):
    """The words as one regex alternative, longest first so "fourteen" is not read as "four"."""
    return "|".join(sorted((re.escape(w) for w in words), key=len, reverse=True))


COUNT = re.compile(r"\b(\d+|" + word_alternation(COUNT_WORDS) + r")\s+ship\b", re.IGNORECASE)
NUMBER = re.compile(r"\b(\d+|" + word_alternation(NUMBER_WORDS) + r")\b", re.IGNORECASE)


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
    return int(token) if token.isdigit() else COUNT_WORDS[token]


def theme_folders(root=THEMES):
    """Every folder in themes/ that carries a config.json, i.e. every pushable theme."""
    if not root.is_dir():
        return []
    return sorted(p.name for p in root.iterdir() if p.is_dir() and (p / "config.json").is_file())


def front_pages(root=ROOT):
    """Every front page: README.md and the translations beside it."""
    return sorted(p for p in root.glob("README*.md") if p.is_file())


def claims(text):
    """[(number, line)] for every line that names themes/ and states how many ship there.

    A page's sentence puts the count either side of the link ("eleven ship in themes/", "staan tien
    thema's"), so the line is read as a whole and every number word on it is taken as a claim. A line
    that names themes/ without a number - the Tools table row that pushes a folder from themes/ - is
    not one.
    """
    found = []
    for line in text.split("\n"):
        if THEMES_LINK not in line:
            continue
        values = {int(t) if t.isdigit() else NUMBER_WORDS[t.lower()] for t in NUMBER.findall(line)}
        if values:
            found.append((values, line))
    return found


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

    def test_every_front_page_names_the_same_themes(self):
        shipped = theme_folders()
        stating = []
        for page in front_pages():
            for values, line in claims(page.read_text(encoding="utf-8")):
                with self.subTest(page=page.name):
                    self.assertEqual(len(values), 1,
                                     f"{page.name} states more than one number beside the themes/ "
                                     f"link, so which one is the count cannot be read: {line}")
                    claimed = next(iter(values))
                    stating.append(page.name)
                    self.assertEqual(
                        claimed, len(shipped),
                        f"{page.name} says {claimed} themes and themes/ holds {len(shipped)}: "
                        f"{', '.join(shipped)}")
        # The walk above passes while finding nothing, which is how the German and Dutch rows went
        # stale in the first place: these four pages state the count today, so one that stops
        # saying it fails here rather than quietly leaving the check. README.uk.md joined them with
        # the Ukrainian words above; before that it stated its count and this list still read three.
        self.assertEqual(sorted(set(stating)),
                         ["README.de.md", "README.md", "README.nl.md", "README.uk.md"],
                         "the front pages that state the theme count are not the four that did: "
                         f"{sorted(set(stating))}")


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

    def test_a_translated_sentence_is_read(self):
        german = "| **Themes** | ... Elf liegen in [`themes/`](themes/) |"
        dutch = "| **Thema's** | In [`themes/`](themes/) staan elf thema's |"
        self.assertEqual([({11}, german)], claims(german))
        self.assertEqual([({11}, dutch)], claims(dutch))

    def test_a_ukrainian_sentence_is_read(self):
        """README.uk.md's row as the page writes it: the count follows the link, then the gloss."""
        ukrainian = ("| **Теми** | Тло, шпалери й розкладка з `config.json`, надісланого через Wi-Fi, "
                     "без перезбирання. У [`themes/`](themes/) постачається чотирнадцять, включно з "
                     "вбудованою `default/` |")
        self.assertEqual([({14}, ukrainian)], claims(ukrainian))

    def test_the_ukrainian_fourteen_is_not_read_as_the_four_it_starts_with(self):
        # "чотирнадцять" opens with "чотири", "тринадцять" with "три", "дванадцять" with "два": the
        # longest-first alternation is what keeps a count of twelve from being read as two.
        for word, number in (("чотирнадцять", 14), ("тринадцять", 13), ("дванадцять", 12)):
            with self.subTest(word=word):
                line = f"| **Теми** | У [`themes/`](themes/) постачається {word} |"
                self.assertEqual([({number}, line)], claims(line))

    def test_a_digits_count_is_read_in_any_sentence(self):
        self.assertEqual([({12}, "| **Themes** | 12 liegen in [`themes/`](themes/) |")],
                         claims("| **Themes** | 12 liegen in [`themes/`](themes/) |"))

    def test_a_row_that_does_not_state_a_count_is_not_a_claim(self):
        tools = "| [`theme.sh`](tools/theme.sh) | Push a folder from [`themes/`](themes/) |"
        self.assertEqual([], claims(tools))
        self.assertEqual([], claims("No themes yet."))

    def test_a_page_stating_one_number_beside_the_link_is_the_count(self):
        page = ("| **Themes** | Background and layout. Zehn liegen in [`themes/`](themes/) |\n"
                "| **[`themes/`](themes/)** | where they live |\n")
        self.assertEqual([({10}, "| **Themes** | Background and layout. Zehn liegen in "
                                 "[`themes/`](themes/) |")], claims(page))

    def test_two_numbers_on_one_line_cannot_be_read_as_the_count(self):
        line = "| **Themes** | Zehn liegen in [`themes/`](themes/), elf mit `default/` |"
        self.assertEqual([({10, 11}, line)], claims(line))

    def test_a_word_the_table_does_not_carry_is_not_silently_skipped(self):
        # "elf" is German and Dutch for eleven and English for nothing this page would say; a page
        # that writes the count in a word missing from the table would read as making no claim at
        # all, which is how the two translations stayed at ten. The table is the one place to add it.
        self.assertIn("elf", NUMBER_WORDS)
        self.assertNotIn("een", NUMBER_WORDS)

    def test_the_thirteenth_theme_is_read_as_a_word(self):
        """docs/workflows/theme.md step 5 as a thirteenth theme follows it.

        The row states its count in the words the page writes ("thirteen ship in themes/"), which is
        the shape every theme from the twelfth on takes. The row used to be read through a twelve-word
        table of its own while the sentences went through the one with twenty, so this line was refused
        as "the Themes row states no count" while `claims()` read 13 out of the very same line.
        """
        row = ("| **Themes** | Background, wallpaper and layout from a `config.json` pushed over Wi-Fi, "
               "no rebuild. thirteen ship in [`themes/`](themes/), the built-in `default/` included |")
        self.assertEqual(13, count_in(row))
        # The two readers over that line must not disagree again.
        self.assertEqual([({13}, row)], claims(row))

    def test_every_word_the_sentences_are_read_in_is_a_count_in_the_row(self):
        for word, number in (("twelve", 12), ("thirteen", 13), ("fourteen", 14), ("seventeen", 17),
                             ("twenty", 20)):
            with self.subTest(word=word):
                # Longest first: "fourteen ship" must not be read as the "four" it starts with.
                self.assertEqual(number, count_in(
                    f"| **Themes** | ... {word} ship in [`themes/`](themes/) |"))

    def test_one_ship_in_is_a_count_beside_that_anchor(self):
        # "one" is the word the shared table leaves out, because a bare "een"/"ein" on a page line is
        # an article: the row's number is anchored to "ship", so here it is a count like any other.
        self.assertEqual(1, count_in("| **Themes** | ... one ship in [`themes/`](themes/) |"))

    def test_a_word_outside_the_table_still_reads_as_no_count(self):
        self.assertEqual(None, count_in("| **Themes** | ... a few ship in [`themes/`](themes/) |"))

    def test_a_stand_in_tree_of_thirteen_themes_and_a_row_saying_thirteen_agree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ("aurora", "cinematic", "default", "dusk", "glass", "halloween", "nature",
                         "retro", "ruska", "scenes", "six-columns", "sweden", "storm"):
                (root / "themes" / name).mkdir(parents=True)
                (root / "themes" / name / "config.json").write_text("{}", encoding="utf-8")
            shipped = theme_folders(root / "themes")
            self.assertEqual(13, len(shipped))
            row = "| **Themes** | ... thirteen ship in [`themes/`](themes/) |"
            self.assertEqual(len(shipped), count_in(row))

    def test_every_front_page_in_a_stand_in_tree_is_walked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "README.md").write_text("eleven ship in [`themes/`](themes/)", encoding="utf-8")
            (root / "README.de.md").write_text("Zehn liegen in [`themes/`](themes/)", encoding="utf-8")
            (root / "docs").mkdir()
            (root / "docs" / "README.other.md").write_text("nothing", encoding="utf-8")
            self.assertEqual(["README.de.md", "README.md"],
                             [p.name for p in front_pages(root)])

    def test_a_page_that_names_one_too_few_is_refused(self):
        """The failure the German and Dutch pages carried: a stand-in tree with four themes and a
        page that says three."""
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ("aurora", "default", "glass", "ruska"):
                (root / "themes" / name).mkdir(parents=True)
                (root / "themes" / name / "config.json").write_text("{}", encoding="utf-8")
            shipped = theme_folders(root / "themes")
            self.assertEqual(4, len(shipped))
            page = "| **Themes** | Drei liegen in [`themes/`](themes/) |"
            (root / "README.de.md").write_text(page, encoding="utf-8")
            claimed = next(iter(claims(page)))[0]
            self.assertNotEqual(claimed, {len(shipped)},
                                "a page saying three themes must not read as the four that ship")


if __name__ == "__main__":
    unittest.main()

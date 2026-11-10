"""Checks the count a list header states as "the N below plus a package".

`examples/tcl-c635/debloat.txt` opened with:

    # - 15 packages are disabled in total: the 13 below plus com.google.android.partnersetup
    #   (partner attribution). au.com.stan.and and tv.wuaki.apptv arrived pre-disabled at factory.

and `com.google.android.partnersetup` is the seventh of the thirteen lines below that sentence, so
the header counted it twice: thirteen lines plus a package that is one of them is fourteen, not
fifteen, and a reader sizing the list up from the header got a number that describes no file. The
sentence is a claim about the file in the same way a folder README's count is
(`tests/test_example_list_count.py`), and the same drift applies: this list's line was there from
the day the folder landed, so nothing re-read it as the file changed.

The nuance the sentence is there for is real and survives: the two factory-disabled packages are
not lines in the list, they are what the total counts on top of it. So the check reads the words,
not the number: a package named after "plus" is an addition to the lines below, and it must not be
one of them. The count in "the N below" is read too: it has to be how many lines the list sends, so
a sentence that miscounts its own list is caught even when the package it names is not in it.

Both sentences in the tree are read (`examples/tcl-mt9655-cn-android14/debloat.txt` says "28
packages disabled - the 27 below plus com.tcl.t_solo", and t_solo is commented out there), and a
sweep that read nothing fails rather than passing: the rule is only worth having while lists state
their size this way.

A second list also counts itself against the folder's other list: `examples/tcl-65c7k-google-tv/
debloat-75t8c-eu.txt` is the EU 75T8C unit beside the Brazilian 65C7K's debloat.txt, and its header
says "eight more packages are off here than there". That sentence is the only way a reader learns how
the two lists differ without diffing the two files by hand, and nothing read it either: it said "nine"
from 3cc882ea (1 Nov, the commit that was itself matching the header to the file) until this check,
while the two files differ by eight (the EU list sends dreamx, launcherx, play.games, the calendar sync
adapter, Play Movies, YouTube Music, backgrounddetection and the MediaTek webview, and the Brazilian
one sends none of them). So the phrase is read too, as a claim about the pair: N is how many packages
this list sends that the sibling `debloat*.txt` in the same folder does not. "off here than there"
can only mean that - a package is off here only if this list sends it - and a folder with one list
states no such comparison, so the walk reads only the folders that ship two.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
TRAILING_COMMENT = re.compile(r"\s+#.*$")
# `the 13 below plus com.google.android.partnersetup` - the package is named in words or in
# backticks, and it is an addition to the lines below the sentence.
EXTRA = re.compile(r"the\s+(\d+)\s+below\s+plus\s+`?([A-Za-z][\w.]*\.[\w.]+)`?", re.IGNORECASE)
# `eight more packages are off here than there` - a second list counting what it sends on top of the
# folder's other list. The number is written in words or in digits.
MORE = re.compile(r"\b([A-Za-z0-9]+)\s+more\s+(?:packages?\s+)?(?:are\s+)?off\s+here\s+than\s+there\b",
                  re.IGNORECASE)
WORDS = {word: number for number, word in enumerate(
    "one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen sixteen "
    "seventeen eighteen nineteen twenty".split(), 1)}


def extras(text):
    """Every "the N below plus a package" a file states, as (the count, the package)."""
    return [(int(m.group(1)), m.group(2)) for m in EXTRA.finditer(text)]


def packages(path):
    """The package names tools/disable.sh would send for this list, in file order."""
    text = path.read_text(encoding="utf-8")
    if text.startswith("\ufeff"):
        text = text[1:]
    names = []
    for line in text.split("\n"):
        line = TRAILING_COMMENT.sub("", line.rstrip("\r"))
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        names.append(line.split()[0])
    return names


def lists():
    return sorted(
        p for p in EXAMPLES.glob("*/debloat*.txt") if p.is_file() and p.name.startswith("debloat")
    )


def more_off_here(text):
    """Every "N more ... are off here than there" a file states, as N. A word the map has not got is
    left out: the sentence is read as a number, not guessed at."""
    found = []
    for match in MORE.finditer(text):
        token = match.group(1).lower()
        if token.isdigit():
            found.append(int(token))
        elif token in WORDS:
            found.append(WORDS[token])
    return found


def siblings(path):
    """The other debloat list(s) the same folder ships."""
    return sorted(p for p in path.parent.glob("debloat*.txt") if p.is_file() and p != path)


class HeaderCountTest(unittest.TestCase):
    def test_a_package_named_as_an_extra_is_not_one_of_the_lines_below(self):
        read = 0
        for path in lists():
            sent = packages(path)
            for count, package in extras(path.read_text(encoding="utf-8")):
                read += 1
                with self.subTest(list=path.parent.name, package=package):
                    self.assertNotIn(
                        package,
                        sent,
                        f"examples/{path.parent.name}/{path.name} says it sends {count} packages "
                        f"and then {package} on top, and {package} is one of them: the count that "
                        f"follows double-counts it",
                    )
        self.assertGreaterEqual(
            read, 1, "no debloat list states a count as 'the N below plus a package' any more"
        )

    def test_the_count_below_matches_the_lines_below(self):
        for path in lists():
            sent = packages(path)
            for count, _ in extras(path.read_text(encoding="utf-8")):
                with self.subTest(list=path.parent.name):
                    self.assertEqual(
                        count,
                        len(sent),
                        f"examples/{path.parent.name}/{path.name} says 'the {count} below' and "
                        f"carries {len(sent)} packages",
                    )


class ReaderTest(unittest.TestCase):
    """The reader above, against sentences written here."""

    def test_a_named_package_is_read(self):
        text = "# 15 packages are disabled in total: the 13 below plus com.example.extra\n"
        self.assertEqual(extras(text), [(13, "com.example.extra")])

    def test_a_backticked_package_is_read(self):
        text = "# the 7 below plus `com.example.extra`, which became opt-in\n"
        self.assertEqual(extras(text), [(7, "com.example.extra")])

    def test_a_header_that_names_no_extra_is_not_read(self):
        text = "# 12 packages are disabled, all of them telemetry\n"
        self.assertEqual(extras(text), [])


class SecondListDifferenceTest(unittest.TestCase):
    def test_the_number_the_header_states_is_the_difference_between_the_two_lists(self):
        read = 0
        for path in lists():
            for count in more_off_here(path.read_text(encoding="utf-8")):
                read += 1
                with self.subTest(list=path.parent.name, file=path.name):
                    others = siblings(path)
                    self.assertEqual(
                        1, len(others),
                        f"examples/{path.parent.name}/{path.name} counts itself against \"there\", "
                        f"and the folder ships {len(others)} other lists: {[o.name for o in others]}")
                    here, there = set(packages(path)), set(packages(others[0]))
                    self.assertEqual(
                        count, len(here - there),
                        f"examples/{path.parent.name}/{path.name} says {count} more packages are off "
                        f"here than in {others[0].name}, and it sends {len(here - there)} that "
                        f"{others[0].name} does not: {sorted(here - there)}")
        self.assertGreaterEqual(
            read, 1,
            "no debloat list states a difference against the folder's other list any more")


class SecondListReaderTest(unittest.TestCase):
    """The reader above, against sentences written here."""

    def test_a_number_in_digits_is_read(self):
        self.assertEqual(more_off_here("# and 8 more are off here than there.\n"), [8])

    def test_a_number_in_words_is_read(self):
        self.assertEqual(more_off_here("# ... and eight more packages are off here than there.\n"), [8])

    def test_a_header_with_no_comparison_is_not_read(self):
        self.assertEqual(more_off_here("# nine more are off here than in the C755 list.\n"), [])

    def test_a_word_the_map_has_not_got_is_not_guessed_at(self):
        self.assertEqual(more_off_here("# many more are off here than there.\n"), [])


if __name__ == "__main__":
    unittest.main()

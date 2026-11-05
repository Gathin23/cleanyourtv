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


if __name__ == "__main__":
    unittest.main()

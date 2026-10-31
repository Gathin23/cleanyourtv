"""docs/privacy.md: the group headings the report prints, and the cap it reads at, are privacy.py's.

The page is where an owner decides whether to run the audit at all: it says the report comes back "in up
to four groups - the three kinds it can name a job for, and a fourth headed 'Matched by name only,
nothing else known'", and it says "The audit reads `dumpsys package` for at most 25 packages, so on a TV
with a longer list of name matches the report shows the first 25 and says how many it left out, and
`--write` writes only those".

The tests that read privacy.py read the report's own output: each of the four headings is asserted to
appear in a run (tests/test_privacy_audit.py, tests/test_privacy_google_tv_telemetry.py), and the cap
is in the fixture of tests/test_privacy_write_target_first.py. Nothing read the page against them, so a
fifth group could join TITLES with the page still saying four, and the page's cap is a second copy of
MAX_PROBES that no test compares with the one the report prints: an owner told the audit stops at 50
would read the "N more name matches were not probed" line as a bug, and one told 10 would stop reading
the report at 10 packages.

What this file does not read: the package notes in CATALOGUE and the evidence lines classify() builds.
Both are what a run prints next to a package, and a page that paraphrases them ("whether the package
declares RECORD_AUDIO") is not stating a number to hold to one.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import io
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import privacy  # noqa: E402

PAGE = ROOT / "docs" / "privacy.md"

# `privacy.py` states the cap twice: "for at most 25 packages" and "the report shows the first 25". The
# page wraps its lines by hand, so every reader works on one flat line.
CAP = re.compile(r"at most (\d+) packages")
FIRST = re.compile(r"the report shows the first (\d+)")
GROUPS = re.compile(r"up to (\w+) groups")
# The heading the page quotes for the group a name match lands in: `"Matched by name only, ..."`.
QUOTED = re.compile(r'"([A-Z][^"]{3,80})"')
# The report's own line when the probe cap left candidates unread.
REPORT_CAP = re.compile(r"the audit stops at (\d+)")
WORDS = {2: "two", 3: "three", 4: "four", 5: "five", 6: "six"}


def flat(text):
    """One line: the page wraps its sentences by hand, so `at most 25\npackages` is one claim."""
    return " ".join(text.split())


def caps(text):
    """The cap the page states, as [(where, number)], read from each sentence that states one."""
    line = flat(text)
    out = [(f"at most {n} packages", int(n)) for n in CAP.findall(line)]
    out += [("the report shows the first %s" % n, int(n)) for n in FIRST.findall(line)]
    return out


def groups(text):
    """The number of groups the page says the report comes back in, as an int, or None."""
    found = GROUPS.search(flat(text))
    if not found:
        return None
    word = found.group(1).lower()
    if word.isdigit():
        return int(word)
    return next((number for number, name in WORDS.items() if name == word), None)


def quoted_headings(text):
    """The headings the page quotes, e.g. the fourth group's."""
    return QUOTED.findall(flat(text))


def printed_cap(result):
    """The cap the report's own "N more name matches were not probed" line states, or None."""
    out = io.StringIO()
    with contextlib.redirect_stdout(out):
        privacy.print_report(result)
    found = REPORT_CAP.search(out.getvalue())
    return int(found.group(1)) if found else None


def report_fixture(skipped=3):
    """A run whose cap left `skipped` name matches unprobed: no package sections, one skipped line."""
    return {"installed": ["com.example.one"], "disabled": set(), "entries": [], "skipped": skipped}


def problems(text, titles=None, cap=None):
    """`what` for every claim on the page that privacy.py does not keep; [] when they agree."""
    titles = privacy.TITLES if titles is None else titles
    cap = privacy.MAX_PROBES if cap is None else cap
    out = []

    stated = groups(text)
    if stated is None:
        out.append("docs/privacy.md no longer says how many groups the report comes back in")
    elif stated != len(titles):
        out.append(f"docs/privacy.md says the report comes back in {stated} groups and privacy.py's "
                   f"TITLES holds {len(titles)}: {', '.join(title for _, title in titles)}")

    quoted = quoted_headings(text)
    # The group a name match lands in is the `unknown` one wherever it sits in TITLES, not the last
    # one: a fifth kind added below it leaves that heading where it is.
    unknown = next((title for category, title in titles if category == "unknown"), None)
    if unknown is not None and unknown not in quoted:
        out.append(f'docs/privacy.md no longer quotes "{unknown}", the heading privacy.py prints for the '
                   f'group a name match lands in: it quotes {quoted}')

    line = flat(text).lower()
    for category, title in titles:
        if category == "unknown":
            continue
        # A word, not a substring: `reads` carries `ads`, and a kind is named in the page's prose.
        if not re.search(rf"\b{re.escape(category)}\b", line):
            out.append(f"docs/privacy.md does not name the {category!r} group privacy.py prints as "
                       f"{title!r}, so a reader does not know the report has it")

    stated_caps = caps(text)
    if not stated_caps:
        out.append("docs/privacy.md no longer states the cap the audit reads at")
    for where, number in stated_caps:
        if number != cap:
            out.append(f"docs/privacy.md says {where} and privacy.py's MAX_PROBES is {cap}")
    return out


class ReaderTest(unittest.TestCase):
    def test_a_stated_cap_is_read_from_each_sentence(self):
        self.assertEqual([("at most 25 packages", 25), ("the report shows the first 25", 25)],
                         caps("reads for at most 25 packages, so the report shows the first 25 and says"))

    def test_a_cap_wrapped_across_two_lines_is_one_claim(self):
        self.assertEqual([("at most 25 packages", 25)], caps("for at most 25\npackages, so on a TV"))

    def test_a_page_with_no_cap_states_none(self):
        self.assertEqual([], caps("The audit reads `dumpsys package` for every name match."))

    def test_the_group_count_is_read_as_a_word_or_a_digit(self):
        self.assertEqual(4, groups("in up to four groups - the three kinds"))
        self.assertEqual(4, groups("in up to 4 groups"))
        self.assertIsNone(groups("in groups - the three kinds"))

    def test_a_quoted_heading_is_read(self):
        self.assertEqual(["Matched by name only, nothing else known"],
                         quoted_headings('a fourth headed "Matched by name only, nothing else known" - each'))

    def test_the_report_line_states_the_cap(self):
        self.assertEqual(privacy.MAX_PROBES, printed_cap(report_fixture()))
        self.assertIsNone(printed_cap({"installed": [], "disabled": set(), "entries": [], "skipped": 0}),
                          "a run that probed everything says nothing about a cap")

    def test_a_page_that_agrees_is_quiet(self):
        page = ('prints voice, ACR and telemetry candidates in up to four groups - the three kinds it can '
                'name a job for, and a fourth headed "Matched by name only, nothing else known" - each '
                'with: its evidence. The audit reads `dumpsys package` for at most 25 packages, so on a TV '
                'with a longer list of name matches the report shows the first 25 and says how many it '
                'left out.\n')
        self.assertEqual([], problems(page))

    def test_a_second_cap_is_reported_on_its_own(self):
        page = ('prints voice, ACR and telemetry candidates in up to four groups, and a fourth headed '
                '"Matched by name only, nothing else known". The audit reads `dumpsys package` for at '
                'most 25 packages, so the report shows the first 50 and says how many it left out.\n')
        found = problems(page)
        self.assertEqual(1, len(found), found)
        self.assertIn("the report shows the first 50", found[0])
        self.assertIn(str(privacy.MAX_PROBES), found[0])

    def test_a_page_with_no_cap_is_reported(self):
        found = problems('prints voice, ACR and telemetry candidates in up to four groups, and a fourth '
                         'headed "Matched by name only, nothing else known".\n')
        self.assertEqual(["docs/privacy.md no longer states the cap the audit reads at"], found)

    def test_a_fifth_group_is_reported(self):
        titles = list(privacy.TITLES) + [("ads", "Ads and promotions")]
        found = problems('prints voice, ACR and telemetry candidates in up to four groups, and a fourth '
                         'headed "Matched by name only, nothing else known". The audit reads `dumpsys '
                         'package` for at most 25 packages, so the report shows the first 25 and says '
                         'how many it left out.\n', titles=titles)
        self.assertTrue(any("says the report comes back in 4 groups" in problem for problem in found), found)
        self.assertTrue(any("TITLES holds 5" in problem for problem in found), found)

    def test_a_renamed_fourth_heading_is_reported(self):
        titles = list(privacy.TITLES)
        titles[-1] = ("unknown", "Matched by name, nothing more")
        found = problems('prints voice, ACR and telemetry candidates in up to four groups, and a fourth '
                         'headed "Matched by name only, nothing else known". The audit reads `dumpsys '
                         'package` for at most 25 packages, so the report shows the first 25.\n',
                         titles=titles)
        self.assertEqual(1, len(found), found)
        self.assertIn("Matched by name, nothing more", found[0])

    def test_a_group_the_page_does_not_name_is_reported(self):
        titles = [("voice", "Voice assistants and wake word"), ("acr", "ACR"),
                  ("ads", "Ads and promotions"),
                  ("unknown", "Matched by name only, nothing else known")]
        found = problems('prints voice and ACR candidates in up to four groups, and a fourth headed '
                         '"Matched by name only, nothing else known". The audit reads `dumpsys package` '
                         'for at most 25 packages.\n', titles=titles)
        self.assertTrue(any("'ads' group" in problem for problem in found), found)


class PrivacyPageTest(unittest.TestCase):
    def test_the_page_is_read(self):
        text = PAGE.read_text(encoding="utf-8")
        self.assertIn("at most ", text, "docs/privacy.md no longer states the cap")
        self.assertGreaterEqual(len(caps(text)), 2,
                                "docs/privacy.md states the cap once; it stated it in two sentences")

    def test_the_page_states_the_groups_and_the_cap_privacy_py_keeps(self):
        text = PAGE.read_text(encoding="utf-8")
        self.assertEqual([], problems(text),
                         "docs/privacy.md and tools/privacy.py disagree about what the report prints")

    def test_the_printed_cap_is_the_one_the_page_states(self):
        page = caps(PAGE.read_text(encoding="utf-8"))
        printed = printed_cap(report_fixture())
        self.assertIsNotNone(printed, "the report no longer says the cap it stops at")
        for where, number in page:
            self.assertEqual(printed, number, f"docs/privacy.md says {where} and the report prints {printed}")

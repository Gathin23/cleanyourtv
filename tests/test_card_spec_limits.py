"""docs/specs/card.md's Limits table and decision record are the ones Cards.java enforces.

The page calls itself "the contract between a module (the app) and the launcher", and a card author
sizes a card against its two tables: the Fields table for a label and a caption, the Limits table for
how many cards, events and attributes the launcher reads. `tests/test_addon_module_cards.py` compares
the two lengths in the Fields table with `LABEL_MAX`/`CAPTION_MAX` and reads no other number in the
page, so the six limits in the Limits table - cards read per refresh, cards shown, decisions
remembered, XML events, nesting depth, attributes on `<card>` - and the decision file's own name,
format version and logcat tag were stated in the page and in `Cards.java` and read from neither side.
A launcher that lowered `MAX_SHOWN` to 6, or raised `MAX_RECORDS` to 256, left the page telling a card
author 8 and 128 with the suite green, and a card built to the page is one the launcher cuts with a
single `TvHomeCards` log line. The same file is what the troubleshooting step in docs/modules.md sends
a reader to (`logcat -s TvHomeCards`), so the tag and the record's version are read here too.

Only the numbers and names are pinned, never the prose around them: a page that rewrites its sentences
passes, a page whose number no longer matches the constant fails and names both.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SPEC = ROOT / "docs" / "specs" / "card.md"
CARDS = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Cards.java"

# The Limits table's first cell -> the constant in Cards.java that bounds the same thing. "Label and
# caption length" is left out on purpose: tests/test_addon_module_cards.py already compares its two
# numbers with LABEL_MAX and CAPTION_MAX, and a second reader of the same row would red the suite
# twice for one edit.
LIMIT_ROWS = {
    "Cards read per package list refresh": "MAX_DECLARED",
    "Cards shown as tiles": "MAX_SHOWN",
    "Decisions remembered in cards.json": "MAX_RECORDS",  # the page writes `cards.json`, the reader drops the backticks
    "XML events walked per card": "MAX_EVENTS",
    "XML nesting depth": "MAX_DEPTH",
    "Attributes on <card>": "MAX_ATTRIBUTES",
}


def limits_section(page):
    """The text of the page's `## Limits` section, up to the next `## ` heading."""
    parts = page.split("\n## Limits", 1)
    if len(parts) != 2:
        raise AssertionError("docs/specs/card.md has no '## Limits' section, so the card author has no "
                             "table stating what the launcher reads")
    return parts[1].split("\n## ", 1)[0]


def limit_rows(section):
    """{first cell without backticks: the number its second cell starts with}.

    The second cell always opens with the number ("32, the rest are ignored", "8, by label; ..."), so a
    row whose cell opens with words instead is not read rather than guessed at.
    """
    rows = {}
    for line in section.splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) != 2:
            continue
        what, limit = cells
        if what == "What" or not what or set(what) <= set(":- "):
            continue
        number = re.match(r"(\d+)", limit)
        if number:
            rows[what.replace("`", "")] = int(number.group(1))
    return rows


def java_text():
    return CARDS.read_text(encoding="utf-8")


def int_constant(java, name):
    """The value of `static final int <name> = N;` in Cards.java.

    A constant that was renamed is a different edit from one that changed value: this file can no
    longer say what the page should state, and saying that beats a KeyError.
    """
    found = re.search(r"static final int\s+%s\s*=\s*(\d+);" % re.escape(name), java)
    if found is None:
        raise AssertionError("Cards.java no longer declares `static final int %s = <number>;`, so the "
                             "Limits table has no constant to be read against" % name)
    return int(found.group(1))


def string_constant(java, name):
    found = re.search(r'static final String %s\s*=\s*"([^"]*)";' % re.escape(name), java)
    if found is None:
        raise AssertionError("Cards.java no longer declares `static final String %s = \"...\";`" % name)
    return found.group(1)


def header_version(java):
    """The `"version":N` in the decision file's HEADER literal, i.e. the format the file is written in."""
    line = next((line for line in java.splitlines() if "HEADER =" in line), None)
    if line is None:
        raise AssertionError("Cards.java no longer has a HEADER literal, so it no longer says which "
                             "format version cards.json is written in")
    found = re.search(r'version\\*":(\d+)', line)
    if found is None:
        raise AssertionError("Cards.java's HEADER no longer states a version: %s" % line.strip())
    return int(found.group(1))


class ShippedSpecTest(unittest.TestCase):
    """The page and the source in this checkout."""

    def test_the_limits_table_states_every_row_this_file_reads(self):
        rows = limit_rows(limits_section(SPEC.read_text(encoding="utf-8")))
        self.assertGreaterEqual(len(rows), 6, "the Limits table has fewer rows than this file knows about")
        for what in LIMIT_ROWS:
            with self.subTest(limit=what):
                self.assertIn(what, rows, "the Limits table no longer has a row for %r" % what)

    def test_each_limit_is_the_number_cards_java_takes(self):
        rows = limit_rows(limits_section(SPEC.read_text(encoding="utf-8")))
        java = java_text()
        for what, name in LIMIT_ROWS.items():
            with self.subTest(limit=what):
                self.assertEqual(rows[what], int_constant(java, name),
                                 "docs/specs/card.md says %r is %d and Cards.java's %s is %d"
                                 % (what, rows[what], name, int_constant(java, name)))

    def test_the_spec_number_is_the_one_the_launcher_reads(self):
        page = SPEC.read_text(encoding="utf-8")
        spec = int_constant(java_text(), "SPEC_SUPPORTED")
        self.assertTrue("reads spec `%d`" % spec in page,
                        "Cards.java reads spec %d and docs/specs/card.md no longer says so" % spec)

    def test_the_decision_file_is_the_one_the_page_names(self):
        page = SPEC.read_text(encoding="utf-8")
        store = string_constant(java_text(), "STORE_FILE")
        self.assertTrue("files/%s" % store in page,
                        "Cards.java writes files/%s and docs/specs/card.md names another file" % store)
        self.assertTrue("`%s.bad`" % store in page,
                        "Cards.java moves an invalid file to %s.bad and the page no longer names it" % store)

    def test_the_format_version_is_the_one_the_page_states(self):
        page = SPEC.read_text(encoding="utf-8")
        version = header_version(java_text())
        self.assertTrue("format version %d" % version in page,
                        "Cards.java writes format version %d and docs/specs/card.md states another version"
                        % version)

    def test_the_logcat_tag_is_the_one_the_page_hands_out(self):
        page = SPEC.read_text(encoding="utf-8")
        tag = string_constant(java_text(), "TAG")
        self.assertTrue("logcat -s %s" % tag in page,
                        "Cards.java logs under %s and the page hands out another tag" % tag)

    def test_the_page_names_both_decisions_the_file_stores(self):
        page = SPEC.read_text(encoding="utf-8")
        java = java_text()
        show, hide = string_constant(java, "DECISION_SHOW"), string_constant(java, "DECISION_HIDE")
        self.assertTrue("`%s` or `%s`" % (show, hide) in page,
                        "Cards.java stores %r or %r and the page names other decisions" % (show, hide))


class LimitsReaderTest(unittest.TestCase):
    """The readers over a table written here, so a shape the page could grow is read the same way."""

    PAGE = """
| What | Limit |
| :- | :- |
| Cards read per package list refresh | 32, the rest are ignored |
| Cards shown as tiles | 8, by label |
| XML nesting depth | 2 (`<card>` and its direct children) |
"""

    def rows(self, body=PAGE):
        return limit_rows(limits_section("\n## Limits\n" + body + "\n## Launcher behaviour\nprose"))

    def test_a_section_stops_at_the_next_heading(self):
        rows = self.rows("| Cards shown as tiles | 8 |\n\n## Launcher behaviour\n| Cards shown as tiles | 3 |")
        self.assertEqual(rows, {"Cards shown as tiles": 8}, "a row in the next section was read")

    def test_a_row_with_no_number_is_not_read(self):
        rows = self.rows("| Signers | exactly one signing certificate |")
        self.assertEqual(rows, {}, "a limit that states no number was read as one")

    def test_backticks_leave_the_name(self):
        rows = self.rows("| Decisions remembered in `cards.json` | 128, the oldest are dropped |")
        self.assertEqual(rows, {"Decisions remembered in cards.json": 128})

    def test_the_header_row_is_not_a_limit(self):
        self.assertEqual(self.rows("| What | Limit |\n| :- | :- |\n"), {})

    def test_a_renamed_constant_is_named_not_crashed_on(self):
        with self.assertRaises(AssertionError) as caught:
            int_constant("static final int OTHER = 8;", "MAX_SHOWN")
        self.assertIn("static final int MAX_SHOWN", str(caught.exception))

    def test_a_version_is_read_out_of_the_java_literal(self):
        java = '    private static final String HEADER = "{\\"version\\":3,\\"records\\":[";'
        self.assertEqual(header_version(java), 3)


if __name__ == "__main__":
    unittest.main()

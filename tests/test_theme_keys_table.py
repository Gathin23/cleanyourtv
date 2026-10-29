"""The Keys table in themes/README.md defines each key once.

The switch recipe (docs/workflows/launcher-switch.md) says to add a key's row to this table, and four contributors each did exactly
that when they taught `app_keys` a new form (a scancode on 20 Oct, `input:` on 21 Oct, `hold:` on 22
Oct), so the table ended up with four `app_keys` rows: the third one lost the scancode form that the
first two document, and a reader who finds it first concludes scancodes are not supported.
`reduced_motion` had two rows as well, describing its motion differently and neither matching
config.xml. A reader has no way to tell which row is the current one, so the table has to hold one row
per key; a new form belongs in that key's row.

The checks read the first cell of each row only, because the meaning cell names other keys as
well (`library_button` mentions `app_library`, for instance) and those mentions are not definitions.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "themes" / "README.md"
REMOTE_KEYS = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "RemoteKeys.java"

# A key is lowercase to stay readable next to `config.xml`; `app_keys` and `live_tv_package` qualify,
# a backticked value such as `false` or `#RRGGBB` does not.
KEY = re.compile(r"`([a-z][a-z0-9_]*)`")


def keys_section(body):
    """The lines of the '## Keys' section, up to the next '## ' heading (the table lives here)."""
    lines = body.split("\n")
    start = next((i for i, line in enumerate(lines) if line.strip() == "## Keys"), None)
    if start is None:
        return []
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("## ")), len(lines))
    return lines[start + 1:end]


def table_rows(section):
    """The body rows of the first table in the section: no header, no '|---|' separator, no blanks."""
    rows, seen_header = [], False
    for line in section:
        if not line.startswith("|"):
            if seen_header:
                break            # the table ended; a second table in the section is not this one
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if not seen_header:
            seen_header = True   # the header row itself
            continue
        if all(set(cell) <= set("-: ") and cell for cell in cells):
            continue             # the |---|---| separator
        rows.append(line)
    return rows


def defined(section):
    """key -> the rows that define it, from the first cell of each row only."""
    found = {}
    for row in table_rows(section):
        first = row.strip().strip("|").split("|")[0]
        for key in KEY.findall(first):
            found.setdefault(key, []).append(row)
    return found


def duplicates(section):
    """key -> its rows, for every key defined more than once."""
    return {key: rows for key, rows in defined(section).items() if len(rows) > 1}


def app_keys_row(section):
    rows = defined(section).get("app_keys", [])
    return rows[0] if len(rows) == 1 else ""


class KeysTableTest(unittest.TestCase):
    def setUp(self):
        self.section = keys_section(README.read_text(encoding="utf-8"))

    def test_the_table_is_there(self):
        self.assertTrue(table_rows(self.section), "the Keys table moved or lost its rows")
        self.assertGreaterEqual(len(defined(self.section)), 60,
                                "the table lists far fewer keys than it did: is the parser still right?")

    def test_no_key_is_defined_twice(self):
        for key, rows in duplicates(self.section).items():
            with self.subTest(key=key):
                self.fail(f"`{key}` has {len(rows)} rows in the Keys table: one row per key, with a new "
                          f"form of an existing key folded into that key's row. Rows:\n" + "\n".join(rows))

    def test_every_row_names_its_key(self):
        for row in table_rows(self.section):
            with self.subTest(row=row[:60]):
                self.assertTrue(KEY.findall(row.strip().strip("|").split("|")[0]),
                                "a row's first cell names no key in backticks")

    def test_multiple_keys_in_one_row_are_all_counted(self):
        # `live_tv_package`, `live_tv_action` share a row on purpose: one type, one meaning.
        found = defined(self.section)
        self.assertIn("live_tv_package", found)
        self.assertIn("live_tv_action", found)

    def test_app_keys_documents_every_form_the_code_reads(self):
        row = app_keys_row(self.section)
        self.assertTrue(row, "no single `app_keys` row")
        code = REMOTE_KEYS.read_text(encoding="utf-8")
        for form in ("KEYCODE_", "scan:", "input:", "hold:"):
            with self.subTest(form=form):
                self.assertIn(form, code, f"RemoteKeys.java no longer reads {form}; update the row")
                self.assertIn(form, row, f"the app_keys row does not document {form}, which "
                                         f"RemoteKeys.java still reads")


class CheckerTest(unittest.TestCase):
    """The checks above, against small tables: a checker that finds nothing passes for any text."""

    HEADER = "| Key | Type | Meaning |\n|---|---|---|\n"

    def table(self, *rows):
        return ["## Keys", ""] + self.HEADER.strip("\n").split("\n") + list(rows) + ["", "## Wallpapers"]

    def test_a_second_row_for_one_key_is_reported(self):
        section = self.table("| `app_keys` | list | one form |", "| `app_keys` | list | another form |")
        self.assertEqual(list(duplicates(section)), ["app_keys"])

    def test_one_row_per_key_is_quiet(self):
        section = self.table("| `app_keys` | list | the forms |", "| `show_stats` | bool | one line |")
        self.assertEqual(duplicates(section), {})
        self.assertEqual(len(defined(section)), 2)

    def test_a_key_named_in_the_meaning_cell_is_not_a_definition(self):
        # `library_button` mentions `app_library` in its meaning; that is not a second `app_library` row.
        section = self.table("| `app_library` | bool | the library (`show_stats` is elsewhere) |",
                             "| `library_button` | bool | needs `app_library` |")
        self.assertEqual(duplicates(section), {})
        self.assertNotIn("show_stats", defined(section))

    def test_the_section_stops_at_the_next_heading(self):
        section = self.table("| `app_keys` | list | the forms |")
        section += ["", "## Wallpapers", "", "| `app_keys` | list | not this table |"]
        self.assertEqual(list(defined(section)), ["app_keys"])
        self.assertEqual(len(table_rows(section)), 1)

    def test_a_table_without_a_header_row_is_not_read(self):
        # A paragraph with pipes is not a table body; without a header the checker must find nothing.
        section = ["## Keys", "", "| `app_keys` | list | no header above |", ""]
        self.assertEqual(defined(section), {})


if __name__ == "__main__":
    unittest.main()

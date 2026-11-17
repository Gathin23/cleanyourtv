"""The Keys table's stated default for a switch is the one config.xml ships.

`themes/README.md` is what a theme author reads before setting a key, and its rows say what the key
does when the theme leaves it out: "The reorder button in the top bar (`false` by default)", "Minutes
Home may sit untouched ... (`5` by default)". The value behind that sentence is not in the page: it is
the resource `config.xml` defines, read through `Config.java`'s own call for the key.

Nothing read the two together. `tests/test_theme_keys_config.py` checks that a switch read from
`config.xml` has a row at all, not what the row promises, and `tests/test_shipped_config_values.py`
reads a shipped theme's `config.json` against the reader's type and range. One row's phrase is pinned
by hand, in `tests/test_icon_only_inputs.py`; the other sixty were pinned by nothing. A switch flipped
in `config.xml` - the file a maintainer edits to change a default - or a row left behind after that
flip tells a theme author the opposite of what the launcher does, and every run stays green: the two
files simply stop agreeing.

This reads the table's first cell for the key, the `by default` phrases in the row's Meaning cell for
the value it promises, and `config.xml` for what the launcher ships - the second and third through
`Config.java`, so a key whose reader call is dropped, or whose resource is renamed, fails here instead
of being compared against a table of names copied into this file. `test_theme_keys_config.py`'s own
`keys_section()`, `table_rows()` and `meaning_cell()` walk the section, so a second table there is not
read as keys.

Only a stated value is judged. A row that says `(default)`, `Unset by default`, or nothing at all has
promised no value and is left alone; a row naming two keys is judged only when both of their defaults
are the same, because one sentence then states one fact; and a key with no `config.xml` resource
(`wallpaper_rotation_minutes`, `race_calendar`) has nothing here to compare.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
README = Path("themes") / "README.md"
CONFIG_XML = Path("launcher") / "app" / "src" / "main" / "res" / "values" / "config.xml"
CONFIG_JAVA = Path("launcher") / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_theme_keys_config import KEY, keys_section, meaning_cell, table_rows  # noqa: E402

# The resources config.xml defines, in the two forms a switch's default is written in. A colour or a
# string has no "by default" sentence to compare, so neither is read here.
BOOL_RESOURCE = re.compile(r'<bool\s+name="([a-z][a-z0-9_]*)">\s*(true|false)\s*</bool>')
INT_RESOURCE = re.compile(r'<integer\s+name="([a-z][a-z0-9_]*)">\s*(-?\d+)\s*</integer>')
# Config.java's own two halves of a key's default: the field it starts from, and the reader call that
# lets a theme's config.json replace it.
FROM_RESOURCE = re.compile(r"(\w+)\s*=\s*res\.get(Boolean|Integer)\(R\.(?:bool|integer)\.([a-z][a-z0-9_]*)\)")
FROM_THEME = re.compile(r'(\w+)\s*=\s*(?:bool|integer)\(\s*json,\s*"([a-z][a-z0-9_]*)"')

# The shapes the table writes a default in. Each is read on its own so one row carrying two of them
# (`(true by default)`, "true by default") is one claim, not two.
CLAIMS = (
    re.compile(r"`(true|false)`\s+by default"),
    re.compile(r"\((true|false)\s+by default\)"),
    re.compile(r"(?<![`(])\b(true|false)\s+by default"),
    re.compile(r"`(\d+)`\s+by default"),
    re.compile(r"\((\d+)\s+by default\)"),
)
# "Off by default" and "On by default" are the same two values in words, and the table uses them.
OFF_WORDS = re.compile(r"\b[Oo]ff by default\b")
ON_WORDS = re.compile(r"\b[Oo]n by default\b")


def stated_defaults(meaning):
    """The values a row's Meaning cell promises, in the order it states them."""
    found = []
    for pattern in CLAIMS:
        found += [match.group(1) for match in pattern.finditer(meaning)]
    if OFF_WORDS.search(meaning):
        found.append("false")
    if ON_WORDS.search(meaning):
        found.append("true")
    return list(dict.fromkeys(found))


def config_defaults(config_text, java_text):
    """key -> the value config.xml ships for it, as the table writes one ("true", "false", "5").

    The key is the JSON name a theme's config.json uses and the resource is the one `Config.java` reads
    into the field that call hands back as the fallback, so the two are tied together the way the
    launcher ties them: no list of names is copied in here.
    """
    shipped = {}
    for name, value in BOOL_RESOURCE.findall(config_text):
        shipped[name] = value
    for name, value in INT_RESOURCE.findall(config_text):
        shipped[name] = str(int(value))
    field_resource = {field: name for field, _, name in FROM_RESOURCE.findall(java_text)}
    out = {}
    for field, key in FROM_THEME.findall(java_text):
        resource = field_resource.get(field)
        if resource in shipped:
            out[key] = shipped[resource]
    return out


def rows_with_defaults(table_text):
    """[(keys, claims)] for every row that names a key and states a default for it."""
    out = []
    for row in table_rows(keys_section(table_text)):
        keys = KEY.findall(row.strip().strip("|").split("|")[0])
        claims = stated_defaults(meaning_cell(row))
        if keys and claims:
            out.append((keys, claims))
    return out


def problems(root):
    """The rows whose stated default disagrees with config.xml, each as a sentence naming both."""
    root = Path(root)
    table = (root / README).read_text(encoding="utf-8")
    defaults = config_defaults((root / CONFIG_XML).read_text(encoding="utf-8"),
                               (root / CONFIG_JAVA).read_text(encoding="utf-8"))
    out = []
    for keys, claims in rows_with_defaults(table):
        known = {key: defaults[key] for key in keys if key in defaults}
        if not known:
            continue   # a theme-only key, or a value that is not a switch
        if len(set(known.values())) != 1:
            continue   # two keys, two defaults: one sentence is not one fact about either
        actual = next(iter(known.values()))
        for claim in claims:
            if claim != actual:
                out.append(f"themes/README.md: `{'`, `'.join(sorted(known))}` says {claim} by default, "
                           f"and {CONFIG_XML.as_posix()} has {actual}")
    return out


class KeysTableDefaultsTest(unittest.TestCase):
    def test_every_stated_default_is_the_one_config_xml_ships(self):
        self.assertEqual([], problems(ROOT),
                         "a row's stated default and config.xml disagree; a theme author follows the row")

    def test_the_table_is_read(self):
        # A walk that read nothing would pass the test above: the section states 60 defaults today.
        read = rows_with_defaults((ROOT / README).read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(read), 50, f"only {len(read)} rows state a default; the sweep is not reading the table")
        defaults = config_defaults((ROOT / CONFIG_XML).read_text(encoding="utf-8"),
                                   (ROOT / CONFIG_JAVA).read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(defaults), 60,
                                f"only {len(defaults)} keys resolved to a config.xml resource")
        # And the keys the rows name are the ones resolved, not some other set.
        named = {key for keys, _ in read for key in keys}
        self.assertGreaterEqual(len(named & set(defaults)), 45, "the rows and the resolved keys do not meet")


class CheckerTest(unittest.TestCase):
    """The checker has to be able to fail, and to leave alone what is not a stated default."""

    CONFIG = ('<resources>\n  <bool name="dock_title">true</bool>\n  <bool name="reorder_button">false</bool>\n'
              '  <integer name="columns">5</integer>\n</resources>\n')
    JAVA = ("        boolean dockTitle = res.getBoolean(R.bool.dock_title);\n"
            "        boolean reorderButton = res.getBoolean(R.bool.reorder_button);\n"
            "        int columns = res.getInteger(R.integer.columns);\n"
            "            dockTitle = bool(json, \"dock_title\", dockTitle);\n"
            "            reorderButton = bool(json, \"reorder_button\", reorderButton);\n"
            "            columns = integer(json, \"columns\", columns, 1, 8);\n")

    def tree(self, rows, config=None, java=None):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name)
        (root / README).parent.mkdir(parents=True, exist_ok=True)
        table = "".join("| `%s` | bool | %s |\n" % (key, meaning) for key, meaning in rows)
        (root / README).write_text(
            "# Themes\n\n## Keys\n\n| Key | Type | Meaning |\n|---|---|---|\n" + table,
            encoding="utf-8")
        (root / CONFIG_XML).parent.mkdir(parents=True, exist_ok=True)
        (root / CONFIG_XML).write_text(self.CONFIG if config is None else config, encoding="utf-8")
        (root / CONFIG_JAVA).parent.mkdir(parents=True, exist_ok=True)
        (root / CONFIG_JAVA).write_text(self.JAVA if java is None else java, encoding="utf-8")
        return root

    def test_a_wrong_bool_default_is_reported(self):
        root = self.tree([("dock_title", "On (`false` by default)")])
        self.assertEqual(1, len(problems(root)))
        self.assertIn("dock_title", problems(root)[0])
        self.assertIn("true", problems(root)[0])

    def test_the_shipped_default_is_quiet(self):
        root = self.tree([("dock_title", "On (`true` by default)"), ("reorder_button", "Off (false by default)")])
        self.assertEqual([], problems(root))

    def test_off_by_default_is_a_false_claim(self):
        root = self.tree([("dock_title", "The tile strip, off by default")])
        self.assertEqual(1, len(problems(root)), "Off by default claims false, and config.xml has true")
        root = self.tree([("reorder_button", "The button, off by default")])
        self.assertEqual([], problems(root))

    def test_a_number_default_is_compared_as_a_number(self):
        root = self.tree([("columns", "Tiles per row (`6` by default)")])
        self.assertEqual(1, len(problems(root)))
        self.assertIn("5", problems(root)[0])
        root = self.tree([("columns", "Tiles per row (5 by default)")])
        self.assertEqual([], problems(root))

    def test_a_row_that_states_no_value_is_left_alone(self):
        # "(default)" and "Unset by default" name no value, and neither is a claim to check - the
        # first keeps whatever config.xml says, the second is about a key with no resource at all.
        root = self.tree([("dock_title", "On (default)"), ("reorder_button", "Unset by default, and the buttons go")])
        self.assertEqual([], problems(root))

    def test_a_row_naming_two_keys_with_two_defaults_is_not_judged(self):
        root = self.tree([("dock_title", "`reorder_button` and this one (`true` by default)")])
        self.assertEqual([], problems(root))

    def test_a_row_naming_two_keys_with_one_default_is_judged(self):
        root = self.tree([("reorder_button", "With `dock_title` (`true` by default)")])
        self.assertEqual(1, len(problems(root)), "both keys default to a value, and one row states it")

    def test_a_key_with_no_config_xml_resource_is_left_alone(self):
        root = self.tree([("wallpaper_rotation_minutes", "Rotates every hour (`60` by default)")])
        self.assertEqual([], problems(root))

    def test_the_check_reads_config_xml_rather_than_a_copy(self):
        flipped = self.CONFIG.replace('<bool name="dock_title">true</bool>',
                                      '<bool name="dock_title">false</bool>')
        root = self.tree([("dock_title", "On (`true` by default)")], config=flipped)
        self.assertEqual(1, len(problems(root)), "the table still says true and config.xml now says false")

    def test_a_key_whose_reader_call_is_gone_is_not_compared(self):
        # The fallback comes from the field the reader call names: without that call the key has no
        # reader, and a default this file invented for it would be the wrong thing to compare against.
        java = self.JAVA.replace("            dockTitle = bool(json, \"dock_title\", dockTitle);\n", "")
        root = self.tree([("dock_title", "On (`false` by default)")], java=java)
        self.assertEqual([], problems(root))

    def test_a_picture_before_the_table_is_not_read_as_a_row(self):
        root = self.tree([("reorder_button", "Off (`false` by default)")])
        page = root / README
        page.write_text("# Themes\n\n| `dock_title` | bool | On (`false` by default) |\n\n## Keys\n\n"
                        "| Key | Type | Meaning |\n|---|---|---|\n| `reorder_button` | bool | Off (`false` by default) |\n",
                        encoding="utf-8")
        self.assertEqual([], problems(root), "a table before the Keys heading is not the Keys table")


if __name__ == "__main__":
    unittest.main()

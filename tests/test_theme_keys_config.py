"""Every key the launcher reads from config.xml has a row in the Keys table in themes/README.md.

The switch recipe in AGENTS.md asks for a row in that table for every key it adds, because the table
is what a theme author reads and the launcher itself says nothing: an unknown key is ignored, so a
reader who sets one the table never names has no way to tell a typo from a key that does not exist.
`input_sources` is in the table marked "config.xml only", so the table is meant to cover those too -
and `settings_fallback_components`, the list SystemSettings.java walks when nothing usable answers the
standard Settings action, was the one entry missing. A reader whose Settings action opens nothing had
nothing to look up, and the row now says why: the three vendor screens it ships with, the `CTSDummy`
stub Fire OS resolves the action to, and that a package named here also has to be in the launcher's
`<queries>`, so a fourth set needs a build rather than a theme push.

The check runs one way only, the way the table is written: every key the launcher reads from
config.xml has a row. It does not ask the table to stop carrying theme-only keys such as `race_calendar`
or `wallpaper_rotation_minutes`, which no config.xml resource backs - those are the keys a theme sets
and the table is their only home.

`latitude` and `longitude` are the two exceptions, with their reason in EXEMPT: they are config.xml
lines a build sets, docs/sky.md documents them, and tests/test_sky_place_note.py requires the Keys
table to keep naming neither (a theme cannot move the sun). A test here keeps that exemption honest by
checking both are still read from config.xml by SkyView.java.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "themes" / "README.md"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"
MANIFEST = ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml"
JAVA_DIR = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
CONFIG_JAVA = JAVA_DIR / "Config.java"
SYSTEM_SETTINGS = JAVA_DIR / "SystemSettings.java"
SKY_VIEW = JAVA_DIR / "SkyView.java"

# A key is lowercase to stay readable next to `config.xml`; `app_keys` qualifies, a backticked value such
# as `false` or `#RRGGBB` does not.
KEY = re.compile(r"`([a-z][a-z0-9_]*)`")
# The resources config.xml defines: name -> type, as the launcher sees them (a string-array is R.array).
RESOURCE = re.compile(r'<(bool|string|integer|string-array|array|color)\s+name="([a-z][a-z0-9_]*)"')
# What the Java side reads: R.bool.x, R.string.x, R.integer.x, R.array.x, R.color.x.
READ = re.compile(r"R\.(?:bool|string|integer|array|color)\.([a-z][a-z0-9_]*)")
# A key read from a theme's config.json: string(json, "dock_title", ...) and its siblings.
FROM_THEME = re.compile(r"\(\s*json,\s*\"([a-z][a-z0-9_]*)\"")
FALLBACK_ITEM = re.compile(r"<item>([^<]+)</item>")
QUERIES_PACKAGE = re.compile(r'<package\s+android:name="([^"]+)"')

# The keys read from config.xml on purpose that the table must not list, each with the page that
# documents it instead.
EXEMPT = {
    "latitude": "docs/sky.md: the sky's place, a build setting no theme can move",
    "longitude": "docs/sky.md: the sky's place, a build setting no theme can move",
}


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


def table_keys(text):
    """key -> its row, from the first cell of each body row (the meaning cell names other keys)."""
    found = {}
    for row in table_rows(keys_section(text)):
        first = row.strip().strip("|").split("|")[0]
        for key in KEY.findall(first):
            found[key] = row
    return found


def config_keys(text):
    """name -> type, for every resource config.xml defines."""
    return {name: kind for kind, name in RESOURCE.findall(text)}


def read_from_config(config, java_sources):
    """The config.xml keys the launcher reads, from every R.<type>.<name> in the Java sources."""
    names = set()
    for source in java_sources:
        names |= set(READ.findall(source))
    return {name for name in names if name in config}


def missing_rows(table_text, config_text, java_sources):
    """The config.xml keys the launcher reads that no row of the Keys table names, EXEMPT aside."""
    read = read_from_config(config_keys(config_text), java_sources)
    rows = table_keys(table_text)
    return sorted(name for name in read if name not in rows and name not in EXEMPT)


def row_for(table_text, key):
    return table_keys(table_text).get(key, "")


def type_cell(row):
    """The second cell of a table row."""
    cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
    return cells[1] if len(cells) > 1 else ""


def meaning_cell(row):
    cells = [cell.strip() for cell in row.strip().strip("|").split("|")]
    return cells[2] if len(cells) > 2 else ""


def java_sources():
    return [path.read_text(encoding="utf-8") for path in sorted(JAVA_DIR.glob("*.java"))]


class KeysTableCoversConfigXmlTest(unittest.TestCase):
    def setUp(self):
        self.text = README.read_text(encoding="utf-8")
        self.config = CONFIG_XML.read_text(encoding="utf-8")
        self.sources = java_sources()

    def test_the_table_is_there(self):
        self.assertGreaterEqual(len(table_keys(self.text)), 60,
                                "the Keys table moved or lost its rows: is the parser still right?")

    def test_the_parser_finds_what_the_launcher_reads(self):
        # A parser that reads nothing would make the test below pass for any table.
        read = read_from_config(config_keys(self.config), self.sources)
        for name in ("dock_apps", "app_keys", "settings_fallback_components", "live_tv_package"):
            with self.subTest(key=name):
                self.assertIn(name, read, f"config.xml and the Java side no longer agree on `{name}`")
        self.assertGreaterEqual(len(read), 40, "far fewer keys read from config.xml than before")

    def test_every_key_the_launcher_reads_has_a_row(self):
        missing = missing_rows(self.text, self.config, self.sources)
        for name in missing:
            with self.subTest(key=name):
                self.fail(f"the launcher reads `{name}` from config.xml and the Keys table has no row "
                          f"for it: add one (docs/workflows/launcher-switch.md) or, if it is not a key a "
                          f"reader sets, add it to EXEMPT with the page that documents it instead")

    def test_settings_fallback_components_has_its_row(self):
        row = row_for(self.text, "settings_fallback_components")
        self.assertTrue(row, "the Keys table has no settings_fallback_components row")
        self.assertIn("config.xml", type_cell(row), "the row has to say the key is config.xml only")
        meaning = meaning_cell(row)
        for fact in ("CTSDummy", "<queries>", "package/activity"):
            with self.subTest(fact=fact):
                self.assertIn(fact, meaning, f"the row does not state {fact}")

    def test_the_row_survives_a_change_in_the_code(self):
        # The row describes what SystemSettings.java does; if that moves, the row has to move with it.
        code = SYSTEM_SETTINGS.read_text(encoding="utf-8")
        self.assertIn("R.array.settings_fallback_components", code,
                      "SystemSettings.java no longer reads settings_fallback_components: drop or "
                      "rewrite its row")
        self.assertIn("CTSDummy", code, "the CTSDummy skip is gone from SystemSettings.java: the row "
                                       "still claims Fire OS's stub counts as missing")
        self.assertIn('"CTSDummy"', code, "the stub is no longer matched by name")

    def test_a_config_only_row_is_not_read_from_a_theme(self):
        # Every row that says "config.xml only" claims a theme cannot set it; Config.java decides that.
        from_theme = set(FROM_THEME.findall(CONFIG_JAVA.read_text(encoding="utf-8")))
        config_only = [key for key, row in table_keys(self.text).items()
                       if "`config.xml` only" in type_cell(row)]
        self.assertGreaterEqual(len(config_only), 2,
                                "no row says config.xml only any more: is the wording still the same?")
        for key in config_only:
            with self.subTest(key=key):
                self.assertNotIn(key, from_theme,
                                 f"`{key}` is read from a theme's config.json, so its row cannot say "
                                 f"config.xml only")

    def test_the_fallback_entries_are_visible_to_the_launcher(self):
        # config.xml asks for it: a vendor screen whose package is not in <queries> is never found on
        # Android 11, so a new entry that forgets the manifest line would silently do nothing.
        block = re.search(r'<string-array name="settings_fallback_components".*?</string-array>',
                          self.config, re.S)
        array = block.group(0) if block else ""
        self.assertTrue(array, "settings_fallback_components is not a string-array in config.xml")
        items = [item.strip() for item in FALLBACK_ITEM.findall(array)]
        self.assertTrue(items, "settings_fallback_components has no entries left")
        visible = set(QUERIES_PACKAGE.findall(MANIFEST.read_text(encoding="utf-8")))
        for item in items:
            with self.subTest(item=item):
                self.assertIn("/", item, "an entry is not package/activity")
                package = item.split("/")[0]
                self.assertIn(package, visible,
                              f"`{package}` is a settings fallback but not in the manifest's <queries>: "
                              f"package visibility hides it, so the fallback can never match")

    def test_the_exemption_is_still_right(self):
        # latitude and longitude stay out of the table (tests/test_sky_place_note.py requires it), so
        # they may only stay out while they are what that page describes: config.xml lines SkyView reads.
        config = config_keys(self.config)
        sky = SKY_VIEW.read_text(encoding="utf-8")
        for name in EXEMPT:
            with self.subTest(key=name):
                self.assertIn(name, config, f"`{name}` is gone from config.xml: drop its EXEMPT entry")
                self.assertIn(f"R.string.{name}", sky, f"SkyView.java no longer reads `{name}`: is the "
                                                      f"docs/sky.md page still right?")
                self.assertNotIn(name, table_keys(self.text),
                                 f"`{name}` is in the Keys table now: drop its EXEMPT entry")


class CheckerTest(unittest.TestCase):
    """The checks above against small inputs: a checker that finds nothing passes for any table."""

    HEADER = "| Key | Type | Meaning |\n|---|---|---|\n"

    CONFIG = ("<resources>\n"
              '    <string-array name="settings_fallback_components" translatable="false">\n'
              "        <item>com.tcl.settings/com.tcl.settings.ui.MainActivity</item>\n"
              "    </string-array>\n"
              '    <bool name="dock_row">true</bool>\n'
              '    <string name="latitude" translatable="false"></string>\n'
              '    <integer name="columns">5</integer>\n'
              "</resources>\n")

    JAVA = ("R.array.settings_fallback_components; R.bool.dock_row; R.integer.columns; "
            "R.string.latitude; R.string.not_in_config; R.drawable.ic_settings;")

    def table(self, *rows):
        return "## Keys\n\n" + self.HEADER + "\n".join(rows) + "\n\n## Wallpapers\n"

    def test_a_key_without_a_row_is_reported(self):
        table = self.table("| `dock_row` | bool | the dock as a row |",
                           "| `columns` | 1 to 8 | tiles per row |")
        self.assertEqual(missing_rows(table, self.CONFIG, [self.JAVA]),
                         ["settings_fallback_components"])

    def test_a_key_with_a_row_is_quiet(self):
        table = self.table("| `dock_row` | bool | the dock as a row |",
                           "| `columns` | 1 to 8 | tiles per row |",
                           "| `settings_fallback_components` | list, `config.xml` only | vendor screens |")
        self.assertEqual(missing_rows(table, self.CONFIG, [self.JAVA]), [])

    def test_an_exempt_key_is_not_required(self):
        table = self.table("| `dock_row` | bool | the dock as a row |",
                           "| `columns` | 1 to 8 | tiles per row |",
                           "| `settings_fallback_components` | list, `config.xml` only | vendor screens |")
        self.assertIn("latitude", EXEMPT)
        self.assertEqual(missing_rows(table, self.CONFIG, [self.JAVA]), [])

    def test_a_key_nothing_reads_is_not_required(self):
        # config.xml carries keys for the next release too; only what the Java side reads is owed a row.
        table = self.table("| `dock_row` | bool | the dock as a row |",
                           "| `settings_fallback_components` | list, `config.xml` only | vendor screens |")
        self.assertEqual(missing_rows(table, self.CONFIG, [self.JAVA]), ["columns"])

    def test_a_resource_outside_config_xml_is_not_a_key(self):
        # R.string.ic_settings is a drawable and R.string.not_in_config is a strings.xml resource.
        read = read_from_config(config_keys(self.CONFIG), [self.JAVA])
        self.assertNotIn("not_in_config", read)
        self.assertNotIn("ic_settings", read)

    def test_a_table_that_is_not_there_reads_as_no_keys(self):
        # Without this the repo-wide check could pass by finding no table at all... so a section with no
        # table has to report every key as missing, not read as "nothing to check".
        body = "## Wallpapers\n\n| `dock_row` | bool | not this table |\n"
        self.assertEqual(table_keys(body), {})
        self.assertEqual(missing_rows(body, self.CONFIG, [self.JAVA]),
                         ["columns", "dock_row", "settings_fallback_components"])

    def test_the_section_stops_at_the_next_heading(self):
        body = "## Keys\n\n" + self.HEADER + "| `dock_row` | bool | here |\n\n## Wallpapers\n\n" \
               "| `columns` | 1 to 8 | not this table |\n"
        self.assertEqual(list(table_keys(body)), ["dock_row"])


if __name__ == "__main__":
    unittest.main()

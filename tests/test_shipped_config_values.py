"""Every value a theme's or an example's config.json ships is one the launcher keeps.

`Config.java` reads a theme's `config.json` strictly: `bool()` returns the fallback unless the value is a
JSON boolean, `string()` unless it is a String, `integer()` unless it is a Number inside the range it is
handed, `color()` unless it is a six-digit `#RRGGBB`, and the list readers unless it is an array of the
right element type. A value of the wrong type, or a number outside the range, is therefore dropped
without a word: `tools/theme.sh` pushes the file, the launcher keeps the built-in value, and neither the
push nor the screen says anything.

`examples/acer-ar55ar2851udpro` shipped `"wifi_label_width": 320` against `integer(json,
"wifi_label_width", wifiLabelWidth, 0, 300)`, so the launcher fell back to 0 - and 0 is the one value
`MainActivity` reads as "icon only, no network name at all" (`wifiIconOnly`), while that folder's README
said the pill shows the network name. A theme author following themes/README.md, whose Keys row gives
the same 0 to 300, had no way to see it either.

The types and the bounds are not copied in here: they come out of Config.java's own reader calls, so a
reader that changes its range, or stops reading a key, fails this test rather than leaving a shipped
theme quietly ignored. Keys read by another class (`race_calendar` in RaceCalendar.java,
`wallpaper_moods` in WallpaperRotation.java) are not this file's business.

The same bounds are stated a second time, in the Keys table of themes/README.md, and that copy is the
one a theme author reads: the cell gives `0 to 300` for `wifi_label_width`, `1 to 8` for `columns`,
and tools/manage.py set refuses a value outside that cell before it writes anything
(manage.number_in_range). Nothing compared the two, so a reader whose range moved in Config.java left
the table telling authors a number the launcher would drop - `columns` becoming `1, 7` while the row
says 1 to 8 is a theme whose sixth and seventh tiles vanish - and set would keep writing the value it
was told was fine. The tests below hold each range cell to the bounds the reader is handed, and each
integer() reader to a row that states its range.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
import sys
import tempfile
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CONFIG_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"
KEYS_TABLE = ROOT / "themes" / "README.md"

# The readers that take no bounds, and what the launcher needs for each. `string(json, ...)` is matched
# before `stringList(json, ...)` would be by the alternation, so the longer name comes first.
PLAIN_READER = re.compile(r"\b(stringList|intArray|bool|string|color)\(\s*json\s*,\s*\"([a-z0-9_]+)\"")
# integer(json, "<key>", <fallback>, <min>, <max>): the fallback is a field or a literal, never a call.
RANGE_READER = re.compile(r"\binteger\(\s*json\s*,\s*\"([a-z0-9_]+)\"\s*,[^,()]+,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)")
COLOUR = re.compile(r"#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")

# Config.java's floor for `lunar_utc_offset` is -1, the "by locale" sentinel its Keys row's Meaning cell
# calls Unset rather than a number a theme is meant to pass, so the table states 0 to 14 where the reader
# takes -1 to 14. No other key may widen its floor like that; a test below keeps this one at -1.
SENTINEL_FLOOR = {"lunar_utc_offset": -1}

sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402  the same table reader tools/manage.py set judges a value against


def readers(java_text):
    """key -> (kind, (min, max) or None), for every reader of a theme's config.json in Config.java."""
    found = {}
    for kind, key in PLAIN_READER.findall(java_text):
        found[key] = (kind, None)
    for key, low, high in RANGE_READER.findall(java_text):
        found[key] = ("integer", (int(low), int(high)))
    return found


def problems(values, kinds):
    """[(key, why)] for the values the launcher would drop, judged by the reader the key is read with."""
    out = []
    for key, value in values.items():
        if key not in kinds:
            continue                      # read by another class, or not read at all: not this test's call
        kind, bounds = kinds[key]
        if kind == "bool":
            if not isinstance(value, bool):
                out.append((key, f"{json.dumps(value)} is not a boolean, and bool() keeps the fallback"))
        elif kind == "string":
            if not isinstance(value, str):
                out.append((key, f"{json.dumps(value)} is not a string, and string() keeps the fallback"))
        elif kind == "integer":
            if isinstance(value, bool) or not isinstance(value, (int, float)):
                out.append((key, f"{json.dumps(value)} is not a number, and integer() keeps the fallback"))
            else:
                low, high = bounds
                if not (low <= value <= high):
                    out.append((key, f"{json.dumps(value)} is outside {low} to {high}, and integer() "
                                     f"keeps the fallback"))
        elif kind == "stringList":
            if not isinstance(value, list) or any(not isinstance(item, str) for item in value):
                out.append((key, f"{json.dumps(value)} is not a list of strings"))
        elif kind == "intArray":
            if not isinstance(value, list) or any(isinstance(item, bool) or not isinstance(item, int)
                                                  for item in value):
                out.append((key, f"{json.dumps(value)} is not a list of integers"))
        elif kind == "color":
            if not isinstance(value, str) or not COLOUR.fullmatch(value):
                out.append((key, f"{json.dumps(value)} is not a #RRGGBB colour"))
    return out


def config_files(root=None):
    """Every config.json a theme or a list folder ships at any depth, sorted.

    A list folder may ship a whole theme under theme/, and its README tells the owner to edit that
    config.json and push it with tools/theme.sh (examples/philips-google-tv-ta7). Six folders ship
    one; a glob of examples/*/config.json reads none of them, so a value the launcher drops there
    went out without a word, the shape of the 320 above.
    """
    root = ROOT if root is None else root
    return sorted((root / "themes").glob("*/config.json")) + sorted((root / "examples").rglob("config.json"))


def table_ranges(text=None):
    """key -> (low, high) for every Keys-table row whose type cell is a range, such as '0 to 300'.

    Read the way tools/manage.py set reads it - manage.table_rows over the section, manage.plain to
    drop the code marks, manage.declared_range for the cell - so a cell this test understands and set
    does not (or the other way round) cannot happen either. A row's first cell can name two keys, and
    one of them may also be named in the Meaning column: only the first cell is a definition.
    """
    text = KEYS_TABLE.read_text(encoding="utf-8") if text is None else text
    if "## Keys" not in text:
        raise AssertionError("themes/README.md has no ## Keys section")
    out = {}
    for row in manage.table_rows(text[text.index("## Keys"):], "| Key |"):
        low, high = manage.declared_range(manage.plain(row[1]) if len(row) > 1 else "")
        if low is None:
            continue
        for key in re.findall(r"`([a-z_]+)`", row[0]):
            out[key] = (int(low), int(high))
    return out


def range_problems(table, kinds):
    """[(key, why)] where the Keys table's range and Config.java's integer() disagree, or one is missing."""
    out = []
    for key, (low, high) in sorted(table.items()):
        kind, bounds = kinds.get(key, ("", None))
        if kind != "integer":
            out.append((key, f"themes/README.md gives `{key}` the range {low} to {high} and Config.java "
                             f"does not read it with integer()"))
        elif bounds != (SENTINEL_FLOOR.get(key, low), high):
            out.append((key, f"themes/README.md tells a theme author {low} to {high} and Config.java keeps "
                             f"{bounds[0]} to {bounds[1]}"))
    for key, (kind, bounds) in sorted(kinds.items()):
        if kind == "integer" and key not in table:
            out.append((key, f"Config.java reads `{key}` with integer() bounds {bounds[0]} to {bounds[1]} "
                             f"and the Keys table states no range for it"))
    return out


class ShippedConfigValuesTest(unittest.TestCase):
    def setUp(self):
        self.kinds = readers(CONFIG_JAVA.read_text(encoding="utf-8"))

    def test_the_readers_are_there(self):
        # A parser that read nothing would make every check below pass for any value.
        self.assertGreaterEqual(len(self.kinds), 90,
                                "far fewer config.json readers found in Config.java than before")
        for key, kind in (("dock_band", "bool"), ("greeting_name", "string"), ("columns", "integer"),
                          ("dock_apps", "stringList"), ("input_key_keycodes", "intArray"),
                          ("background_color", "color")):
            with self.subTest(key=key):
                self.assertEqual(self.kinds.get(key, ("", None))[0], kind,
                                 f"Config.java no longer reads `{key}` with {kind}()")
        self.assertEqual(self.kinds.get("wifi_label_width"), ("integer", (0, 300)),
                         "wifi_label_width's bounds moved; the example and the Keys row follow them")

    def test_no_shipped_value_is_one_the_launcher_drops(self):
        files = config_files()
        self.assertGreaterEqual(len(files), 50, "fewer config.json files found than the tree ships")
        self.assertTrue(any(path.parent.name == "theme" for path in files),
                        "no list folder's theme/config.json was read: is the walk one level deep again?")
        checked = 0
        for path in files:
            values = json.loads(path.read_text(encoding="utf-8"))
            checked += sum(1 for key in values if key in self.kinds)
            for key, why in problems(values, self.kinds):
                with self.subTest(file=str(path.relative_to(ROOT)), key=key):
                    self.fail(f"{path.relative_to(ROOT)} sets `{key}` and {why}: the launcher shows its "
                              f"built-in value instead, and nothing says so")
        self.assertGreaterEqual(checked, 150, "the sweep read far fewer values than the tree carries")

    def test_a_value_in_a_theme_folder_below_a_list_folder_is_reported(self):
        # The shape of the 320 above, in the file examples/philips-google-tv-ta7/README.md tells the
        # owner to edit: 42 columns is outside the 1 to 8 the launcher keeps, and a one-level glob
        # never read the file it is in.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            nested = root / "examples" / "acme-1" / "theme" / "config.json"
            nested.parent.mkdir(parents=True)
            nested.write_text('{"columns": 42, "dock_apps": ["com.example"]}\n', encoding="utf-8")
            top = root / "examples" / "acme-1" / "config.json"
            top.write_text('{"columns": 5}\n', encoding="utf-8")
            files = config_files(root)
            self.assertEqual([path.relative_to(root).as_posix() for path in files],
                             ["examples/acme-1/config.json", "examples/acme-1/theme/config.json"])
            reported = dict(problems(json.loads(nested.read_text(encoding="utf-8")), self.kinds))
            self.assertEqual(sorted(reported), ["columns"])
            self.assertIn("outside 1 to 8", reported["columns"])

    def test_the_acer_example_keeps_the_network_name_it_documents(self):
        # The fix for the 320 above is to ask for a width the launcher keeps, not to stop asking: the
        # folder's README says the Wi-Fi pill shows the network name.
        path = ROOT / "examples" / "acer-ar55ar2851udpro" / "config.json"
        values = json.loads(path.read_text(encoding="utf-8"))
        self.assertTrue(values.get("icon_only_buttons"), "the name only matters with icon_only_buttons")
        width = values.get("wifi_label_width")
        low, high = self.kinds["wifi_label_width"][1]
        self.assertIsInstance(width, int, "wifi_label_width has to be a number the launcher reads")
        self.assertGreater(width, 0, "0 is icon only: the README says the name shows")
        self.assertLessEqual(width, high, f"{width} is past the {high} the launcher keeps")

    def test_the_readers_read_the_shipped_files(self):
        values = {"dock_band": True, "greeting_name": "Hall", "columns": 6, "dock_apps": ["a.b"],
                  "input_key_keycodes": [563], "background_color": "#101418"}
        self.assertEqual(problems(values, self.kinds), [], "the reader reported a good file")

    def test_a_wrong_type_is_reported(self):
        cases = {
            "dock_band": (3, "not a boolean"),                  # bool() wants a Boolean
            "greeting_name": (42, "not a string"),              # string() wants a String
            "columns": ("6", "not a number"),                   # integer() wants a Number
            "dock_apps": ("com.example", "not a list"),         # stringList() wants an array
            "input_key_keycodes": (["563"], "not a list of integers"),   # intArray() wants integers
            "background_color": ("red", "colour"),              # color() wants #RRGGBB
        }
        reported = dict(problems({key: value for key, (value, _) in cases.items()}, self.kinds))
        self.assertEqual(sorted(reported), sorted(cases), "a value of the wrong type was let through")
        for key, (_, why) in cases.items():
            with self.subTest(key=key):
                self.assertIn(why, reported[key])

    def test_a_number_outside_the_range_is_reported(self):
        # integer() is `int i = ((Number) v).intValue(); return i < min || i > max ? fallback : i;`, so
        # both ends are refused, and so is a fraction on the wrong side of one.
        for key, value in (("columns", 9), ("columns", 0), ("wifi_label_width", 320), ("wifi_label_width", -1),
                           ("dock_opacity", 29), ("sky_intensity", 101)):
            with self.subTest(key=key, value=value):
                reported = dict(problems({key: value}, self.kinds))
                self.assertIn(key, reported)
                low, high = self.kinds[key][1]
                self.assertIn(f"outside {low} to {high}", reported[key])

    def test_both_ends_of_every_range_are_kept(self):
        for key, (kind, bounds) in self.kinds.items():
            if kind != "integer":
                continue
            low, high = bounds
            for value in (low, high):
                with self.subTest(key=key, value=value):
                    self.assertEqual(problems({key: value}, self.kinds), [])

    def test_a_key_another_class_reads_is_left_alone(self):
        # race_calendar (RaceCalendar.java) and wallpaper_moods (WallpaperRotation.java) are read
        # elsewhere, so this sweep must not judge them.
        self.assertNotIn("race_calendar", self.kinds)
        self.assertNotIn("wallpaper_moods", self.kinds)
        self.assertEqual(problems({"race_calendar": [{"round": 1}], "wallpaper_moods": {"morning": []}},
                                  self.kinds), [])


class KeysTableRangesTest(unittest.TestCase):
    """The Keys table's range cells are the bounds Config.java's integer() calls keep.

    The table is the copy a theme author reads and the one tools/manage.py set refuses a value against, so
    a range that moved on either side leaves the page telling authors a number the launcher drops: set writes
    it, the push says nothing, and the launcher keeps its own default.
    """

    def setUp(self):
        self.kinds = readers(CONFIG_JAVA.read_text(encoding="utf-8"))
        self.table = table_ranges()

    def test_every_range_in_the_table_is_the_one_the_reader_keeps(self):
        # A table read as empty, or a Java parse that found no integer() call, would agree with anything.
        self.assertGreaterEqual(len(self.table), 13,
                                "fewer range cells found in the Keys table than before")
        self.assertGreaterEqual(sum(1 for kind, _ in self.kinds.values() if kind == "integer"), 13,
                                "fewer integer() readers found in Config.java than before")
        # The two the page's own examples lean on: columns decides how many tiles fit, and 0 is the one
        # wifi_label_width value MainActivity reads as "icon only", which is why the 320 above went wrong.
        self.assertEqual(self.table.get("columns"), (1, 8))
        self.assertEqual(self.table.get("wifi_label_width"), (0, 300))
        # One failure per drifted key, so a range that moved is named on the side that moved it.
        for key, why in range_problems(self.table, self.kinds):
            with self.subTest(key=key):
                self.fail(f"{why}: a theme set from the page is dropped without a word, and tools/manage.py "
                          f"set keeps writing it")

    def test_the_lunar_offset_keeps_its_unset_sentinel(self):
        # The one row whose numbers differ on purpose: Config.java's floor is -1, the "by locale" sentinel,
        # so SENTINEL_FLOOR lets the table state 0 to 14. If the reader ever stops taking -1, the sentinel
        # is stale and the table would be hiding a real drift behind an exemption.
        self.assertEqual(self.kinds.get("lunar_utc_offset"), ("integer", (-1, 14)),
                         "lunar_utc_offset no longer reads its -1 'by locale' sentinel")
        self.assertEqual(self.table.get("lunar_utc_offset"), (0, 14),
                         "the Keys row for lunar_utc_offset no longer states 0 to 14")
        self.assertEqual(SENTINEL_FLOOR, {"lunar_utc_offset": -1},
                         "another key was given a sentinel floor; the table has to be held to it by name")

    def test_a_range_that_moved_on_either_side_is_reported(self):
        # A table and a set of readers written here, not read from the tree: this is the readers' own test,
        # so it has to fail for the same reason whatever Config.java and the page say today.
        kinds = {"sky_intensity": ("integer", (0, 90)), "columns": ("integer", (1, 8)),
                 "lunar_utc_offset": ("integer", (-1, 14)), "dock_band": ("bool", None)}
        table = {"sky_intensity": (0, 100), "columns": (1, 8), "lunar_utc_offset": (0, 14)}
        reported = dict(range_problems(table, kinds))
        self.assertEqual(sorted(reported), ["sky_intensity"], "a range that did not move was reported")
        self.assertIn("0 to 100", reported["sky_intensity"])
        self.assertIn("0 to 90", reported["sky_intensity"])
        # A row with a range for a key read some other way is the page promising a number nothing keeps.
        reported = dict(range_problems(dict(table, dock_band=(0, 1), dock_columns=(1, 8)), kinds))
        self.assertIn("dock_band", reported)
        self.assertIn("does not read it with integer()", reported["dock_band"])
        self.assertIn("dock_columns", reported)
        self.assertIn("does not read it with integer()", reported["dock_columns"])
        # And a reader whose key lost its row: an author editing that key is told nothing at all.
        reported = dict(range_problems(table, dict(kinds, tile_glow=("integer", (0, 50)))))
        self.assertIn("tile_glow", reported)
        self.assertIn("states no range for it", reported["tile_glow"])
        # A wider floor is a drift too, except for the one key the table may not state it for.
        reported = dict(range_problems(dict(table, columns=(-1, 8)), kinds))
        self.assertIn("columns", reported)
        self.assertIn("-1 to 8", reported["columns"])
        self.assertEqual(range_problems({"columns": (1, 8), "sky_intensity": (0, 90),
                                         "lunar_utc_offset": (0, 14)}, kinds), [],
                         "lunar_utc_offset's sentinel floor was reported as a drift")

    def test_the_reader_reads_a_table(self):
        # The readers are Python over a Markdown page, so they are run over a page written here: the shape
        # of the real one, a row in the section after it, a row whose type cell is not a range at all, a
        # cell written with a link, and a key without code marks (the table's own style, which is what
        # tools/manage.py set reads too - an unbackticked first cell names no key).
        page = (
            "## Keys\n"
            "\n"
            "| Key | Type | Meaning |\n"
            "|---|---|---|\n"
            "| `columns` | 1 to 8 | Tiles per row |\n"
            "| `dock_apps` | list of package names | The dock row |\n"
            "| `wifi_label_width` | 0 to 300 | The network name |\n"
            "| `sky_intensity` | [0 to 90](https://example.test/x) | The backdrop |\n"
            "| `lunar_date` | bool | The lunisolar date |\n"
            "| columns | 2 to 4 | Not a key: no code marks |\n"
            "\n"
            "## Wallpapers\n"
            "\n"
            "| Key | Type | Meaning |\n"
            "|---|---|---|\n"
            "| `rows_below_dock` | 0 to 3 | Rows under the dock |\n"
        )
        self.assertEqual(table_ranges(page),
                         {"columns": (1, 8), "wifi_label_width": (0, 300), "sky_intensity": (0, 90)})

    def test_a_page_with_no_keys_section_is_reported(self):
        # Reading nothing must not read as "no drift": an empty table agrees with every reader.
        with self.assertRaises(AssertionError):
            table_ranges("# Themes\n\nNo table here.\n")


if __name__ == "__main__":
    unittest.main()

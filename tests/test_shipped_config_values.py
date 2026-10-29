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

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
CONFIG_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"

# The readers that take no bounds, and what the launcher needs for each. `string(json, ...)` is matched
# before `stringList(json, ...)` would be by the alternation, so the longer name comes first.
PLAIN_READER = re.compile(r"\b(stringList|intArray|bool|string|color)\(\s*json\s*,\s*\"([a-z0-9_]+)\"")
# integer(json, "<key>", <fallback>, <min>, <max>): the fallback is a field or a literal, never a call.
RANGE_READER = re.compile(r"\binteger\(\s*json\s*,\s*\"([a-z0-9_]+)\"\s*,[^,()]+,\s*(-?\d+)\s*,\s*(-?\d+)\s*\)")
COLOUR = re.compile(r"#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")


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


def config_files():
    """Every config.json a theme or an example ships, sorted."""
    return sorted(ROOT.glob("themes/*/config.json")) + sorted(ROOT.glob("examples/*/config.json"))


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
        checked = 0
        for path in files:
            values = json.loads(path.read_text(encoding="utf-8"))
            checked += sum(1 for key in values if key in self.kinds)
            for key, why in problems(values, self.kinds):
                with self.subTest(file=str(path.relative_to(ROOT)), key=key):
                    self.fail(f"{path.relative_to(ROOT)} sets `{key}` and {why}: the launcher shows its "
                              f"built-in value instead, and nothing says so")
        self.assertGreaterEqual(checked, 150, "the sweep read far fewer values than the tree carries")

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


if __name__ == "__main__":
    unittest.main()

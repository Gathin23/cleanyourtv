"""The accessibility-helper table names the keys, key lists and key switches each helper filters.

`docs/TROUBLESHOOTING.md`'s "Accessibility helper enabled but the remote button does nothing" opens
with a table of the two helpers and the keys they filter. Its Home button row named only
`KEYCODE_ALL_APPS`, while `HomeKeyService` also takes `KEYCODE_SETTINGS`, the codes in
`settings_key_keycodes` and `settings_key_scancodes`, and every `app_keys` entry, before it ever
looks at `KEYCODE_ALL_APPS`. The same section sends a dead gear button to `settings_key_scancodes`
"and install with `HOME_KEY_SERVICE=1`", and the README says `HOME_KEY_SERVICE=1` reads `app_keys`
too, so someone with only that helper and a dead app or gear button read the table and ruled out the
one helper that handles it.

The lists come from the code: the arrays each service reads, directly or through `Config`, have to
be named in that helper's row, so a list added to a helper later fails here until the table names
it too. `settings_key_scancodes` is read from `config.xml` only (no theme key), and the row says so.
A switch that makes a helper take a key it otherwise leaves alone, such as `home_long_press_home` for
an ordinary `KEYCODE_HOME`, has to be named next to that key, so the row does not rule the helper out
for a dead Home button. `consume_volume_keys` is not a row item: both helpers read it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_accessibility_flag_note import SECTION, TROUBLE, section  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"

# Table row (its first cell) -> the service it describes.
ROWS = {"Home button": "HomeKeyService", "Input button": "InputKeyService"}

# Keycodes a helper takes without any config entry, and the method in which it checks each one.
FIXED_KEYS = {
    "HomeKeyService": {"KEYCODE_ALL_APPS": "onKeyEvent", "KEYCODE_SETTINGS": "isSettingsKey"},
    "InputKeyService": {"KEYCODE_TV": "isInputKey", "KEYCODE_TV_INPUT": "isInputKey"},
}

RESOURCE_ARRAY = re.compile(r"R\.array\.(\w+)")
CONFIG_FIELD = re.compile(r"\bconfig\.(\w+)")
# `if (homeLongPressHome && event.getKeyCode() == KeyEvent.KEYCODE_HOME) {`: a switch that takes one key.
SWITCH_KEY = re.compile(r"if \((\w+) && event\.getKeyCode\(\) == KeyEvent\.(KEYCODE_\w+)\)\s*\{")
# `int[] settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes);` and the List<String> form.
CONFIG_ARRAY = re.compile(r"\b(\w+)\s*=\s*(?:new ArrayList<>\(Arrays\.asList\()?res\.get\w*Array\(R\.array\.(\w+)\)")


def table_rows(text):
    """The rows of the first markdown table in `text`, as {first cell: [cells]}."""
    rows = {}
    for line in text.split("\n"):
        if not line.startswith("|"):
            if rows:
                break
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if cells and not set(cells[0]) <= set("-: "):
            rows[cells[0]] = cells
    return rows


def config_arrays(config_java):
    """`Config` field -> the config.xml array it is read from."""
    return {field: name for field, name in CONFIG_ARRAY.findall(config_java)}


def arrays_read(service_java, arrays):
    """The config.xml arrays a service reads, itself or through a `Config` field."""
    names = set(RESOURCE_ARRAY.findall(service_java))
    names.update(arrays[field] for field in CONFIG_FIELD.findall(service_java) if field in arrays)
    return names


def method_body(java, name):
    """The text of method `name` from its signature to the brace that closes it."""
    match = re.search(r"\b" + re.escape(name) + r"\([^)]*\)\s*\{", java)
    return braced(java, match.end() - 1) if match else ""


def switches_taking_keys(service_java):
    """config key -> keycode, for each boolean switch with which `onKeyEvent` takes that key.

    The config key comes from the field's doc comment (`/** home_long_press_home: ... */`)."""
    body = method_body(service_java, "onKeyEvent")
    found = {}
    for match in SWITCH_KEY.finditer(body):
        field, keycode = match.groups()
        doc = re.search(r"/\*\* (\w+): (?:(?!\*/).)*\*/\s*private boolean " + re.escape(field) + ";",
                        service_java, re.DOTALL)
        if doc and "return true" in braced(body, match.end() - 1):
            found[doc.group(1)] = keycode
    return found


def braced(java, start):
    """The text from the brace at `start` to the brace that closes it."""
    depth = 0
    for i in range(start, len(java)):
        if java[i] == "{":
            depth += 1
        elif java[i] == "}":
            depth -= 1
            if depth == 0:
                return java[start:i + 1]
    return ""


class HelperTableTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rows = table_rows(section(TROUBLE.read_text(encoding="utf-8"), SECTION))
        cls.config = (JAVA / "Config.java").read_text(encoding="utf-8")
        cls.arrays = config_arrays(cls.config)
        cls.java = {service: (JAVA / f"{service}.java").read_text(encoding="utf-8")
                    for service in ROWS.values()}

    def test_the_table_has_a_row_for_each_helper(self):
        for row, service in ROWS.items():
            with self.subTest(row=row):
                self.assertIn(row, self.rows)
                self.assertIn(f"`com.gathin.tvhome/.{service}`", self.rows[row][1])

    def test_each_row_names_every_list_its_helper_reads(self):
        for row, service in ROWS.items():
            names = arrays_read(self.java[service], self.arrays)
            self.assertTrue(names, f"{service} reads no config.xml array: the reader is broken")
            missing = sorted(name for name in names if f"`{name}`" not in self.rows[row][2])
            with self.subTest(row=row):
                self.assertEqual(missing, [], f"{service} filters the keys in these lists, so its "
                                              "row in the helper table has to name them")

    def test_each_row_names_the_keycodes_its_helper_always_takes(self):
        for row, service in ROWS.items():
            for keycode, method in FIXED_KEYS[service].items():
                with self.subTest(row=row, keycode=keycode):
                    self.assertIn(f"KeyEvent.{keycode}", method_body(self.java[service], method),
                                  f"{service}.{method} no longer checks {keycode}: update the table")
                    self.assertIn(f"`{keycode}`", self.rows[row][2])

    def test_each_row_names_the_switches_that_make_its_helper_take_a_key(self):
        # home_long_press_home makes HomeKeyService take every Home press, so "not an ordinary
        # KEYCODE_HOME" alone would rule it out for a dead Home button on a TV that sets it.
        for row, service in ROWS.items():
            for switch, keycode in switches_taking_keys(self.java[service]).items():
                where = "`config.xml` or a theme" if f'"{switch}"' in self.config else "`config.xml` only"
                with self.subTest(row=row, switch=switch):
                    self.assertIn(f"`{keycode}` only with `{switch}`, {where}", self.rows[row][2],
                                  f"{service} takes {keycode} when {switch} is on, so its row has to say so")

    def test_the_switch_reader_finds_home_long_press_home(self):
        # A reader that found nothing would let the Home row drop the switch unnoticed.
        self.assertEqual(switches_taking_keys(self.java["HomeKeyService"]).get("home_long_press_home"),
                         "KEYCODE_HOME")
        java = ("/** a_key: takes A, Up/Down too. */\n    private boolean a;\n/** b_key: lets B by. */\n    private boolean b;\n"
                "protected boolean onKeyEvent(KeyEvent event) {\n"
                "    if (a && event.getKeyCode() == KeyEvent.KEYCODE_A) { if (up) { open(); } return true; }\n"
                "    if (b && event.getKeyCode() == KeyEvent.KEYCODE_B) { redirect(); return false; }\n"
                "    return false;\n}")
        self.assertEqual(switches_taking_keys(java), {"a_key": "KEYCODE_A"})

    def test_home_key_service_takes_its_lists_before_all_apps(self):
        # The point of the row: an app or gear button is handled even though Home is not ALL_APPS.
        body = method_body(self.java["HomeKeyService"], "onKeyEvent")
        all_apps = body.find("KEYCODE_ALL_APPS")
        # The lookups can be wrapped (#1855 picks a front-app target first), so the map names are searched for.
        for call in ("appScancodes", "isSettingsKey(event)", "appKeys"):
            with self.subTest(call=call):
                self.assertNotEqual(body.find(call), -1)
                self.assertLess(body.find(call), all_apps)

    def test_settings_key_scancodes_is_config_xml_only(self):
        # The row tells a reader where to set it; a theme key for it would change that sentence.
        self.assertNotIn('"settings_key_scancodes"', self.config)
        self.assertIn("getResources().getIntArray(R.array.settings_key_scancodes)", self.java["HomeKeyService"])
        self.assertIn("`settings_key_scancodes` (`config.xml` only)", self.rows["Home button"][2])

    def test_the_config_reader_finds_the_key_lists_in_config_java(self):
        # A reader that found nothing would let a helper's list go unchecked.
        for field, name in (("settingsKeycodes", "settings_key_keycodes"),
                            ("inputKeycodes", "input_key_keycodes"), ("appKeys", "app_keys")):
            with self.subTest(field=field):
                self.assertEqual(self.arrays.get(field), name)

    def test_the_readers_see_both_routes_into_a_list(self):
        self.assertEqual(arrays_read("x = getResources().getIntArray(R.array.a);\ny = config.b;",
                                     {"b": "b_list"}), {"a", "b_list"})
        self.assertEqual(config_arrays(
            "int[] settingsKeycodes = res.getIntArray(R.array.settings_key_keycodes);\n"
            "List<String> appKeys = new ArrayList<>(Arrays.asList(res.getStringArray(R.array.app_keys)));"),
            {"settingsKeycodes": "settings_key_keycodes", "appKeys": "app_keys"})

    def test_table_rows_reads_a_table_and_stops_after_it(self):
        sample = "text\n\n| A | B |\n| --- | --- |\n| one | `x` |\n\nprose\n| two | y |"
        self.assertEqual(table_rows(sample), {"A": ["A", "B"], "one": ["one", "`x`"]})

    def test_method_body_follows_nested_braces(self):
        java = "boolean f(int a) { if (a) { return x; } return y; }\nvoid g() { z(); }"
        self.assertEqual(method_body(java, "f"), "{ if (a) { return x; } return y; }")
        self.assertEqual(method_body(java, "missing"), "")


if __name__ == "__main__":
    unittest.main()

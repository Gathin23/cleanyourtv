"""Every key a shipped config.json sets is one the launcher reads from that file.

`tools/theme.sh` pushes a folder's `config.json`, and `Config.read()` overlays it on the built-in
`config.xml` setting by setting: every value is asked for by name, and a key no reader asks for is
dropped without a word, so the push says the theme is on the TV while the screen keeps the built-in
value. That is the same silent shape as a value outside its reader's range, one level up
(tests/test_shipped_config_values.py).

`examples/skyworth-7t861-a23/config.json` shipped `settings_fallback_components`, the vendor settings
screens `SystemSettings` walks - which it takes from `R.array.settings_fallback_components` in
`config.xml`, never from a theme, exactly as its own Keys row says ("list, `config.xml` only ... Not
read from a theme"). An owner who pushed that folder's config.json got the dock, the hidden app and the
Live TV package and no vendor settings entry, with nothing saying which of the four keys had been
ignored.

The readers are not copied in here: they come out of Config.java's own calls on the parsed overlay, so
a reader that is renamed or dropped fails this test rather than leaving the key it read quietly dead.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import re
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONFIG_JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "Config.java"
THEMES_README = ROOT / "themes" / "README.md"

# The readers Config.java calls on the overlay it read from the TV: bool(json, "columns", ...) and the
# two plain lookups a nested value takes, json.optJSONObject("wallpaper_moods") and
# json.optJSONArray("race_calendar").
NAMED_READER = re.compile(r"\b(?:bool|string|stringList|intArray|color|integer)\(\s*json\s*,\s*\"([a-z0-9_]+)\"")
# Config.festivalName(o) reads the festival from the overlay under another variable name.
FESTIVAL_READER = re.compile(r"\bstring\(\s*o\s*,\s*\"(festival|seasonal_skin)\"")
LOOKUP = re.compile(r"json\.optJSON(?:Object|Array)\(\s*\"([a-z0-9_]+)\"")


def overlay_keys(java_text):
    """Every key Config.java reads out of the pushed config.json."""
    return set(NAMED_READER.findall(java_text)) | set(LOOKUP.findall(java_text)) | set(FESTIVAL_READER.findall(java_text))


def shipped_configs():
    """(path, values) for every config.json a theme or an example folder ships, in path order."""
    paths = sorted((ROOT / "themes").glob("*/config.json")) + sorted((ROOT / "examples").glob("*/config.json"))
    return [(path, json.loads(path.read_text(encoding="utf-8"))) for path in paths]


def unread(values, known):
    """The keys of a config.json that no reader of the overlay asks for."""
    return [key for key in values if key not in known]


def key_rows(text):
    """key -> type cell, for every row of themes/README.md's Keys table (a row can name two keys)."""
    part = text[text.index("## Keys"):] if "## Keys" in text else text
    rows = {}
    for line in part.splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split(" | ")]
        if len(cells) < 2:
            continue
        for key in re.findall(r"`([a-z0-9_]+)`", cells[0]):
            rows.setdefault(key, cells[1])
    return rows


class ShippedConfigKeysTest(unittest.TestCase):
    def setUp(self):
        self.known = overlay_keys(CONFIG_JAVA.read_text(encoding="utf-8"))
        self.configs = shipped_configs()

    def test_every_shipped_key_is_read_by_the_launcher(self):
        problems = []
        for path, values in self.configs:
            for key in unread(values, self.known):
                problems.append(f"{path.relative_to(ROOT)}: {key} is not read from a theme's config.json "
                                f"by Config.java, so a push drops it and the launcher keeps its own value")
        self.assertEqual([], problems)

    def test_the_readers_and_the_files_were_really_read(self):
        # A run that read nothing, or half of Config.java, must not pass by finding no problems.
        self.assertGreaterEqual(len(self.known), 100, f"only {len(self.known)} overlay keys read out of Config.java")
        self.assertGreaterEqual(len(self.configs), 60, f"only {len(self.configs)} shipped config.json files")
        keys = {key for _, values in self.configs for key in values}
        self.assertGreaterEqual(len(keys), 50, f"only {len(keys)} distinct keys in the shipped configs")
        for expected in ("dock_apps", "hidden_apps", "live_tv_package", "wallpaper_moods"):
            self.assertIn(expected, self.known)

    def test_a_config_with_an_unread_key_is_named(self):
        # The rule, on a file written here: a key nobody reads is reported with its name.
        self.assertEqual(["settings_fallback_components"],
                         unread({"settings_fallback_components": ["com.example/com.example.Main"],
                                 "dock_apps": []}, self.known))

    def test_config_xml_only_keys_are_in_no_shipped_config(self):
        # The Keys table says which keys a theme cannot set; those are the ones to keep out, and the
        # reason the reader above matters is that the launcher cannot answer for them itself.
        rows = key_rows(THEMES_README.read_text(encoding="utf-8"))
        config_only = {key for key, kind in rows.items() if "config.xml" in kind}
        self.assertGreaterEqual(len(config_only), 2, f"the Keys table names {sorted(config_only)} as config.xml only")
        self.assertIn("settings_fallback_components", config_only)
        problems = [f"{path.relative_to(ROOT)} sets {key}, which the Keys table says is config.xml only"
                    for path, values in self.configs for key in values if key in config_only]
        self.assertEqual([], problems)


if __name__ == "__main__":
    unittest.main()

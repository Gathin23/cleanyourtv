"""tools/manage.py set refuses a value the launcher's closed-set string readers answer with a default.

Four keys in the Keys table name the values they take - `glow_style` (radial or horizon), `glass_style` (glass,
rim or halo), `wallpaper_order` (shuffle or sequential) and `clock_format` (12, 24 or system) - and the readers in
the launcher keep those and nothing else: Config.glowStyle() is `GLOW_HORIZON.equalsIgnoreCase(value.trim()) ?
GLOW_HORIZON : GLOW_RADIAL`, glassStyle() answers glass for anything else, WallpaperRotation.validOrder() is
`ORDER_SEQUENTIAL.equals(order) || ORDER_SHUFFLE.equals(order)` and ClockFormat applies "12" or "24" and lets
every other mode follow the TV. A string key of another type is refused by value_as since #3398, but these four
were read as free text: `set glow_style off` printed `glow_style = "off"`, wrote the theme and pushed it, and the
launcher answered radial - the same dead value #3429 removed from themes/cinematic and themes/red-rain.

The values come from the launcher, not from a second copy in the tool: the tests below read the GLOW_/GLASS_/
ORDER_ constants and the comparison each reader makes out of the Java, so a new value in the launcher fails here
instead of leaving set refusing a value the TV would have kept.
"""
import json
import re
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

JAVA = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome"
CONFIG_JAVA = JAVA / "Config.java"
ROTATION = JAVA / "WallpaperRotation.java"
CLOCK = JAVA / "ClockFormat.java"
README = ROOT / "themes" / "README.md"

# The four keys this rule is about, with a value none of them takes and the one the launcher answers instead.
REFUSED = {
    "glow_style": ("off", "radial"),
    "glass_style": ("frosted", "glass"),
    "wallpaper_order": ("random", "shuffle"),
    "clock_format": ("13", "system"),
}


def constants(source, prefix):
    """The string values of the NAME = "value" constants that start with prefix, as a set."""
    return set(re.findall(prefix + r'[A-Z_]+ = "([^"]+)"', source))


def keys_row(key):
    """The Keys-table row of a key in themes/README.md, as (type cell, meaning cell)."""
    text = README.read_text(encoding="utf-8")
    for row in manage.table_rows(text[text.index("## Keys"):], "| Key |"):
        if key in re.findall(r"`([a-z_]+)`", row[0]):
            return (manage.plain(row[1]), manage.plain(row[2]))
    return None


class SetEnumTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.theme = Path(self.tmp.name) / "my-theme"
        self.theme.mkdir()
        self.config = self.theme / "config.json"
        # cmd_set ends by pushing the theme with tools/theme.sh; the tests stop before the TV.
        patcher = mock.patch.object(manage, "run")
        self.push = patcher.start()
        self.push.return_value = 0
        self.addCleanup(patcher.stop)

    def set_key(self, key, value):
        """Runs set the way main() does; returns the refusal text, or None when the value was written."""
        try:
            with mock.patch("sys.stdout"):
                manage.cmd_set(key, value, str(self.theme))
        except SystemExit as stop:
            return str(stop.code)
        return None

    def written(self):
        return json.loads(self.config.read_text(encoding="utf-8")) if self.config.is_file() else None

    def test_every_closed_set_key_refuses_a_value_it_does_not_take(self):
        for key, (value, fallback) in REFUSED.items():
            with self.subTest(key=key):
                self.config.unlink(missing_ok=True)
                self.push.reset_mock()
                refusal = self.set_key(key, value)
                self.assertIsNotNone(refusal, f"{key} took {value!r}, which the launcher answers with {fallback}")
                self.assertIn(key, refusal)
                self.assertIn(repr(value), refusal, "the refusal should quote what was passed")
                for allowed in manage.ENUMS[key][0]:
                    self.assertIn(allowed, refusal, f"the refusal should name {allowed}, another value it takes")
                self.assertIn("Nothing was written or pushed", refusal)
                self.assertIsNone(self.written(), f"a refused value must not touch the theme ({key})")
                self.push.assert_not_called()

    def test_the_refusal_says_what_the_launcher_answers_instead(self):
        refusal = self.set_key("glow_style", "off")
        self.assertIn("radial", refusal)
        refusal = self.set_key("glass_style", "frosted")
        self.assertIn("glass", refusal)
        refusal = self.set_key("wallpaper_order", "random")
        self.assertIn("shuffle", refusal)
        refusal = self.set_key("clock_format", "13")
        self.assertIn("system", refusal)

    def test_the_style_keys_are_read_trimming_and_ignoring_case(self):
        # Config.glowStyle()/glassStyle() trim the string and compare with equalsIgnoreCase, so these are kept.
        for key, value, kept in (("glow_style", " HORIZON ", "HORIZON"), ("glass_style", " Rim ", "Rim"),
                                 ("glow_style", "radial", "radial"), ("glass_style", "halo", "halo")):
            with self.subTest(key=key, value=value):
                self.assertIsNone(self.set_key(key, value), f"{value!r} is a value {key} takes")
                self.assertEqual(self.written()[key], kept)

    def test_wallpaper_order_is_read_exactly(self):
        # validOrder() compares the string with equals, so a capital is not the value it looks like.
        refusal = self.set_key("wallpaper_order", "Sequential")
        self.assertIn("shuffle", refusal)
        self.assertIsNone(self.written())
        self.assertIsNone(self.set_key("wallpaper_order", " sequential "))
        self.assertEqual(self.written()["wallpaper_order"], "sequential")

    def test_clock_format_takes_12_24_and_system(self):
        for value in ("12", "24", "system", "System"):
            with self.subTest(value=value):
                self.assertIsNone(self.set_key("clock_format", value), f"{value!r} is a mode the clock keeps")
                self.assertEqual(self.written()["clock_format"], value.strip())
        self.assertIsNotNone(self.set_key("clock_format", "12-hour"))

    def test_a_value_written_is_still_the_text_the_author_typed(self):
        # The launcher trims every string it reads, so set writes the trimmed one and nothing else is changed.
        self.assertIsNone(self.set_key("glow_style", "horizon"))
        self.assertEqual(self.written(), {"glow_style": "horizon"})

    def test_a_string_key_with_no_closed_set_still_takes_any_text(self):
        for key, value in (("greeting_name", "off"), ("festival", "random"), ("locale", "vi")):
            with self.subTest(key=key):
                self.assertIsNone(self.set_key(key, value), f"{key} is free text, so {value!r} is fine")
                self.assertEqual(self.written()[key], value)

    def test_a_number_key_is_unaffected(self):
        self.assertIsNone(self.set_key("columns", "4"))
        self.assertEqual(self.written()["columns"], 4)

    def test_the_values_are_the_ones_the_launcher_keeps(self):
        config = CONFIG_JAVA.read_text(encoding="utf-8")
        rotation = ROTATION.read_text(encoding="utf-8")
        self.assertEqual(set(manage.ENUMS["glow_style"][0]), constants(config, "GLOW_"))
        self.assertEqual(set(manage.ENUMS["glass_style"][0]), constants(config, "GLASS_"))
        self.assertEqual(set(manage.ENUMS["wallpaper_order"][0]), constants(rotation, "ORDER_"))
        # ClockFormat has no constant for "system": it is the mode the clock keeps for anything that is not 12
        # or 24, so the third value is named here and the two the Java compares are read out of it.
        self.assertEqual(set(manage.ENUMS["clock_format"][0]), {"12", "24", "system"})

    def test_every_closed_set_key_has_a_row_that_names_its_values(self):
        for key, (values, fallback, _) in manage.ENUMS.items():
            with self.subTest(key=key):
                row = keys_row(key)
                self.assertIsNotNone(row, f"{key} is read as a closed set but themes/README.md has no row for it")
                cell = " ".join(row)
                for value in values:
                    self.assertIn(value, cell, f"the {key} row should name {value}, a value the launcher keeps")
                self.assertIn(fallback, cell, f"the {key} row should say what it answers otherwise")

    def test_enum_value_leaves_a_key_with_no_closed_set_alone(self):
        self.assertEqual(manage.enum_value("greeting_name", "  off  "), "  off  ")
        self.assertEqual(manage.enum_value("not_a_key", "off"), "off")


if __name__ == "__main__":
    unittest.main()

"""A colour `tools/manage.py set` writes is one the launcher keeps, not one it answers with its own colour.

themes/README.md gives two keys the `#RRGGBB` cell, `background_color` and `accent_color`, and the set script's
docstring promises it "refuses one the launcher would ignore". A colour is a String, so the type check passed
anything, and neither reader is Config.string(): both parse the colour themselves and answer a value they cannot
read with the colour the theme already had, which is what makes the write a no-op on the TV while set reports it:

    // Config.color(), the background
    int c = Color.parseColor(((String) v).trim());
    return c | 0xFF000000;            // catch (IllegalArgumentException e) { return fallback; }

    // Festival.parseColor(), the festive skin's accent
    if (!t.matches("#[0-9A-Fa-f]{6}")) return 0;   // 0 -> the skin's own accent

Measured on 3b6b3a15 with `set background_color '#12345' --theme <tmp>`: the file was written with `"#12345"`,
the theme was pushed, and Config keeps black - nothing on the TV changed. The same for `set accent_color gold`
and `set accent_color '#12345'`: Festival.parseColor() answers 0, so the skin keeps diya gold. The alpha half is
the quieter one: `#80FF0000` is read and then ORed opaque, so the theme is written as `#FFFF0000` on the TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

# The two keys the Keys table gives the `#RRGGBB` cell.
COLOUR_KEYS = {"background_color", "accent_color"}
CONFIG_JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/Config.java"
FESTIVAL_JAVA = ROOT / "launcher/app/src/main/java/com/gathin/tvhome/Festival.java"


class ColourValueTest(unittest.TestCase):
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

    def test_a_five_digit_background_is_refused_with_what_the_launcher_would_do(self):
        # `#12345` is a hex colour with a digit missing: Color.parseColor() throws and Config keeps black.
        refusal = self.set_key("background_color", "#12345")
        self.assertIsNotNone(refusal, "a malformed colour was written, and Config.color() keeps the built-in black")
        self.assertIn("background_color", refusal)
        self.assertIn("#12345", refusal)
        self.assertIn("Color.parseColor()", refusal)
        self.assertIn("Nothing was written or pushed", refusal)
        self.assertIsNone(self.written(), "a refused colour must not touch the theme")
        self.push.assert_not_called()

    def test_a_colour_name_is_refused_for_the_accent_but_read_for_the_background(self):
        # Festival.parseColor() reads `#[0-9A-Fa-f]{6}` and nothing else, so a name leaves the skin's own accent;
        # Config.color() hands a value with no '#' to Color.parseColor(), which knows names such as red.
        refusal = self.set_key("accent_color", "gold")
        self.assertIsNotNone(refusal, "accent_color took a name, and Festival.parseColor() answers 0 for one")
        self.assertIn("Festival.parseColor()", refusal)
        self.assertIsNone(self.written())
        self.push.assert_not_called()
        self.assertIsNone(self.set_key("background_color", "red"))
        self.assertEqual("red", self.written()["background_color"])

    def test_an_accent_with_an_alpha_channel_is_refused(self):
        # #AARRGGBB is a colour to Color.parseColor() and not to Festival.parseColor(), which wants six digits.
        refusal = self.set_key("accent_color", "#FF7A1AFF")
        self.assertIsNotNone(refusal, "an eight-digit colour was written for a key that reads six")
        self.assertIn("accent_color", refusal)
        self.assertIsNone(self.written())

    def test_a_background_alpha_is_refused_with_the_colour_the_tv_would_show(self):
        # Config.color() ORs 0xFF000000, so #80FF0000 reaches the TV as #FFFF0000: the write would not be the
        # colour that was asked for, and nothing in it would say so.
        refusal = self.set_key("background_color", "#80FF0000")
        self.assertIsNotNone(refusal, "an alpha channel was written, and the launcher overwrites it")
        self.assertIn("opaque", refusal)
        self.assertIn("#FFFF0000", refusal)
        self.assertIsNone(self.written())
        self.push.assert_not_called()

    def test_an_opaque_eight_digit_background_is_taken(self):
        self.assertIsNone(self.set_key("background_color", "#FF000000"))
        self.assertEqual("#FF000000", self.written()["background_color"])

    def test_six_hex_digits_are_taken_and_trimmed(self):
        # The launcher trims every colour it reads (`((String) v).trim()`), so the value written is the trimmed one.
        self.assertIsNone(self.set_key("background_color", "  #0A0B0C "))
        self.assertEqual("#0A0B0C", self.written()["background_color"])
        self.assertIsNone(self.set_key("accent_color", " #FF7A1A "))
        self.assertEqual("#FF7A1A", self.written()["accent_color"])

    def test_another_string_key_is_not_held_to_a_colour(self):
        # greeting_name's row is `string`, read by Config.string(): "#12345" is the text it is.
        self.assertIsNone(self.set_key("greeting_name", "#12345"))
        self.assertEqual("#12345", self.written()["greeting_name"])

    def test_the_refusals_quote_the_readers_the_launcher_has(self):
        # Pinned to the two parsers, so a reader that changes shape stops agreeing with the message that quotes it.
        config = CONFIG_JAVA.read_text(encoding="utf-8")
        self.assertIn("Color.parseColor(((String) v).trim())", config)
        self.assertIn("return c | 0xFF000000;", config)
        self.assertIn("} catch (IllegalArgumentException e) {", config)
        festival = FESTIVAL_JAVA.read_text(encoding="utf-8")
        self.assertIn('if (!t.matches("#[0-9A-Fa-f]{6}")) return 0;', festival)


class KeysTableTest(unittest.TestCase):
    """The table is where set learns which keys are colours, and where an author reads the rule."""

    def colour_rows(self):
        return {item["id"]: item for item in manage.switches()
                if (item.get("type") or "").strip() == manage.COLOUR_CELL}

    def test_the_shipped_colour_rows_are_the_two_keys(self):
        # A key added to the table with this cell joins the refusal above; one that moves off it stops being read
        # this way, so the set of keys is pinned here rather than assumed.
        self.assertEqual(COLOUR_KEYS, set(self.colour_rows()))

if __name__ == "__main__":
    unittest.main()

"""A keycode list `tools/manage.py set` writes is one the launcher keeps, entry by entry.

themes/README.md hands a theme author `set settings_key_keycodes '[83]'` for a remote's gear button, and the
set script's own docstring promises it "refuses one the launcher would ignore". The list was read as a list
and nothing else, and Config.intArray() is stricter than that in a way that costs the whole key:

    if (!(item instanceof Number)) return fallback;
    out[i] = ((Number) item).intValue();
    if (out[i] < 1) return fallback;

so one entry that is a word, or a number under 1, and the launcher keeps config.xml's `<integer-array>`, which
ships empty for both keys - the gear the owner was adding opens nothing, while set printed
`settings_key_keycodes = [83, 0] in ~/my-theme/config.json` and pushed the file. Measured on d451b175 with
`set settings_key_keycodes '[83, 0]'` and `set input_key_keycodes '["KEYCODE_TV"]'`: both were written and
both are dropped by intArray() as a whole. A list of any other kind is not read this way - Config.stringList()
drops the one entry it cannot read and keeps the rest - so the rule below is the keycode cell's alone.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

# The two keys the Keys table gives the `list of keycodes` cell, and the sentence both rows now carry.
KEYCODE_KEYS = {"settings_key_keycodes", "input_key_keycodes"}


class KeycodeListTest(unittest.TestCase):
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

    def test_a_zero_entry_is_refused_with_what_the_launcher_would_do(self):
        # `[83, 0]` is the shape a reader types when they are copying `[83]` and adding a code by hand.
        refusal = self.set_key("settings_key_keycodes", "[83, 0]")
        self.assertIsNotNone(refusal, "a list holding 0 was written, and intArray() drops the whole list for it")
        self.assertIn("settings_key_keycodes", refusal)
        self.assertIn("0 is below 1", refusal)
        self.assertIn("ignores the whole list", refusal)
        self.assertIsNone(self.written(), "a refused list must not touch the theme")
        self.push.assert_not_called()

    def test_a_list_of_words_is_refused(self):
        # The key is a list of keycodes, not of the constants' names: intArray() has no answer for a String.
        refusal = self.set_key("input_key_keycodes", '["KEYCODE_TV"]')
        self.assertIsNotNone(refusal, "a list of words was written, and intArray() keeps config.xml's array for it")
        self.assertIn("'KEYCODE_TV' is not a number", refusal)
        self.assertIsNone(self.written())
        self.push.assert_not_called()

    def test_a_fraction_is_refused_with_the_number_the_launcher_would_read(self):
        # Number.intValue() truncates rather than dropping, so 83.5 is key 83 - not the button that was meant.
        refusal = self.set_key("settings_key_keycodes", "[83.5]")
        self.assertIsNotNone(refusal, "a fraction was written, and the launcher reads it as key 83")
        self.assertIn("83.5 is not a whole number", refusal)
        self.assertIn("reads it as 83", refusal)
        self.assertIsNone(self.written())

    def test_both_keys_take_whole_numbers(self):
        for key in sorted(KEYCODE_KEYS):
            with self.subTest(key=key):
                self.assertIsNone(self.set_key(key, "[83, 176]"))
                self.assertEqual([83, 176], self.written()[key])

    def test_a_whole_number_written_with_a_decimal_point_is_taken(self):
        # 83.0 is a whole number, and the launcher reads it as 83: refusing it would be stricter than intArray().
        self.assertIsNone(self.set_key("settings_key_keycodes", "[83.0]"))
        self.assertEqual(83, int(self.written()["settings_key_keycodes"][0]))

    def test_an_empty_list_is_taken(self):
        # `[]` is how an owner clears buttons they added; intArray() keeps an empty array as it is.
        self.assertIsNone(self.set_key("settings_key_keycodes", "[]"))
        self.assertEqual([], self.written()["settings_key_keycodes"])

    def test_a_list_of_another_kind_is_not_held_to_keycodes(self):
        # dock_apps is a list of package names, read by stringList(), which drops only the entry it cannot read.
        self.assertIsNone(self.set_key("dock_apps", '["com.example.player", "com.example.tv"]'))
        self.assertEqual(["com.example.player", "com.example.tv"], self.written()["dock_apps"])


class KeycodeProblemTest(unittest.TestCase):
    def test_the_entries_intArray_would_keep(self):
        self.assertIsNone(manage.keycode_problem([]))
        self.assertIsNone(manage.keycode_problem([83, 176]))
        self.assertIsNone(manage.keycode_problem([1]))
        self.assertIsNone(manage.keycode_problem([83.0]))

    def test_the_entries_it_would_throw_the_list_away_for(self):
        self.assertIn("is not a number", manage.keycode_problem(["83"]))
        self.assertIn("is not a number", manage.keycode_problem([True]))
        self.assertIn("0 is below 1", manage.keycode_problem([83, 0]))
        self.assertIn("-4 is below 1", manage.keycode_problem([-4]))
        self.assertIn("not a whole number", manage.keycode_problem([83.5]))


class KeysTableTest(unittest.TestCase):
    """The table is where set learns which keys are keycode lists, and where an author reads the rule."""

    def keycode_rows(self):
        return {item["id"]: item for item in manage.switches() if (item.get("type") or "").strip() == manage.KEYCODES}

    def test_the_shipped_keycode_rows_are_the_two_keys(self):
        # A key added to the table with this cell joins the refusal above; one that moves off it stops being read
        # this way, so the set of keys is pinned here rather than assumed.
        self.assertEqual(KEYCODE_KEYS, set(self.keycode_rows()))

    def test_every_keycode_row_states_the_rule_set_applies(self):
        for key, item in sorted(self.keycode_rows().items()):
            with self.subTest(key=key):
                self.assertEqual("list", manage.declared_kind(item), f"{key} is not read as a list")
                self.assertIn("whole number", item["what"], f"{key}'s row does not state the whole-number rule")
                self.assertIn("at least 1", item["what"], f"{key}'s row does not say the floor is 1")


if __name__ == "__main__":
    unittest.main()

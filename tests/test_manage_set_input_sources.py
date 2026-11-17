"""The Inputs-menu values `tools/manage.py set` writes are ones the launcher reads.

themes/README.md hands a theme author two keys whose value is a number the Inputs menu sends to the set's own
source picker: `input_sources`, one `HW<n>=<number>` mapping per line, and `source_panel_extra`, one
`"name=value"` int extra. Both are read by parsers of their own rather than by Config.stringList(), and both
answer a value they cannot read with "nothing" instead of an error:

    // InputSources.mapped()
    int equals = mapping.indexOf('=');
    if (equals <= 0 || !mapping.substring(0, equals).trim().equals(input)) continue;
    String value = mapping.substring(equals + 1).trim();
    if (value.equals("-")) return -1;
    try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return null; }

    // MainActivity.openSourcePanel()
    int eq = sourcePanelExtra.indexOf('=');
    if (eq > 0) {
        i.putExtra(sourcePanelExtra.substring(0, eq).trim(),
                   Integer.parseInt(sourcePanelExtra.substring(eq + 1).trim()));
    } catch (NumberFormatException ignored) {}

so an entry with no '=' at all, with the '=' first, with a '/' in its left half (the id it is compared against
is the last part after the input id's own '/'), with a right half that is neither `-` nor a whole number, or a
second entry for an input already mapped is skipped without a word, and the row falls back to the standard path
while `set` has printed the value as written. Measured on b9538048 with `set input_sources '["HW5=abc","HW6"]'`
and `set source_panel_extra 'control_center_command'`: both were written, both are dropped by those readers.

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


# The entries the shipped example configs carry, plus the two shapes the Keys row gives.
GOOD_SOURCES = ["HW5=23", "HW6=24", "HW7=25", "HW1=2", "HW2=-"]


class SetTest(unittest.TestCase):
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

    def refused(self, key, value, *words):
        refusal = self.set_key(key, value)
        self.assertIsNotNone(refusal, f"{value!r} was written")
        for word in words:
            self.assertIn(word, refusal)
        self.assertIn("Nothing was written or pushed.", refusal)
        self.push.assert_not_called()
        return refusal


class InputSourcesTest(SetTest):
    def test_takes_the_mappings_the_shipped_lists_carry(self):
        for value in GOOD_SOURCES:
            with self.subTest(value=value):
                self.assertIsNone(self.set_key("input_sources", json.dumps([value])))
                self.assertEqual([value], self.written()["input_sources"])

    def test_takes_the_whole_list_at_once(self):
        self.assertIsNone(self.set_key("input_sources", json.dumps(GOOD_SOURCES)))
        self.assertEqual(GOOD_SOURCES, self.written()["input_sources"])
        self.push.assert_called_once()

    def test_takes_an_empty_list(self):
        # A theme that maps nothing sends the array away and the Inputs menu falls back for every row.
        self.assertIsNone(self.set_key("input_sources", "[]"))
        self.assertEqual([], self.written()["input_sources"])

    def test_refuses_a_right_half_mapped_cannot_read(self):
        for right in ("abc", "", "23a", "2.5"):
            with self.subTest(right=right):
                self.refused("input_sources", json.dumps([f"HW5={right}"]), "HW5", "not mapped")
                self.assertIsNone(self.written())

    def test_refuses_a_number_outside_an_int(self):
        # Integer.parseInt throws for a value an int cannot hold, and mapped() answers it with null.
        self.refused("input_sources", json.dumps(["HW5=99999999999"]), "HW5", "not mapped")

    def test_refuses_an_entry_with_no_equals(self):
        self.refused("input_sources", json.dumps(["HW6"]), "no '='", "not mapped")

    def test_refuses_an_entry_with_nothing_before_the_equals(self):
        for entry in ("=2", " =2"):
            with self.subTest(entry=entry):
                self.refused("input_sources", json.dumps([entry]), "nothing before", "not mapped")

    def test_refuses_a_slash_in_the_left_half(self):
        # mapped() compares the left half with the input id's last part, so a full id can never match it.
        self.refused("input_sources", json.dumps(["com.mediatek.tvinput/.hdmi.HDMIInputService/HW5=23"]),
                     "last part", "matches no input")

    def test_refuses_a_second_entry_for_an_input_already_mapped(self):
        self.refused("input_sources", json.dumps(["HW5=23", "HW5=24"]), "HW5", "second time", "never read")
        self.assertIsNone(self.written())

    def test_refuses_an_entry_that_is_not_a_string(self):
        self.refused("input_sources", json.dumps([5]), "not a string")

    def test_a_half_may_be_padded_and_the_launcher_still_reads_it(self):
        # mapped() trims both halves before it compares and parses, so the padding is kept as written.
        self.assertIsNone(self.set_key("input_sources", json.dumps(["HW5 = 23"])))
        self.assertEqual(["HW5 = 23"], self.written()["input_sources"])


class PanelExtraTest(SetTest):
    def test_takes_the_pair_the_shipped_theme_carries(self):
        self.assertIsNone(self.set_key("source_panel_extra", "control_center_command=100"))
        self.assertEqual("control_center_command=100", self.written()["source_panel_extra"])
        self.push.assert_called_once()

    def test_takes_a_quoted_value(self):
        self.assertIsNone(self.set_key("source_panel_extra", '"control_center_command=100"'))
        self.assertEqual("control_center_command=100", self.written()["source_panel_extra"])

    def test_keeps_the_empty_value(self):
        # Empty is the built-in default: no extra at all, which is what a theme that needs none carries.
        self.assertIsNone(self.set_key("source_panel_extra", '""'))
        self.assertEqual("", self.written()["source_panel_extra"])

    def test_refuses_a_value_with_no_equals(self):
        self.refused("source_panel_extra", "control_center_command", "name before", "without it")

    def test_refuses_a_value_with_nothing_before_the_equals(self):
        for value in ("=100", " =100", "100"):
            with self.subTest(value=value):
                self.refused("source_panel_extra", value, "name before")

    def test_refuses_a_half_that_is_not_a_whole_number(self):
        for value in ("control_center_command=abc", "control_center_command=", "control_center_command=1.5"):
            with self.subTest(value=value):
                self.refused("source_panel_extra", value, "no extra at all")
                self.assertIsNone(self.written())

    def test_a_half_may_be_padded_and_the_extra_is_still_sent(self):
        self.assertIsNone(self.set_key("source_panel_extra", "control_center_command = 100"))
        self.assertEqual("control_center_command = 100", self.written()["source_panel_extra"])


class ReadersTest(unittest.TestCase):
    """The theme rows say what set refuses."""

    def test_the_two_rows_say_what_set_refuses(self):
        sources = manage.find("input_sources")
        extra = manage.find("source_panel_extra")
        self.assertEqual("list", manage.declared_kind(sources))
        self.assertEqual("string", manage.declared_kind(extra))
        for item in (sources, extra):
            with self.subTest(key=item["id"]):
                self.assertIn("manage.py", item["what"])
                self.assertIn("refuses", item["what"])


class ShippedValuesTest(unittest.TestCase):
    """A config.json hand-edited rather than written by set is held to the same shape."""

    def shipped(self, key):
        """Every (path, value) a shipped config.json carries for the key."""
        out = []
        for folder in ("examples", "themes"):
            for path in sorted((ROOT / folder).glob("*/config.json")):
                config = json.loads(path.read_text(encoding="utf-8"))
                if isinstance(config, dict) and key in config:
                    out.append((path, config[key]))
        return out

    def test_every_shipped_mapping_is_one_the_launcher_reads(self):
        shipped = self.shipped("input_sources")
        self.assertGreaterEqual(len(shipped), 3, "the sweep read the shipped example configs")
        for path, values in shipped:
            with self.subTest(path=str(path.relative_to(ROOT))):
                self.assertIsInstance(values, list)
                self.assertIsNone(manage.input_source_problem(values))

    def test_every_shipped_extra_is_one_the_launcher_sends(self):
        shipped = self.shipped("source_panel_extra")
        self.assertGreaterEqual(len(shipped), 1, "the sweep read the shipped example configs")
        for path, value in shipped:
            with self.subTest(path=str(path.relative_to(ROOT))):
                self.assertIsInstance(value, str)
                self.assertIsNone(manage.panel_extra_problem(value))


if __name__ == "__main__":
    unittest.main()

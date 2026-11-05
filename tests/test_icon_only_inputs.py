"""icon_only_inputs: the switch that turns the Inputs pill into a round icon must reach every hop.

icon_only_buttons (#556) makes Wi-Fi and Settings round icons but keeps the Inputs label. On a TV with
few inputs, or an owner who knows the icon, that label is the widest thing in the top bar.
icon_only_inputs does the same for Inputs with #556's own makeIconOnly, so it costs about 120 bytes of
APK and nothing at runtime unless it is on.

The flag only works if every hop exists: config.xml defines it (off upstream, so existing installs are
unchanged; a fork may turn it on), Config reads it and lets a theme override it, MainActivity applies it, and the theme table
lists it. Each hop gets its own assertion, as in test_key_filter.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    """Text of a repo file, or empty if it does not exist yet (each test then fails with its own message)."""
    path = ROOT / relative
    return path.read_text(encoding="utf-8") if path.is_file() else ""


CONFIG_XML = read("launcher/app/src/main/res/values/config.xml")
CONFIG_JAVA = read("launcher/app/src/main/java/com/gathin/tvhome/Config.java")
MAIN = read("launcher/app/src/main/java/com/gathin/tvhome/MainActivity.java")
THEMES_README = read("themes/README.md")


class TestIconOnlyInputsWiring(unittest.TestCase):

    def test_config_xml_defines_it(self):
        # Off upstream so existing installs are unchanged; a fork's own config.xml may turn it on.
        self.assertRegex(CONFIG_XML, r'<bool name="icon_only_inputs">(true|false)</bool>',
                         "config.xml must define icon_only_inputs")

    def test_config_reads_resource_and_theme_override(self):
        self.assertIn("R.bool.icon_only_inputs", CONFIG_JAVA, "Config must read the icon_only_inputs resource")
        self.assertIn('"icon_only_inputs"', CONFIG_JAVA,
                      'Config must accept a theme config.json "icon_only_inputs" override')
        self.assertIn("this.iconOnlyInputs = iconOnlyInputs", CONFIG_JAVA, "Config must store iconOnlyInputs")

    def test_main_activity_applies_it_with_the_shared_helper(self):
        self.assertIn("if (cfg.iconOnlyInputs) makeIconOnly((TextView) btnInputs);", MAIN,
                      "MainActivity must make the Inputs pill icon-only with makeIconOnly, as #556 does for "
                      "Wi-Fi and Settings")

    def test_theme_table_lists_it_off_by_default(self):
        row = next((line for line in THEMES_README.splitlines() if line.startswith("| `icon_only_inputs`")), "")
        self.assertTrue(row, "themes/README.md's config.json table must list icon_only_inputs")
        self.assertIn("`false` by default", row, "the table must say it is off by default")


if __name__ == "__main__":
    unittest.main()

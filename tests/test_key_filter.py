"""key_filter: the switch that keeps remote key repeat must reach both helpers and the docs.

On TCL Google TV 12 builds (observed on a 55P71B Pro, `G09_4K_GB`, STT2.230203.001.AS23) any bound
accessibility service requesting FLAG_REQUEST_FILTER_KEY_EVENTS breaks key auto-repeat from the real
remote: long-pressing volume steps once per press with either helper alone or both. `key_filter`
defaults to true so existing installs are unchanged; set false, each helper strips the flag from its
own AccessibilityServiceInfo at bind time via RemoteKeys.applyKeyFilter.

The flag only helps if every hop of it exists: config.xml defines it, Config reads it (and a theme can
override it), both services call applyKeyFilter, applyKeyFilter actually clears the flag, and the
theme/table docs tell a user the switch exists. A rename at any one of those points silently returns
the broken behaviour, so each hop gets its own assertion.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]


def read(relative):
    """Text of a repo file, or empty if it does not exist yet (each test then fails with its own message)."""
    path = ROOT / relative
    return path.read_text(encoding="utf-8") if path.is_file() else ""


CONFIG_XML = read("launcher/app/src/main/res/values/config.xml")
CONFIG_JAVA = read("launcher/app/src/main/java/com/gathin/tvhome/Config.java")
REMOTE_KEYS = read("launcher/app/src/main/java/com/gathin/tvhome/RemoteKeys.java")
HOME_KEY = read("launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java")
INPUT_KEY = read("launcher/app/src/main/java/com/gathin/tvhome/InputKeyService.java")
THEMES_README = read("themes/README.md")
TROUBLE = read("docs/TROUBLESHOOTING.md")


class TestKeyFilterWiring(unittest.TestCase):

    def test_config_xml_defines_key_filter(self):
        # Upstream ships true. This fork's prebuilt APK ships false, so a held volume key keeps repeating on
        # firmware where any key-filtering service breaks auto-repeat (the Google TV Streamer among them).
        self.assertIn('<bool name="key_filter">false</bool>', CONFIG_XML,
                      "this fork's config.xml defines key_filter as false for the prebuilt APK")

    def test_config_reads_resource_and_theme_override(self):
        self.assertIn('R.bool.key_filter', CONFIG_JAVA,
                      "Config must read the key_filter resource")
        self.assertIn('"key_filter"', CONFIG_JAVA,
                      "Config must accept a theme config.json \"key_filter\" override")
        self.assertRegex(CONFIG_JAVA, r'\bboolean keyFilter\b',
                         "Config must carry a keyFilter field")
        self.assertIn('this.keyFilter = keyFilter', CONFIG_JAVA,
                      "Config constructor must store keyFilter")

    def test_apply_key_filter_clears_the_flag(self):
        self.assertIn('applyKeyFilter', REMOTE_KEYS)
        self.assertIn('FLAG_REQUEST_FILTER_KEY_EVENTS', REMOTE_KEYS,
                      "applyKeyFilter must clear FLAG_REQUEST_FILTER_KEY_EVENTS when key_filter is false")
        self.assertIn('setServiceInfo', REMOTE_KEYS,
                      "applyKeyFilter must apply the info back through setServiceInfo()")
        self.assertRegex(REMOTE_KEYS, r'if \(keyFilter\) return',
                         "applyKeyFilter must be a no-op when key_filter is true")

    def test_both_services_apply_it(self):
        for name, src in (("HomeKeyService", HOME_KEY), ("InputKeyService", INPUT_KEY)):
            self.assertIn('RemoteKeys.applyKeyFilter(this, config.keyFilter)', src,
                          f"{name} must call RemoteKeys.applyKeyFilter with the config value")

    def test_docs_mention_the_switch(self):
        self.assertIn('key_filter', THEMES_README,
                      "themes/README.md's config.json table must list key_filter")
        self.assertIn('key_filter', TROUBLE,
                      "docs/TROUBLESHOOTING.md must tell a user hitting broken repeat about key_filter")


if __name__ == "__main__":
    unittest.main()

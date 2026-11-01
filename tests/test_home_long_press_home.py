"""home_long_press_home: a held Home press must reach this launcher when the switch is on, and nothing else.

On a Philips 65OLED804/12 (TPM191E, Android TV 12, STT2.230526.001) Android turns a Home press held past
400 ms into ALL_APPS for the system-image Home, which is the settings app's FallbackHome once the stock
launcher is disabled, so a firm press on a slow remote did nothing. With the switch on, HomeKeyService
takes the Home key itself and opens Home on release.

Each hop gets its own assertion, as in test_key_filter.py: config.xml defines it off, Config reads it from
the resource and a theme, HomeKeyService checks it before the stock_home_redirect path (which lets a held
press through on purpose), and the docs name it.

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
HOME_KEY = read("launcher/app/src/main/java/com/gathin/tvhome/HomeKeyService.java")
THEMES_README = read("themes/README.md")
TROUBLE = read("docs/TROUBLESHOOTING.md")


class TestHomeLongPressHomeWiring(unittest.TestCase):

    def test_config_xml_defines_it_off(self):
        self.assertIn('<bool name="home_long_press_home">false</bool>', CONFIG_XML,
                      "config.xml must define home_long_press_home, off, so no TV changes until it is set")

    def test_config_reads_resource_and_theme_override(self):
        self.assertIn('R.bool.home_long_press_home', CONFIG_JAVA)
        self.assertIn('"home_long_press_home"', CONFIG_JAVA,
                      "a theme's config.json must be able to set home_long_press_home")
        self.assertIn('this.homeLongPressHome = homeLongPressHome', CONFIG_JAVA)

    def test_service_takes_home_before_the_stock_redirect(self):
        takes = HOME_KEY.find('homeLongPressHome && event.getKeyCode() == KeyEvent.KEYCODE_HOME')
        redirect = HOME_KEY.find('event.getKeyCode() == KeyEvent.KEYCODE_HOME && !stockHome.isEmpty()')
        self.assertNotEqual(takes, -1, "HomeKeyService.onKeyEvent must check home_long_press_home")
        self.assertNotEqual(redirect, -1, "the stock_home_redirect check moved; recheck the order here")
        self.assertLess(takes, redirect,
                        "the switch must come first: redirectHomeKey passes a held press on to Android")
        block = HOME_KEY[takes:redirect]
        self.assertIn('return true', block, "the Home key must be consumed, or Android still sees the hold")
        self.assertRegex(block, r'ACTION_UP\s*&&\s*!event\.isCanceled\(\)\)\s*openHome\(\)',
                         "Home must open on release, once per press")

    def test_docs_name_it(self):
        self.assertIn('`home_long_press_home`', THEMES_README, "themes/README.md Keys table needs a row")
        self.assertIn('home_long_press_home', TROUBLE, "TROUBLESHOOTING.md should point the symptom at it")
        self.assertIn('LONG_PRESS_HOME_ALL_APPS', TROUBLE)

    def test_docs_state_the_volume_trade(self):
        self.assertIn('cannot have both', TROUBLE,
                      "the entry must say a firm Home press and a held volume ramp exclude each other")
        self.assertIn('Volume Up/Down stop repeating', THEMES_README,
                      "the Keys row must warn that held keys stop repeating")
        self.assertIn('Volume Up/Down stop repeating', CONFIG_XML,
                      "the config.xml comment must warn that held keys stop repeating")


if __name__ == "__main__":
    unittest.main()

"""The Connecting page names the way in when a firmware has its own ADB switch as well.

A projector can offer two debugging routes: the vendor's `ADB debug` switch under its own settings, and
the stock Android Developer options that Activity Launcher opens. The page already says how to reach the
second one when it is hidden, and how to pair once through it, but both recipes assume the firmware's own
switch is either absent or harmless.

#4296 is a Magcubic HY300 PRO (not the Plus), Android 14, where it was not: the vendor switch was on,
`adb pair` kept failing on TLS, and the route that worked was to switch the vendor ADB off first, then
enable Android's own "USB debug" and "Wireless debug" - which freezes the projector on the first enable
and needs the power button - then pair with the code and `adb connect <tv ip>` with no port.

Two things here are easy to undo by accident, so they are checked rather than left to the prose:

- the bullet must not take the pair-once recipe's place in `tests/test_readme_wireless_debugging.py`,
  which wants exactly one Connecting bullet naming `adb pair` - that is why this one says "pair from the
  computer" instead;
- the README links that recipe, so this bullet has to stay a second route beside it, not a rewrite of it.

Everything asserted here is read from the owner's report in #4296 and nothing was measured on a set by
the person writing this test: the projector, its Android version and the missing build string are what
that comment states.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PAGE = ROOT / "docs" / "TROUBLESHOOTING.md"
SECTION = "## Connecting"
ISSUE = "https://github.com/Gathin23/cleanyourtv/issues/4296"
HEADER = "../examples/magcubic-hy300-pro-plus/debloat.txt"


def connecting(text=None):
    """The Connecting section's text, or an empty string when the heading is gone."""
    text = PAGE.read_text(encoding="utf-8") if text is None else text
    start = text.find(SECTION)
    if start == -1:
        return ""
    rest = text[start + len(SECTION):]
    end = re.search(r"^## ", rest, re.M)
    return rest if end is None else rest[:end.start()]


def bullets(text=None):
    """The section's bullets, continuation lines joined: one string per bullet."""
    out = []
    for line in connecting(text).splitlines():
        if line.startswith("- "):
            out.append(line[2:].strip())
        elif out and line.startswith("  ") and line.strip():
            out[-1] = out[-1] + " " + line.strip()
        elif not line.strip():
            continue
    return out


def flat(text):
    return re.sub(r"\s+", " ", text)


def vendor_bullet(text=None):
    """The bullet about a firmware's own ADB switch beside Android's page, or None."""
    found = [b for b in bullets(text) if "vendor ADB off" in b]
    return found[0] if len(found) == 1 else None


class ThePageAnswersIt(unittest.TestCase):
    def test_the_section_still_parses(self):
        self.assertTrue(connecting(), "docs/TROUBLESHOOTING.md has no '## Connecting' section")
        self.assertGreaterEqual(len(bullets()), 10, "the Connecting reader broke, not the page")

    def test_one_bullet_covers_the_two_switches(self):
        self.assertIsNotNone(
            vendor_bullet(),
            "nothing in Connecting says what to do when a firmware's own ADB switch sits beside "
            "Android's Developer options page and pairing fails (#4296)")

    def test_it_names_the_set_it_was_reported_on(self):
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertIn("Magcubic HY300 PRO", bullet)
        self.assertIn("not the Plus", bullet, "the Plus has its own folder; this is the other model")
        self.assertIn("Android 14", bullet)
        self.assertIn("build not reported", bullet,
                      "the report carries no build string, so the bullet must not imply one")

    def test_it_turns_the_vendor_switch_off_before_opening_android_s_page(self):
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertIn("vendor", bullet)
        self.assertLess(bullet.index("vendor ADB off"), bullet.index("Activity Launcher"),
                        "the vendor switch goes off first: that is the step the owner reports as the "
                        "one that made pairing work")

    def test_it_names_the_freeze_and_the_power_button(self):
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertIn("freezes", bullet)
        self.assertIn("power button", bullet,
                      "the first Wireless debugging enable freezes the set, and only a held power "
                      "button gets it back")

    def test_it_keeps_the_pairing_port_apart_from_the_connection_port(self):
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertIn("not the connection port", bullet,
                      "pairing with the default port is the mistake the owner made first")

    def test_it_ends_on_a_plain_connect(self):
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertIn("`adb connect <tv ip>` with no port", bullet,
                      "after pairing, the connect takes no port")

    def test_it_links_the_report_and_the_family_s_header(self):
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertIn(ISSUE, bullet)
        self.assertIn(HEADER, bullet)


class ItStaysOutOfTheRecipe(unittest.TestCase):
    def test_it_does_not_read_as_the_pair_once_recipe(self):
        bullet = vendor_bullet()
        assert bullet is not None
        # tests/test_readme_wireless_debugging.py wants exactly one Connecting bullet naming `adb pair`,
        # and the README links that one.
        self.assertNotIn("adb pair", bullet)
        self.assertNotIn("adb tcpip 5555", bullet)

    def test_the_recipe_is_still_the_one_bullet_with_adb_pair(self):
        recipes = [b for b in bullets() if "adb pair" in b]
        self.assertEqual(len(recipes), 1,
                         "the pair-once recipe moved or was duplicated: the README links it")

    def test_it_leaves_the_cable_route_its_bullet(self):
        # tests/test_connecting_no_wireless_note.py takes the first bullet naming both `adb tcpip 5555`
        # and `adb devices` as the cable route; this note names neither, so that reader is unaffected.
        bullet = vendor_bullet()
        assert bullet is not None
        self.assertNotIn("adb devices", bullet)
        self.assertTrue([b for b in bullets() if "adb tcpip 5555" in b and "adb devices" in b],
                        "the cable route left the Connecting section")


class TheReaderJoinsBullets(unittest.TestCase):
    def test_a_wrapped_bullet_is_one_string(self):
        page = "## Connecting\n\n- **A** one\n  two three\n- **B** four\n\n## Working\n"
        self.assertEqual(bullets(page), ["**A** one two three", "**B** four"])

    def test_the_bullet_does_not_leak_past_the_section(self):
        page = "## Connecting\n- **vendor ADB off** one\n\n## Working on the TV\n- two\n"
        self.assertEqual(len(bullets(page)), 1)
        self.assertEqual(flat(vendor_bullet(page) or ""), "**vendor ADB off** one")


if __name__ == "__main__":
    unittest.main()

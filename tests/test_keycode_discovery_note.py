"""Finding a dead remote button's keycode when the documented routes come back empty (#853).

The page and `config.xml` used to send a reader two ways: `logcat -v time -s InputReader:D` while
pressing the button, and, for a press that arrives as `KEY_UNKNOWN`, the Linux scancode from
`getevent -lt` into `settings_key_scancodes`. On a Google TV 14 PEAQ "AI PONT DE" both failed for the
same button - `InputReader` logged no key line at all, and the `MSC_SCAN` `getevent` printed
(`0x000c009f`) is not the scanCode the framework reports - so the button read as "never delivered".
A temporary `Log.d` at the top of the helper's `onKeyEvent` is what named the delivered values
(`keyCode=83 scanCode=240`), and the keycode then worked.

Documentation only, so these tests pin what a reader has to be able to act on: that the logcat route
can come back empty, that a `getevent` number is not the framework's scanCode, the recipe that does
work, the evidence that it does, and that the same two caveats reach the `config.xml` comments a
customiser reads before the page. The command names and the `Log.d` calls are asserted verbatim
because a misspelled one is a reader reporting "no output" again.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
CONFIG_XML = ROOT / "launcher" / "app" / "src" / "main" / "res" / "values" / "config.xml"
START = "- **Which keycode** a dead button sends"
LINK = re.compile(r"\]\(([^)\s]+)\)")


def bullet(text, start):
    """The markdown bullet that begins with `start`, plus the continuation lines under it."""
    lines = text.split("\n")
    first = next(i for i, line in enumerate(lines) if line.startswith(start))
    out = [lines[first]]
    for line in lines[first + 1:]:
        if line.startswith("- **") or line.startswith("## "):
            break
        out.append(line)
    return "\n".join(out)


def comment_before(xml_text, array_name):
    """The `<!-- ... -->` immediately above `<integer-array name="array_name">`.

    Searched in the text before the tag, not with one greedy pattern: several comments above it end
    with `-->` too, and the launcher config opens with a file-header comment.
    """
    marker = '<integer-array name="%s">' % array_name
    if marker not in xml_text:
        return ""
    comments = re.findall(r"<!--(.*?)-->", xml_text.split(marker, 1)[0], re.S)
    return comments[-1] if comments else ""


class KeycodeDiscoveryNoteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.trouble_text = TROUBLE.read_text(encoding="utf-8")
        cls.config_text = CONFIG_XML.read_text(encoding="utf-8")
        cls.note = bullet(cls.trouble_text, START)
        cls.keycodes_comment = comment_before(cls.config_text, "settings_key_keycodes")
        cls.scancodes_comment = comment_before(cls.config_text, "settings_key_scancodes")

    def test_bullet_exists_once(self):
        self.assertEqual(self.trouble_text.count(START), 1,
                         "the note must live in one bullet so the two routes cannot drift apart")

    def test_bullet_says_the_logcat_route_can_be_empty(self):
        # The whole point of the report: following the page on this firmware printed nothing at all,
        # which reads as "the button is dead" instead of "this filter sees nothing here".
        self.assertIn("InputReader:D", self.note)
        self.assertIn("no key line", self.note)

    def test_bullet_separates_getevent_from_the_framework_scancode(self):
        self.assertIn("MSC_SCAN", self.note)
        self.assertIn("evdev", self.note)
        self.assertIn("getScanCode()", self.note)
        self.assertIn("settings_key_scancodes", self.note)

    def test_bullet_gives_the_logd_recipe_verbatim(self):
        self.assertIn("Log.d", self.note)
        self.assertIn("onKeyEvent", self.note)
        for call in ("getKeyCode()", "getScanCode()", "getDeviceId()"):
            self.assertIn(call, self.note)
        self.assertIn("TvHomeKeys", self.note)
        self.assertIn("logcat -s TvHomeKeys:D", self.note)
        self.assertIn("HOME_KEY_SERVICE=1", self.note)

    def test_bullet_keeps_the_evidence(self):
        # A reader needs the worked example to recognise their own dump, and the two spellings that
        # were tried and did nothing, so the getevent route is not retried as if it were untested.
        self.assertIn("MSC_SCAN 000c009f", self.note)
        self.assertIn("159", self.note)
        self.assertIn("786591", self.note)
        self.assertIn("keyCode=83 scanCode=240", self.note)
        self.assertIn("settings_key_keycodes = [83]", self.note)

    def test_bullet_names_the_device_and_build(self):
        self.assertIn("DVB99PQ", self.note)
        self.assertIn("changi", self.note)
        self.assertIn("UKNV.260504.001", self.note)

    def test_bullet_says_a_mismatch_is_not_proof(self):
        # Without this sentence the old advice still reads as authoritative: no match, dead button.
        self.assertIn("not proof the press never arrived", self.note)

    def test_bullet_keeps_the_vendor_receiver_fact(self):
        self.assertIn("com.homwee.factory/.FactoryKeyReciver", self.note)
        self.assertIn("dumpsys window policy", self.note)

    def test_bullet_links_the_issue(self):
        self.assertIn("[#853](https://github.com/Gathin23/cleanyourtv/issues/853)", self.note)

    def test_config_xml_keycodes_comment_points_at_the_logd_route(self):
        self.assertIn("InputReader:D", self.keycodes_comment)
        self.assertIn("Log.d", self.keycodes_comment)
        self.assertIn("onKeyEvent", self.keycodes_comment)

    def test_config_xml_scancodes_comment_warns_about_msc_scan(self):
        self.assertIn("MSC_SCAN", self.scancodes_comment)
        self.assertIn("scanCode", self.scancodes_comment)

    def test_comment_before_finds_the_comment_it_was_asked_for(self):
        # The two comments are adjacent and nearly identical in shape; a helper that returned the
        # first one it saw would make both comment tests pass on the wrong text.
        self.assertIn("keycode 83", self.keycodes_comment)
        self.assertNotIn("KEY_TAPE", self.keycodes_comment)
        self.assertIn("KEY_TAPE", self.scancodes_comment)
        self.assertNotIn("keycode 83", self.scancodes_comment)
        self.assertEqual(comment_before(self.config_text, "no_such_array"), "")

    def test_bullet_extraction_stops_at_the_next_bullet(self):
        sample = "\n".join([START + " one", "  two", "- **Next** three"])
        self.assertEqual(bullet(sample, START), START + " one\n  two")

    def test_bullet_links_resolve(self):
        broken = [target for target in LINK.findall(self.note)
                  if not target.startswith(("http://", "https://", "#"))
                  and not (TROUBLE.parent / target.split("#", 1)[0]).exists()]
        self.assertEqual(broken, [], "the bullet must not link a page that does not exist")

    def test_config_xml_is_still_well_formed(self):
        # The change is a comment, but a comment edit that breaks the XML breaks the launcher build.
        root = ET.fromstring(self.config_text)
        self.assertEqual(root.tag, "resources")
        for name in ("settings_key_scancodes", "settings_key_keycodes"):
            self.assertIsNotNone(root.find("./integer-array[@name='%s']" % name))


if __name__ == "__main__":
    unittest.main()

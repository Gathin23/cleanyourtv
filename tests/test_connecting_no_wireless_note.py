"""The Connecting page tells an owner what is left when 5555 is closed and there is no Wireless debugging.

`docs/TROUBLESHOOTING.md` answers "USB debugging never opens port 5555" with one recipe: pair once through
Wireless debugging, `adb connect`, `adb tcpip 5555`. That recipe needs a pairing code on screen, so a set
whose Developer options show USB debugging and nothing else had no answer on the page at all - #1489 is a
Philips 58PUS8505/62 The One (Android TV 12, `STT2.230526.001`) with USB debugging on, no Wireless
debugging entry, `adb connect <tv ip>:5555` refused and a USB cable that got no connection either.

The two routes that remain are the port scan the same section already documents (`TV_PORT`) and the
cable, when the set enumerates: `adb tcpip 5555` with the stock `adb` opens the port the tools use. That
last part rests on the tools being TCP-only, which is read out of `tools/tv.py` here rather than asserted
in prose: if `tv.py` ever grows a USB transport, this pair fails and the bullet has to be rewritten.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PAGE = ROOT / "docs" / "TROUBLESHOOTING.md"
TVPY = ROOT / "tools" / "tv.py"
SECTION = "## Connecting"


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


def connect_body(text=None):
    """The body of tools/tv.py's connect(), which is where the transport is chosen."""
    text = TVPY.read_text(encoding="utf-8") if text is None else text
    m = re.search(r"^def connect\(.*?(?=^def |\Z)", text, re.M | re.S)
    return m.group(0) if m else ""


def bullet_naming(*needles, text=None):
    """The first bullet that names every needle, or None."""
    for b in bullets(text):
        if all(n in b for n in needles):
            return b
    return None


def cable_bullet(text=None):
    """The bullet that offers the cable route: `adb tcpip 5555` next to the `adb devices` check.

    The pair-once bullet names `adb tcpip 5555` as well, so the `adb devices` check is what tells the
    two apart.
    """
    return bullet_naming("adb tcpip 5555", "adb devices", text=text)


class ToolsAreTcpOnly(unittest.TestCase):
    def test_connect_opens_a_tcp_connection_to_host_and_port(self):
        body = connect_body()
        self.assertIn("AdbDeviceTcp(HOST, PORT", body,
                      "tv.py no longer connects with AdbDeviceTcp(HOST, PORT): the page's "
                      "'<tv ip>:5555' needs checking")
        self.assertIn("TV_PORT", TVPY.read_text(encoding="utf-8"),
                      "tv.py no longer honours TV_PORT, which the bullets hand out")
        self.assertRegex(TVPY.read_text(encoding="utf-8"), r"TV_PORT\"\)\s*or\s*\"5555\"",
                         "the default port in tv.py is no longer 5555")

    def test_there_is_no_usb_transport_to_document(self):
        src = TVPY.read_text(encoding="utf-8")
        self.assertNotIn("AdbDeviceUsb", src,
                         "tv.py now has a USB transport: the Connecting bullet that says the tools "
                         "speak TCP only, and the cable is just a way to open the port, needs rewriting")


class ThePageAnswersIt(unittest.TestCase):
    def test_the_pair_once_recipe_is_still_there_and_before_the_new_bullet(self):
        pair = bullet_naming("adb pair")
        self.assertIsNotNone(pair, "the pair-once recipe left the Connecting section; the new bullet "
                                    "refers to it as 'the recipe above'")
        self.assertIn("Wireless debugging", pair)

    def test_a_closed_5555_with_no_wireless_debugging_is_answered(self):
        bullet = cable_bullet()
        self.assertIsNotNone(
            bullet,
            "nothing in Connecting tells an owner whose set has no Wireless debugging entry how to reach "
            "it: the pair-once recipe needs a code on screen, so the page ends there (#1489)")
        assert bullet is not None
        self.assertIn("adb devices", bullet, "the cable route needs the check that the set enumerates")
        self.assertIn("TV_PORT", bullet, "the port scan is the other route left, and it needs TV_PORT")
        self.assertIn("5555", bullet)
        self.assertIn("1489", bullet, "the bullet should link the report it comes from")
        self.assertIn("USB", bullet)

    def test_the_new_bullet_comes_after_the_pair_once_recipe(self):
        cable = cable_bullet()
        self.assertIsNotNone(cable, "no cable bullet in the Connecting section")
        sections = bullets()
        pair_at = [i for i, b in enumerate(sections) if "adb pair" in b]
        cable_at = [i for i, b in enumerate(sections) if b == cable]
        self.assertTrue(pair_at, "no bullet names `adb pair` any more: %r" % (sections,))
        self.assertTrue(cable_at, "the cable bullet did not read back out of the section")
        self.assertLess(pair_at[0], cable_at[0],
                        "'the recipe above' has to be above the bullet that says it cannot be followed")

    def test_the_bullet_does_not_promise_the_tools_run_over_usb(self):
        bullet = cable_bullet()
        assert bullet is not None
        self.assertIn("over USB", bullet,
                      "the bullet has to say the cable is not a transport for these tools, or an owner "
                      "will keep a cable plugged in and expect tools/tv.py to use it")


class TheReader(unittest.TestCase):
    def test_a_wrapped_bullet_is_joined(self):
        page = "## Connecting\n\n- **A** one\n  two three\n- **B** four\n\n## Working\n"
        self.assertEqual(bullets(page), ["**A** one two three", "**B** four"])

    def test_a_bullet_in_another_section_is_not_read(self):
        page = "## Connecting\n- **A** one\n\n## Working\n- **B** adb tcpip 5555\n"
        self.assertIsNone(bullet_naming("adb tcpip 5555", text=page))

    def test_the_section_stops_at_the_next_heading(self):
        page = "## Connecting\n- **A** one\n## Working on the TV\nmore\n"
        self.assertEqual(len(bullets(page)), 1)

    def test_a_page_without_the_section_reads_as_nothing(self):
        self.assertEqual(bullets("# Troubleshooting\n\n## Working\n- x\n"), [])
        self.assertIsNone(bullet_naming("adb tcpip 5555", text="# Nothing here\n"))

    def test_naming_every_needle_is_required(self):
        page = "## Connecting\n- **A** adb tcpip\n- **B** adb tcpip 5555 TV_PORT\n"
        self.assertEqual(bullet_naming("adb tcpip", "TV_PORT", text=page), "**B** adb tcpip 5555 TV_PORT")

    def test_the_real_page_reads_as_a_section_of_bullets(self):
        self.assertGreaterEqual(len(bullets()), 10,
                                "the Connecting section parsed as %d bullets: the reader is broken, not "
                                "the page" % len(bullets()))


if __name__ == "__main__":
    unittest.main()

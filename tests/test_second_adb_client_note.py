"""The Connecting page says what to stop when another ADB client is holding the TV.

Some firmware's `adbd` serves one client at a time. On a Xiaomi Mi TV 4A (Android 6.0.1) `tools/tv.py`
failed with `TcpTimeoutException: Reading from <ip>:5555 timed out` whenever the platform `adb` server held
a connection to the same port, and connected after `adb kill-server` (#1185, offered there as a note for
this page). A TCL 55C61B (Google TV 12) owner had to disable Home Assistant's Android Debug Bridge
integration first, because it was holding the TV's ADB connection (#1526).

The page named neither: only the Mi TV 4A's device note (`docs/devices/xiaomi-mitv4-ansm0.md`) said it.
`tools/tv.py`'s message for a handshake timeout names the "Allow USB debugging?" prompt and `TV_HOST`, then
sends the reader to `docs/TROUBLESHOOTING.md#connecting`, where nothing covered a second client. The
bullet is pinned here, and so is the message that leads to it, since the bullet quotes it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_adb_key_fallback import bullets, connecting  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
TVPY = ROOT / "tools" / "tv.py"
ISSUES = "https://github.com/Gathin23/cleanyourtv/issues/"


def second_client_bullet():
    """The Connecting bullet that names both clients seen holding a TV, or None."""
    said = [b for b in bullets(connecting()) if "adb kill-server" in b and "Home Assistant" in b]
    return said[0] if said else None


def connect_error_body():
    """The body of tools/tv.py's connect_error(), which words the handshake timeout."""
    text = TVPY.read_text(encoding="utf-8")
    m = re.search(r"^def connect_error\(.*?(?=^def |\Z)", text, re.M | re.S)
    return m.group(0) if m else ""


class PageTest(unittest.TestCase):
    def test_a_bullet_names_the_clients_seen_holding_a_tv(self):
        self.assertIsNotNone(
            second_client_bullet(),
            "no Connecting bullet names both `adb kill-server` and Home Assistant, so a reader whose "
            "tools/tv.py times out while another ADB client holds the TV is pointed only at the "
            "debugging prompt and TV_HOST (#1185, #1526)")

    def test_the_bullet_says_why(self):
        bullet = second_client_bullet() or ""
        self.assertIn("one client at a time", bullet,
                      "the bullet does not say that some adbd serves one client at a time, which is why "
                      "a second client makes tv.py time out")

    def test_the_bullet_names_what_the_reader_sees(self):
        bullet = second_client_bullet() or ""
        self.assertIn("TcpTimeoutException", bullet, "the bullet does not name the exception tv.py prints")
        self.assertIn("did not finish the handshake", bullet,
                      "the bullet does not quote tools/tv.py's handshake-timeout message")

    def test_the_bullet_names_the_integration_that_uses_adb(self):
        # Home Assistant has two Android TV integrations. Only Android Debug Bridge holds an ADB
        # connection; disabling Android TV Remote changes nothing for tv.py.
        bullet = second_client_bullet() or ""
        self.assertIn("Android Debug Bridge", bullet,
                      "the bullet does not say which Home Assistant integration holds the ADB connection")

    def test_the_bullet_links_its_reports(self):
        bullet = second_client_bullet() or ""
        for number, what in (("1185", "the Mi TV 4A and the stock adb server"),
                             ("1526", "the TCL 55C61B and Home Assistant")):
            self.assertIn(ISSUES + number, bullet, f"the bullet does not link #{number} ({what})")


class SourceTest(unittest.TestCase):
    def test_a_handshake_timeout_still_points_at_connecting(self):
        body = connect_error_body()
        self.assertTrue(body, "tools/tv.py has no connect_error() to read")
        self.assertIn("TcpTimeoutException", body,
                      "tools/tv.py no longer words a TcpTimeoutException itself: check the bullet's quote")
        self.assertIn("did not finish the handshake", body,
                      "tools/tv.py's handshake-timeout message changed: the Connecting bullet quotes it")
        self.assertIn("docs/TROUBLESHOOTING.md#connecting", body,
                      "tools/tv.py's connect errors no longer point at the Connecting section, which is "
                      "how a reader with a handshake timeout finds the bullet")


if __name__ == "__main__":
    unittest.main()

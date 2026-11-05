"""The English README names Wireless debugging where a reader connects by hand and where they close up.

`tools/tv.py` talks to network ADB on port 5555, and on the Chromecast with Google TV and the Google TV
Streamer that port refuses until the TV has been paired once through Wireless debugging and opened with
`adb tcpip 5555` (all three Google lists say so). README.zh-CN.md, README.vi.md and README.ja.md open
"Doing it by hand" with that note; the English page went straight to `tools/tv.py --probe`, so an owner
following it by hand got "connection refused" and nothing to go on.

The FAQ "Isn't ADB access a security hole?" is where an owner closes ADB again, and its steps named only
USB debugging and Network/ADB debugging. On a Chromecast with Google TV 4K (`sabrina`, UTTC.250917.004)
turning USB debugging off left the Wireless debugging port accepting connections, and
`tools/adb-closed.py` checks 5555 unless it is given another port (docs/TROUBLESHOOTING.md, Connecting,
and that list's header). So the steps now switch Wireless debugging off as well, say where its port is
listed, and check that port too.

That port check has to come before the reboot. The 4K turns Wireless debugging off at every reboot (the
second unit in its list's header), and where it does come back it is on a new port each time it starts
(docs/devices/sony-k75xr50-ae2-cn.md, hisense-65d62qh.md, sony-k85xr70-ae2.md). Either way the number
noted beforehand reads closed after the reboot, including on a TV where Wireless debugging is still on.
So step 1 checks the port straight after switching Wireless debugging off, and step 2, after the
reboot, checks 5555 and that Wireless debugging has not come back. `tools/adb-closed.py` gives the same
order when it finds a port still open, and names Wireless debugging with the other two toggles.

The FAQ heading itself must not move: tools/disable.sh and tools/disable.ps1 send a reader to it by name
when the TV reports `ro.adb.secure=0`, and tests/test_adb_exposure.py pins that warning. The checks below
read the scripts for the quoted heading instead of copying it, so a rename on either side fails here.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import socket
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_accessibility_flag_note import section  # noqa: E402
from test_adb_closed import check  # noqa: E402
from test_sabrina_cast_restart_note import bullets  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
README = (ROOT / "README.md").read_text(encoding="utf-8")
TROUBLE = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8")
SCRIPTS = (ROOT / "tools" / "disable.sh", ROOT / "tools" / "disable.ps1")
HEADING = "Isn't ADB access a security hole?"
SUMMARY = f"<summary><b>{HEADING}</b></summary>"
LINK = "docs/TROUBLESHOOTING.md#connecting"


def flat(text):
    """Collapse line wrapping, so a phrase split over two lines still reads as one."""
    return re.sub(r"\s+", " ", text)


def faq_answer(text, summary):
    """The body of the README's <details> block under `summary`, or None when the summary is gone."""
    start = text.find(summary)
    if start == -1:
        return None
    end = text.find("</details>", start)
    return text[start + len(summary):] if end == -1 else text[start + len(summary):end]


def steps(answer):
    """The numbered steps in one FAQ answer, keyed by number, continuation lines folded in."""
    found, current = {}, None
    for line in answer.splitlines():
        m = re.match(r"^(\d+)\. ", line)
        if m:
            current = int(m.group(1))
            found[current] = line[m.end():]
        elif current is not None and line.startswith("   "):
            found[current] += " " + line.strip()
        else:
            current = None
    return {n: flat(s) for n, s in found.items()}


class DoingItByHandTest(unittest.TestCase):
    def test_the_section_says_how_a_wireless_debugging_tv_gets_port_5555(self):
        by_hand = flat(section(README, "## Doing it by hand"))
        self.assertIn("port 5555", by_hand)
        self.assertIn("Wireless debugging", by_hand)
        self.assertIn("adb tcpip 5555", by_hand)
        self.assertIn(f"({LINK})", by_hand)

    def test_the_note_comes_before_the_first_command(self):
        # A reader who runs `tools/tv.py --probe` first has already hit the refused port.
        by_hand = flat(section(README, "## Doing it by hand"))
        self.assertIn("tools/tv.py --probe", by_hand)
        self.assertIn("Wireless debugging", by_hand)
        self.assertLess(by_hand.index("Wireless debugging"), by_hand.index("tools/tv.py --probe"))

    def test_the_link_lands_on_the_pairing_steps(self):
        self.assertIn("## Connecting", TROUBLE.splitlines())
        connecting = section(TROUBLE, "## Connecting")
        pairing = [flat(b) for b in bullets(connecting) if "adb pair" in b]
        self.assertEqual(len(pairing), 1, "expected one Connecting bullet with the `adb pair` steps")
        self.assertIn("adb tcpip 5555", pairing[0])


class CloseUpStepsTest(unittest.TestCase):
    def setUp(self):
        answer = faq_answer(README, SUMMARY)
        self.assertIsNotNone(answer, f"README.md has no {SUMMARY!r}")
        self.steps = steps(answer)
        self.assertEqual(sorted(self.steps), [1, 2], "the FAQ answer no longer reads as two steps")

    def test_step_one_switches_every_debugging_toggle_off(self):
        first = self.steps[1]
        for toggle in ("**USB debugging**", "**Network/ADB debugging**", "**Wireless debugging**"):
            self.assertIn(toggle, first)
        self.assertIn("revoke the USB debugging authorisations", first)

    def test_step_one_says_why_and_where_the_port_is_listed(self):
        first = self.steps[1]
        self.assertIn("stays open when USB debugging goes off", first)
        self.assertIn("Chromecast with Google TV 4K", first)
        self.assertIn("`adb mdns services`", first)
        self.assertIn("`_adb-tls-connect._tcp`", first)

    def test_step_one_checks_the_wireless_debugging_port_before_the_reboot(self):
        # After the reboot the noted number reads closed whether or not Wireless debugging is still
        # on, so this is the only point where checking it proves anything.
        first = self.steps[1]
        self.assertIn("check it straight after, before the reboot", first)
        self.assertIn("`python3 tools/adb-closed.py <tv ip> <port>` must say that port is closed", first)
        self.assertIn("takes a new port each time it starts", first)

    def test_step_two_checks_5555_and_that_wireless_debugging_stayed_off(self):
        second = self.steps[2]
        self.assertTrue(second.startswith("Reboot the TV, then"), second)
        self.assertIn("`python3 tools/adb-closed.py <tv ip>` must say the port is closed", second)
        self.assertNotIn("<port>", second, "the noted port is out of date once the TV has rebooted")
        self.assertIn("Wireless debugging must still be off in Developer options", second)
        self.assertIn("`adb mdns services` must list no `_adb-tls-connect._tcp` entry", second)
        self.assertIn("check its new port as in step 1", second)
        self.assertIn('only "Connection refused" counts', second)

    def test_adb_closed_checks_the_port_the_step_hands_it(self):
        # The step's form, `<tv ip> <port>`, against a listener that stands in for the Wireless
        # debugging port: the script has to report that port open, not fall back to 5555.
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check("127.0.0.1", str(port))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_adb_closed_names_wireless_debugging_and_checks_before_the_reboot(self):
        # The step sends a reader to this message with the Wireless debugging port, so it has to name
        # that toggle, and put the check again before the reboot, as step 1 does.
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            result = check("127.0.0.1", str(server.getsockname()[1]))
        self.assertEqual(result.returncode, 1, result.stderr)
        advice = flat(result.stderr)
        for toggle in ("USB debugging", "Network/ADB debugging", "Wireless debugging"):
            self.assertIn(toggle, advice)
        self.assertLess(advice.index("check again"), advice.index("reboot"), advice)


class QuotedHeadingTest(unittest.TestCase):
    def test_the_scripts_quote_a_heading_the_readme_still_has(self):
        self.assertEqual(README.count(SUMMARY), 1)
        for script in SCRIPTS:
            with self.subTest(script=script.name):
                self.assertIn(HEADING, script.read_text(encoding="utf-8"))


class StepReaderTest(unittest.TestCase):
    """The reader above has to see the shape the FAQ really uses, and stop where the list stops."""

    def test_wrapped_steps_are_joined_and_the_closing_line_is_left_out(self):
        answer = "Intro:\n\n1. One\n   more.\n2. Two\n   and\n   three.\n\nThe launcher keeps working.\n"
        self.assertEqual({1: "One more.", 2: "Two and three."}, steps(answer))

    def test_a_missing_summary_reads_as_none(self):
        self.assertIsNone(faq_answer("<details>\n</details>\n", SUMMARY))


if __name__ == "__main__":
    unittest.main()

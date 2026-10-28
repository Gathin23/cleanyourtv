"""PROMPT.md's closing step closes every way into ADB, and checks it with the tool (#1574).

The step named only USB debugging and Network/ADB debugging, although PROMPT.md pairs through Wireless
debugging and prefers it. Wireless debugging listens on a port of its own: on a Chromecast with Google
TV 4K (`sabrina`, UTTC.250917.004) turning USB debugging off left that port accepting connections, and
`tools/adb-closed.py` checks 5555 unless it is handed another port. So an agent could report ADB closed
with the Wireless debugging port still open. The step now switches Wireless debugging off as well,
notes its port first and checks that port before the reboot (after it the old number reads closed
either way: Wireless debugging is off, or back on a new port), then checks 5555 after the reboot.

The same step leaves USB debugging on only when the person asks, and only while 5555 still checks
closed: on most TVs USB debugging is what keeps 5555 open.

"Back up first" read `host_connected=false` as proof that a USB Ethernet adapter was gone. The same
Chromecast read false with the adapter up as `eth0` (docs/TROUBLESHOOTING.md, Connecting), so the
reading is no proof, and the connectivity check after it decides.

AGENTS.md repeats the closing step for agents, so it is held to the same points. The README FAQ's
version of these steps is tests/test_readme_wireless_debugging.py's job.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PROMPT = (ROOT / "PROMPT.md").read_text(encoding="utf-8")
AGENTS = (ROOT / "AGENTS.md").read_text(encoding="utf-8")


def flat(text):
    """Collapse line wrapping, so a phrase split over two lines still reads as one."""
    return re.sub(r"\s+", " ", text)


def list_items(text):
    """Every `- ` item of a markdown doc with its wrapped lines, flattened."""
    found, current = [], None
    for line in text.splitlines():
        if line.startswith("- "):
            if current is not None:
                found.append(current)
            current = line
        elif current is not None and line.startswith("  "):
            current += "\n" + line
        else:
            if current is not None:
                found.append(current)
            current = None
    if current is not None:
        found.append(current)
    return [flat(item) for item in found]


def item_with(text, needle):
    matches = [item for item in list_items(text) if needle in item]
    assert len(matches) == 1, f"expected one item with {needle!r}, found {len(matches)}"
    return matches[0]


class ClosingStepTest(unittest.TestCase):
    STEPS = (("PROMPT.md", PROMPT, "close network ADB instead of leaving it open"),
             ("AGENTS.md", AGENTS, "**Close ADB afterwards.**"))

    def steps(self):
        for name, text, needle in self.STEPS:
            yield name, item_with(text, needle)

    def test_every_debugging_toggle_goes_off(self):
        for name, step in self.steps():
            with self.subTest(doc=name):
                for toggle in ("Wireless debugging", "Network/ADB debugging", "USB debugging"):
                    self.assertIn(toggle, step)
                self.assertIn("authorisations", step)

    def test_the_wireless_debugging_port_is_noted_and_checked_before_the_reboot(self):
        for name, step in self.steps():
            with self.subTest(doc=name):
                self.assertIn("`adb mdns services`", step)
                self.assertIn("`_adb-tls-connect._tcp`", step)
                port_check = step.index("`tools/adb-closed.py <tv ip> <port>`")
                reboot = step.index("reboot", port_check)
                self.assertIn("before the reboot", step[port_check:reboot + len("reboot")])

    def test_5555_is_checked_after_the_reboot_and_wireless_debugging_stayed_off(self):
        for name, step in self.steps():
            with self.subTest(doc=name):
                after = step[step.index("Then reboot"):]
                self.assertIn("`tools/adb-closed.py <tv ip>`", after)
                self.assertIn("5555", after)
                self.assertRegex(after, r"Wireless debugging (?:is )?still off")

    def test_usb_debugging_stays_on_only_while_5555_checks_closed(self):
        for name, step in self.steps():
            with self.subTest(doc=name):
                self.assertRegex(step, r"USB debugging (?:too )?unless (?:I|the person) asks? to keep it")
                self.assertRegex(step, r"If 5555 still answers (?:while|with) USB debugging")

    def test_only_the_tool_proves_the_port_closed(self):
        for name, step in self.steps():
            with self.subTest(doc=name):
                self.assertRegex(step, r"Only the tool(?:'s \"closed\"| saying the port is closed) counts")

    def test_the_old_two_toggle_step_is_gone(self):
        self.assertNotIn("switch USB debugging and Network/ADB debugging off", flat(PROMPT))
        self.assertNotIn("USB debugging and Network/ADB debugging off, authorisations revoked", flat(AGENTS))


class HostConnectedTest(unittest.TestCase):
    def test_host_connected_false_is_not_proof_the_adapter_is_gone(self):
        text = flat(PROMPT)
        self.assertNotIn("`host_connected=false` means the adapter is already gone", text)
        self.assertIn("`host_connected=false` is no proof the adapter is gone", text)
        # The check that does decide comes right after it.
        self.assertGreater(text.index("`dumpsys connectivity"), text.index("is no proof the adapter"))

    def test_troubleshooting_still_has_the_case_it_rests_on(self):
        trouble = flat((ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8"))
        self.assertIn("`host_connected=false` was no proof the adapter was gone", trouble)


if __name__ == "__main__":
    unittest.main()

"""The Lumio Vision 7 notes give a Wi-Fi check that shows which saved network is in use.

On the contributor's unit setupwraith owned one saved network, but the network in use was system-owned, so
`FORCE_SETUPWRAITH=1` was safe there. The README and the list header tell other units to add the override only
once the network they are connected to is system-owned. `dumpsys wifi | grep -o 'cname=[^ ]*'` cannot tell them:
it prints one bare owner per saved network, with no SSID and no connected flag, so a unit whose network in use
belongs to setupwraith prints the same two lines as the contributor's. The check has to pair each entry's
`Currently Connected:` flag with its owner (examples/xgimi-mogo-4/README.md reads the same flag).

The README's command is run here, as grep would run it, against a `dumpsys wifi` excerpt shaped like the one a
Chromecast with Google TV 4K (Google TV 14) prints, with made-up network names.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_apply_override_caveat import OVERRIDE, apply_blocks  # noqa: E402


ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / "examples" / "lumio-vision-7"
README = (FOLDER / "README.md").read_text(encoding="utf-8")
LIST = (FOLDER / "debloat.txt").read_text(encoding="utf-8")
SETUP = "com.google.android.tungsten.setupwraith"
SYSTEM = "android.uid.system:1000"

# `tools/tv.py "dumpsys wifi | grep -o 'PATTERN'"` or `grep -oE`.
CHECK = re.compile(r'tools/tv\.py "dumpsys wifi \| grep -oE? \'([^\']+)\'"')


def network(number, name, connected, owner, uid):
    """One saved network as WifiConfigManager dumps it: the ID line first, the creator further down."""
    mark = "* " if connected else ""
    return (
        f'{mark}ID: {number} SSID: "{name}" PROVIDER-NAME: null BSSID: null FQDN:  HOME-PROVIDER-NETWORK: false '
        f"PRIO: 0 HIDDEN: false PMF: false CarrierId: -1 SubscriptionId: -1 SubscriptionGroup: null "
        f"Currently Connected: {'true' if connected else 'false'} User Selected: false\n"
        " NetworkSelectionStatus NETWORK_SELECTION_ENABLED\n"
        " KeyMgmt: WPA_PSK Protocols: WPA RSN\n"
        f" cuid={uid} cname={owner} luid={uid} lname={owner}\n"
    )


def dumpsys(home_connected):
    """A system-owned home network and a setupwraith-owned one; `home_connected` says which is in use."""
    return (
        "WifiConfigManager - Configured networks Begin ----\n"
        + network(0, "Home", home_connected, SYSTEM, 1000)
        + network(1, "Hotspot", not home_connected, SETUP, 10031)
        + "WifiConfigManager - Configured networks End ----\n"
    )


def grep_o(pattern, text):
    """What `grep -o` (or `grep -oE`) prints: every match, line by line, in order."""
    return [m.group(0) for line in text.splitlines() for m in re.finditer(pattern, line)]


def owner_in_use(output):
    """The `cname=` that follows `Currently Connected: true` in the check's output, or None."""
    for flag, owner in zip(output, output[1:]):
        if flag == "Currently Connected: true" and owner.startswith("cname="):
            return owner[len("cname="):]
    return None


def setupwraith_section():
    parts = re.split(r"(?m)^## ", README)
    matches = [p for p in parts[1:] if "setupwraith" in p.split("\n", 1)[0]]
    assert len(matches) == 1, f"expected one README section about setupwraith, found {len(matches)}"
    return matches[0]


def header():
    return "\n".join(line for line in LIST.splitlines() if line.startswith("#"))


class LumioConnectedWifiCheckTest(unittest.TestCase):
    def setUp(self):
        self.patterns = CHECK.findall(setupwraith_section())

    def test_the_readme_gives_a_wifi_check(self):
        self.assertTrue(self.patterns, "the setupwraith section gives no `tools/tv.py \"dumpsys wifi | grep ...\"` check")

    def test_the_bare_owner_list_cannot_tell_which_network_is_in_use(self):
        # Why the check needs more than the owners: both units print the same lines.
        bare = "cname=[^ ]*"
        self.assertEqual(grep_o(bare, dumpsys(True)), grep_o(bare, dumpsys(False)))

    def test_every_check_in_the_readme_names_the_owner_of_the_network_in_use(self):
        for pattern in self.patterns:
            with self.subTest(pattern=pattern):
                self.assertEqual(SYSTEM, owner_in_use(grep_o(pattern, dumpsys(True))),
                                 "on the contributor's unit the check must show the system-owned network in use")
                self.assertEqual(SETUP, owner_in_use(grep_o(pattern, dumpsys(False))),
                                 "on a unit on setupwraith's network the check must show setupwraith in use")

    def test_the_check_prints_no_network_names(self):
        # Owners post their output in issues; the check should not carry the SSIDs along.
        for pattern in self.patterns:
            with self.subTest(pattern=pattern):
                output = "\n".join(grep_o(pattern, dumpsys(True)))
                self.assertNotIn("Home", output)
                self.assertNotIn("Hotspot", output)

    def test_the_override_stays_tied_to_ethernet_or_a_system_owned_connection(self):
        text = re.sub(r"\s+", " ", setupwraith_section())
        self.assertIn("only on Ethernet", text)
        self.assertIn("`android.uid.system`", text)
        self.assertIn("Currently Connected: true", text)

    def test_the_header_points_to_the_connected_network_check(self):
        text = re.sub(r"#\s*", " ", header())
        text = re.sub(r"\s+", " ", text)
        self.assertIn("Currently Connected", text)
        self.assertIn("network in use", text)

    def test_the_apply_line_still_leaves_the_override_out(self):
        blocks = apply_blocks(LIST)
        self.assertEqual(1, len(blocks))
        self.assertIsNone(OVERRIDE.search(" ".join(blocks[0][1])))


if __name__ == "__main__":
    unittest.main()

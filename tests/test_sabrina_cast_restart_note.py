"""The Chromecast with Google TV 4K notes must keep the receiver restart and the wireless-debugging close.

On `sabrina` (UTTC.250917.004), after a reboot, re-enabling setupwraith alone brought no
`ClientAuthSigner` line within 3 minutes and phones did not list the device. Restarting the receiver
with setupwraith enabled loads the credentials in seconds, but only `am crash` restarts it for good:
started by hand with `am start-foreground-service`, `CastReceiverService` never calls
`startForeground()`, Android kills it 30 s later, and the copy that comes back after setupwraith is off
has no credentials (#2615). So the notes say `am crash`, and to check the pid survives 30 s before
disabling setupwraith. Closing up, turning USB debugging off left the Wireless debugging port open,
which tools/adb-closed.py does not check.

Run from the repository root:
python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
DOC = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8")
EXAMPLE = (ROOT / "examples" / "google-chromecast-gtv-4k" / "debloat.txt").read_text(encoding="utf-8")
CRASH = "am crash com.google.android.apps.mediashell"
PIDOF = "pidof com.google.android.apps.mediashell"


def bullets(text):
    """Split a markdown doc into its `- **Title**` bullets, wrapping lines included."""
    found, current = [], None
    for line in text.splitlines():
        if line.startswith("- **"):
            if current is not None:
                found.append(current)
            current = line
        elif current is not None and (line.startswith("  ") or not line.strip()):
            current += "\n" + line
        else:
            if current is not None:
                found.append(current)
            current = None
    if current is not None:
        found.append(current)
    return found


def bullet_containing(needle):
    matches = [b for b in bullets(DOC) if needle in b]
    assert len(matches) == 1, f"expected one bullet with {needle!r}, found {len(matches)}"
    return re.sub(r"\s+", " ", matches[0])


class SabrinaCastRestartNoteTest(unittest.TestCase):
    def test_the_casting_bullet_restarts_the_receiver(self):
        casting = bullet_containing("Casting from a phone fails")
        self.assertIn("sabrina", casting)
        self.assertIn(CRASH, casting)
        self.assertIn(PIDOF, casting)
        self.assertIn("30 s", casting)
        self.assertIn("ClientAuthSigner", casting)
        # The receiver is restarted before setupwraith goes off again, not after.
        self.assertLess(casting.index(CRASH),
                        casting.index("pm disable-user --user 0 com.google.android.tungsten.setupwraith"))

    def test_the_wireless_bullet_says_to_switch_it_off_separately(self):
        wireless = bullet_containing("USB debugging never opens port 5555")
        self.assertIn("switch Wireless debugging off on its own", wireless)
        self.assertIn("tools/adb-closed.py", wireless)

    def test_the_example_header_carries_both(self):
        header = "\n".join(l for l in EXAMPLE.splitlines() if l.startswith("#"))
        self.assertIn(CRASH, header)
        self.assertIn(PIDOF, header)
        # The old restart is only named as the one not to use.
        self.assertIn("Not am force-stop plus am start-foreground-service", header)
        self.assertIn("Wireless debugging", header)


if __name__ == "__main__":
    unittest.main()

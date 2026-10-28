"""The G10 list keeps `com.google.android.tv.remote.service`, and the mic note moves with it (#1441).

On a TCL 43T6C (G10, Realtek RTD2875P, Google TV 12, firmware `STT2.230203.001`) the remote's mic
button stopped working after `examples/tcl-g10-google-tv/debloat.txt` was applied. The press produces
no key events on any `/dev/input/event*` — the voice goes over the Bluetooth audio channel — and
logcat shows what the system tries and cannot find:

    W ActivityManager: Unable to start service Intent {
      act=com.google.android.tv.remote.service.AtvSearchAssistantService
      pkg=com.google.android.tv.remote.service } U=0: not found

`AtvSearchAssistantService` lives inside the package the list disabled as "Google TV phone-app remote".
Re-enabling only that package, with every other line still applied, brought the button back on the real
remote. So the list now leaves it out, and the troubleshooting bullet that enumerates the lists still
disabling it has to drop the folder: a reader sent from that bullet to this list would find it keeps the
package.

The tests below read the list and the bullet by identifier, never by sentence, so the prose in either
file can be rewritten without touching this file.
"""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
EXAMPLE = "tcl-g10-google-tv"
PACKAGE = "com.google.android.tv.remote.service"

# The troubleshooting bullet opens with this and closes on "Those TVs", wrapped over several lines.
ANCHOR = "the lists that still disable"
CLOSE = "Those TVs"


def active_packages(text):
    """Package names a list actually applies: anything left after the `#` comment is dropped."""
    names = set()
    for line in text.splitlines():
        code = line.split("#", 1)[0].strip()
        if code:
            names.add(code.split()[0])
    return names


def disabler_list(doc):
    """The example folders the troubleshooting bullet names as still disabling the package."""
    start = doc.index(ANCHOR)
    tail = doc[start:]
    end = tail.index(CLOSE) if CLOSE in tail else len(tail)
    return re.findall(r"`([a-z0-9][a-z0-9-]+)`", tail[:end])


class G10KeepsTheMicBridge(unittest.TestCase):
    def setUp(self):
        self.list_text = (ROOT / "examples" / EXAMPLE / "debloat.txt").read_text(encoding="utf-8")
        self.doc = (ROOT / "docs" / "TROUBLESHOOTING.md").read_text(encoding="utf-8")

    def test_the_list_does_not_disable_it(self):
        self.assertNotIn(
            PACKAGE,
            active_packages(self.list_text),
            "the G10 list disables the package the remote's mic button starts AtvSearchAssistantService in",
        )

    def test_the_commented_line_names_the_service_that_needs_it(self):
        lines = self.list_text.splitlines()
        hits = [
            i
            for i, line in enumerate(lines)
            if line.lstrip().startswith("#") and PACKAGE in line
        ]
        self.assertTrue(hits, "the package should stay visible as a commented-out line with its reason")
        notes = ["\n".join(lines[max(0, i - 4) : i + 2]) for i in hits]
        self.assertTrue(
            any("AtvSearchAssistantService" in note for note in notes),
            "the note beside the commented line should name the service a mic press starts",
        )

    def test_the_troubleshooting_bullet_drops_the_folder(self):
        names = disabler_list(self.doc)
        self.assertGreaterEqual(len(names), 10, f"the bullet should still enumerate its lists, read {names}")
        self.assertNotIn(
            EXAMPLE,
            names,
            "the bullet still sends readers to a list that now keeps the package",
        )

    def test_every_folder_the_bullet_names_still_disables_it(self):
        # One-directional on purpose: a list may disable the package without being named, but a named
        # list that quietly keeps it sends a reader the wrong way, which is what this PR fixes.
        for name in disabler_list(self.doc):
            path = ROOT / "examples" / name / "debloat.txt"
            with self.subTest(example=name, path=str(path)):
                self.assertTrue(path.exists(), f"{name} is named but has no list")
                self.assertIn(PACKAGE, active_packages(path.read_text(encoding="utf-8")))


if __name__ == "__main__":
    unittest.main()

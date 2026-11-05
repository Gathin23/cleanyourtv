"""Anchors for the Streamer watcher note (#796): the heading, its three buttons, and the reboot limit.

The note says a shell-owned `getevent` watcher takes the remote's app buttons without binding a
key-filtering accessibility service, so the key auto-repeat a helper costs stays. Three things in it are
worth failing a later edit over, and they are the whole test: the Streamer's reported HID usages, the
limit that a reader cannot consume the press (which is why suppressing a button still needs a helper), and
that the watcher does not survive a reboot - the version that did survive needed TCP ADB left on, which
this page no longer offers. Wording is not pinned here; the maintainer asked for anchors, not a transcript.
"""
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
STREAMER = ROOT / "examples" / "google-tv-streamer-4k-kirkwood" / "debloat.txt"

HEADING = "Remapping the remote's app buttons without a key helper"

# As #796 reports them. A row that stops matching the report is a row this note invented.
REPORTED = (("YouTube", "0x000c0077"), ("Star", "0x000c0186"), ("Netflix", "0x000c0078"))


def section(text, heading):
    """Body of the '## <heading>' section, or None. Stops at the next level-1 or 2 heading."""
    match = re.search(r"^#{1,2}\s+" + re.escape(heading) + r"\s*$(.*?)(?=^#{1,2}\s|\Z)", text, re.M | re.S)
    return match.group(1) if match else None


class StreamerWatcherNoteTest(unittest.TestCase):
    def setUp(self):
        page = TROUBLE.read_text(encoding="utf-8")
        body = section(page, HEADING)
        self.assertIsNotNone(body, "the note's section is missing from TROUBLESHOOTING.md")
        self.raw = body or ""
        self.body = re.sub(r"\s+", " ", self.raw)

    def test_the_heading_is_unique_and_the_example_header_points_at_it(self):
        self.assertEqual(len(re.findall(r"^##\s+" + re.escape(HEADING) + r"\s*$",
                                        TROUBLE.read_text(encoding="utf-8"), re.M)), 1)
        header = re.sub(r"\s+", " ", STREAMER.read_text(encoding="utf-8"))
        self.assertIn('docs/TROUBLESHOOTING.md, "' + HEADING + '"', header)
        self.assertIn("#796", header)

    def test_the_three_buttons_match_the_report(self):
        rows = [line for line in self.raw.splitlines() if line.startswith("|")]
        for button, usage in REPORTED:
            row = next((r for r in rows if r.split("|")[1].strip() == button), None)
            self.assertIsNotNone(row, f"the {button} button is missing from the table")
            self.assertIn(usage, row or "")

    def test_keeps_the_limit_that_only_a_helper_can_suppress_a_button(self):
        self.assertIn("cannot consume the press", self.body)
        self.assertIn("Netflix starts too", self.body)

    def test_does_not_promise_the_watcher_survives_a_reboot(self):
        # The reboot-proof arrangement needed a TCP ADB client on the TV, i.e. port 5555 left listening;
        # the maintainer asked for that recipe to go, so the note must not quietly grow it back.
        self.assertIn("after every reboot", self.body)
        self.assertIn("detached from the `adb shell` session", self.body)
        for recipe in ("local ADB client", "authorized key", "127.0.0.1:5555"):
            self.assertNotIn(recipe, self.body)
        self.assertIn("../README.md#questions-people-ask", self.body)
        self.assertTrue((TROUBLE.parent / ".." / "README.md").resolve().exists())


if __name__ == "__main__":
    unittest.main()

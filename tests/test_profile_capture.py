"""`profile.py capture` writes a household profile from the master unit.

The master is the unit the person has already set up: its third-party apps become the profile's
installable list, and its disabled packages become the profile's debloat list, in the list format
tools/disable.sh and tools/restore.sh already accept.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from profile_fake import Harness, MASTER, package_lines, tv_state  # noqa: E402


class CaptureTest(unittest.TestCase):
    def setUp(self):
        self.h = Harness(self)
        self.h.put(MASTER, tv_state(
            installed=["com.example.brave", "com.example.tube", "com.google.android.feedback",
                       "com.google.android.tvlauncher", "com.gathin.tvhome"],
            third_party=["com.example.brave", "com.example.tube"],
            disabled=["com.google.android.feedback", "com.google.android.tvlauncher"]))

    def test_capture_writes_both_lists_and_the_header(self):
        result = self.h.run("capture", str(self.h.profile))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(package_lines(self.h.profile / "apps.txt"),
                         ["com.example.brave", "com.example.tube"])
        self.assertEqual(package_lines(self.h.profile / "debloat.txt"),
                         ["com.google.android.feedback", "com.google.android.tvlauncher"])
        header = (self.h.profile / "profile.json").read_text()
        self.assertIn('"model": "Chromecast"', header)
        self.assertIn('"device": "sabrina"', header)
        self.assertIn('"sdk": "34"', header)

    def test_capture_changes_nothing_on_the_tv(self):
        self.h.run("capture", str(self.h.profile))
        self.assertEqual(self.h.commands(MASTER), [])

    def test_capture_refuses_an_unreachable_tv(self):
        (self.h.states / (MASTER.replace(":", "_") + ".json")).unlink()
        result = self.h.run("capture", str(self.h.profile))
        self.assertEqual(result.returncode, 2)
        self.assertFalse((self.h.profile / "apps.txt").exists())

    def test_captured_debloat_list_is_disable_shaped(self):
        self.h.run("capture", str(self.h.profile))
        lines = (self.h.profile / "debloat.txt").read_text().splitlines()
        self.assertTrue(lines[0].startswith("#"), "the list opens with a comment, as the repo's lists do")
        self.assertIn("capture", lines[0])


if __name__ == "__main__":
    unittest.main()

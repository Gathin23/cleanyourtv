"""`profile.py check` reports how a unit differs from its household profile.

Three answers, the codes tools/check.py already uses: 0 the unit matches, 1 it drifted, 2 the run
could not check. A report-only run sends nothing that changes state on the TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from profile_fake import Harness, MASTER, TARGET, tv_state  # noqa: E402


class CheckTest(unittest.TestCase):
    def setUp(self):
        self.h = Harness(self)
        self.h.profile.mkdir(parents=True)
        (self.h.profile / "apps.txt").write_text(
            "# apps\ncom.example.brave\ncom.example.tube\n")
        (self.h.profile / "debloat.txt").write_text(
            "# disabled\ncom.google.android.feedback\n")
        self.h.put(TARGET, tv_state(
            installed=["com.example.brave", "com.example.tube", "com.google.android.feedback",
                       "com.gathin.tvhome"],
            third_party=["com.example.brave", "com.example.tube"],
            disabled=["com.google.android.feedback"]))

    def test_matching_unit_exits_0(self):
        result = self.h.run("check", str(self.h.profile), host=TARGET)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("matches the profile", result.stdout)
        self.assertEqual(self.h.commands(TARGET), [])

    def test_missing_app_is_drift(self):
        self.h.put(TARGET, tv_state(
            installed=["com.example.brave", "com.google.android.feedback", "com.gathin.tvhome"],
            third_party=["com.example.brave"], disabled=["com.google.android.feedback"]))
        result = self.h.run("check", str(self.h.profile), host=TARGET)
        self.assertEqual(result.returncode, 1)
        self.assertIn("missing (1): com.example.tube", result.stdout)

    def test_extra_app_is_drift_and_is_only_reported(self):
        self.h.put(TARGET, tv_state(
            installed=["com.example.brave", "com.example.tube", "com.example.other",
                       "com.google.android.feedback", "com.gathin.tvhome"],
            third_party=["com.example.brave", "com.example.tube", "com.example.other"],
            disabled=["com.google.android.feedback"]))
        result = self.h.run("check", str(self.h.profile), host=TARGET)
        self.assertEqual(result.returncode, 1)
        self.assertIn("not in the profile (1): com.example.other", result.stdout)
        self.assertEqual(self.h.commands(TARGET), [], "a report never changes the TV")

    def test_package_back_on_after_the_profile_disabled_it_is_drift(self):
        self.h.put(TARGET, tv_state(
            installed=["com.example.brave", "com.example.tube", "com.google.android.feedback",
                       "com.gathin.tvhome"],
            third_party=["com.example.brave", "com.example.tube"], disabled=[]))
        result = self.h.run("check", str(self.h.profile), host=TARGET)
        self.assertEqual(result.returncode, 1)
        self.assertIn("should be disabled (1): com.google.android.feedback", result.stdout)

    def test_package_disabled_but_the_profile_wants_it_is_drift(self):
        self.h.put(TARGET, tv_state(
            installed=["com.example.brave", "com.example.tube", "com.google.android.feedback"],
            third_party=["com.example.brave", "com.example.tube"],
            disabled=["com.example.tube", "com.google.android.feedback"]))
        result = self.h.run("check", str(self.h.profile), host=TARGET)
        self.assertEqual(result.returncode, 1)
        self.assertIn("disabled but the profile installs it (1): com.example.tube", result.stdout)

    def test_unreachable_tv_exits_2(self):
        (self.h.states / (TARGET.replace(":", "_") + ".json")).unlink()
        result = self.h.run("check", str(self.h.profile), host=TARGET)
        self.assertEqual(result.returncode, 2)
        self.assertIn("could not read", result.stderr)

    def test_missing_profile_exits_2(self):
        result = self.h.run("check", str(self.h.base / "nope"), host=TARGET)
        self.assertEqual(result.returncode, 2)


if __name__ == "__main__":
    unittest.main()

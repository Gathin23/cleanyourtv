"""tools/disable.sh and tools/disable.ps1 say when a safety override is on (#1845).

The overrides (FORCE_SETUPWRAITH, FORCE_HOME, TVINPUT_ALLOW, HANDLERS_ALLOW) are read from the
environment. `FORCE_HOME=1 tools/disable.sh ...` lasts one command, but in PowerShell a `$env:` value
stays set until the window closes and reaches child processes (README's `powershell ... -File` form,
check.py --fix), so a later run skips its safety check with nothing in the output saying why. Both
scripts now name every override that is set before they touch the TV.

The fake TV is the Home guard test's (tests/test_home_guard.py), which runs the real scripts against a
simulated transport.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it; CI has no PowerShell.
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_home_guard as home_guard  # noqa: E402

OVERRIDES = ("FORCE_SETUPWRAITH", "FORCE_HOME", "TVINPUT_ALLOW", "HANDLERS_ALLOW")


class OverrideNoticeTest(home_guard.HomeGuardTest):
    def test_an_override_that_is_set_is_named_before_the_tv_is_touched(self):
        for entry in self.entries():
            for override in OVERRIDES:
                with self.subTest(entry=entry, override=override):
                    self.tv()
                    result, _ = self.disable(entry, extra_env={override: "1"})
                    self.assertIn(f"{override}=1", self.output(result),
                                  "a run with an override on must name it")
                    self.assertIn("safety check it overrides stays off", self.output(result),
                                  "the note must say what the override costs")

    def test_a_run_without_overrides_says_nothing_about_them(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, _ = self.disable(entry)
                self.assertNotIn("stays off until it is", self.output(result),
                                 "only a run with an override on carries the note")

    def test_the_powershell_note_says_how_to_clear_the_override(self):
        if not home_guard.PWSH:
            raise unittest.SkipTest("the PowerShell twin needs pwsh on PATH")
        self.tv()
        result, _ = self.disable("ps1", extra_env={"FORCE_HOME": "1"})
        self.assertIn("Remove-Item env:FORCE_HOME", self.output(result))

    def test_the_shell_note_says_how_to_clear_the_override(self):
        self.tv()
        result, _ = self.disable("sh", extra_env={"FORCE_HOME": "1"})
        self.assertIn("unset FORCE_HOME", self.output(result))


if __name__ == "__main__":
    unittest.main()

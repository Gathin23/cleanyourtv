"""The wrong-HDMI-handler note (#805) says how to disable the app that answers: HANDLERS_ALLOW=1.

docs/TROUBLESHOOTING.md's "Picking HDMI 1 in the Inputs menu opened an unrelated app" gives two ways
out: hide the rows with a theme, or disable the app that answers. The bullet was written before
#1092, which made tools/disable.sh, disable.ps1 and check.py --fix send the bullet's own query and
keep every listed app it names unless HANDLERS_ALLOW=1. Putting ES File Explorer in a list therefore
ended in "Skipping com.estrongs.android.pop: the TV opens its HDMI/AV inputs and channels with it",
and check.py --fix left it on and exited 1 on every run. The bullet now names the switch, keeps it to
a list of that one app so the rest of the list keeps the guard, and quotes the message the scripts
print; the scripts are run here to show that is what they do.

Two more things the bullet has to say. `$env:HANDLERS_ALLOW=1` stays set in the PowerShell window,
so the next disable.ps1 or check.py --fix on the main list would skip the guard as well; the bullet
says to clear it. And the undo is `pm enable` for that app: the one-app list's record holds this
project's launcher as Home and the stock launcher as disabled (launcher/build-and-install.sh turned it
off before), so restore.sh with it turns the app back on and leaves Home on this project's launcher
(#1714); that is run here too.

The doc tests borrow the bullet reader of tests/test_input_intent_note.py, the disable.sh tests the
fake client of tests/test_handler_guard.py, the check.py tests the fake TV of
tests/test_drift_check.py, and the restore.sh test the simulated TV of
tests/test_recovery_record.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import re
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_drift_check as drift  # noqa: E402  (imported as a module so its tests do not run twice)
import test_handler_guard as guard  # noqa: E402  (imported as a module so its tests do not run twice)
from test_input_intent_note import START, TROUBLE, bullet  # noqa: E402
import test_recovery_record as rec  # noqa: E402  (imported as a module so its tests do not run twice)

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = {
    "disable.sh": "${HANDLERS_ALLOW:-}",
    "disable.ps1": "$env:HANDLERS_ALLOW",
    "check.py": 'os.environ.get("HANDLERS_ALLOW")',
}
# The app that took HDMI 1 on the RCA set, and how `query-activities --brief` named it there.
ES = "com.estrongs.android.pop"
ES_ANSWER = (f'printf "%s\\n" "1 activities found:" '
             f'"  {ES}/.app.transferstation.FileTransferStationActivity" == "No activities found"')
SKIPPED = "the TV opens its HDMI/AV inputs and channels with it"


class WrongHandlerNoteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # One line per sentence would do as well; the wrapping is not what is being pinned.
        cls.note = " ".join(bullet(TROUBLE.read_text(encoding="utf-8"), START).split())

    def test_the_bullet_names_the_switch(self):
        self.assertIn("disable the app that answers", self.note)
        self.assertIn("`HANDLERS_ALLOW=1`", self.note)
        self.assertIn("`$env:HANDLERS_ALLOW`", self.note)

    def test_the_switch_goes_with_a_list_of_that_app_alone(self):
        # HANDLERS_ALLOW=1 on the whole list would let it disable the Live TV and Settings apps too.
        self.assertIn("a list of its own", self.note)

    def test_the_bullet_says_check_fix_needs_it_too(self):
        self.assertIn("`tools/venv/bin/python -I tools/check.py --fix`", self.note)

    def test_the_quoted_message_is_what_the_scripts_print(self):
        quoted = re.findall(r'"(the TV opens [^"]+)"', self.note)
        self.assertEqual(quoted, [SKIPPED])
        for name in SCRIPTS:
            with self.subTest(script=name):
                self.assertIn(SKIPPED, (ROOT / "tools" / name).read_text(encoding="utf-8"))

    def test_the_switch_is_the_one_the_scripts_read(self):
        for name, reads in SCRIPTS.items():
            with self.subTest(script=name):
                self.assertIn(reads, (ROOT / "tools" / name).read_text(encoding="utf-8"))

    def test_powershell_clears_the_switch_afterwards(self):
        # $env: is set for the whole window, not for one command as `HANDLERS_ALLOW=1 cmd` is.
        self.assertIn("`Remove-Item Env:HANDLERS_ALLOW`", self.note)
        self.assertIn("on your main list skips the guard too", self.note)

    def test_the_undo_is_pm_enable_for_that_app(self):
        self.assertIn("`tools/tv.py 'pm enable --user 0 <package>'`", self.note)
        self.assertNotIn("with that list undoes it", self.note)

    def test_the_bullet_says_what_restore_does_to_home(self):
        # Pinned by WrongHandlerRestoreTest below.
        self.assertIn("`tools/restore.sh` with that list", self.note)
        self.assertIn("leaves Home on this project's launcher", self.note)
        self.assertIn("only if the stock launcher was on when the list was applied", self.note)


class WrongHandlerDisableTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_handler_guard.py do not run twice.
    run_disable = guard.HandlerGuardTest.run_disable

    def test_a_line_in_the_main_list_is_skipped(self):
        with mock.patch.object(guard, "LIST", f"# My TV\ncom.example.before\n{ES}\ncom.example.after\n"):
            commands, stderr = self.run_disable(handlers=ES_ANSWER)
        self.assertEqual(commands[:2], ["pm disable-user --user 0 com.example.before",
                                        "pm disable-user --user 0 com.example.after"])
        self.assertNotIn(f"pm disable-user --user 0 {ES}", commands)
        self.assertIn(f"Skipping {ES}: {SKIPPED}", stderr)

    def test_a_list_of_its_own_with_the_switch_disables_it(self):
        with mock.patch.object(guard, "LIST", f"# The app that took HDMI 1 (#805)\n{ES}\n"):
            commands, _ = self.run_disable(handlers=ES_ANSWER, extra_env={"HANDLERS_ALLOW": "1"})
        self.assertIn(f"pm disable-user --user 0 {ES}", commands)


class WrongHandlerCheckFixTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_drift_check.py do not run twice.
    setUp = drift.DriftCheckTest.setUp
    write_list = drift.DriftCheckTest.write_list
    tv = drift.DriftCheckTest.tv
    run_check = drift.DriftCheckTest.run_check
    sent = drift.DriftCheckTest.sent

    def back_on(self):
        # A firmware update turned the app back on, and it still answers the channel query.
        self.write_list([ES])
        self.tv(installed=drift.BASE + [ES], input_apps=[ES])

    def test_fix_leaves_it_on_without_the_switch(self):
        self.back_on()
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"leaving {ES} on: {SKIPPED}", result.stdout)
        self.assertNotIn(f"pm disable-user --user 0 {ES}", self.sent())

    def test_fix_disables_it_with_the_switch(self):
        self.back_on()
        result = self.run_check("--fix", str(self.list_path), env={"HANDLERS_ALLOW": "1"})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.sent(), [f"pm disable-user --user 0 {ES}"])


class WrongHandlerRestoreTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_recovery_record.py do not run twice.
    setUp = rec.RecoveryRecordTest.setUp
    write_list = rec.RecoveryRecordTest.write_list
    tv = rec.RecoveryRecordTest.tv
    run_script = rec.RecoveryRecordTest.run_script
    record = rec.RecoveryRecordTest.record
    sent = rec.RecoveryRecordTest.sent
    clear_sent = rec.RecoveryRecordTest.clear_sent

    def test_restoring_the_one_app_list_leaves_this_projects_launcher_on_home(self):
        # The main list is applied and launcher/build-and-install.sh has made this project's
        # launcher Home, disabling the stock launcher outside any list. Then the one-app list goes on.
        # The record notes the stock launcher as disabled, so undo leaves it off and Home where it
        # was (#1714); it used to turn the stock launcher on and hand it Home.
        custom = "com.gathin.tvhome/.MainActivity"
        stock = "com.google.android.tvlauncher"
        self.write_list([ES])
        self.tv(installed=[ES, stock, "com.gathin.tvhome"], disabled=[stock], home=custom)
        with mock.patch.dict(os.environ, {"HANDLERS_ALLOW": "1"}):
            result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"pm disable-user --user 0 {ES}", self.sent())
        self.assertEqual(self.record()["home"], custom)
        self.assertEqual(self.record()["packages"][stock], "disabled")
        self.clear_sent()

        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn(f"pm enable {ES}", sent)
        self.assertNotIn(f"pm enable {stock}", sent)
        home = [c for c in sent if c.startswith("cmd package set-home-activity")]
        self.assertEqual(home, [f"cmd package set-home-activity --user 0 {custom}"])


if __name__ == "__main__":
    unittest.main()

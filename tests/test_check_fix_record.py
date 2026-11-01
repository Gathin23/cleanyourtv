"""check.py --fix and a second disable.sh run keep Undo able to turn back on what they disable.

restore.sh puts back only what the recovery record holds. check.py --fix never touched the record, so
a name the list gained after the last disable.sh run was disabled by --fix and then left off by
restore.sh for good ("not in the record, leaving alone"). A package the record calls absent, because
the TV did not have it when disable.sh first ran, was left off the same way once an update or the Play
Store installed it and --fix or disable.sh disabled it: save() only ever added missing names. Both now
go through recovery.extend_record(), which adds the names the record does not hold and records an
absent package that is installed and enabled now as enabled. check.py only extends a record that
exists: a record of the TV after cleanup would make Undo leave the whole list off.

The TV is the drift check's fake tools/tv.py (tests/test_drift_check.py), which applies what --fix and
a batch send, and the real disable.sh, restore.sh, check.py and recovery.py run against it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_drift_check as drift  # noqa: E402  (imported as a module so its tests do not run twice)
import test_recovery_record as record_test  # noqa: E402  (the same)

ROOT = drift.ROOT
STOCK = drift.STOCK
WEATHER = "com.example.weather"


class CheckFixRecordTest(unittest.TestCase):
    # The drift check's harness: a temporary repo with the fake tv.py, HOME, the list and the TV state.
    write_list = drift.DriftCheckTest.write_list
    tv = drift.DriftCheckTest.tv
    run_check = drift.DriftCheckTest.run_check
    sent = drift.DriftCheckTest.sent
    # The recovery record test's runner for disable.sh and restore.sh, on the same repo and state.
    run_script = record_test.RecoveryRecordTest.run_script

    def setUp(self):
        # The record's old name carries the port, so a TV_PORT left in the environment (by the user,
        # or by an earlier test in the same run) must not reach the scripts.
        environ = mock.patch.dict(os.environ)
        environ.start()
        self.addCleanup(environ.stop)
        os.environ.pop("TV_PORT", None)
        drift.DriftCheckTest.setUp(self)
        for script in ("disable.sh", "restore.sh"):
            shutil.copy2(ROOT / "tools" / script, self.repo / "tools" / script)
        # The scripts call tools/venv/bin/python: run the real interpreter, which imports the fake
        # tv.py for the transport and the real recovery.py for the logic.
        client = self.repo / "tools" / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        self.write_list(["com.example.music", "com.example.store"])

    def state(self):
        return json.loads(self.state_path.read_text())

    def set_state(self, **changes):
        state = self.state()
        state.update(changes)
        self.state_path.write_text(json.dumps(state))

    def record_file(self):
        files = list((self.home / ".cleanyourtv").glob("recovery-*.json"))
        self.assertEqual(len(files), 1, files)
        return files[0]

    def record(self):
        return json.loads(self.record_file().read_text())["packages"]

    def apply_list(self):
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return result

    def undo(self):
        self.set_state(commands=[])
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return self.sent(), result.stderr

    def test_a_name_the_list_gained_is_recorded_before_fix_disables_it(self):
        self.tv(installed=drift.BASE + [WEATHER], disabled=[])
        self.apply_list()
        # A git pull adds a line; the next --fix disables it.
        self.write_list(["com.example.music", "com.example.store", WEATHER])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"pm disable-user --user 0 {WEATHER}", self.sent())
        self.assertIn("added 1 names the list gained since then", result.stdout)
        self.assertEqual(self.record()[WEATHER], "enabled")

        sent, err = self.undo()
        self.assertIn(f"pm enable {WEATHER}", sent)
        self.assertNotIn("not in the record", err)

    def test_a_package_installed_after_the_record_is_recorded_before_fix_disables_it(self):
        self.write_list(["com.example.music", "com.example.store", "com.example.later"])
        self.tv(installed=drift.BASE, disabled=[STOCK])
        self.apply_list()
        self.assertEqual(self.record()["com.example.later"], "absent")
        # A TV refuses pm disable-user for a package it has no copy of, which this fake does not; then
        # an update installs it, enabled.
        state = self.state()
        self.set_state(installed=state["installed"] + ["com.example.later"],
                       disabled=[name for name in state["disabled"] if name != "com.example.later"])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("pm disable-user --user 0 com.example.later", self.sent())
        self.assertEqual(self.record()["com.example.later"], "enabled")

        sent, err = self.undo()
        self.assertIn("pm enable com.example.later", sent)
        self.assertNotIn("leaving com.example.later alone", err)

    def test_disable_run_again_records_a_package_installed_since(self):
        self.write_list(["com.example.music", "com.example.store", "com.example.later"])
        self.tv(installed=drift.BASE, disabled=[STOCK])
        self.apply_list()
        state = self.state()
        self.set_state(installed=state["installed"] + ["com.example.later"],
                       disabled=[name for name in state["disabled"] if name != "com.example.later"])
        self.apply_list()
        self.assertEqual(self.record()["com.example.later"], "enabled")
        sent, _ = self.undo()
        self.assertIn("pm enable com.example.later", sent)

    def test_entries_from_before_cleanup_are_not_overwritten(self):
        self.write_list(["com.example.music", "com.example.store", "com.example.later"])
        self.tv(installed=drift.BASE, disabled=["com.example.store", STOCK])
        self.apply_list()
        first = self.record()
        self.assertEqual(first["com.example.store"], "disabled")
        # Installed since, but disabled: nobody can say by whom, so it stays absent, which restore
        # leaves alone exactly as it would leave a disabled one.
        state = self.state()
        self.set_state(installed=state["installed"] + ["com.example.later"],
                       disabled=[name for name in state["disabled"] if name != "com.example.music"])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.record(), first)

    def test_a_report_without_fix_leaves_the_record_alone(self):
        self.tv(installed=drift.BASE + [WEATHER], disabled=[])
        self.apply_list()
        before = self.record_file().read_bytes()
        self.write_list(["com.example.music", "com.example.store", WEATHER])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(self.record_file().read_bytes(), before)

    def test_a_record_fix_cannot_use_stops_it_before_the_tv(self):
        for case in ("damaged", "two names"):
            with self.subTest(case=case):
                self.setUp()
                self.tv(disabled=[])
                self.apply_list()
                path = self.record_file()
                if case == "damaged":
                    path.write_text("{not json")
                else:
                    # The name older versions gave a record on the network, next to the one it gets now.
                    shutil.copy2(path, path.with_name(path.name.replace("192.168.1.50-",
                                                                        "192.168.1.50_5555-")))
                before = path.read_bytes()
                # An update turned one listed package back on.
                self.set_state(commands=[], disabled=["com.example.store"])
                result = self.run_check("--fix", str(self.list_path))
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(str(path), result.stderr)
                self.assertEqual(self.sent(), [], "nothing reached the TV")
                self.assertEqual(path.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()

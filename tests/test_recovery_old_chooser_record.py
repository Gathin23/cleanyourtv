"""tools/recovery.py reads a record that an older save() wrote with Android's HOME chooser in it.

Before save() learned to leave the chooser out (tests/test_recovery_home_chooser.py), disable.sh on a
TV whose Home had no default recorded "home": "android/com.android.internal.app.ResolverActivity"
and "android": "enabled" among the packages, and exited 0. A Chromecast with Google TV 4K (Google
TV 14, UTTC.250917.004) lists package:android in `pm list packages` and not in `pm list packages -d`,
so "enabled" is what such a TV got. load_record refused those records ("not a package name:
'android'"), and moving the file aside, the only way past that, loses the state the record exists
to keep: restore without a record enables every listed package, including the ones the owner had
disabled before cleanup (#239), and a new disable.sh run records the TV after cleanup as the
original. Everything else in such a record is right, so load_record now drops the chooser and its
one entry and reads the rest.

The first three tests fail without that and pass with it. The last one passes either way: it pins
that only the chooser's own entry is dropped, and that any other bad name is still refused.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import shutil
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_recovery_home_chooser as chooser  # noqa: E402
import test_recovery_record as records  # noqa: E402

CHOOSER = chooser.CHOOSER
STOCK = chooser.STOCK


class OldChooserRecordTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_recovery_record.py do not run a
    # second time under this class.
    setUp = records.RecoveryRecordTest.setUp
    write_list = records.RecoveryRecordTest.write_list
    tv = records.RecoveryRecordTest.tv
    run_script = records.RecoveryRecordTest.run_script
    record = records.RecoveryRecordTest.record
    sent = records.RecoveryRecordTest.sent
    clear_sent = records.RecoveryRecordTest.clear_sent

    def write_old_record(self, **edits):
        """The record the older save() left on a TV whose Home had no default, and its path."""
        self.tv(home=chooser.CHOOSER_ANSWER)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        path = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
        record = json.loads(path.read_text())
        record["home"] = CHOOSER
        record["packages"]["android"] = "enabled"
        record.update(edits)
        path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
        self.clear_sent()
        return path

    def test_restore_replays_an_old_record_without_the_chooser(self):
        path = self.write_old_record()
        before = path.read_bytes()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        # The batch a record written by the fixed save() gives: no `pm enable android`, Home goes to
        # what the stock launcher resolves to, and the package that was off before cleanup stays off.
        self.assertEqual(self.sent(), [
            "pm enable com.example.enabled",
            f"pm enable {STOCK}",
            "cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a "
            f"android.intent.action.MAIN -c android.intent.category.HOME {STOCK} | tail -n 1)",
            "settings put global window_animation_scale 0.5",
            "settings delete global transition_animation_scale",
            "settings put global animator_duration_scale 1.0",
            "pm list packages -d",
        ])
        self.assertIn("leaving com.example.already_off alone", result.stderr)
        self.assertIn(f"the recorded Home is Android's chooser ({CHOOSER})", result.stderr)
        self.assertEqual(path.read_bytes(), before, "restore reads the record, it does not rewrite it")

    def test_disable_runs_again_on_an_old_record_and_keeps_it(self):
        path = self.write_old_record()
        before = path.read_bytes()
        # The retry happens on a TV where the list has already been applied.
        self.tv(home=chooser.CHOOSER_ANSWER, installed=["com.example.already_off", STOCK],
                disabled=["com.example.enabled", "com.example.already_off"])
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("keeping the recovery record", result.stdout)
        self.assertEqual(path.read_bytes(), before, "nothing was added, so the file is not rewritten")
        self.assertIn("pm disable-user --user 0 com.example.enabled", self.sent())

    def test_a_list_that_gained_names_writes_the_old_record_back_without_the_chooser(self):
        path = self.write_old_record()
        first = json.loads(path.read_text())
        self.write_list(["com.example.enabled", "com.example.already_off", "com.example.absent",
                         "com.example.added_later"])
        self.tv(home=chooser.CHOOSER_ANSWER,
                installed=["com.example.already_off", "com.example.added_later", STOCK],
                disabled=["com.example.enabled", "com.example.already_off"])
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        record = self.record()
        self.assertIsNone(record["home"])
        # The entries from before cleanup are kept as they were, not the TV as it is now, including
        # the stock launcher save() recorded alongside the list (#1714).
        self.assertEqual(record["packages"], {
            "com.example.enabled": "enabled",
            "com.example.already_off": "disabled",
            "com.example.absent": "absent",
            "com.example.added_later": "enabled",
            STOCK: "enabled",
        })
        self.assertEqual(record["created"], first["created"])
        self.assertEqual(record["settings"], first["settings"])

    def test_only_the_chooser_entry_is_dropped(self):
        # "android" with a launcher as Home is no chooser record, and a chooser record that holds
        # another bad name is not one the old save() wrote: both are refused as before, and the file
        # and the TV are left alone.
        cases = {
            "android without the chooser": dict(home=records.STOCK_HOME),
            "another bad name": dict(packages={"com.example.enabled": "enabled", "android": "enabled",
                                               "com.example.x; reboot #": "enabled"}),
        }
        for case, edits in cases.items():
            for script in ("restore.sh", "disable.sh"):
                with self.subTest(case=case, script=script):
                    shutil.rmtree(self.home / ".cleanyourtv", ignore_errors=True)
                    path = self.write_old_record(**edits)
                    before = path.read_bytes()
                    result = self.run_script(script, str(self.list_path))
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn("not a usable recovery record (not a package name", result.stderr)
                    self.assertEqual(self.sent(), [], "nothing reached the TV")
                    self.assertEqual(path.read_bytes(), before, "the file is left for inspection")


if __name__ == "__main__":
    unittest.main()

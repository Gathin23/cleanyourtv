"""tools/recovery.py does not record Android's HOME chooser as the Home to restore.

When Home has no default, `cmd package resolve-activity --brief` ends at the chooser. A Chromecast
with Google TV 4K (Google TV 14, UTTC.250917.004) prints a lookup that ends there as two lines, a
priority line and then the component; there a plain LAUNCHER lookup does:

    priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false
    android/com.android.internal.app.ResolverActivity

recovery.py took the last line as the Home that owned the TV, which put "android" among the
recorded packages. That is not a package name, so load_record refused the file: disable.sh wrote it
and exited 0, and from then on restore.sh and every later disable.sh run stopped at "not a usable
recovery record" until the owner moved the file aside by hand. The chooser cases in
tests/test_home_guard.py fake the answer as a bare "android", which is why they did not catch it.

The record tests borrow the fake TV and helpers of tests/test_recovery_record.py, and the check.py
tests those of tests/test_drift_check.py. query_home is shared with check.py, whose report on the
chooser stays what it was, so the fix lives in save() and the last two tests pin that.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_drift_check as drift  # noqa: E402
import test_recovery_record as records  # noqa: E402

CHOOSER = "android/com.android.internal.app.ResolverActivity"
CHOOSER_ANSWER = "priority=0 preferredOrder=0 match=0x0 specificIndex=-1 isDefault=false\n" + CHOOSER
STOCK = "com.google.android.tvlauncher"


class RecoveryHomeChooserTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_recovery_record.py do not run a
    # second time under this class.
    setUp = records.RecoveryRecordTest.setUp
    write_list = records.RecoveryRecordTest.write_list
    tv = records.RecoveryRecordTest.tv
    run_script = records.RecoveryRecordTest.run_script
    record = records.RecoveryRecordTest.record
    sent = records.RecoveryRecordTest.sent
    clear_sent = records.RecoveryRecordTest.clear_sent

    def test_the_chooser_is_not_recorded_as_home(self):
        self.tv(home=CHOOSER_ANSWER)
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        record = self.record()
        self.assertIsNone(record["home"])
        # No "android" entry: it is not a package name, and a record holding it cannot be read back.
        # The installed stock launcher is recorded too, so undo knows it was on (#1714).
        self.assertEqual(record["packages"], {
            "com.example.enabled": "enabled",
            "com.example.already_off": "disabled",
            "com.example.absent": "absent",
            STOCK: "enabled",
        })
        self.assertIn("Android's chooser", result.stdout)

    def test_restore_after_the_chooser_resolves_the_stock_launcher(self):
        # The same batch as on a TV where nothing answered HOME: no `pm enable android`, and Home goes
        # to whatever the stock launcher resolves to, never to the chooser.
        self.tv(home=CHOOSER_ANSWER)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
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

    def test_a_second_run_after_the_chooser_keeps_the_record(self):
        self.tv(home=CHOOSER_ANSWER)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        first = self.record()
        # The retry happens on a TV where the list has already been applied.
        self.tv(home=CHOOSER_ANSWER, installed=["com.example.already_off", STOCK],
                disabled=["com.example.enabled", "com.example.already_off"])
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("keeping the recovery record", result.stdout)
        self.assertEqual(self.record(), first)
        self.assertIn("pm disable-user --user 0 com.example.enabled", self.sent())


class CheckHomeChooserTest(unittest.TestCase):
    # check.py reads Home through the same query_home, and these two pass with or without the fix:
    # a fix there instead of in save() would turn the first one into "Home is nothing" and exit 1 on
    # every cron run, and --fix would then move Home on a TV that only debloats.
    setUp = drift.DriftCheckTest.setUp
    write_list = drift.DriftCheckTest.write_list
    tv = drift.DriftCheckTest.tv
    run_check = drift.DriftCheckTest.run_check
    sent = drift.DriftCheckTest.sent

    def test_the_chooser_without_our_launcher_is_not_drift(self):
        self.write_list(["com.example.music", "com.example.store"])
        self.tv(installed=["com.example.music", "com.example.store", STOCK, "com.example.launcher"],
                disabled=["com.example.music", "com.example.store"], home=CHOOSER_ANSWER,
                home_activities=[f"{STOCK}/.MainActivity", "com.example.launcher/.Home"])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"nothing drifted: 2 listed packages are still disabled and Home is {CHOOSER}",
                      result.stdout)
        self.assertEqual(self.sent(), [])

    def test_the_chooser_with_our_launcher_is_drift_by_name(self):
        self.tv(home=CHOOSER_ANSWER)
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"Home is {CHOOSER}, not {drift.CUSTOM}", result.stdout)
        self.assertEqual(self.sent(), [])


if __name__ == "__main__":
    unittest.main()

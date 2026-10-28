"""tools/recovery.py does not record a Home that is no launcher as the Home to restore.

save() kept Android's chooser out of the record (tests/test_recovery_home_chooser.py), but recorded
any other component that answered HOME, and restore.sh replays the recorded Home as
`cmd package set-home-activity --user 0 <home>`. Some of what answers HOME is not a launcher, and
disable.sh and check.py already refuse to count it as one: setupwraith's `.RecoveryActivity`, which
takes Home on an Acer R4G or a Panasonic 4KTV-3MUP once the firmware turns setupwraith back on at a
cold boot, leaving a black screen (on a Xiaomi MiTV-MZTU0 system_server crash-looped on it), a
vendor `FallbackHome`, the placeholder a TCL or a Xiaomi MiTV-MFFU0 shows when no launcher is enabled,
and the factory test that answers HOME on a Xiaomi Mi TV 4A (docs/TROUBLESHOOTING.md and the lists).
A list applied in that state, a fix-up list or a new privacy.txt, took that as the TV before cleanup,
and undo then pinned Home to it, even after re-enabling the stock launcher. A Chromecast with Google TV
4K (Google TV 14, UTTC.250917.004) lists both setupwraith/.RecoveryActivity and
com.android.tv.settings/.system.FallbackHome among its HOME activities.

Now save() leaves such a Home out, as it does the chooser, and load_record reads one in an older record
as no Home, so restore resolves the stock launcher. The package's own entry stays: its state before
cleanup is real. The record tests borrow the fake TV and helpers of tests/test_recovery_record.py.
The cases are copied into recovery.py rather than shared with check.py, which imports recovery.py, and
the last class keeps the copies in step. Every test but test_a_launcher_is_still_recorded fails
without the fix.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import json
from pathlib import Path
import re
import shutil
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tests"))
import recovery  # noqa: E402
import test_recovery_record as records  # noqa: E402

spec = importlib.util.spec_from_file_location("cyt_check", ROOT / "tools" / "check.py")
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)

SETUPWRAITH = "com.google.android.tungsten.setupwraith"
RECOVERY = f"{SETUPWRAITH}/.RecoveryActivity"
LAUNCHERX = "com.google.android.apps.tv.launcherx"
# What answers HOME on the TVs named above, and none of it is a launcher to put back.
NO_LAUNCHER_HOMES = [
    RECOVERY,
    "com.tcl.keycustomfunctionservice/.FallbackHome",
    "com.android.tv.settings/.system.FallbackHome",
    "com.xiaomi.mitv.settings/.entry.FallbackHome",
    "com.duokan.factorytest/.FactoryLauncher",
]
LIST_STATES = {
    "com.example.enabled": "enabled",
    "com.example.already_off": "disabled",
    "com.example.absent": "absent",
}


class RecoveryHomeNotALauncherTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_recovery_record.py do not run a
    # second time under this class.
    setUp = records.RecoveryRecordTest.setUp
    write_list = records.RecoveryRecordTest.write_list
    tv = records.RecoveryRecordTest.tv
    run_script = records.RecoveryRecordTest.run_script
    record = records.RecoveryRecordTest.record
    sent = records.RecoveryRecordTest.sent
    clear_sent = records.RecoveryRecordTest.clear_sent

    def tv_on(self, home, **overrides):
        package = home.split("/", 1)[0]
        self.tv(home=home, installed=["com.example.enabled", "com.example.already_off",
                                      "com.google.android.tvlauncher", LAUNCHERX, package], **overrides)

    def test_a_home_that_is_no_launcher_is_not_recorded(self):
        for home in NO_LAUNCHER_HOMES:
            with self.subTest(home=home):
                shutil.rmtree(self.home / ".cleanyourtv", ignore_errors=True)
                self.tv_on(home)
                result = self.run_script("disable.sh", str(self.list_path))
                self.assertEqual(result.returncode, 0, result.stderr)
                record = self.record()
                self.assertIsNone(record["home"])
                # The package keeps its entry: it was enabled before cleanup. So do the two stock
                # launchers installed here, which undo turns back on only because they were on (#1714).
                self.assertEqual(record["packages"], {**LIST_STATES, home.split("/", 1)[0]: "enabled",
                                                      "com.google.android.tvlauncher": "enabled",
                                                      LAUNCHERX: "enabled"})
                self.assertIn(f"HOME before: {home}, which is not a launcher", result.stdout)

    def test_a_launcher_is_still_recorded(self):
        # Passes with or without the fix: only the cases disable.sh and check.py skip are left out.
        for home in (f"{LAUNCHERX}/.home.HomeActivity", records.STOCK_HOME,
                     f"{recovery.CUSTOM_LAUNCHER}/.MainActivity"):
            with self.subTest(home=home):
                shutil.rmtree(self.home / ".cleanyourtv", ignore_errors=True)
                self.tv_on(home)
                self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
                self.assertEqual(self.record()["home"], home)

    def test_restore_after_the_recovery_screen_resolves_the_stock_launcher(self):
        # The Acer R4G case: launcherx is the stock launcher, and Home goes to what it resolves to,
        # never to setupwraith's recovery screen. Both stock launchers were on when the record was
        # taken, so both are in it and both come back, in the record's order (#1714).
        self.tv_on(RECOVERY)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path), LAUNCHERX)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.sent(), [
            "pm enable com.example.enabled",
            f"pm enable {LAUNCHERX}",
            f"pm enable {SETUPWRAITH}",
            "pm enable com.google.android.tvlauncher",
            "cmd package set-home-activity --user 0 $(cmd package resolve-activity --brief -a "
            f"android.intent.action.MAIN -c android.intent.category.HOME {LAUNCHERX} | tail -n 1)",
            "settings put global window_animation_scale 0.5",
            "settings delete global transition_animation_scale",
            "settings put global animator_duration_scale 1.0",
            "pm list packages -d",
        ])

    def write_old_record(self, home):
        """The record an older save() left on a TV whose Home was `home`, and its path."""
        self.tv()
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        path = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
        record = json.loads(path.read_text())
        record["packages"].pop("com.google.android.tvlauncher")
        record["home"] = home
        record["packages"][home.split("/", 1)[0]] = "enabled"
        path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
        self.clear_sent()
        return path

    def test_restore_reads_an_old_record_with_such_a_home_as_no_home(self):
        for home in NO_LAUNCHER_HOMES:
            with self.subTest(home=home):
                shutil.rmtree(self.home / ".cleanyourtv", ignore_errors=True)
                path = self.write_old_record(home)
                before = path.read_bytes()
                result = self.run_script("restore.sh", str(self.list_path), LAUNCHERX)
                self.assertEqual(result.returncode, 0, result.stderr)
                sent = self.sent()
                self.assertNotIn(f"cmd package set-home-activity --user 0 {home}", sent)
                self.assertIn("cmd package set-home-activity --user 0 $(cmd package resolve-activity "
                              "--brief -a android.intent.action.MAIN -c android.intent.category.HOME "
                              f"{LAUNCHERX} | tail -n 1)", sent)
                self.assertIn(f"pm enable {home.split('/', 1)[0]}", sent)
                self.assertIn(f"the recorded Home is {home}, which is not a launcher", result.stderr)
                self.assertEqual(path.read_bytes(), before,
                                 "restore reads the record, it does not rewrite it")

    def test_a_list_that_gained_names_writes_the_old_record_back_without_that_home(self):
        path = self.write_old_record(RECOVERY)
        first = json.loads(path.read_text())
        self.write_list([*LIST_STATES, "com.example.added_later"])
        self.tv(installed=["com.example.already_off", "com.example.added_later", SETUPWRAITH],
                disabled=["com.example.enabled", "com.example.already_off"], home=RECOVERY)
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        record = self.record()
        self.assertIsNone(record["home"])
        self.assertEqual(record["packages"], {**LIST_STATES, SETUPWRAITH: "enabled",
                                              "com.example.added_later": "enabled"})
        self.assertEqual(record["created"], first["created"])


class NotALauncherCopiesTest(unittest.TestCase):
    def test_recovery_py_skips_what_check_py_skips(self):
        self.assertEqual(recovery.NOT_LAUNCHERS, check.NOT_LAUNCHERS)
        for name in ("FALLBACK_HOME_RE", "FACTORY_HOME_RE"):
            with self.subTest(pattern=name):
                ours, theirs = getattr(recovery, name), getattr(check, name)
                self.assertEqual((ours.pattern, ours.flags), (theirs.pattern, theirs.flags))

    def test_recovery_py_skips_the_packages_the_disable_scripts_skip(self):
        sh = (ROOT / "tools" / "disable.sh").read_text(encoding="utf-8")
        line = re.search(r"grep -vxF((?: -e \S+)+)", sh)
        self.assertIsNotNone(line, "no setup wizard and settings names found in tools/disable.sh")
        self.assertEqual(set(re.findall(r"-e (\S+)", line.group(1))), recovery.NOT_LAUNCHERS)
        ps1 = (ROOT / "tools" / "disable.ps1").read_text(encoding="utf-8")
        names = re.search(r"^\$notLaunchers = @\(([^)]*)\)$", ps1, re.M)
        self.assertIsNotNone(names, "no $notLaunchers found in tools/disable.ps1")
        self.assertEqual(set(re.findall(r"'([^']+)'", names.group(1))), recovery.NOT_LAUNCHERS)

    def test_what_counts_as_no_launcher(self):
        for home in NO_LAUNCHER_HOMES + ["com.android.settings/.FallbackHome",
                                     "com.android.provision/.DefaultActivity"]:
            with self.subTest(home=home):
                self.assertTrue(recovery.not_a_launcher(home))
        launchers = (f"{LAUNCHERX}/.home.HomeActivity", records.STOCK_HOME,
                     "com.mitv.tvhome/.MainActivity", f"{recovery.CUSTOM_LAUNCHER}/.MainActivity")
        for home in (*launchers, None):
            with self.subTest(home=home):
                self.assertFalse(recovery.not_a_launcher(home))


if __name__ == "__main__":
    unittest.main()

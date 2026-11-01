"""Undo turns the stock launcher back on only when the record says it was on before cleanup (#1714).

`restore_commands()` sent `pm enable <stock launcher>` whenever the record did not hold that launcher
as disabled, and save() recorded only the list and the package that owned Home. A stock launcher in
neither was missing from the record, so restore turned it on whatever its state had been:

1. examples/verizon-stream-tv leaves tvlauncher out of its list because the factory disabled it, and
   restore.sh turned it on anyway.
2. After launcher/build-and-install.sh made com.gathin.tvhome Home and disabled tvlauncher, undoing a
   second list (privacy.py prints `tools/restore.sh privacy.txt`) turned tvlauncher back on and, as
   the recorded Home was this project's launcher, gave Home to tvlauncher too.

Now save() records the state of every installed stock launcher that build-and-install.sh knows, listed
or not, and restore enables only what the record holds as enabled. When this project's launcher owned
Home and the stock launcher was not on, Home goes back to this project's launcher. A record taken
before this change holds no state for an unlisted stock launcher, so restore leaves it alone and says
how to turn it on by hand.

The cases borrow the fake TV and helpers of tests/test_recovery_record.py. The restore.ps1 case needs
`pwsh` on PATH and skips without it; CI's Ubuntu runner has it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tests"))
import recovery  # noqa: E402
import test_recovery_record as records  # noqa: E402

STOCK = "com.google.android.tvlauncher"
CUSTOM_HOME = f"{recovery.CUSTOM_LAUNCHER}/.MainActivity"
VERIZON_HOME = "com.verizon.tv.launcher/.HomeActivity"
PWSH = shutil.which("pwsh")


def home_commands(sent):
    return [c for c in sent if c.startswith("cmd package set-home-activity")]


class RestoreStockLauncherTest(unittest.TestCase):
    # Borrowed rather than inherited, so the tests of tests/test_recovery_record.py do not run a
    # second time under this class.
    setUp = records.RecoveryRecordTest.setUp
    write_list = records.RecoveryRecordTest.write_list
    tv = records.RecoveryRecordTest.tv
    run_script = records.RecoveryRecordTest.run_script
    record = records.RecoveryRecordTest.record
    sent = records.RecoveryRecordTest.sent
    clear_sent = records.RecoveryRecordTest.clear_sent

    def disable_then_restore(self, *restore_args, **tv):
        self.write_list(["com.example.ads"])
        self.tv(**tv)
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.clear_sent()
        result = self.run_script("restore.sh", str(self.list_path), *restore_args)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result

    def test_a_stock_launcher_the_factory_disabled_stays_off(self):
        # Case 1, examples/verizon-stream-tv: tvlauncher is off from the factory, Verizon's launcher is Home.
        self.disable_then_restore(installed=["com.example.ads", STOCK, "com.verizon.tv.launcher"],
                                  disabled=[STOCK], home=VERIZON_HOME)
        self.assertEqual(self.record()["packages"][STOCK], "disabled")
        sent = self.sent()
        self.assertNotIn(f"pm enable {STOCK}", sent)
        self.assertEqual(home_commands(sent), [f"cmd package set-home-activity --user 0 {VERIZON_HOME}"])

    def test_undoing_a_second_list_leaves_this_projects_launcher_on_home(self):
        # Case 2: build-and-install.sh made com.gathin.tvhome Home and disabled tvlauncher, then a second
        # list (privacy.txt) was applied and is now undone with no launcher argument.
        result = self.disable_then_restore(
            installed=["com.example.ads", STOCK, recovery.CUSTOM_LAUNCHER], disabled=[STOCK],
            home=CUSTOM_HOME)
        sent = self.sent()
        self.assertNotIn(f"pm enable {STOCK}", sent)
        self.assertIn(f"pm enable {recovery.CUSTOM_LAUNCHER}", sent)
        self.assertEqual(home_commands(sent), [f"cmd package set-home-activity --user 0 {CUSTOM_HOME}"])
        self.assertLess(sent.index(f"pm enable {recovery.CUSTOM_LAUNCHER}"), sent.index(home_commands(sent)[0]))
        self.assertIn(f"{STOCK} was disabled then", re.sub(r"\s+", " ", result.stderr))

    def test_a_stock_launcher_that_was_on_comes_back_and_takes_home(self):
        # What README promises: the stock launcher was on when the record was taken (the install
        # script disabled it afterwards), so undo turns it on and gives it Home, as before.
        self.disable_then_restore(installed=["com.example.ads", STOCK, recovery.CUSTOM_LAUNCHER],
                                  home=CUSTOM_HOME)
        self.assertEqual(self.record()["packages"][STOCK], "enabled")
        sent = self.sent()
        self.assertEqual(sent.count(f"pm enable {STOCK}"), 1)
        home = home_commands(sent)
        self.assertEqual(len(home), 1, sent)
        self.assertIn(f"resolve-activity --brief -a android.intent.action.MAIN -c "
                      f"android.intent.category.HOME {STOCK}", home[0])
        self.assertLess(sent.index(f"pm enable {STOCK}"), sent.index(home[0]))

    def test_only_installed_stock_launchers_are_recorded(self):
        self.disable_then_restore(installed=["com.example.ads", STOCK], home=f"{STOCK}/.MainActivity")
        packages = self.record()["packages"]
        self.assertEqual(packages, {"com.example.ads": "enabled", STOCK: "enabled"})
        self.assertNotIn("com.google.android.apps.tv.launcherx", packages)

    def test_no_home_and_a_stock_launcher_that_was_off_leaves_home_alone(self):
        result = self.disable_then_restore(installed=["com.example.ads", STOCK], disabled=[STOCK],
                                           home=None)
        sent = self.sent()
        self.assertNotIn(f"pm enable {STOCK}", sent)
        self.assertEqual(home_commands(sent), [])
        self.assertIn("leaving Home alone", result.stderr)

    def old_record_without_the_stock_launcher(self, home):
        """A record taken before save() noted the stock launchers: the list and Home only."""
        self.write_list(["com.example.ads"])
        self.tv(installed=["com.example.ads", STOCK, recovery.CUSTOM_LAUNCHER], disabled=[STOCK],
                home=home)
        self.assertEqual(self.run_script("disable.sh", str(self.list_path)).returncode, 0)
        path = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
        record = json.loads(path.read_text())
        record["packages"].pop(STOCK)
        path.write_text(json.dumps(record, indent=2, sort_keys=True) + "\n")
        self.clear_sent()

    def test_an_old_record_does_not_turn_on_a_stock_launcher_it_does_not_hold(self):
        self.old_record_without_the_stock_launcher(CUSTOM_HOME)
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertNotIn(f"pm enable {STOCK}", sent)
        self.assertEqual(home_commands(sent), [f"cmd package set-home-activity --user 0 {CUSTOM_HOME}"])
        said = re.sub(r"\s+", " ", result.stderr)
        self.assertIn(f"{STOCK} was not in the record then", said)
        self.assertIn(f"tools/tv.py 'pm enable {STOCK}'", said)

    def test_an_old_record_with_no_home_still_resolves_the_stock_launcher(self):
        # Nothing says the stock launcher was off, so Home still goes to whatever it resolves to; only
        # the `pm enable` is gone.
        self.old_record_without_the_stock_launcher(None)
        result = self.run_script("restore.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertNotIn(f"pm enable {STOCK}", sent)
        home = home_commands(sent)
        self.assertEqual(len(home), 1, sent)
        self.assertIn(f"android.intent.category.HOME {STOCK}", home[0])

    def test_restore_ps1_sends_the_same_batch(self):
        # restore.ps1 hands the same arguments to recovery.py, so case 2 gives the same batch.
        if not PWSH:
            raise unittest.SkipTest("restore.ps1 needs pwsh on PATH")
        tools = self.repo / "tools"
        shutil.copy2(ROOT / "tools" / "restore.ps1", tools / "restore.ps1")
        client = tools / "venv" / "Scripts" / "python.exe"
        client.parent.mkdir(parents=True, exist_ok=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        self.disable_then_restore(installed=["com.example.ads", STOCK, recovery.CUSTOM_LAUNCHER],
                                  disabled=[STOCK], home=CUSTOM_HOME)
        expected = self.sent()
        self.clear_sent()
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        result = subprocess.run([PWSH, "-NoProfile", "-File", str(tools / "restore.ps1"),
                                 str(self.list_path)], cwd=self.repo, env=env, capture_output=True,
                                text=True, timeout=120)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.sent(), expected)
        self.assertNotIn(f"pm enable {STOCK}", expected)


class StockLaunchersInStepTest(unittest.TestCase):
    def test_recovery_knows_the_stock_launchers_build_and_install_knows(self):
        sh = (ROOT / "launcher" / "build-and-install.sh").read_text(encoding="utf-8")
        ps1 = (ROOT / "launcher" / "build-and-install.ps1").read_text(encoding="utf-8")
        sh_known = re.search(r'^KNOWN_STOCK_LAUNCHERS="([^"]+)"', sh, re.M).group(1).split()
        ps1_known = re.findall(r"'([^']+)'", re.search(r"^\$knownStock = @\(([^)]*)\)", ps1, re.M).group(1))
        self.assertEqual(list(recovery.STOCK_LAUNCHERS), sh_known)
        self.assertEqual(list(recovery.STOCK_LAUNCHERS), ps1_known)


if __name__ == "__main__":
    unittest.main()

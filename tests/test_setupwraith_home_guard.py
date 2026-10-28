"""The scripts do not disable the stock launcher that owns Home while setupwraith stays enabled (#1573).

On Google TV, setupwraith's `.RecoveryActivity` answers HOME ahead of an installed launcher: on a
Chromecast with Google TV 4K (Google TV 14, UTTC.250917.004) launcherx answers at priority 2,
setupwraith at 1 and com.gathin.tvhome at 0. With launcherx disabled and setupwraith left on, Home goes
to the RecoveryActivity, the black screen system_server crash-loops on (docs/TROUBLESHOOTING.md).

disable.sh and disable.ps1 reached that state: the Wi-Fi guard kept setupwraith because it owned the
saved network, and the Home guard let launcherx go because com.gathin.tvhome was installed, although it
was not Home (build-and-install.sh rolls back when set-home-activity does not take, which that
Chromecast's list records). Now, when Home's owner is listed and setupwraith answers HOME and stays
enabled, the owner is skipped too, with FORCE_HOME=1 as the override, as for the rest of the Home
guard. A launcher that already owns Home is not touched by this (examples/prismplus-q65-ultra-r10g
disables launcherx and keeps setupwraith on once com.gathin.tvhome is Home).

check.py --fix had the same gap: when set-home-activity did not take it went on to disable the packages
that came back, launcherx included, although its own comment says a Home that cannot be fixed stops
the run first. It now stops there. It also refuses a launcher argument that the list disables or that
is no launcher: `--fix <list> com.google.android.tungsten.setupwraith` set Home to the
RecoveryActivity and called it fixed.

The disable cases borrow the fake TV of tests/test_home_guard.py and the check.py cases the one of
tests/test_drift_check.py. The PowerShell cases need `pwsh` on PATH and skip without it; CI's Ubuntu
runner has it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_drift_check as drift  # noqa: E402
import test_home_guard as guard  # noqa: E402

SETUP = "com.google.android.tungsten.setupwraith"
LAUNCHERX = "com.google.android.apps.tv.launcherx"
CUSTOM = "com.gathin.tvhome"
RECOVERY = f"{SETUP}/.RecoveryActivity"
SETTINGS_FALLBACK = "com.android.tv.settings/.system.FallbackHome"
LAUNCHERX_HOME = f"{LAUNCHERX}/.home.HomeActivity"
# The Chromecast's HOME claimants, in its order.
CLAIMS = [LAUNCHERX_HOME, RECOVERY, f"{CUSTOM}/.MainActivity", SETTINGS_FALLBACK]
WIFI_SETUP = f" cuid=10031 cname={SETUP}:10031 allowAutojoin=true"
WIFI_SYSTEM = " cuid=1000 cname=android.uid.system:1000 allowAutojoin=true"


class DisableKeepsTheOwnerWhileSetupwraithStays(unittest.TestCase):
    setUp = guard.HomeGuardTest.setUp
    write_list = guard.HomeGuardTest.write_list
    disable = guard.HomeGuardTest.disable
    entries = guard.HomeGuardTest.entries
    output = guard.HomeGuardTest.output

    def tv(self, **overrides):
        state = dict(installed=["com.example.ads", SETUP, LAUNCHERX, CUSTOM, "com.android.tv.settings"],
                     home=LAUNCHERX_HOME, claims=CLAIMS, wifi=WIFI_SETUP)
        state.update(overrides)
        guard.HomeGuardTest.tv(self, **state)

    def said(self, result):
        return " ".join(self.output(result).split())

    def test_the_issue_case_keeps_both(self):
        # The list names both; the Wi-Fi guard keeps setupwraith, so launcherx has to stay too.
        self.write_list([SETUP, LAUNCHERX, "com.example.ads"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {LAUNCHERX}", sent)
                self.assertNotIn(f"pm disable-user --user 0 {SETUP}", sent)
                self.assertIn("pm disable-user --user 0 com.example.ads", sent)
                said = self.said(result)
                self.assertIn(f"Skipping {LAUNCHERX}: it owns Home, and {SETUP} stays enabled", said)
                self.assertIn("RecoveryActivity", said)
                self.assertIn("FORCE_HOME=1", said)

    def test_an_unlisted_setupwraith_keeps_the_owner_too(self):
        self.write_list([LAUNCHERX, "com.example.ads"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {LAUNCHERX}", sent)
                self.assertIn(f"Skipping {LAUNCHERX}", self.said(result))

    def test_force_home_disables_the_owner(self):
        self.write_list([SETUP, LAUNCHERX, "com.example.ads"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry, {"FORCE_HOME": "1"})
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {LAUNCHERX}", sent)
                self.assertNotIn(f"pm disable-user --user 0 {SETUP}", sent,
                                 "FORCE_HOME is not FORCE_SETUPWRAITH: the Wi-Fi guard still holds")

    def test_setupwraith_disabled_in_the_same_run_lets_the_owner_go(self):
        # The system owns the saved network, so setupwraith goes with launcherx and cannot take Home.
        self.write_list([SETUP, LAUNCHERX, "com.example.ads"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(wifi=WIFI_SYSTEM)
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {SETUP}", sent)
                self.assertIn(f"pm disable-user --user 0 {LAUNCHERX}", sent)

    def test_a_launcher_that_owns_home_lets_the_list_disable_launcherx(self):
        # examples/prismplus-q65-ultra-r10g: com.gathin.tvhome is Home, setupwraith stays on, and the
        # list disables launcherx. Home's owner is not listed, so this guard has nothing to say.
        self.write_list([LAUNCHERX, "com.example.ads"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home=f"{CUSTOM}/.MainActivity")
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {LAUNCHERX}", sent)
                self.assertNotIn("RecoveryActivity", self.said(result))

    def test_without_setupwraith_answering_home_another_launcher_still_counts(self):
        # Android TV without setupwraith (or with it disabled already): the guard is what it was.
        self.write_list([LAUNCHERX, "com.example.ads"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(claims=[LAUNCHERX_HOME, f"{CUSTOM}/.MainActivity", SETTINGS_FALLBACK])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {LAUNCHERX}", sent)


class CheckFixKeepsHomeWhileSetupwraithStays(unittest.TestCase):
    setUp = drift.DriftCheckTest.setUp
    write_list = drift.DriftCheckTest.write_list
    run_check = drift.DriftCheckTest.run_check
    sent = drift.DriftCheckTest.sent

    def tv(self, **overrides):
        state = dict(installed=["com.example.music", SETUP, LAUNCHERX, CUSTOM],
                     disabled=["com.example.music"], home=LAUNCHERX_HOME,
                     home_activities=CLAIMS, wifi=WIFI_SETUP)
        state.update(overrides)
        drift.DriftCheckTest.tv(self, **state)

    def test_a_home_switch_that_does_not_take_stops_before_anything_is_disabled(self):
        # The issue's run: an update turned both back on, setupwraith owns the Wi-Fi network, and the
        # switch to com.gathin.tvhome does not take. Disabling launcherx then left Home to the
        # RecoveryActivity.
        self.write_list([SETUP, LAUNCHERX, "com.example.music"])
        self.tv(home_locked=True)
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("nothing was disabled", result.stdout)
        self.assertEqual(self.sent(), [f"cmd package set-home-activity --user 0 {CUSTOM}/.MainActivity"])

    def test_a_home_switch_that_takes_lets_launcherx_go(self):
        # With Home on com.gathin.tvhome, launcherx can go; setupwraith stays for the Wi-Fi network.
        self.write_list([SETUP, LAUNCHERX, "com.example.music"])
        self.tv()
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)  # setupwraith left on
        self.assertIn(f"Home: set to {CUSTOM}/.MainActivity", result.stdout)
        self.assertIn(f"leaving {SETUP} on", result.stdout)
        self.assertIn(f"pm disable-user --user 0 {LAUNCHERX}", self.sent())
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", self.sent())

    def test_a_launcher_argument_the_list_disables_is_refused(self):
        self.write_list([LAUNCHERX, "com.example.music"])
        self.tv()
        for args in (("--fix", str(self.list_path), LAUNCHERX), (str(self.list_path), LAUNCHERX)):
            with self.subTest(args=args):
                result = self.run_check(*args)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("this list disables it", result.stderr)
                self.assertEqual(self.sent(), [])

    def test_a_launcher_argument_that_is_no_launcher_is_refused(self):
        self.write_list(["com.example.music"])
        self.tv()
        for preferred in (SETUP, "com.android.tv.settings", "com.duokan.factorytest"):
            with self.subTest(preferred=preferred):
                result = self.run_check("--fix", str(self.list_path), preferred)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn("without being a launcher", result.stderr)
                self.assertEqual(self.sent(), [])


if __name__ == "__main__":
    unittest.main()

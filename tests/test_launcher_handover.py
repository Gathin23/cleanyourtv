"""Run launcher/build-and-install.sh against a simulated TV, without gradle or a real device.

The handover is the step that can leave a TV with no working Home, so the failures worth testing are
the ones no TV reproduces on demand: a launcher that does not start, and a connection that drops
between disabling the stock launcher and confirming the result (#239).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
NEW_HOME = "com.gathin.tvhome/.MainActivity"
FOREIGN_HOME = "com.unknown.oem.launcher/.Home"

# Stands in for tools/tv.py: a TV that answers shell commands and keeps the state the script changes.
FAKE_TV = r'''
import json
import os
import sys


class LinkLost(Exception):
    pass


STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


state = load()


def run(command):
    if "pm install" in command:
        state["home"] = state.get("home_after_install", state["home"])
        return "Success\n"
    # A TV that never held WRITE_SECURE_SETTINGS: the revoke step finds it denied. The step itself is run
    # against fake pm and dumpsys in test_launcher_min_sdk; here only the handover is under test.
    if "pm revoke" in command and "WRITE_SECURE_SETTINGS" in command:
        return "secure-settings-revoked\n"
    if "am start" in command:
        if state["start_fails"]:
            return ("Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\n"
                    "Error: Activity not started, unable to resolve Intent\n")
        return ("Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\n"
                "Status: ok\nActivity: com.gathin.tvhome/.MainActivity\n")
    if "pm disable-user" in command:
        if state["disable_fails"]:
            return "Error: java.lang.SecurityException\n"
        state["stock_disabled"] = True
        if state["drop_after_handover"]:
            state["link_lost"] = True
        return ""
    if "pm enable" in command:
        if state["enable_fails"]:
            return "Error: java.lang.SecurityException\n"
        state["stock_disabled"] = False
        return ""
    if "set-home-activity" in command:
        # The rollback path resolves the stock component first (its command contains resolve-activity);
        # the forward handover sets our launcher. set-home-activity prints Success when it took.
        if "resolve-activity" in command:
            state["home"] = STOCK
            return "Success\n"
        if state["set_home_fails"]:
            return "Error: java.lang.SecurityException\n"
        state["home"] = NEW
        return "Success\n"
    if "query-activities" in command:
        # The HOME claimants. A second launcher stays a claimant next to ours; a plain handover
        # leaves only whatever owns Home.
        if state["second_home"]:
            return NEW + "\n" + "com.spocky.projengone/.MainActivity\n"
        # An unknown third-party launcher owns Home: the unknown-launcher refusal names it (#3471).
        if state["foreign_home"]:
            return FOREIGN + "\n"
        return state["home"] + "\n"
    if "resolve-activity" in command:
        if state["link_lost"]:
            raise LinkLost()
        # A second registered HOME app ties with ours and no default is set, so resolve-activity
        # answers with Android's chooser even though set-home-activity succeeded (#4068).
        if state["second_home"] and state["home"] == NEW:
            return "android\n"
        # An unknown third-party launcher owns Home: initial detection cannot match a known stock
        # launcher and must refuse with the candidates listed (#3471).
        if state["foreign_home"]:
            return FOREIGN + "\n"
        # A disabled package is not a HOME candidate, so the answer falls back to the chooser.
        if state["stock_disabled"] and state["home"] == STOCK:
            return "android\n"
        return state["home"] + "\n"
    return ""


def main():
    args = [a for a in sys.argv[1:] if a != "-I"]
    if any(a.endswith("b64push.py") for a in args):
        print("pushed")
        return 0
    if "--batch" in args:
        commands = [line.strip() for line in sys.stdin
                    if line.strip() and not line.strip().startswith("#")]
    elif args:
        commands = [args[-1]]
    else:
        commands = []
    for command in commands:
        state["commands"].append(command)
        try:
            sys.stdout.write(run(command))
        except LinkLost:
            save()
            sys.stderr.write("adb: connection reset by peer\n")
            return 2
        sys.stdout.flush()
    save()
    return 0


sys.exit(main())
'''.replace("STOCK", repr(STOCK_HOME)).replace("NEW", repr(NEW_HOME)).replace("FOREIGN", repr(FOREIGN_HOME))


class HandoverTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.launcher = self.base / "launcher"
        self.launcher.mkdir()
        shutil.copy2(ROOT / "launcher" / "build-and-install.sh", self.launcher)
        # No JDK and no Android SDK here: the build step only has to produce a file to push.
        gradlew = self.launcher / "gradlew"
        gradlew.write_text('#!/bin/sh\n'
                           'mkdir -p app/build/outputs/apk/release\n'
                           'printf apk > app/build/outputs/apk/release/app-release.apk\n')
        gradlew.chmod(0o755)
        tools = self.base / "tools"
        (tools / "venv" / "bin").mkdir(parents=True)
        (tools / "b64push.py").write_text("")
        fake = self.base / "fake_tv.py"
        fake.write_text(FAKE_TV)
        client = tools / "venv" / "bin" / "python"
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "{fake}" "$@"\n')
        client.chmod(0o755)
        self.state_path = self.base / "tv-state.json"
        self.home = self.base / "home"
        self.home.mkdir()

    def handover(self, env_extra=None, **overrides):
        state = {
            "stock_home": STOCK_HOME,
            "home": STOCK_HOME,
            "stock_disabled": False,
            "start_fails": False,
            "disable_fails": False,
            "set_home_fails": False,
            "enable_fails": False,
            "drop_after_handover": False,
            "link_lost": False,
            "second_home": False,
            "foreign_home": False,
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        env = {k: v for k, v in os.environ.items()
               if k not in ("STOCK_LAUNCHER", "INPUT_KEY_SERVICE", "HOME_KEY_SERVICE", "RESTORE_KEY_SERVICES")}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path),
                   HOME=str(self.home), JAVA_HOME=str(self.base / "java"),
                   ANDROID_HOME=str(self.base / "android-sdk"))
        env.update(env_extra or {})
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("build-and-install.sh is a POSIX script; it needs bash")
        result = subprocess.run(
            ["bash", str(self.launcher / "build-and-install.sh")],
            cwd=self.launcher, env=env, capture_output=True, text=True, timeout=180,
        )
        with open(self.state_path) as f:
            state = json.load(f)
        return result, state

    def record(self):
        files = list(self.home.glob("cleanyourtv-handover-*.txt"))
        self.assertLessEqual(len(files), 1)
        return files[0].read_text() if files else None

    def test_success_hands_home_over(self):
        result, state = self.handover()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Installed.", result.stdout)
        commands = state["commands"]
        start = next(i for i, c in enumerate(commands) if "am start" in c)
        disable = next(i for i, c in enumerate(commands) if "pm disable-user" in c)
        self.assertLess(start, disable, "the launcher is started before the stock Home is disabled")
        self.assertTrue(state["stock_disabled"])
        self.assertEqual(state["home"], NEW_HOME)
        self.assertIsNone(self.record())

    def test_launcher_that_does_not_start_leaves_stock_home_enabled(self):
        result, state = self.handover(start_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("did not start", result.stderr)
        self.assertIn("was left enabled", result.stderr)
        self.assertNotIn("Installed.", result.stdout)
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))
        self.assertFalse(state["stock_disabled"])

    def test_lost_connection_reports_incomplete_recovery(self):
        result, state = self.handover(drop_after_handover=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("stopped answering", result.stderr)
        self.assertIn("recovery is incomplete", result.stderr)
        self.assertTrue(any("pm enable --user 0" in c for c in state["commands"]))
        text = self.record()
        self.assertIsNotNone(text, "an unreachable TV needs the recovery commands on disk")
        self.assertIn("pm enable --user 0 com.google.android.tvlauncher", text or "")

    def test_home_that_stays_on_another_launcher_is_rolled_back(self):
        result, state = self.handover(set_home_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("owns Home again", result.stderr)
        self.assertIn("setupwraith", result.stderr)
        self.assertFalse(state["stock_disabled"])
        self.assertIsNone(self.record())

    def test_failed_rollback_is_not_reported_as_recovered(self):
        result, state = self.handover(set_home_fails=True, enable_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("owns Home again", result.stderr)
        self.assertIn("recovery is incomplete", result.stderr)
        self.assertTrue(any("pm enable --user 0" in c for c in state["commands"]))
        self.assertIsNotNone(self.record())

    def test_stock_launcher_none_keeps_stock_home_and_enables_home_key_service(self):
        # #4070: tvlauncher holds HOME at priority 2 on a TCL Android TV 8, so set-home-activity leaves it Home.
        result, state = self.handover(set_home_fails=True,
                                      env_extra={"STOCK_LAUNCHER": "none", "HOME_KEY_SERVICE": "1"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"Home stays {STOCK_HOME}", result.stdout)
        self.assertIn("Installed.", result.stdout)
        commands = state["commands"]
        self.assertTrue(any("enabled_accessibility_services" in c and "HomeKeyService" in c for c in commands))
        self.assertTrue(any("WRITE_SECURE_SETTINGS" in c for c in commands))
        self.assertFalse(any("pm disable-user" in c or "pm enable" in c for c in commands))
        self.assertIsNone(self.record())

    def test_stock_launcher_none_without_home_key_service_says_what_is_missing(self):
        result, state = self.handover(set_home_fails=True, env_extra={"STOCK_LAUNCHER": "none"})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("HOME_KEY_SERVICE=1", result.stdout)
        self.assertFalse(any("HomeKeyService" in c for c in state["commands"]))

    def test_none_picked_because_home_was_ours_still_stops(self):
        # An upgrade: Home already belonged to this launcher, so the script picks none itself. A Home that
        # then lands elsewhere is not a stock_home_redirect setup.
        result, state = self.handover(home=NEW_HOME, home_after_install=STOCK_HOME, set_home_fails=True,
                                      env_extra={"HOME_KEY_SERVICE": "1"})
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Home already belongs to this launcher", result.stdout)
        self.assertIn("not to the new launcher", result.stderr)
        self.assertFalse(any("HomeKeyService" in c for c in state["commands"]))

    def test_ps1_keeps_the_stock_home_only_for_a_none_passed_in(self):
        ps1 = (ROOT / "launcher" / "build-and-install.ps1").read_text(encoding="utf-8")
        code = "\n".join(line for line in ps1.splitlines() if not line.lstrip().startswith("#"))
        self.assertLess(code.index("$keepStockHome = $StockLauncher -eq 'none'"), code.index("if (-not $StockLauncher)"))
        kept = code.index("-and $keepStockHome) {")
        self.assertLess(kept, code.index("$restored = Undo-Handover"))
        self.assertLess(kept, code.index("if ($HomeKeyService) { Enable-Service 'HomeKeyService' }"))

    def test_second_launcher_tie_is_not_rolled_back(self):
        # A second launcher (e.g. Projectivy) keeps claiming HOME, so after a successful handover
        # resolve-activity answers with Android's chooser. That must not be read as failure (#4068).
        result, state = self.handover(second_home=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Installed.", result.stdout)
        self.assertTrue(state["stock_disabled"], "a correct handover must not be rolled back")
        self.assertEqual(state["home"], NEW_HOME)
        self.assertFalse(any("pm enable --user 0" in c for c in state["commands"]),
                         "the stock launcher must not be re-enabled when our launcher is a HOME claimant")
        self.assertIsNone(self.record())

    def test_chooser_after_a_failed_set_home_is_rolled_back(self):
        # Our launcher is a HOME candidate whenever it is installed, so a chooser answer appears both
        # after a successful handover AND after a set-home-activity that did not take. With a second
        # launcher present and set-home failing, the chooser must still roll back, not be mistaken for
        # a kept handover (the maintainer's catch on #4068).
        result, state = self.handover(second_home=True, set_home_fails=True)
        self.assertNotEqual(result.returncode, 0, "a failed set-home must not be accepted as a tie")
        self.assertIn("owns Home again", result.stderr)
        self.assertNotIn("Installed.", result.stdout)
        self.assertFalse(state["stock_disabled"], "the stock launcher must be re-enabled on rollback")
        self.assertTrue(any("pm enable --user 0" in c for c in state["commands"]))

    def test_unknown_launcher_refusal_lists_candidates(self):
        # An unknown third-party launcher owns Home and no STOCK_LAUNCHER was given: the script refuses
        # before touching anything, and the message now names the TV's actual HOME candidates (#3471).
        result, state = self.handover(foreign_home=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not a stock launcher this script knows", result.stderr)
        self.assertIn("HOME candidates on this TV:", result.stderr)
        self.assertIn("com.unknown.oem.launcher", result.stderr)
        # Nothing was disabled or started: the refusal happens during detection.
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))
        self.assertFalse(state["stock_disabled"])


if __name__ == "__main__":
    unittest.main()

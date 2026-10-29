"""tools/disable.sh and tools/disable.ps1 must not disable the app that owns Home (#239).

A list from `examples/` (the Xiaomi one, for instance) includes the stock launcher, and running it
before the replacement launcher is Home leaves the TV with nothing to start. The property worth
testing is the one no TV reproduces on demand, so tools/tv.py is replaced by a fake that answers the
queries from a JSON file: the real disable script runs, only the ADB transport is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it; CI has no PowerShell.
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
STOCK = "com.google.android.tvlauncher"
CUSTOM = "com.gathin.tvhome"
PWSH = shutil.which("pwsh")

# Stands in for tools/tv.py: answers the queries the disable script makes and records what a batch
# would send, without a TV.
FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


state = load()


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


class Fail(Exception):
    pass


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    # Only the HOME query: the input/Settings handler guard asks the same command for other intents.
    if command.startswith("cmd package query-activities") and "category.HOME" in command:
        if state["claims_fail"]:
            raise Fail()
        return "".join(f"  {c if '/' in c else c + '/.MainActivity'}\n" for c in state["claims"])
    if command.startswith("cmd package resolve-activity"):
        if state["home_fails"]:
            raise Fail()
        return (state["home"] or "") + "\n"
    if command.startswith("dumpsys activity"):
        return state.get("home_process", "")
    if command.startswith("settings get global "):
        return "null\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        # recovery.py's own queries: a failing one answers nothing, as adb_shell would on a bad reply.
        try:
            return answer(command)
        except Fail:
            return ""

    def close(self):
        pass


def connect():
    # recovery.py imports this module and calls connect() directly.
    return Device()


def main():
    args = sys.argv[1:]
    if "--batch" in args:
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                state["commands"].append(line)
        save()
        return 0
    try:
        print(answer(args[-1]), end="")
    except Fail:
        # Exercise a failed query that emitted partial stdout before the transport failed.
        print(state.get("failed_output", ""), end="")
        return 1   # tools/tv.py exits non-zero when the TV does not answer
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class HomeGuardTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("disable.sh", "disable.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        # The scripts call the tools venv's Python. Run the real interpreter, which imports the fake
        # tv.py for the transport and the real recovery.py for the logic. disable.ps1 asks for the
        # Windows path, which PowerShell resolves to tools/venv/Scripts/python.exe here too.
        for client in (self.tools / "venv" / "bin" / "python",
                       self.tools / "venv" / "Scripts" / "python.exe"):
            client.parent.mkdir(parents=True, exist_ok=True)
            client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
            client.chmod(0o755)

        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.write_list(["com.example.before", STOCK, "com.example.after"])

    def write_list(self, names):
        self.list_path.write_text("# My TV\n" + "".join(f"{name}\n" for name in names))

    def tv(self, **overrides):
        state = {
            "installed": ["com.example.before", "com.example.after", STOCK],
            "disabled": [],
            "home": f"{STOCK}/.MainActivity",
            "claims": [STOCK],
            "home_fails": False,
            "claims_fail": False,
            "home_process": "",
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def disable(self, entry, extra_env=None, list_path=None):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path), **(extra_env or {}))
        if entry == "sh":
            argv = ["bash", str(self.tools / "disable.sh"), str(list_path or self.list_path)]
        else:
            argv = [PWSH, "-NoProfile", "-File", str(self.tools / "disable.ps1"),
                    "-TvHost", "192.168.1.50", str(list_path or self.list_path)]
        result = subprocess.run(argv, cwd=self.repo, env=env, capture_output=True, text=True,
                                timeout=180)
        with open(self.state_path) as f:
            return result, json.load(f)["commands"]

    def entries(self):
        return ("sh", "ps1") if PWSH else ("sh",)

    def output(self, result):
        return result.stdout + result.stderr

    def test_the_owner_of_home_is_skipped_when_it_is_the_only_home(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "the only Home the TV has must not be disabled")
                self.assertEqual(sent[:2], ["pm disable-user --user 0 com.example.before",
                                            "pm disable-user --user 0 com.example.after"])
                self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_a_second_home_lets_the_list_disable_the_owner(self):
        # The usual end state: the new launcher is Home, and the stock one is still enabled.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(installed=["com.example.before", "com.example.after", STOCK, CUSTOM],
                        home=f"{CUSTOM}/.MainActivity", claims=[CUSTOM, STOCK])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_a_list_that_disables_every_home_keeps_the_owner(self):
        self.write_list(["com.example.before", STOCK, CUSTOM])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(installed=["com.example.before", STOCK, CUSTOM], claims=[STOCK, CUSTOM])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "the app that owns Home stays enabled")
                self.assertIn(f"pm disable-user --user 0 {CUSTOM}", sent,
                              "the other launcher listed is still disabled")

    def test_the_setup_wizard_does_not_count_as_another_home(self):
        # On Google TV, setupwraith and the settings app answer HOME as well. If they counted, the stock
        # launcher would go and setupwraith would take Home: the crash loop in docs/TROUBLESHOOTING.md.
        wizard = "com.google.android.tungsten.setupwraith"
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(installed=["com.example.before", "com.example.after", STOCK, wizard,
                                   "com.android.tv.settings"],
                        claims=[STOCK, wizard, "com.android.tv.settings"])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "the setup wizard is not a launcher to fall back on")
                self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_a_vendor_fallback_home_does_not_count_as_another_home(self):
        # Android TV sets register a FallbackHome activity so the firmware has something to show when
        # no launcher is enabled. It answers category.HOME, but it is not a home the viewer can use.
        # Verified on a TCL BeyondTV2 (Android TV 9): com.tcl.keycustomfunctionservice/.FallbackHome
        # and com.android.tv.settings/.system.FallbackHome both answer HOME.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(claims=[STOCK, "com.tcl.keycustomfunctionservice/.FallbackHome",
                                "com.android.tv.settings/.system.FallbackHome"])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "a vendor fallback is not a launcher to fall back on")
                self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_a_factory_test_app_does_not_count_as_another_home(self):
        # A Xiaomi Mi TV 4A answers the HOME query with com.duokan.factorytest/.FactoryLauncher next to
        # the launcher and a FallbackHome. Counting the factory test as another Home let a list disable
        # the owner and hand Home to the factory test screen.
        for entry in self.entries():
            for factory in ("com.duokan.factorytest/.FactoryLauncher", "com.example.factory_test/.Main",
                            "com.mediatek.factorymode/.FactoryHome"):
                with self.subTest(entry=entry, factory=factory):
                    self.tv(claims=[factory, STOCK, "com.android.tv.settings/.system.FallbackHome"])
                    result, sent = self.disable(entry)
                    self.assertEqual(result.returncode, 0, self.output(result))
                    self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                     "a factory test app is not a launcher to fall back on")
                    self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_a_tv_that_cannot_list_its_homes_is_left_with_one(self):
        # tv.py fails or the TV answers nothing: the owner is the only Home this run knows about.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(claims_fail=True)
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_a_firmware_without_cmd_package_still_finds_home(self):
        # `cmd` arrived with Android 7. On a KitKat set the query answers the shell's own error, and the
        # guard used to be skipped in silence while the list went on to disable the only Home (#795).
        # The framework has printed the package holding Home since 4.x: `dumpsys activity` says
        # "mHomeProcess: ProcessRecord{52f4a5c8 2f4b:com.google.android.tvlauncher/u0a56}".
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="/system/bin/sh: cmd: not found",
                        home_process="  mHomeProcess: ProcessRecord{52f4a5c8 2f4b:"
                                     + STOCK + "/u0a56}")
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "Home was readable from dumpsys, so the guard must still run")
                self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_a_tv_that_cannot_name_its_home_is_told_so(self):
        # Neither query names Home: the guard cannot check anything, and the run says that instead of
        # looking like it checked. Silently skipping the guard is what the brick report did not see.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="/system/bin/sh: cmd: not found", home_process="")
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn("Could not tell which app owns Home", self.output(result))

    def test_force_disables_the_owner(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, sent = self.disable(entry, {"FORCE_HOME": "1"})
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_the_guard_does_not_touch_a_list_without_the_owner(self):
        # Home belongs to the new launcher: the stock one in the list is fair game.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(installed=["com.example.before", "com.example.after", STOCK, CUSTOM],
                        home=f"{CUSTOM}/.MainActivity", claims=[CUSTOM, STOCK])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertNotIn("Skipping", self.output(result))


    # Review M1: Android's chooser ("android") or a failed/empty resolve used to skip the guard
    # entirely, so a list holding the only launcher went through. Without a known owner the guard now
    # falls back to the HOME claimants, and without those to the known stock launchers.
    def test_chooser_answer_still_keeps_the_only_launcher(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="android", claims=[STOCK, "com.android.tv.settings"])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "the chooser answer must not switch the Home guard off")
                self.assertIn(f"Skipping {STOCK}", self.output(result))
                self.assertIn("pm disable-user --user 0 com.example.before", sent)

    def test_chooser_answer_is_reported_as_the_chooser(self):
        # The TV did answer: Home has no default. Saying neither query named one is wrong here.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="android", claims=[STOCK, CUSTOM])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                out = " ".join(self.output(result).split())
                self.assertIn("Home has no default on this TV", out)
                self.assertNotIn("named one", out)

    def test_failed_resolve_still_keeps_the_only_launcher(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home_fails=True)
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_failed_resolve_discards_partial_output(self):
        self.tv(home_fails=True, failed_output="com.example.partial/.Home\n")
        result, sent = self.disable("sh")
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
        self.assertIn("Skipping", result.stderr)

    def test_unrecognised_resolve_answer_uses_claimants(self):
        for entry in self.entries():
            self.tv(home="No activity found")
            result, sent = self.disable(entry)
            self.assertEqual(result.returncode, 0, self.output(result))
            self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_empty_resolve_still_keeps_the_only_launcher(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="")
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_chooser_with_an_unlisted_launcher_lets_the_list_through(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(installed=["com.example.before", "com.example.after", STOCK, CUSTOM],
                        home="android", claims=[STOCK, CUSTOM])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertNotIn("Skipping", self.output(result))

    def test_chooser_with_only_a_vendor_fallback_keeps_the_launcher(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="android", claims=[STOCK, "com.tcl.keycustomfunctionservice/.FallbackHome"])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_no_owner_and_no_claimants_keeps_known_launchers(self):
        # Neither query answers: skip every listed known stock launcher, fail-closed, and say why.
        self.write_list(["com.example.before", STOCK, "com.mitv.tvhome", "com.example.after"])
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home_fails=True, claims_fail=True)
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertNotIn("pm disable-user --user 0 com.mitv.tvhome", sent)
                self.assertIn("pm disable-user --user 0 com.example.before", sent)
                self.assertIn("could not", self.output(result).lower())

    def test_chooser_does_not_count_a_factory_test_app_as_another_home(self):
        # The factory-test exclusion lives in the shared claimant filter, so it holds without an owner
        # too: Android's chooser plus a Mi TV 4A FactoryLauncher must not let the only launcher go.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="android", claims=["com.duokan.factorytest/.FactoryLauncher", STOCK])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent,
                                 "a factory test app is not a launcher to fall back on")
                self.assertIn(f"Skipping {STOCK}", self.output(result))

    def test_no_cmd_and_no_home_process_falls_back_to_claimants(self):
        # #795 meets the fail-closed path: resolve-activity is missing and dumpsys has no Home process
        # (mHomeProcess: null), so no owner is known. The guard still keeps the only launcher, and the
        # run still says it could not name Home.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="/system/bin/sh: cmd: not found", home_process="  mHomeProcess: null\n")
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertIn(f"Skipping {STOCK}", self.output(result))
                self.assertIn("Could not tell which app owns Home", self.output(result))
                self.assertIn("pm disable-user --user 0 com.example.before", sent)

    def test_no_cmd_and_no_home_process_lets_an_unlisted_launcher_through(self):
        # Same blind spot, but another launcher stays enabled: the stock one in the list may go.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(installed=["com.example.before", "com.example.after", STOCK, CUSTOM],
                        home="/system/bin/sh: cmd: not found", home_process="",
                        claims=[STOCK, CUSTOM])
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertNotIn("Skipping", self.output(result))

    def test_dumpsys_owner_with_unlistable_claimants_keeps_the_owner(self):
        # dumpsys names the owner but the claimant query fails: the owner is the only Home known.
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="/system/bin/sh: cmd: not found", claims_fail=True,
                        home_process="  mHomeProcess: ProcessRecord{52f4a5c8 2f4b:" + STOCK + "/u0a56}")
                result, sent = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertNotIn(f"pm disable-user --user 0 {STOCK}", sent)
                self.assertIn(f"Skipping {STOCK}", self.output(result))
                self.assertNotIn("Could not tell which app owns Home", self.output(result))

    def test_force_overrides_the_chooser_fallback(self):
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv(home="android", claims_fail=True)
                result, sent = self.disable(entry, {"FORCE_HOME": "1"})
                self.assertEqual(result.returncode, 0, self.output(result))
                self.assertIn(f"pm disable-user --user 0 {STOCK}", sent)

    def test_the_skipped_launcher_stays_in_the_recovery_record(self):
        self.tv()
        result, sent = self.disable("sh")
        self.assertEqual(result.returncode, 0, self.output(result))
        record = next((self.home / ".cleanyourtv").glob("recovery-*.json"))
        self.assertEqual(json.loads(record.read_text())["packages"][STOCK], "enabled")


if __name__ == "__main__":
    unittest.main()

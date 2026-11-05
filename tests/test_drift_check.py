"""tools/check.py against a simulated TV (#359).

The property worth testing is the one no TV shows on demand: what the drift report says after a
firmware update, and that --fix puts back only what drifted, without writing a recovery record of
its own. tools/tv.py is replaced by a fake that answers the queries from a JSON file, so the
real check.py logic runs and only the ADB transport is simulated.

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
STOCK = "com.google.android.tvlauncher"
CUSTOM = "com.gathin.tvhome"

# Stands in for tools/tv.py: answers the queries check.py makes, and applies what --fix sends.
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


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if command.startswith("dumpsys wifi"):
        return state.get("wifi", "")
    if command.startswith("dumpsys package "):
        return dumpsys_package(command.split()[-1])
    if "resolve-activity" in command:
        return (state["home"] or "") + "\n"
    if "query-activities" in command and "category.HOME" in command:
        return "".join(f"  {component}\n" for component in state["home_activities"])
    if "query-activities" in command and "cursor.item/channel" in command:
        return "".join(f"  {name}/.MainActivity\n" for name in state.get("input_apps", []))
    return ""


def dumpsys_package(name):
    """What `dumpsys package <name>` prints, scoped to the permissions the package asks for."""
    requested = ["android.permission.INTERNET"]
    if name in state.get("input_drivers", []):
        requested.append("android.permission.TV_INPUT_HARDWARE")
    lines = [f"Packages:", f"  Package [{name}]", "    requested permissions:"]
    lines += [f"      {permission}" for permission in requested]
    lines.append("    install permissions:")
    lines.append("      android.permission.INTERNET: granted=true")
    return "\n".join(lines) + "\n"


def apply(command):
    state["commands"].append(command)
    if command.startswith("pm disable-user --user 0 "):
        name = command.split()[-1]
        # A firmware that pins a package ignores the command, like some OEM builds do.
        if name not in state["disabled"] and name not in state.get("pinned", []):
            state["disabled"].append(name)
    elif command.startswith("cmd package set-home-activity --user 0 "):
        # Some firmware answers the switch and keeps Home where it was (the Chromecast with Google TV
        # 4K list records it); home_locked plays that firmware.
        if not state.get("home_locked"):
            state["home"] = command.split()[-1]
    save()


class Device:
    def shell(self, command, read_timeout_s=None):
        # The TV runs what it is sent and answers the queries; the fake does the same.
        if command.startswith(("pm disable-user", "pm enable", "cmd package set-home-activity")):
            apply(command)
            return ""
        return answer(command)

    def close(self):
        pass


def connect():
    if state.get("unreachable"):
        raise ConnectionError("could not connect to 192.168.1.50:5555")
    return Device()


def main():
    args = sys.argv[1:]
    if "--batch" in args:
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                apply(line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class DriftCheckTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        for name in ("check.py", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, tools / name)
        (tools / "tv.py").write_text(FAKE_TV)

        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.write_list(["com.example.music", "com.example.store", STOCK])

    def write_list(self, names):
        self.list_path.write_text("# My TV\n" + "".join(f"{name}\n" for name in names))

    def tv(self, **overrides):
        state = {
            "installed": ["com.example.music", "com.example.store", STOCK, CUSTOM],
            "disabled": ["com.example.music", "com.example.store", STOCK],
            "home": f"{CUSTOM}/.MainActivity",
            "home_activities": [f"{CUSTOM}/.MainActivity", f"{STOCK}/.MainActivity"],
            "pinned": [],
            "commands": [],
            "unreachable": False,
            # Empty means the TV shows no saved network at all, which is what the safety checks treat
            # as "could not tell" - the same answer a set with Wi-Fi off gives.
            "wifi": "",
            "input_drivers": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def run_check(self, *args, env=None):
        env = dict(dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                        FAKE_TV_STATE=str(self.state_path)), **(env or {}))
        return subprocess.run(
            [sys.executable, str(self.repo / "tools" / "check.py"), *args],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=120,
        )

    def sent(self):
        with open(self.state_path) as f:
            return json.load(f)["commands"]

    def test_clean_tv_reports_nothing(self):
        self.tv()
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("nothing drifted", result.stdout)
        self.assertNotIn("back on", result.stdout)
        self.assertEqual(self.sent(), [])

    def test_package_turned_back_on_is_drift(self):
        self.tv(disabled=[STOCK])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn("back on (2): com.example.music, com.example.store", result.stdout)
        self.assertIn("drift", result.stdout)
        # A report-only run changes nothing on the TV.
        self.assertEqual(self.sent(), [])

    def test_missing_package_is_not_drift(self):
        # A firmware update can drop an app for good; there is nothing to disable afterwards.
        self.tv(installed=["com.example.store", STOCK, CUSTOM], disabled=["com.example.store", STOCK])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("not installed any more (1): com.example.music", result.stdout)

    def test_home_back_on_the_stock_launcher_is_drift(self):
        self.tv(home=f"{STOCK}/.MainActivity")
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"Home is {STOCK}/.MainActivity, which this list disables", result.stdout)

    def test_fix_disables_what_came_back(self):
        self.tv(disabled=[STOCK])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("disabled 2 package(s)", result.stdout)
        self.assertEqual(self.sent(), ["pm disable-user --user 0 com.example.music",
                                       "pm disable-user --user 0 com.example.store"])
        # The next run is clean: --fix left the TV in the state the list describes.
        again = self.run_check(str(self.list_path))
        self.assertEqual(again.returncode, 0, again.stdout + again.stderr)

    def test_fix_reports_a_package_the_firmware_pins(self):
        self.tv(disabled=[STOCK], pinned=["com.example.store"])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn("still enabled after --fix: com.example.store", result.stdout)

    def test_fix_puts_the_new_launcher_back_on_home(self):
        self.tv(home=f"{STOCK}/.MainActivity")
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"Home: set to {CUSTOM}/.MainActivity", result.stdout)
        self.assertEqual(self.sent(), [f"cmd package set-home-activity --user 0 {CUSTOM}/.MainActivity"])

    def test_fix_without_a_launcher_to_switch_to_says_so(self):
        # Nothing but launchers the list disables answers HOME: switching Home would leave the TV
        # without one, so --fix refuses instead of disabling the stock launcher first.
        self.tv(installed=["com.example.music", "com.example.store", STOCK],
                disabled=["com.example.music", "com.example.store"],
                home=f"{STOCK}/.MainActivity",
                home_activities=[f"{STOCK}/.MainActivity"])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn("launcher/build-and-install.sh", result.stdout)
        self.assertNotIn("cmd package set-home-activity", " ".join(self.sent()))

    def test_fix_takes_the_launcher_it_is_given(self):
        self.tv(home=f"{STOCK}/.MainActivity", installed=COMMON_LAUNCHERS,
                disabled=["com.example.music", "com.example.store"],
                home_activities=[f"{STOCK}/.MainActivity", f"{CUSTOM}/.MainActivity",
                                 f"{FORK}/.MainActivity"])
        result = self.run_check("--fix", str(self.list_path), FORK)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"Home: set to {FORK}/.MainActivity", result.stdout)

    def test_fix_leaves_the_recovery_record_alone(self):
        # Undo still means undo: --fix re-disables packages the record already describes, so it must
        # not write a record of its own that would make restore.sh enable everything afterwards.
        self.tv(disabled=[STOCK])
        self.assertEqual(self.run_check("--fix", str(self.list_path)).returncode, 0)
        records = list((self.home / ".cleanyourtv").glob("*.json")) \
            if (self.home / ".cleanyourtv").exists() else []
        self.assertEqual(records, [])

    def test_home_on_a_stock_launcher_the_list_leaves_out_is_drift(self):
        # The case that prompted #359: build-and-install.sh disables the stock launcher, so no list
        # names it, and an update that turns it back on and takes Home matches no line of the list.
        self.write_list(["com.example.music", "com.example.store"])
        self.tv(disabled=["com.example.music", "com.example.store"], home=f"{STOCK}/.MainActivity")
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"Home is {STOCK}/.MainActivity, not {CUSTOM}", result.stdout)

    def test_fix_takes_home_back_from_a_stock_launcher_the_list_leaves_out(self):
        self.write_list(["com.example.music", "com.example.store"])
        self.tv(disabled=["com.example.music", "com.example.store"], home=f"{STOCK}/.MainActivity")
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"Home: set to {CUSTOM}/.MainActivity", result.stdout)
        again = self.run_check(str(self.list_path))
        self.assertEqual(again.returncode, 0, again.stdout + again.stderr)

    def test_stock_home_without_our_launcher_is_not_drift(self):
        # Debloat only, no launcher installed: the stock launcher on Home is the intended state, and
        # --fix has nothing to switch Home to.
        self.write_list(["com.example.music", "com.example.store"])
        self.tv(installed=["com.example.music", "com.example.store", STOCK],
                disabled=["com.example.music", "com.example.store"],
                home=f"{STOCK}/.MainActivity", home_activities=[f"{STOCK}/.MainActivity"])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_home_on_another_launcher_than_the_one_given_is_drift(self):
        self.tv(installed=COMMON_LAUNCHERS, disabled=["com.example.music", "com.example.store", STOCK],
                home=f"{CUSTOM}/.MainActivity",
                home_activities=[f"{CUSTOM}/.MainActivity", f"{FORK}/.MainActivity"])
        result = self.run_check(str(self.list_path), FORK)
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"not {FORK}", result.stdout)

    def test_fix_leaves_setupwraith_on_while_it_owns_the_wifi(self):
        # The update turned setupwraith back on, and disabling it again deletes the saved network and
        # takes the TV off Wi-Fi - the one mistake here a cron --fix could make on its own.
        self.write_list([SETUP, "com.example.music"])
        self.tv(installed=BASE + [SETUP], disabled=["com.example.music", "com.example.store", STOCK],
                wifi=f" cuid=10031 cname={SETUP}:10031 allowAutojoin=true")
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"leaving {SETUP} on", result.stdout)
        self.assertIn("not fully back to the list", result.stdout)
        self.assertNotIn(f"pm disable-user --user 0 {SETUP}", self.sent())

    def test_fix_disables_setupwraith_when_the_system_owns_the_wifi(self):
        self.write_list([SETUP, "com.example.music"])
        self.tv(installed=BASE + [SETUP], disabled=["com.example.music", "com.example.store", STOCK],
                wifi=" cuid=1000 cname=android.uid.system:1000 allowAutojoin=true")
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"pm disable-user --user 0 {SETUP}", self.sent())

    def test_fix_leaves_the_app_that_opens_the_inputs_on(self):
        # The update turned the Live TV app back on; it is what the launcher's HDMI tiles open.
        self.write_list(["com.example.livetv", "com.example.music"])
        self.tv(installed=BASE + ["com.example.livetv"], input_apps=["com.example.livetv"],
                disabled=["com.example.store", STOCK])
        result = self.run_check("--fix", str(self.list_path))
        self.assertIn("leaving com.example.livetv on", result.stdout)
        self.assertIn("pm disable-user --user 0 com.example.music", self.sent())
        self.assertNotIn("pm disable-user --user 0 com.example.livetv", self.sent())

    def test_fix_leaves_setupwraith_on_when_the_wifi_cannot_be_read(self):
        self.write_list([SETUP])
        self.tv(installed=BASE + [SETUP], disabled=["com.example.store", STOCK], wifi="")
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"leaving {SETUP} on", result.stdout)
        self.assertIn("FORCE_SETUPWRAITH=1", result.stdout)

    def test_fix_takes_the_forced_setupwraith(self):
        self.write_list([SETUP])
        self.tv(installed=BASE + [SETUP], disabled=["com.example.store", STOCK],
                wifi=f" cuid=10031 cname={SETUP}:10031 allowAutojoin=true")
        result = self.run_check("--fix", str(self.list_path), env={"FORCE_SETUPWRAITH": "1"})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"pm disable-user --user 0 {SETUP}", self.sent())

    def test_fix_leaves_an_hdmi_input_driver_on(self):
        # #321: disabling the package under the HDMI/AV inputs leaves every port black until a full
        # restore and a cold boot, so --fix has to keep the same guard disable.sh has.
        self.write_list([MEDIATEK_INPUT, "com.example.music"])
        self.tv(installed=BASE + [MEDIATEK_INPUT],
                disabled=["com.example.store", STOCK],
                input_drivers=[MEDIATEK_INPUT])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn(f"leaving {MEDIATEK_INPUT} on", result.stdout)
        self.assertIn("TV_INPUT_HARDWARE", result.stdout)
        self.assertNotIn(f"pm disable-user --user 0 {MEDIATEK_INPUT}", self.sent())
        # The rest of the drift is still fixed.
        self.assertIn("pm disable-user --user 0 com.example.music", self.sent())

    def test_fix_takes_an_input_driver_with_TVINPUT_ALLOW(self):
        self.write_list([MEDIATEK_INPUT])
        self.tv(installed=BASE + [MEDIATEK_INPUT],
                disabled=["com.example.music", "com.example.store", STOCK],
                input_drivers=[MEDIATEK_INPUT])
        result = self.run_check("--fix", str(self.list_path), env={"TVINPUT_ALLOW": "1"})
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"pm disable-user --user 0 {MEDIATEK_INPUT}", self.sent())

    def test_fix_prefers_the_launcher_over_a_vendor_fallback(self):
        # A FallbackHome is the placeholder a TCL set shows when no launcher is enabled; taking it
        # as Home would leave the TV in the placeholder (#435).
        self.write_list(["com.example.music", STOCK])
        self.tv(disabled=["com.example.music", "com.example.store"],
                home=f"{STOCK}/.MainActivity",
                home_activities=[f"{STOCK}/.MainActivity", FALLBACK, f"{CUSTOM}/.MainActivity"])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"Home: set to {CUSTOM}/.MainActivity", result.stdout)
        self.assertNotIn(FALLBACK, " ".join(self.sent()))

    def test_fix_does_not_take_home_to_a_factory_test_app(self):
        # com.duokan.factorytest/.FactoryLauncher answers HOME on a Xiaomi Mi TV 4A; Home on it is the
        # factory test screen, so with no launcher left --fix changes nothing rather than move Home there.
        self.write_list(["com.example.music", STOCK])
        self.tv(installed=["com.example.music", "com.example.store", STOCK, "com.duokan.factorytest"],
                disabled=["com.example.music", "com.example.store"],
                home=f"{STOCK}/.MainActivity",
                home_activities=[FACTORY, f"{STOCK}/.MainActivity"])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn("launcher/build-and-install.sh", result.stdout)
        self.assertNotIn("set-home-activity", " ".join(self.sent()))

    def test_fix_refuses_a_home_only_a_fallback_can_take(self):
        self.write_list(["com.example.music", STOCK])
        self.tv(installed=["com.example.music", "com.example.store", STOCK, "com.tcl.tv"],
                disabled=["com.example.music", "com.example.store"],
                home=f"{STOCK}/.MainActivity",
                home_activities=[f"{STOCK}/.MainActivity", FALLBACK])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 1)
        self.assertIn("launcher/build-and-install.sh", result.stdout)
        self.assertNotIn("set-home-activity", " ".join(self.sent()))

    def test_unreachable_tv_is_not_drift(self):
        self.tv(unreachable=True)
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 2)
        self.assertIn("could not reach the TV", result.stderr)

    def test_a_hand_edited_list_stops_before_the_tv(self):
        self.write_list(["com.example.music", "com.example.store; reboot"])
        self.tv(disabled=[STOCK])
        result = self.run_check("--fix", str(self.list_path))
        # 2, the list could not be used: 1 is what a weekly job reads as drift.
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("not a package name", result.stderr)
        self.assertEqual(self.sent(), [])


FORK = "com.example.forklauncher"
COMMON_LAUNCHERS = ["com.example.music", "com.example.store", STOCK, CUSTOM, FORK]
# The first-run wizard's app, which owns the Wi-Fi network a TV saved during setup.
SETUP = "com.google.android.tungsten.setupwraith"
# On MediaTek sets this is the package behind every HDMI, AV and tuner input.
MEDIATEK_INPUT = "com.mediatek.tvinput"
# The packages every fake TV here starts with, before a test adds the one it is about.
BASE = ["com.example.music", "com.example.store", STOCK, CUSTOM]
# What a TCL set shows when no launcher is enabled; not a home the viewer can use.
FALLBACK = "com.tcl.keycustomfunctionservice/.FallbackHome"
# Xiaomi's factory test, which answers HOME on a Mi TV 4A.
FACTORY = "com.duokan.factorytest/.FactoryLauncher"


if __name__ == "__main__":
    unittest.main()

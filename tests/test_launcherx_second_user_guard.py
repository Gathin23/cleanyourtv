"""The install stops before disabling launcherx on a set where that crashes the TV's key service (#2973).

Skyworth/Coocaa Google TV boards ship a persistent `com.smartdevice.global.service`. When more than one
Android user exists, its app-button handler queries launcherx's LiveTvContentProvider, and with launcherx
disabled that throws on every press until RescueParty resets settings. So with launcherx as the stock
launcher, that service installed and `pm list users` showing a second user, launcher/build-and-install.sh
stops before anything is pushed or disabled and points at stock_home_redirect (STOCK_LAUNCHER=none).

The shell script runs against a fake TV. The PowerShell twin is checked by reading it, as
test_build_install_ps1_order does.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
LAUNCHERX = "com.google.android.apps.tv.launcherx"
KEY_SERVICE = "com.smartdevice.global.service"

FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]
with open(STATE_PATH) as f:
    state = json.load(f)


def run(command):
    if "pm list users" in command:
        return "Users:\n" + "".join("\tUserInfo{%d:%s:c13} running\n" % (i, name)
                                    for i, name in enumerate(state["users"]))
    if "pm path" in command:
        package = command.split()[-1]
        return "package:/system_ext/app/x/x.apk\n" if package in state["installed"] else ""
    if "pm install" in command:
        return "Success\n"
    if "pm revoke" in command and "WRITE_SECURE_SETTINGS" in command:
        return "secure-settings-revoked\n"
    if "am start" in command:
        return "Status: ok\nActivity: com.gathin.tvhome/.MainActivity\n"
    if "pm disable-user" in command:
        state["stock_disabled"] = True
        return ""
    if "set-home-activity" in command:
        state["home"] = "com.gathin.tvhome/.MainActivity"
        return ""
    if "resolve-activity" in command:
        return state["home"] + "\n"
    return ""


args = [a for a in sys.argv[1:] if a != "-I"]
if any(a.endswith("b64push.py") for a in args):
    state["pushed"] = True
    commands = []
elif "--batch" in args:
    commands = [line.strip() for line in sys.stdin if line.strip()]
else:
    commands = [args[-1]]
for command in commands:
    state["commands"].append(command)
    sys.stdout.write(run(command))
with open(STATE_PATH, "w") as f:
    json.dump(state, f)
'''


class SecondUserGuardTest(unittest.TestCase):
    def setUp(self):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("build-and-install.sh is a POSIX script; it needs bash")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.launcher = self.base / "launcher"
        self.launcher.mkdir()
        shutil.copy2(ROOT / "launcher" / "build-and-install.sh", self.launcher)
        apk = self.launcher / "app" / "build" / "outputs" / "apk" / "release"
        apk.mkdir(parents=True)
        (apk / "app-release.apk").write_text("apk")
        tools = self.base / "tools"
        (tools / "venv" / "bin").mkdir(parents=True)
        fake = self.base / "fake_tv.py"
        fake.write_text(FAKE_TV)
        client = tools / "venv" / "bin" / "python"
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "{fake}" "$@"\n')
        client.chmod(0o755)
        self.state_path = self.base / "tv-state.json"

    def install(self, users, installed, **env_extra):
        self.state_path.write_text(json.dumps({
            "home": LAUNCHERX + "/.MainActivity", "users": users, "installed": installed,
            "stock_disabled": False, "pushed": False, "commands": [],
        }))
        env = {k: v for k, v in os.environ.items()
               if k not in ("STOCK_LAUNCHER", "INPUT_KEY_SERVICE", "HOME_KEY_SERVICE", "RESTORE_KEY_SERVICES")}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path), SKIP_BUILD="1",
                   HOME=str(self.base), **env_extra)
        result = subprocess.run(["bash", str(self.launcher / "build-and-install.sh")], cwd=self.launcher,
                                env=env, capture_output=True, text=True, timeout=120)
        return result, json.loads(self.state_path.read_text())

    def test_second_user_with_the_key_service_stops_before_anything_changes(self):
        result, state = self.install(["Owner", "new_user"], [KEY_SERVICE])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(KEY_SERVICE, result.stderr)
        self.assertIn("STOCK_LAUNCHER=none", result.stderr)
        self.assertIn("stock_home_redirect", result.stderr)
        self.assertFalse(state["pushed"])
        self.assertFalse(state["stock_disabled"])
        self.assertFalse(any("pm install" in c for c in state["commands"]))

    def test_a_single_user_disables_launcherx_as_before(self):
        result, state = self.install(["Owner"], [KEY_SERVICE])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(state["stock_disabled"])

    def test_a_second_user_without_the_key_service_disables_launcherx_as_before(self):
        result, state = self.install(["Owner", "new_user"], [])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(state["stock_disabled"])

    def test_stock_home_redirect_installs_and_keeps_launcherx(self):
        result, state = self.install(["Owner", "new_user"], [KEY_SERVICE], STOCK_LAUNCHER="none")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(state["stock_disabled"])
        self.assertFalse(any("pm list users" in c for c in state["commands"]))


class SecondUserGuardPs1Test(unittest.TestCase):
    def test_the_powershell_twin_checks_before_the_push(self):
        ps1 = (ROOT / "launcher" / "build-and-install.ps1").read_text(encoding="utf-8")
        code = "\n".join(line for line in ps1.splitlines() if not line.lstrip().startswith("#"))
        self.assertIn("pm list users", code)
        self.assertIn(f"pm path {KEY_SERVICE}", code)
        self.assertRegex(code, r"-StockLauncher none")
        guard = code.index("pm list users")
        self.assertLess(guard, code.index("b64push.py"), "the users are read before the APK is pushed")
        self.assertTrue(re.search(rf"\$StockLauncher -eq '{re.escape(LAUNCHERX)}'", code))


class SecondUserPromptTest(unittest.TestCase):
    def test_prompt_names_the_check(self):
        prompt = (ROOT / "PROMPT.md").read_text(encoding="utf-8")
        self.assertIn("pm list users", prompt)
        self.assertIn(KEY_SERVICE, prompt)
        self.assertIn("stock_home_redirect", prompt)


if __name__ == "__main__":
    unittest.main()

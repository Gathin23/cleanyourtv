"""launcher/build-and-install.ps1 checks the venv and the TV's SDK before it builds, and only sets
Home once the launcher has started (#3187).

The PowerShell twin had none of the three guards of build-and-install.sh: it set Home before it
checked that the launcher starts (and did not undo it), it built and pushed before asking the TV's
SDK, and it built before noticing that the tools venv is missing. The property worth testing is the
one no TV reproduces on demand, so tools/tv.py is replaced by a fake that keeps the TV's state in a
JSON file: the real script runs, only the ADB transport is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
The cases need `pwsh` on PATH and skip without it; CI's Ubuntu runner has it.
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
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
NEW_HOME = "com.gathin.tvhome/.MainActivity"
PWSH = shutil.which("pwsh")

# Stands in for tools/tv.py: answers the SDK level, the Home query, pm install and am start, and keeps
# the commands the script sent so a test can prove what reached the TV.
FAKE_TV = r'''
import json
import os
import sys


STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


state = load()


def run(command):
    if "getprop ro.build.version.sdk" in command:
        return state["sdk"] + "\n"
    if "pm install" in command:
        return state["install_output"]
    if "am start" in command:
        if state["start_fails"]:
            return ("Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\n"
                    "Error: Activity not started, unable to resolve Intent\n")
        return ("Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\n"
                "Status: ok\nActivity: com.gathin.tvhome/.MainActivity\n")
    if "pm disable-user" in command:
        state["stock_disabled"] = True
        return ""
    if "pm enable" in command:
        state["stock_disabled"] = False
        return ""
    if "set-home-activity" in command:
        # The rollback resolves the stock component on the TV; the handover names the new launcher.
        state["home"] = STOCK if "resolve-activity" in command else NEW
        return ""
    if "resolve-activity" in command:
        return state["home"] + "\n"
    if "WRITE_SECURE_SETTINGS" in command:
        # The step prints its marker only once the TV shows the state it wants.
        return "secure-settings-revoked\n"
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
        if "--batch" in args:
            # As tools/tv.py does: a batch echoes each command before its output.
            sys.stdout.write(f"$ {command}\n")
        sys.stdout.write(run(command))
        sys.stdout.flush()
    save()
    return 0


sys.exit(main())
'''.replace("STOCK", repr(STOCK_HOME)).replace("NEW", repr(NEW_HOME))


@unittest.skipUnless(PWSH, "build-and-install.ps1 needs pwsh on PATH")
class LauncherPs1GuardTest(unittest.TestCase):
    """The venv, minSdk and start guards, in the order the script has to run them."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.launcher = self.base / "launcher"
        (self.launcher / "app").mkdir(parents=True)
        shutil.copy2(ROOT / "launcher" / "build-and-install.ps1", self.launcher)
        self.gradle = self.launcher / "app" / "build.gradle"
        self.gradle.write_text("android {\n    defaultConfig {\n        minSdk 26\n    }\n}\n")
        # The build step only has to leave a marker: no JDK or SDK is involved here.
        self.marker = self.base / "built"
        gradlew = self.launcher / "gradlew.bat"
        if os.name == "nt":
            gradlew.write_text("@echo off\r\n"
                               f'break > "{self.marker}"\r\n'
                               "mkdir app\\build\\outputs\\apk\\release 2>nul\r\n"
                               'break > app\\build\\outputs\\apk\\release\\app-release.apk\r\n')
        else:
            # pwsh runs the file directly on Linux, where a .bat needs a shebang to start at all.
            gradlew.write_text(f'#!/bin/sh\ntouch "{self.marker}"\n'
                               'mkdir -p app/build/outputs/apk/release\n'
                               'printf apk > app/build/outputs/apk/release/app-release.apk\n')
            gradlew.chmod(0o755)
        tools = self.base / "tools"
        tools.mkdir()
        (tools / "tv.py").write_text(FAKE_TV)
        (tools / "b64push.py").write_text("print('pushed')\n")
        self.make_venv(tools)
        # The script wants a JDK 17+ in JAVA_HOME; only the file's presence is checked.
        (self.base / "java" / "bin").mkdir(parents=True)
        (self.base / "java" / "bin" / "java.exe").write_text("")
        self.state_path = self.base / "tv-state.json"
        self.home = self.base / "home"
        self.home.mkdir()

    def make_venv(self, tools):
        """The tools venv the script runs tv.py with.

        On Windows the venv's own Scripts\\python.exe runs the scripts' calls unchanged: PowerShell
        cannot start the POSIX shim the other suites write there. On Linux that shim is executable
        and stands in for the venv.
        """
        client = tools / "venv" / "Scripts" / "python.exe"
        client.parent.mkdir(parents=True)
        if os.name == "nt":
            subprocess.run([sys.executable, "-m", "venv", "--without-pip", str(tools / "venv")],
                           check=True, capture_output=True, timeout=180)
            return
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

    def install(self, **overrides):
        state = {
            "sdk": "34",
            "home": STOCK_HOME,
            "install_output": "Success\n",
            "start_fails": False,
            "stock_disabled": False,
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        env = {k: v for k, v in os.environ.items()
               if k not in ("STOCK_LAUNCHER", "INPUT_KEY_SERVICE", "HOME_KEY_SERVICE",
                            "RESTORE_KEY_SERVICES", "SKIP_BUILD")}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path),
                   HOME=str(self.home), JAVA_HOME=str(self.base / "java"),
                   ANDROID_HOME=str(self.base / "android-sdk"))
        result = subprocess.run(
            [PWSH, "-NoProfile", "-File", str(self.launcher / "build-and-install.ps1"),
             "-TvHost", "192.168.1.50"],
            cwd=self.launcher, env=env, capture_output=True, text=True, timeout=180,
        )
        with open(self.state_path) as f:
            return result, json.load(f)

    def said(self, result):
        """stderr with PowerShell's colour escapes and throw framing taken out."""
        text = re.sub(r"\x1b\[[0-9;]*m", "", result.stderr)
        # A long throw is wrapped, and pwsh puts a `|` in the gutter between the two lines.
        text = re.sub(r"\s+\|\s+", " ", text)
        return " ".join(text.split())

    def test_a_launcher_that_does_not_start_leaves_home_with_the_stock_launcher(self):
        result, state = self.install(start_fails=True)
        self.assertNotEqual(result.returncode, 0)
        said = self.said(result)
        self.assertIn("did not start", said)
        self.assertIn("was left enabled", said)
        self.assertNotIn("Installed.", result.stdout)
        self.assertFalse(any("set-home-activity" in c for c in state["commands"]),
                         "Home must not point at a launcher that does not start")
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))
        self.assertFalse(state["stock_disabled"])
        self.assertEqual(state["home"], STOCK_HOME)

    def test_a_tv_below_min_sdk_is_stopped_before_the_build(self):
        result, state = self.install(sdk="25")
        self.assertNotEqual(result.returncode, 0)
        said = self.said(result)
        self.assertIn("SDK 25", said)
        self.assertIn("minSdk 26", said)
        self.assertIn("INSTALL_FAILED_OLDER_SDK", said)
        self.assertIn("Nothing was built", said)
        self.assertFalse(self.marker.exists(), "the APK must not be built for a set that refuses it")
        self.assertEqual(state["commands"], ["getprop ro.build.version.sdk"],
                         "the SDK probe is all that may reach the TV")

    def test_a_missing_venv_is_named_before_the_build(self):
        shutil.rmtree(self.base / "tools" / "venv")
        result, state = self.install()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.marker.exists(), "nothing can be built without the tools venv")
        said = self.said(result)
        self.assertIn("python -m venv tools\\venv", said)
        self.assertIn("pip install", said)
        self.assertIn("tools\\requirements.txt", said)
        self.assertEqual(state["commands"], [], "no command may reach the TV without the venv")

    def test_a_tv_at_min_sdk_installs_and_sets_home_after_the_start(self):
        result, state = self.install(sdk="26")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Installed.", result.stdout)
        self.assertTrue(self.marker.exists())
        commands = state["commands"]
        start = next(i for i, c in enumerate(commands) if "am start" in c)
        set_home = next(i for i, c in enumerate(commands) if "set-home-activity" in c)
        self.assertLess(start, set_home, "the launcher is started before Home is handed over")
        self.assertTrue(state["stock_disabled"])
        self.assertEqual(state["home"], NEW_HOME)

    def test_an_unanswered_getprop_does_not_block_the_install(self):
        result, state = self.install(sdk="")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(self.marker.exists())
        self.assertTrue(any("pm install" in c for c in state["commands"]))

    def test_install_refused_for_the_sdk_names_the_cause(self):
        result, state = self.install(
            sdk="34",
            install_output="Failure [INSTALL_FAILED_OLDER_SDK: Failed parse during installPackageLI: "
                           "requires newer sdk version]\n",
        )
        self.assertNotEqual(result.returncode, 0)
        said = self.said(result)
        self.assertIn("INSTALL_FAILED_OLDER_SDK", said)
        self.assertIn("minSdk 26", said)
        self.assertIn("stock launcher was left untouched", said)
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))


if __name__ == "__main__":
    unittest.main()

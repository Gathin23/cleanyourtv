"""The install script asks the TV's SDK level before it builds, and the docs answer #576.

A set older than the launcher's minSdk used to cost a gradle build, a push and a `pm install` that
answered INSTALL_FAILED_OLDER_SDK - a Failure line that reads like an ADB problem. The scripts now
read minSdk from launcher/app/build.gradle (so a lower-minSdk build follows without touching the
shell script) and stop before the build. Both halves are pinned here: the guard against a fake TV,
and the text that explains what is left to do on such a set (#576).

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
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"
NEW_HOME = "com.gathin.tvhome/.MainActivity"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
README = ROOT / "README.md"
EXAMPLES = ROOT / "examples" / "README.md"
PROMPT = ROOT / "PROMPT.md"

# Stands in for tools/tv.py: answers the SDK level, the Home query and pm install, and keeps the
# commands the script sent so a test can prove nothing reached the TV.
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


def run(command):
    if "getprop ro.build.version.sdk" in command:
        return state["sdk"] + "\n"
    if "pm install" in command:
        return state["install_output"]
    if "am start" in command:
        return "Status: ok\n"
    if "set-home-activity" in command:
        state["home"] = NEW
        return ""
    if "resolve-activity" in command:
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
        sys.stdout.write(run(command))
        sys.stdout.flush()
    save()
    return 0


sys.exit(main())
'''.replace("NEW", repr(NEW_HOME))


class MinSdkGuardTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.launcher = self.base / "launcher"
        (self.launcher / "app").mkdir(parents=True)
        shutil.copy2(ROOT / "launcher" / "build-and-install.sh", self.launcher)
        self.gradle = self.launcher / "app" / "build.gradle"
        self.gradle.write_text("android {\n    defaultConfig {\n        minSdk 26\n    }\n}\n")
        # The build step only has to leave a marker: no JDK or SDK is involved here.
        self.marker = self.base / "built"
        gradlew = self.launcher / "gradlew"
        gradlew.write_text(f'#!/bin/sh\ntouch "{self.marker}"\n'
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

    def install(self, **overrides):
        state = {
            "sdk": "34",
            "home": STOCK_HOME,
            "install_output": "Success\n",
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        env = {k: v for k, v in os.environ.items()
               if k not in ("STOCK_LAUNCHER", "INPUT_KEY_SERVICE", "HOME_KEY_SERVICE")}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path),
                   HOME=str(self.home), JAVA_HOME=str(self.base / "java"),
                   ANDROID_HOME=str(self.base / "android-sdk"))
        result = subprocess.run(
            ["bash", str(self.launcher / "build-and-install.sh")],
            cwd=self.launcher, env=env, capture_output=True, text=True, timeout=180,
        )
        with open(self.state_path) as f:
            state = json.load(f)
        return result, state

    def test_a_tv_below_min_sdk_is_stopped_before_the_build(self):
        result, state = self.install(sdk="25")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SDK 25", result.stderr)
        self.assertIn("minSdk 26", result.stderr)
        self.assertIn("INSTALL_FAILED_OLDER_SDK", result.stderr)
        self.assertIn("Nothing was built", result.stderr)
        self.assertFalse(self.marker.exists(), "the APK must not be built for a set that refuses it")
        self.assertEqual(state["commands"], ["getprop ro.build.version.sdk"],
                         "the SDK probe is all that may reach the TV")

    def test_a_tv_at_min_sdk_installs(self):
        result, state = self.install(sdk="26")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Installed.", result.stdout)
        self.assertTrue(self.marker.exists())
        self.assertTrue(any("pm install" in c for c in state["commands"]))

    def test_the_number_comes_from_the_build_file(self):
        # What #608 (minSdk 24) changes: the guard has to follow the build file, not a copy of it.
        self.gradle.write_text("android {\n    defaultConfig {\n        minSdk 24\n    }\n}\n")
        result, _ = self.install(sdk="25")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.marker.exists())

    def test_an_unanswered_getprop_does_not_block_the_install(self):
        result, _ = self.install(sdk="")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(self.marker.exists())

    def test_a_missing_build_file_does_not_block_the_install(self):
        self.gradle.unlink()
        result, _ = self.install(sdk="25")
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_install_refused_for_the_sdk_names_the_cause(self):
        result, state = self.install(
            sdk="34",
            install_output="Failure [INSTALL_FAILED_OLDER_SDK: Failed parse during installPackageLI: "
                           "requires newer sdk version]\n",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("minSdk 26", result.stderr)
        self.assertIn("stock launcher was left untouched", result.stderr)
        self.assertFalse(any("pm disable-user" in c for c in state["commands"]))


class Android7DocsTest(unittest.TestCase):
    """The text #576 asked for: what an older set can and cannot do with this repo."""

    def setUp(self):
        self.trouble = re.sub(r"\s+", " ", TROUBLE.read_text(encoding="utf-8"))
        self.readme = re.sub(r"\s+", " ", README.read_text(encoding="utf-8"))
        self.prompt = re.sub(r"\s+", " ", PROMPT.read_text(encoding="utf-8"))

    def test_troubleshooting_has_the_older_sdk_bullet(self):
        self.assertIn("INSTALL_FAILED_OLDER_SDK", self.trouble)
        start = self.trouble.index("INSTALL_FAILED_OLDER_SDK")
        bullet = self.trouble[max(0, start - 200):start + 2200]
        self.assertIn("getprop ro.build.version.sdk", bullet)
        self.assertIn("launcher/app/build.gradle", bullet)
        self.assertIn("#576", bullet)
        # The half that still works has to be named, and so does the one that does not.
        self.assertIn("debloat", bullet.lower())
        self.assertIn("minSdk", bullet)

    def test_troubleshooting_sends_a_rebuilt_apk_elsewhere(self):
        start = self.trouble.index("INSTALL_FAILED_OLDER_SDK")
        bullet = self.trouble[max(0, start - 200):start + 2600]
        self.assertIn("INSTALL_FAILED_VERIFICATION_FAILURE", bullet,
                      "the firmware-verifier case is the other reason an install fails")
        self.assertIn("build-and-install.sh", bullet)

    def test_readme_states_the_launcher_requirement_with_a_pointer(self):
        start = self.readme.index("## The launcher")
        section = self.readme[start:start + 1600]
        self.assertIn("API 26", section)
        self.assertIn("launcher/app/build.gradle", section)
        self.assertIn("docs/TROUBLESHOOTING.md", section)

    def test_prompt_tells_an_assistant_not_to_promise_it(self):
        self.assertIn("minSdk", self.prompt)
        self.assertIn("ro.build.version.sdk", self.prompt)

    def test_the_stale_min_sdk_24_row_is_gone(self):
        rows = [line for line in EXAMPLES.read_text(encoding="utf-8").split("\n")
                if line.startswith("| `sony-kd-55x8509c/`")]
        self.assertEqual(len(rows), 1)
        self.assertNotIn("minSdk 24", rows[0])
        self.assertIn("minSdk 26", rows[0])


if __name__ == "__main__":
    unittest.main()

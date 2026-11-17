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
import subprocess
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
    if "WRITE_SECURE_SETTINGS" in command:
        return secure_step(command)
    return ""


# The WRITE_SECURE_SETTINGS step decides its own marker on the TV, so it runs here as written,
# in sh, against a pm and a dumpsys that keep the grant in the state file.
# A refused grant prints pm's exception and exits non-zero; a revoke the TV ignores says nothing.
PM = """#!/bin/sh
case "$1" in
  grant)
    if [ "$(cat "$FAKE_SECURE.grant")" != 1 ]; then
      echo "Exception occurred while executing 'grant':" >&2; exit 255
    fi
    echo granted > "$FAKE_SECURE" ;;
  revoke) [ "$(cat "$FAKE_SECURE.revoke")" = 1 ] && echo denied > "$FAKE_SECURE" ;;
esac
exit 0
"""
# Laid out as Android 8 to 11 print it: the requested list always names the permission, and the
# install-permissions line is there only while it is granted. A failed readback stops after the requested
# list with a non-zero exit; a timed-out one stops there too, says so, and exits 0 as dumpsys does.
DUMPSYS = """#!/bin/sh
echo "  Package [com.gathin.tvhome] (1a2b3c):"
echo "    requested permissions:"
echo "      android.permission.WRITE_SECURE_SETTINGS"
case "$(cat "$FAKE_SECURE.readback")" in
  fail) exit 1 ;;
  timeout) echo "*** SERVICE 'package' DUMP TIMEOUT (10000ms) EXPIRED ***"; exit 0 ;;
esac
if [ "$(cat "$FAKE_SECURE")" = granted ]; then
  echo "    install permissions:"
  echo "      android.permission.WRITE_SECURE_SETTINGS: granted=true"
fi
echo "    User 0: ceDataInode=4242 installed=true hidden=false suspended=false stopped=false"
"""


def secure_step(command):
    shims = os.path.join(os.path.dirname(STATE_PATH), "tv-bin")
    os.makedirs(shims, exist_ok=True)
    for name, body in (("pm", PM), ("dumpsys", DUMPSYS)):
        path = os.path.join(shims, name)
        with open(path, "w") as f:
            f.write(body)
        os.chmod(path, 0o755)
    secure = shims + "/secure"
    with open(secure, "w") as f:
        f.write(state["secure"] + "\n")
    for suffix, value in ((".grant", "1" if state["grant_works"] else "0"),
                          (".revoke", "1" if state["revoke_works"] else "0"),
                          (".readback", state["readback"])):
        with open(secure + suffix, "w") as f:
            f.write(value + "\n")
    env = dict(os.environ, PATH=shims + ":" + os.environ["PATH"], FAKE_SECURE=secure)
    out = subprocess.run(["sh", "-c", command], env=env, capture_output=True, text=True).stdout
    with open(secure) as f:
        state["secure"] = f.read().strip()
    return out


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
        if state["link_fails"] and "WRITE_SECURE_SETTINGS" in command:
            save()
            sys.stderr.write("Error: could not reach the TV\n")
            return 2
        if "--batch" in args:
            # As tools/tv.py does: a batch echoes each command before its output.
            sys.stdout.write(f"$ {command}\n")
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

    def install(self, restore=False, **overrides):
        state = {
            "sdk": "34",
            "home": STOCK_HOME,
            "install_output": "Success\n",
            "secure": "denied",
            "revoke_works": True,
            "grant_works": True,
            "readback": "ok",
            "link_fails": False,
            "commands": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        env = {k: v for k, v in os.environ.items()
               if k not in ("STOCK_LAUNCHER", "INPUT_KEY_SERVICE", "HOME_KEY_SERVICE")}
        env.update(TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path),
                   HOME=str(self.home), JAVA_HOME=str(self.base / "java"),
                   ANDROID_HOME=str(self.base / "android-sdk"))
        env.pop("RESTORE_KEY_SERVICES", None)
        if restore:
            env["RESTORE_KEY_SERVICES"] = "1"
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("build-and-install.sh is a POSIX script; it needs bash")
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

    def test_a_grant_the_tv_keeps_stops_the_install(self):
        result, state = self.install(secure="granted", revoke_works=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("secure-settings-revoked missing", result.stderr)
        self.assertEqual(state["secure"], "granted")

    def test_a_failed_or_timed_out_readback_stops_the_revoke(self):
        for readback in ("fail", "timeout"):
            with self.subTest(readback=readback):
                result, state = self.install(secure="granted", revoke_works=False, readback=readback)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("secure-settings-revoked missing", result.stderr)
                self.assertEqual(state["secure"], "granted")

    def test_a_refused_grant_stops_the_install(self):
        result, state = self.install(restore=True, grant_works=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("restore-granted missing", result.stderr)
        self.assertEqual(state["secure"], "denied")

    def test_a_grant_whose_readback_fails_or_times_out_stops_the_install(self):
        for readback in ("fail", "timeout"):
            with self.subTest(readback=readback):
                result, _ = self.install(restore=True, readback=readback)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("restore-granted missing", result.stderr)

    def test_a_tv_that_cannot_be_reached_gets_the_message(self):
        # tv.py exits non-zero for this step only; set -e must not stop the script before the error.
        result, _ = self.install(link_fails=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("secure-settings-revoked missing", result.stderr)

    def test_a_granted_tv_is_revoked(self):
        result, state = self.install(secure="granted")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(state["secure"], "denied")

    def test_restore_key_services_grants(self):
        result, state = self.install(restore=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("restore-granted", result.stdout)
        self.assertEqual(state["secure"], "granted")

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

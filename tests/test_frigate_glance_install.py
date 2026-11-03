"""Frigate Glance's install.sh must hand the config to the app in a form the app can read, and say so (#3016).

On Android 14 the adb shell can cp into Android/data, but the file belongs to the shell (uid 2000) and the app
gets EACCES opening it. The script used to try cp first, print "config pushed", and only fail later with
"MQTT: no config". It now writes through the app's ConfigProvider first, as tools/theme.sh does since #842,
and reads the app's log to report whether the app imported the file.

These tests run the real install.sh against a stub tools/tv.py whose "TV shell" is /bin/sh on this machine.
Stub `content`, `cp` and `mkdir` commands track which files and folders the shell owns, and a stub app reads
the pushed file the way the real one does: it can open a file the shell owns only on Android 8 and 9, where
sdcardfs hides the owner.
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
ADDON = ROOT / "addons" / "frigate-glance"
PKG = "io.github.nickld.frigateglance"
DEVICE_DIR = f"/sdcard/Android/data/{PKG}/files"
SECRET = "hunter2-secret"

FAKE_TV = r'''
import os
import shutil
import subprocess
import sys

root = os.environ["FAKE_ROOT"]


def local(text):
    return (text.replace("/sdcard/Android/data/io.github.nickld.frigateglance/files", root + "/device/files")
                .replace("/data/local/tmp/", root + "/tmp/"))


if sys.argv[1] == "--push":
    shutil.copyfile(sys.argv[2], local(sys.argv[3]))
else:
    env = {**os.environ, "PATH": root + "/bin" + os.pathsep + os.environ["PATH"]}
    result = subprocess.run(["sh", "-c", local(" ".join(sys.argv[1:]))], capture_output=True, text=True, env=env)
    sys.stdout.write(result.stdout)
    sys.stderr.write(result.stderr)
    # tv.py passes the shell's text through, not its exit status.
'''

# The app: what ConfigStore.importPushed() and GlanceService log, against the fake TV's files.
FAKE_APP = r'''
import json
import os
import sys

root = os.environ["FAKE_ROOT"]
pushed = os.path.join(root, "device", "files", "config.json")
private = os.path.join(root, "private-config.json")
owned = os.path.join(root, "shell-owned.txt")


def log(level, message):
    with open(os.path.join(root, "logcat.txt"), "a") as f:
        f.write(f"11-02 10:00:00.000  4242  4242 {level} FrigateGlance: {message}\n")


def shell_owns(path):
    return os.path.exists(owned) and path in open(owned).read().splitlines()


def import_pushed():
    if not os.path.isfile(pushed):
        return
    if shell_owns(pushed) and os.environ.get("FAKE_ANDROID") != "9":
        log("W", "pushed config unreadable: /storage/emulated/0/Android/data/io.github.nickld.frigateglance"
                 "/files/config.json: open failed: EACCES (Permission denied)")
        return
    text = open(pushed).read()
    os.remove(pushed)
    if os.environ.get("FAKE_REJECT"):
        # GlanceConfig passes a parser's message through, and that can quote the config.
        log("W", "pushed config rejected: bad value " + json.loads(text)["mqtt"])
        return
    open(private, "w").write(text)
    log("I", "config imported")


import_pushed()
if sys.argv[1] == "start":
    if os.path.exists(private):
        log("I", "mqtt connected")
        log("I", "frigate ok")
    else:
        log("W", "mqtt error: no config")
'''

# `content write` runs inside the app's process on a TV, so the file it creates is the app's. Android 8 and 9
# have no write subcommand; a provider that throws prints its exception. Both exit 0, as on a TV.
FAKE_CONTENT = r'''
import os
import subprocess
import sys

root = os.environ["FAKE_ROOT"]
owned = os.path.join(root, "shell-owned.txt")
mode = os.environ.get("FAKE_CONTENT", "ok")
with open(os.path.join(root, "content.log"), "a") as f:
    f.write(" ".join(sys.argv[1:]) + "\n")


def shell_owns(path):
    return os.path.exists(owned) and path in open(owned).read().splitlines()


# A file or folder the shell removed is no longer the shell's: the provider creates it anew as the app.
if os.path.exists(owned):
    kept = [p for p in open(owned).read().splitlines() if os.path.exists(p)]
    open(owned, "w").write("".join(p + "\n" for p in kept))
if mode == "android9" and sys.argv[1] == "write":
    print("usage: adb shell content [subcommand] [options]")
    print("[ERROR] Unsupported operation: write")
    sys.exit(0)
folder = os.path.join(root, "device", "files")
path = os.path.join(folder, "config.json")
if mode == "error" or shell_owns(folder) or shell_owns(path):
    print("Error while accessing provider:io.github.nickld.frigateglance.config")
    print("java.io.FileNotFoundException: open failed: EACCES (Permission denied)")
    sys.exit(0)
os.makedirs(folder, exist_ok=True)
with open(path, "wb") as f:
    f.write(sys.stdin.buffer.read())
# ConfigProvider imports the file as soon as it is closed.
subprocess.run([sys.executable, os.path.join(root, "app.py"), "close"], check=True)
'''


@unittest.skipIf(os.name == "nt", "install.sh requires a POSIX shell")
class FrigateGlanceInstallTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        addon = self.root / "addons" / "frigate-glance"
        addon.mkdir(parents=True)
        shutil.copy2(ADDON / "install.sh", addon / "install.sh")
        (addon / "gradlew").write_text("#!/bin/sh\nexit 0\n")
        (addon / "gradlew").chmod(0o755)
        (addon / "local.properties").write_text("sdk.dir=/nowhere\n")
        tools = self.root / "tools"
        (tools / "venv" / "bin").mkdir(parents=True)
        (tools / "venv" / "bin" / "python").symlink_to(sys.executable)
        (tools / "b64push.py").write_text("pass\n")
        (tools / "tv.py").write_text(FAKE_TV)
        (self.root / "app.py").write_text(FAKE_APP)
        (self.root / "content.py").write_text(FAKE_CONTENT)
        (self.root / "tmp").mkdir()
        (self.root / "device").mkdir()
        self.files = self.root / "device" / "files"
        self.owned = self.root / "shell-owned.txt"
        py = sys.executable
        stubs = {
            "pm": "echo Success",
            "appops": "exit 0",
            "cmd": "exit 0",
            "sleep": "exit 0",
            "settings": '[ "$1" = get ] && echo null; exit 0',
            "logcat": f'case "$1" in -c) : > "$FAKE_ROOT/logcat.txt" ;; -d) cat "$FAKE_ROOT/logcat.txt" ;; esac',
            "am": f'[ "$1" = start ] && "{py}" "$FAKE_ROOT/app.py" start; exit 0',
            "content": f'exec "{py}" "$FAKE_ROOT/content.py" "$@"',
            # A folder or file the adb shell creates is the shell's.
            "mkdir": '[ "$1" = -p ] && shift\nfor d in "$@"; do [ -d "$d" ] || echo "$d" >> "$FAKE_ROOT/shell-owned.txt"; '
                     '/bin/mkdir -p "$d" || exit 1; done',
            "cp": 'for last; do :; done\n/bin/cp "$@" && echo "$last" >> "$FAKE_ROOT/shell-owned.txt"',
        }
        (self.root / "bin").mkdir()
        for name, body in stubs.items():
            path = self.root / "bin" / name
            path.write_text("#!/bin/sh\n" + body + "\n")
            path.chmod(0o755)
        self.config = self.root / "config.json"
        self.config.write_text(json.dumps({"mqtt": f"mqtt://glance:{SECRET}@broker.invalid:1883",
                                           "frigateUrl": "http://frigate.invalid:5000"}))

    def install(self, **env):
        # The stubs go on the script's own PATH too, so its 2 s waits do not slow the failure cases.
        full = {**os.environ, "TV_HOST": "192.0.2.10", "FAKE_ROOT": str(self.root),
                "PATH": f"{self.root / 'bin'}{os.pathsep}{os.environ['PATH']}", **env}
        result = subprocess.run(["bash", str(self.root / "addons" / "frigate-glance" / "install.sh"),
                                 str(self.config)], cwd=self.root, env=full, capture_output=True, text=True,
                                timeout=120)
        self.assertNotIn(SECRET, result.stdout + result.stderr, "install.sh promises never to print the config")
        return result

    def shell_owned(self):
        return self.owned.read_text().splitlines() if self.owned.exists() else []

    def test_android_14_config_goes_through_the_provider(self):
        result = self.install()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Config: read by the app\nMQTT: ok\nFrigate: ok\n", result.stdout)
        self.assertIn("Installed.", result.stdout)
        self.assertEqual(self.shell_owned(), [], "neither cp nor mkdir has to run when the provider answers")
        self.assertFalse((self.root / "tmp" / "fg-config.json").exists(), "the staged copy holds passwords")

    def test_a_leftover_copy_from_the_old_script_is_removed_first(self):
        """The provider cannot truncate a file the shell owns, so the stale copy has to go before it writes."""
        self.files.mkdir()
        (self.files / "config.json").write_text("{}")
        self.owned.write_text(f"{self.files / 'config.json'}\n")
        result = self.install()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Config: read by the app", result.stdout)

    def test_android_9_without_content_write_falls_back_to_cp(self):
        """`content` exits 0 there, so only its message tells the script to run cp."""
        result = self.install(FAKE_CONTENT="android9", FAKE_ANDROID="9")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        calls = (self.root / "content.log").read_text().splitlines()
        self.assertEqual(calls, [f"write --uri content://{PKG}.config/config.json"], "the provider is tried first")
        self.assertIn(str(self.files / "config.json"), self.shell_owned(), "cp did the writing")
        self.assertIn("Config: read by the app", result.stdout)

    def test_a_copy_the_app_cannot_open_fails_at_the_config_line(self):
        """The reported failure: the shell wrote the file, the app got EACCES, and the old script blamed MQTT."""
        result = self.install(FAKE_CONTENT="error")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Config: unreadable: ", result.stdout)
        self.assertIn("EACCES (Permission denied)", result.stdout)
        self.assertNotIn("Installed.", result.stdout)

    def test_a_new_config_the_app_cannot_open_is_not_hidden_by_the_old_one(self):
        """pm install -r keeps the app's data, so MQTT and Frigate can come up on the last config imported."""
        (self.root / "private-config.json").write_text("{}")
        result = self.install(FAKE_CONTENT="error")
        self.assertIn("MQTT: ok", result.stdout)
        self.assertIn("Config: unreadable: ", result.stdout)
        self.assertNotEqual(result.returncode, 0, "the config just pushed never reached the app")
        self.assertNotIn("Installed.", result.stdout)

    def test_a_folder_the_old_script_made_is_reported_not_hidden(self):
        """A files folder the shell made refuses the provider too; the script has to say the app never read it."""
        self.files.mkdir()
        self.owned.write_text(f"{self.files}\n")
        result = self.install()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Config: unreadable: ", result.stdout)

    def test_a_rejected_config_is_named_without_its_contents(self):
        result = self.install(FAKE_REJECT="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Config: rejected by the app", result.stdout)


if __name__ == "__main__":
    unittest.main()

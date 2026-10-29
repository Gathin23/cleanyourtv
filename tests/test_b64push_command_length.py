"""b64push.py sizes its chunks from the command the TV is sent, the remote path included.

Each chunk is one shell command - echo '<base64>' >> '<remote>.b64.part' && echo okN - and Google ADB
refuses a service string longer than MAX_PAYLOAD_V1 (4096 bytes) unless the shell protocol is in use,
which tools/tv.py's -x turns off. The service string is "shell:" plus the command, so the staging path
the command carries comes out of the same 4096 bytes. The chunk was sized with a flat 64-byte
allowance for the whole wrapper, so a remote path past about 40 characters put the command over: an APK
staged as /data/local/tmp/tvhome.apk (34 characters) fitted, and a theme wallpaper staged as
/data/local/tmp/cyt-theme-wallpapers-01-dusk-peaks.jpg (59) did not - native-adb's theme.sh stopped at
the first wallpaper with "shell command too long", after it had written the new config.json and cleared
the old collection (#2614).

The fake tools/tv.py below is the adb client for those runs: its shell() refuses "shell:" + command
over 4096 bytes in adb's own words, then runs the command on /bin/sh, so a push whose chunk sizes are
right finishes and the file arrives. A second fake has no command bound at all, which is the adb_shell
transport: its chunks stay at 48 KB.

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

# The Google ADB backend: a 4 KB service string, "shell:" + the command, and a shell that really runs.
FAKE_ADB_TV = r'''
import json
import os
import subprocess

LOG = os.environ["FAKE_LOG"]
LIMIT = 4 * 1024   # adb's MAX_PAYLOAD_V1


class Device:
    max_chunk_size = 4096
    max_shell_command_size = LIMIT - len("shell:")

    def shell(self, command, read_timeout_s=None):
        with open(LOG, "a") as f:
            f.write(json.dumps(command) + "\n")
        # adb's client/commandline.cpp, RemoteShell(): a service string over MAX_PAYLOAD_V1 is
        # refused when the shell protocol is off, which -x turns off.
        if len("shell:" + command) > LIMIT:
            raise RuntimeError("Google ADB shell on fake: error: shell command too long")
        return subprocess.run(["/bin/sh", "-c", command], capture_output=True, text=True).stdout

    def close(self):
        pass


def connect():
    return Device()
'''

# The adb_shell backend: no bound on how long a shell command may be, so b64push keeps its 48 KB chunks.
FAKE_ADB_SHELL_TV = r'''
import json
import os
import subprocess

LOG = os.environ["FAKE_LOG"]


class Device:
    max_chunk_size = 65536

    def shell(self, command, read_timeout_s=None):
        with open(LOG, "a") as f:
            f.write(json.dumps(command) + "\n")
        return subprocess.run(["/bin/sh", "-c", command], capture_output=True, text=True).stdout

    def close(self):
        pass


def connect():
    return Device()
'''


@unittest.skipUnless(os.name == "posix" and shutil.which("md5sum") and shutil.which("base64"),
                     "the fake TV's shell is /bin/sh, and md5sum and base64 are needed")
class CommandLengthTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", self.tools / "b64push.py")
        self.remote_dir = self.base / "remote"
        self.remote_dir.mkdir()
        self.log = self.base / "log.jsonl"

    def fake_tv(self, text):
        (self.tools / "tv.py").write_text(text)

    def push(self, data, remote):
        local = self.base / "local.bin"
        local.write_bytes(data)
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update(FAKE_LOG=str(self.log))
        return subprocess.run([sys.executable, str(self.tools / "b64push.py"), str(local), str(remote)],
                              env=full, capture_output=True, text=True, timeout=120)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def chunk_commands(self):
        return [c for c in self.commands() if c.startswith("echo '")]

    def test_a_theme_wallpaper_path_is_pushed_in_commands_the_tv_takes(self):
        self.fake_tv(FAKE_ADB_TV)
        remote = self.remote_dir / "cyt-theme-wallpapers-01-dusk-peaks.jpg"
        # 59 characters of path in the staging name, which is the one #2614 hit.
        self.assertGreater(len(str(remote)), 50)
        data = os.urandom(30000)
        result = self.push(data, remote)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.remote_dir / "cyt-theme-wallpapers-01-dusk-peaks.jpg").read_bytes(), data)
        self.assertNotIn("too long", result.stdout + result.stderr)
        chunks = self.chunk_commands()
        self.assertGreater(len(chunks), 1, "a 30 KB file does not fit one command")
        self.assertLessEqual(max(len("shell:" + c) for c in chunks), 4096,
                             "every chunk command has to fit the service string the TV takes")

    def test_a_much_longer_path_still_fits(self):
        self.fake_tv(FAKE_ADB_TV)
        name = "cyt-theme-wallpapers-" + "deeper-" * 25 + "dusk.jpg"   # about 200 characters
        data = os.urandom(5000)
        result = self.push(data, self.remote_dir / name)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual((self.remote_dir / name).read_bytes(), data)
        self.assertLessEqual(max(len("shell:" + c) for c in self.chunk_commands()), 4096)

    def test_a_path_that_leaves_no_room_for_a_chunk_is_refused(self):
        self.fake_tv(FAKE_ADB_TV)
        remote = self.remote_dir / ("a" * 4090)
        result = self.push(os.urandom(5000), remote)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("leaves no room for a chunk", result.stderr)
        self.assertIn("Nothing was sent to the TV.", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(self.commands(), [], "a refused path sends no command at all")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), [])


@unittest.skipUnless(os.name == "posix" and shutil.which("md5sum") and shutil.which("base64"),
                     "the fake TV's shell is /bin/sh, and md5sum and base64 are needed")
class AdbShellChunksTest(unittest.TestCase):
    """A device that states no command bound is left with the chunks it had."""

    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        tools = self.base / "tools"
        tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", tools / "b64push.py")
        (tools / "tv.py").write_text(FAKE_ADB_SHELL_TV)
        self.script = tools / "b64push.py"
        self.remote_dir = self.base / "remote"
        self.remote_dir.mkdir()
        self.log = self.base / "log.jsonl"

    def test_the_48_kb_chunk_is_kept(self):
        local = self.base / "app.apk"
        local.write_bytes(os.urandom(100000))
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update(FAKE_LOG=str(self.log))
        remote = self.remote_dir / "app.apk"
        result = subprocess.run([sys.executable, str(self.script), str(local), str(remote)], env=full,
                                capture_output=True, text=True, timeout=120)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(remote.read_bytes(), local.read_bytes())
        chunks = [json.loads(line) for line in self.log.read_text().splitlines() if "echo '" in line]
        self.assertEqual(len(chunks), 3, "100 KB is three 48 KB chunks")


if __name__ == "__main__":
    unittest.main()

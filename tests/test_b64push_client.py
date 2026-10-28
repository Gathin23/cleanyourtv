"""tools/b64push.py: a client that is not installed is refused, not a traceback.

The script is started both ways, like the other tools: every add-on install script and
`tools/theme.sh` run it as `tools/venv/bin/python -I tools/b64push.py`, and a reader who has just
cloned the repository types `python3 tools/b64push.py app.apk /data/local/tmp/app.apk`. That second
run ended in a ModuleNotFoundError out of `import tv` - a traceback with exit 1, the code this file
keeps for a transfer that failed, although nothing had been sent and no TV had been opened - and it
named neither tools/requirements.txt nor the venv sitting next to it.

b64push.py is the fourth place the client is imported. tools/privacy.py's and tools/recovery.py's
connect() stop with the line that builds the venv, tools/bench.py and tools/trace.py start again
under it, and tools/tv.py does the same when it is the script that was started; this file now answers
with that line and exits 2, which is what its own exit status reserves for a run that sent nothing.

The first run below copies the real tools/tv.py beside a copy of the script and starts it with -S, so
the interpreter cannot import adb_shell whatever it is installed into: that is the shape a fresh clone
gives. The second puts a stand-in tv.py there instead, and shows the handler is entered only when the
import fails: a client that loads is used as it was.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

# A tools/tv.py that records being opened, so a run that gets past the import leaves a file behind.
FAKE_TV = r'''import os

open(os.environ["FAKE_CONNECTED"], "w").write("connected")


class Device:
    max_chunk_size = 4096

    def shell(self, command, read_timeout_s=None):
        return ""

    def close(self):
        pass


def connect():
    return Device()
'''


class MissingClientTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        tools = self.base / "tools"
        tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", tools / "b64push.py")
        shutil.copy2(ROOT / "tools" / "tv.py", tools / "tv.py")
        self.script = tools / "b64push.py"
        self.connected = self.base / "connected"
        self.local = self.base / "app.apk"
        self.local.write_bytes(b"an apk")

    def run_b64push(self, *args, isolated=False):
        """The script, with no TV_HOST in the environment and adb_shell unavailable under -S."""
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
        env["FAKE_CONNECTED"] = str(self.connected)
        command = [sys.executable] + (["-S"] if isolated else []) + [str(self.script), *args]
        return subprocess.run(command, env=env, capture_output=True, text=True, timeout=60)

    def assert_client_refused(self, result):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("cannot load tools/tv.py", result.stderr)
        self.assertIn("tools/venv", result.stderr, "the message names the venv to install the packages into")
        self.assertIn("tools/requirements.txt", result.stderr, "and the file the packages are listed in")
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "", "nothing is reported as transferred")
        self.assertFalse(self.connected.exists(), "the TV is not opened")

    def test_a_run_without_the_packages_is_refused(self):
        self.assert_client_refused(self.run_b64push(str(self.local), "/data/local/tmp/app.apk",
                                                   isolated=True))

    def test_the_refusal_comes_before_the_tv_host_check(self):
        # tv.py reads TV_HOST at import time, so a run with no TV_HOST and no packages has to answer
        # about the packages: that is the thing the reader can fix.
        result = self.run_b64push(str(self.local), "/data/local/tmp/app.apk", isolated=True)
        self.assertNotIn("Set TV_HOST", result.stderr)
        self.assertIn("adb_shell", result.stderr, "the missing package is named")

    def test_a_word_count_that_is_wrong_still_gets_the_usage_line(self):
        # The usage message is written before the client is imported, so this refusal is unchanged.
        result = self.run_b64push(str(self.local), isolated=True)
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("usage: b64push.py", result.stderr)
        self.assertNotIn("cannot load", result.stderr)

    def test_a_client_that_loads_is_used_as_before(self):
        (self.base / "tools" / "tv.py").write_text(FAKE_TV, encoding="utf-8")
        result = self.run_b64push(str(self.local), "/data/local/tmp/app.apk")
        self.assertNotIn("cannot load tools/tv.py", result.stderr,
                         "the handler is for a failed import, not for every run")
        self.assertTrue(self.connected.exists(), "the client beside the copy was imported and used")


if __name__ == "__main__":
    unittest.main()

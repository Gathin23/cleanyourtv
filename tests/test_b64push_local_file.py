"""tools/b64push.py: a local file it cannot read is refused before the TV is opened.

The script is what the docs and every add-on's install script use for a large push (an APK that
the plain client hangs on). A path typed from the wrong folder, or a build that left no APK
behind, used to end in FileNotFoundError from `open(local, "rb")` with a traceback and exit 1 -
the code the script's own callers read as "the transfer failed", although nothing had been sent
and no TV had been opened.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

# A tools/tv.py that records being opened. b64push.py adds its own directory to sys.path and
# imports this, so a run that reaches the TV leaves a file behind.
FAKE_TV = r'''
import os

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


class LocalFileTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        tools = self.base / "tools"
        tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", tools / "b64push.py")
        (tools / "tv.py").write_text(FAKE_TV)
        self.script = tools / "b64push.py"
        self.connected = self.base / "connected"
        self.local = self.base / "app.apk"
        self.local.write_bytes(b"an apk")

    def run_b64push(self, *args):
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env["FAKE_CONNECTED"] = str(self.connected)
        return subprocess.run([sys.executable, str(self.script), *args], env=env,
                              capture_output=True, text=True, timeout=60)

    def assert_refused(self, result, path):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("cannot read", result.stderr)
        self.assertIn(repr(str(path)), result.stderr, "the message names the path it could not read")
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "", "nothing is reported as transferred")
        self.assertFalse(self.connected.exists(), "the TV is not opened for a file that cannot be read")

    def test_a_file_that_is_not_there_is_refused_before_the_tv_is_opened(self):
        missing = self.base / "no-such-app.apk"
        self.assert_refused(self.run_b64push(str(missing), "/data/local/tmp/app.apk"), missing)

    def test_a_directory_is_refused(self):
        self.assert_refused(self.run_b64push(str(self.base), "/data/local/tmp/app.apk"), self.base)

    def test_a_path_with_a_file_in_the_middle_is_refused(self):
        path = self.local / "app.apk"
        self.assert_refused(self.run_b64push(str(path), "/data/local/tmp/app.apk"), path)

    def test_an_empty_local_argument_is_refused(self):
        self.assert_refused(self.run_b64push("", "/data/local/tmp/app.apk"), "")

    def test_the_read_is_guarded_before_tv_is_imported(self):
        """A TV that is opened and then fails is not the same as one never opened."""
        source = (ROOT / "tools" / "b64push.py").read_text()
        read = source.index("with open(local")
        imported = source.index("import tv")
        guard = source[read:imported]
        self.assertIn("except OSError", guard, "the read is inside a try that catches OSError")
        self.assertIn("return 2", guard, "a file that cannot be read is an argument refused, not a failure")
        self.assertLess(read, imported, "the file is read before the TV is imported")


if __name__ == "__main__":
    unittest.main()

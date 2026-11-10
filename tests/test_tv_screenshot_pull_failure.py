"""tools/tv.py --screenshot answers a picture the TV does not send, like --pull does.

adb_shell 0.4.4 opens the local path for writing before it asks the TV for the file, so a pull that
fails truncated the file that was already there - the earlier screenshot a change was to be compared
against - to 0 bytes, or wrote half a picture, and `dev.pull()` was called straight out of
screenshot(): the reader got a traceback out of the command PROMPT.md, AGENTS.md and CONTRIBUTING.md
all hand out, exit 1 (this client's code for a capture that came back empty, so a lost connection read
as a TV with nothing on screen), and the capture left behind in /data/local/tmp. --pull has answered
this in words since #3191 and --push since #3311; the capture's own pull is the same transfer.

The runs here use a stub adb_shell that opens the local file the way the library does and then answers
the way the TV does, so no socket is opened.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"
# A complete PNG: the signature, some chunk bytes, and the IEND chunk `screencap -p` always ends with.
PNG_END = b"IEND\xaeB`\x82"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 32 + PNG_END
OLD = b"the screenshot that was already here"
STUB_EXCEPTIONS = '''\
class AdbCommandFailureException(Exception):
    pass


class AdbConnectionError(Exception):
    pass


class AdbTimeoutError(Exception):
    pass


class InvalidCommandError(Exception):
    pass


class TcpTimeoutException(Exception):
    pass
'''
STUB_DEVICE = '''\
import os

from adb_shell.exceptions import AdbCommandFailureException


class AdbDeviceTcp:
    """Captures on the TV, then pulls the way adb_shell 0.4.4 does: the local file is opened for
    writing first, and only then does the TV answer. STUB_PULL picks the answer: ok, drop (the
    connection resets after part of the file), missing (no such file) or bug (not the TV's)."""

    def __init__(self, host, port, **kwargs):
        pass

    def connect(self, **kwargs):
        pass

    def shell(self, command, **kwargs):
        with open(os.environ["STUB_LOG"], "a", encoding="utf-8") as log:
            log.write(command + "\\n")
        return "rc=0\\n" if "screencap" in command else ""

    def pull(self, remote, local, **kwargs):
        with open(local, "wb") as stream:
            how = os.environ.get("STUB_PULL", "ok")
            if how == "missing":
                raise AdbCommandFailureException("Command failed: No such file or directory")
            if how == "drop":
                stream.write(b"half of the")
                raise ConnectionResetError(104, "Connection reset by peer")
            if how == "bug":
                raise RuntimeError("a bug in the client")
            with open(os.environ["STUB_PNG_FILE"], "rb") as picture:
                stream.write(picture.read())

    def close(self):
        pass
'''
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"


class ScreenshotPullFailureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        base = Path(cls._tmp.name) / "stub"
        (base / "adb_shell" / "auth").mkdir(parents=True)
        (base / "adb_shell" / "__init__.py").write_text("", encoding="utf-8")
        (base / "adb_shell" / "exceptions.py").write_text(STUB_EXCEPTIONS, encoding="utf-8")
        (base / "adb_shell" / "adb_device.py").write_text(STUB_DEVICE, encoding="utf-8")
        (base / "adb_shell" / "auth" / "__init__.py").write_text("", encoding="utf-8")
        (base / "adb_shell" / "auth" / "keygen.py").write_text(STUB_KEYGEN, encoding="utf-8")
        (base / "adb_shell" / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER, encoding="utf-8")
        cls.base = base
        # A stock ADB key under a temporary HOME, so neither tools/adbkey nor the real ~/.android is used.
        home = Path(cls._tmp.name) / "home"
        (home / ".android").mkdir(parents=True)
        (home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.home = home

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def setUp(self):
        self.folder = Path(tempfile.mkdtemp(dir=self._tmp.name))
        self.target = self.folder / "after.png"
        self.png = self.folder / "picture.png"
        self.png.write_bytes(PNG)
        self.log = self.folder / "shell.log"
        self.log.write_text("", encoding="utf-8")

    def run_screenshot(self, how="ok"):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH",
                                                                "TV_ADB_BINARY")}
        env["HOME"] = str(self.home)
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["PYTHONPATH"] = str(self.base)
        env["TV_HOST"] = "tv.local"      # the stub answers, so no socket is opened
        env["STUB_PULL"] = how
        env["STUB_LOG"] = str(self.log)
        env["STUB_PNG_FILE"] = str(self.png)
        return subprocess.run([sys.executable, str(SCRIPT), "--screenshot", str(self.target)],
                              cwd=str(ROOT), env=env, capture_output=True, text=True,
                              encoding="utf-8", errors="replace")

    def commands(self):
        return [line for line in self.log.read_text(encoding="utf-8").splitlines() if line.strip()]

    def assertFailedInWords(self, r):
        self.assertEqual(1, r.returncode, r.stderr)
        self.assertNotIn("Traceback", r.stderr)
        self.assertNotIn("screenshot", r.stdout)
        self.assertIn("could not pull", r.stderr)
        self.assertIn(str(self.target), r.stderr)

    def test_a_dropped_connection_leaves_the_earlier_picture_as_it_was(self):
        self.target.write_bytes(OLD)
        r = self.run_screenshot("drop")
        self.assertFailedInWords(r)
        self.assertIn("ConnectionResetError", r.stderr)
        self.assertIn(f"{self.target} was left as it was", r.stderr)
        self.assertEqual(OLD, self.target.read_bytes(), "half a capture must not replace the whole picture")
        self.assertEqual(["after.png", "picture.png", "shell.log"], sorted(os.listdir(self.folder)),
                         "no part file is left beside it")

    def test_a_dropped_connection_leaves_no_half_file_behind(self):
        r = self.run_screenshot("drop")
        self.assertFailedInWords(r)
        self.assertIn(f"{self.target} was not written", r.stderr)
        self.assertEqual(["picture.png", "shell.log"], sorted(os.listdir(self.folder)),
                         "a failed capture must not leave a 0-byte or half-written file")

    def test_a_picture_the_tv_does_not_have_is_answered_the_same_way(self):
        self.target.write_bytes(OLD)
        r = self.run_screenshot("missing")
        self.assertFailedInWords(r)
        self.assertIn("No such file or directory", r.stderr)
        self.assertEqual(OLD, self.target.read_bytes())

    def test_the_capture_is_removed_from_the_tv_even_when_the_pull_failed(self):
        self.run_screenshot("drop")
        removed = [c for c in self.commands() if c.startswith("rm -f ") and "screencap" not in c]
        self.assertTrue(removed, f"the capture is left on the TV: {self.commands()}")

    def test_an_error_that_is_not_the_tvs_keeps_its_traceback_and_the_picture(self):
        self.target.write_bytes(OLD)
        r = self.run_screenshot("bug")
        self.assertNotEqual(0, r.returncode)
        self.assertIn("Traceback", r.stderr, "a bug in the client shows where it is")
        self.assertEqual(OLD, self.target.read_bytes())

    def test_a_capture_that_works_still_prints_its_one_line(self):
        r = self.run_screenshot("ok")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual([f"screenshot {self.target} ({len(PNG)} bytes)"], r.stdout.splitlines())
        self.assertEqual(PNG, self.target.read_bytes())
        self.assertEqual(["after.png", "picture.png", "shell.log"], sorted(os.listdir(self.folder)))
        removed = [c for c in self.commands() if c.startswith("rm -f ") and "screencap" not in c]
        self.assertTrue(removed, f"the capture is left on the TV: {self.commands()}")


if __name__ == "__main__":
    unittest.main()

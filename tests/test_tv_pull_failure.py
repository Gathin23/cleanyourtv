"""tools/tv.py --pull leaves the local file as it was when the pull fails.

adb_shell 0.4.4 opens the local path for writing before it asks the TV for the file, so a pull of a path
the TV does not have (AdbCommandFailureException: "No such file or directory") or one the TV dropped
halfway truncated the file that was already there to 0 bytes, or left a 0-byte file where there was
none, and then ended in a traceback. A `--pull /sdcard/notes.txt work/notes.txt` with a typo in the
remote path cost the reader the copy they had.

The pull now goes into a file of its own beside the target, which replaces the target only once the
pull is done; a failed pull removes it, says what failed in words and exits 1. The runs here use a stub
adb_shell (as tests/test_tv_transfer_paths.py does) that opens the local file the way the library does
and then fails the way the library does.

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
    """Pulls the way adb_shell 0.4.4 does: the local file is opened for writing first, then the TV is
    asked. STUB_PULL picks the TV's answer: ok, missing (no such file), drop (the connection resets
    after part of the file) or bug (an error that is not the TV's)."""

    def __init__(self, host, port, **kwargs):
        pass

    def connect(self, **kwargs):
        pass

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
            stream.write(b"new contents from the TV")

    def shell(self, command, **kwargs):
        return ""

    def close(self):
        pass
'''
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"
OLD = b"the copy that was already here"


class PullFailureTest(unittest.TestCase):
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
        # A stock ADB key in the temporary HOME, so neither tools/adbkey nor the real ~/.android is used.
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
        self.target = self.folder / "notes.txt"

    def run_pull(self, how):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH",
                                                                "TV_ADB_BINARY")}
        env["HOME"] = str(self.home)
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["PYTHONPATH"] = str(self.base)
        env["TV_HOST"] = "192.0.2.1"     # TEST-NET-1: the stub answers, so no socket is opened
        env["STUB_PULL"] = how
        return subprocess.run([sys.executable, str(SCRIPT), "--pull", "/sdcard/notes.txt", str(self.target)],
                              cwd=str(ROOT), env=env, capture_output=True, text=True)

    def assertFailedInWords(self, r):
        self.assertEqual(1, r.returncode, r.stderr)
        self.assertNotIn("Traceback", r.stderr)
        self.assertIn("/sdcard/notes.txt", r.stderr)
        self.assertNotIn("pulled", r.stdout)

    def test_a_missing_file_on_the_tv_leaves_the_local_copy_as_it_was(self):
        self.target.write_bytes(OLD)
        r = self.run_pull("missing")
        self.assertFailedInWords(r)
        self.assertIn("No such file or directory", r.stderr)
        self.assertIn(f"{self.target} was left as it was", r.stderr)
        self.assertEqual(OLD, self.target.read_bytes(), "a failed pull must not empty the file that was there")
        self.assertEqual(["notes.txt"], os.listdir(self.folder), "nothing is left beside it")

    def test_a_missing_file_on_the_tv_leaves_no_empty_file_behind(self):
        r = self.run_pull("missing")
        self.assertFailedInWords(r)
        self.assertIn(f"{self.target} was not written", r.stderr)
        self.assertEqual([], os.listdir(self.folder), "a failed pull must not leave a 0-byte file")

    def test_a_dropped_connection_leaves_the_local_copy_as_it_was(self):
        self.target.write_bytes(OLD)
        r = self.run_pull("drop")
        self.assertFailedInWords(r)
        self.assertIn("ConnectionResetError", r.stderr)
        self.assertEqual(OLD, self.target.read_bytes(), "half a file must not replace the whole one")
        self.assertEqual(["notes.txt"], os.listdir(self.folder))

    def test_an_error_that_is_not_the_tvs_keeps_its_traceback_and_the_file(self):
        self.target.write_bytes(OLD)
        r = self.run_pull("bug")
        self.assertNotEqual(0, r.returncode)
        self.assertIn("Traceback", r.stderr, "a bug in the client shows where it is")
        self.assertEqual(OLD, self.target.read_bytes())
        self.assertEqual(["notes.txt"], os.listdir(self.folder))

    def test_a_pull_that_works_replaces_the_file(self):
        self.target.write_bytes(OLD)
        r = self.run_pull("ok")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn(f"pulled /sdcard/notes.txt -> {self.target}", r.stdout)
        self.assertEqual(b"new contents from the TV", self.target.read_bytes())
        self.assertEqual(["notes.txt"], os.listdir(self.folder))

    def test_a_pull_that_works_writes_a_new_file(self):
        r = self.run_pull("ok")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual(b"new contents from the TV", self.target.read_bytes())
        self.assertEqual(["notes.txt"], os.listdir(self.folder))


if __name__ == "__main__":
    unittest.main()

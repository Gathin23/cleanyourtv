"""tools/tv.py --push answers a transfer the TV refused in words, not with a traceback.

adb_shell's push() opens the local file, writes it to the TV and closes the sync session in one call,
so every way a push can fail left main() as a stack trace: PushFailedError for a path the TV will not
write ("b'No such file or directory'" - a read-only mount, a folder, a name the shell user does not
own), AdbConnectionError for a transport that went, and NativeAdbError from the Google ADB backend.
Exit 1 was this client's code for a transfer that failed, but nothing named the file, the path or what
the TV said, and a reader with a traceback cannot tell "the TV refused this path" from a bug in tv.py.

--pull has answered its failures this way since #3191. --push now says the same thing: which local
file, which path on the TV, what stopped it, and that the file there may be incomplete or missing --
a push that stopped part-way can leave a cut-short file behind, so the message does not claim the old
one is still there. An error that is not the transport's (a bug in the client) keeps its traceback.

The runs here use a stub adb_shell (as tests/test_tv_pull_failure.py does) that fails the way the
library does; STUB_PUSH picks the answer.

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


class DevicePathInvalidError(Exception):
    pass


class PushFailedError(Exception):
    pass


class TcpTimeoutException(Exception):
    pass
'''
STUB_DEVICE = '''\
import os

from adb_shell.exceptions import AdbCommandFailureException, AdbConnectionError, PushFailedError


class AdbDeviceTcp:
    """Pushes the way adb_shell 0.4.4 does: one call, raising where the library raises. STUB_PUSH
    picks the TV's answer: refused (the TV's own bytes), denied (a FAIL packet), drop (the transport
    went), bug (an error that is not the transport's) or ok."""

    def __init__(self, host, port, **kwargs):
        pass

    def connect(self, **kwargs):
        pass

    def push(self, local, remote, **kwargs):
        how = os.environ.get("STUB_PUSH", "ok")
        if how == "refused":
            raise PushFailedError(b"No such file or directory")
        if how == "denied":
            raise AdbCommandFailureException(
                "adb: error: failed to copy 'app-release.apk' to '/system/app/app-release.apk': "
                "remote Permission denied")
        if how == "drop":
            raise AdbConnectionError("Connection reset by peer")
        if how == "bug":
            raise RuntimeError("a bug in the client")

    def pull(self, remote, local, **kwargs):
        raise AssertionError("--push never pulls")

    def shell(self, command, **kwargs):
        return ""

    def reboot(self, **kwargs):
        pass

    def close(self):
        pass
'''
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"


class PushFailureTest(unittest.TestCase):
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
        self.local = self.folder / "app-release.apk"
        self.local.write_bytes(b"an apk")
        self.remote = "/system/app/app-release.apk"

    def run_push(self, how):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH",
                                                                "TV_ADB_BINARY")}
        env["HOME"] = str(self.home)
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["PYTHONPATH"] = str(self.base)
        env["TV_HOST"] = "192.0.2.1"     # TEST-NET-1: the stub answers, so no socket is opened
        env["STUB_PUSH"] = how
        return subprocess.run([sys.executable, str(SCRIPT), "--push", str(self.local), self.remote],
                              cwd=str(ROOT), env=env, capture_output=True, text=True)

    def assertFailedInWords(self, r):
        self.assertEqual(1, r.returncode, r.stderr)
        self.assertNotIn("Traceback", r.stderr)
        self.assertIn(f"could not push {self.local} to {self.remote}", r.stderr)
        self.assertIn(f"{self.remote} may be incomplete or missing", r.stderr)
        self.assertNotIn("pushed", r.stdout, "a failed push must not read as one that worked")

    def test_a_path_the_tv_will_not_write_names_the_tv_s_own_words(self):
        r = self.run_push("refused")
        self.assertFailedInWords(r)
        # PushFailedError carries the answer as bytes; it is decoded, not printed as b'...'.
        self.assertIn("PushFailedError: No such file or directory", r.stderr)
        self.assertNotIn("b'", r.stderr)

    def test_a_fail_packet_from_the_tv_is_named(self):
        r = self.run_push("denied")
        self.assertFailedInWords(r)
        self.assertIn("Permission denied", r.stderr)

    def test_a_transport_that_went_is_named(self):
        r = self.run_push("drop")
        self.assertFailedInWords(r)
        self.assertIn("AdbConnectionError: Connection reset by peer", r.stderr)

    def test_an_error_that_is_not_the_transports_keeps_its_traceback(self):
        r = self.run_push("bug")
        self.assertNotEqual(0, r.returncode)
        self.assertIn("Traceback", r.stderr, "a bug in the client shows where it is")
        self.assertIn("a bug in the client", r.stderr)

    def test_a_push_that_works_still_says_so(self):
        r = self.run_push("ok")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn(f"pushed {self.local} -> {self.remote}", r.stdout)
        self.assertEqual("", r.stderr)


if __name__ == "__main__":
    unittest.main()

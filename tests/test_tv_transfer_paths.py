"""tools/tv.py --push/--pull judge their two paths before the TV is opened.

adb_shell opens the local path itself, so a path typed from the wrong folder (the add-on READMEs hand
out `tv.py --push app/build/outputs/apk/release/app-release.apk ...`, which does not exist until the
build has run, and they run from wherever the owner stands) ended in a FileNotFoundError traceback -
exit 1, which is this client's code for a screenshot that came back empty, and not a word saying the
run had sent nothing. `--push <file> ""` and `--pull "" <local>` were adb_shell's
DevicePathInvalidError out of the same place, and `--pull <remote> <a folder>` was IsADirectoryError
after the TV had already been asked for the file.

All of them are wrong before anything can be sent, so they are now answered in check_arity(), which
runs before connect(): exit 2, the path named, nothing on stdout, no traceback. The runs here use a
stub adb_shell (as tests/test_tv_help.py does) that fails the way the library does and records whether
a device was opened, so "nothing was sent to the TV" is observed rather than assumed.

A folder stays a valid --push argument (adb_shell sends the files inside it) and a pull into a name in
a folder that is there still runs; both are pinned here so the refusal cannot start refusing a run
that works.

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
STUB_DEVICE = '''\
import os


class DevicePathInvalidError(Exception):
    pass


class AdbDeviceTcp:
    """Stands in for adb_shell's client, failing where the real one does.

    AdbDevice.push refuses an empty device path, walks a folder and otherwise opens the local file;
    AdbDevice.pull refuses an empty device path and opens the local file for writing. Every call is
    recorded, and so is the connection, so a test can tell a refusal from a transfer.
    """

    def __init__(self, host, port, **kwargs):
        with open(os.environ["STUB_MARK"], "w") as f:
            f.write(f"{host}:{port}")

    def connect(self, **kwargs):
        pass

    def _note(self, line):
        with open(os.environ["STUB_CMDS"], "a") as f:
            f.write(line + "\\n")

    def push(self, local, remote, **kwargs):
        if not remote:
            raise DevicePathInvalidError("Cannot push to an empty device path")
        self._note(f"push {local} -> {remote}")
        if os.path.isdir(local):
            for name in os.listdir(local):
                with open(os.path.join(local, name), "rb"):
                    pass
            return
        with open(local, "rb"):
            pass

    def pull(self, remote, local, **kwargs):
        if not remote:
            raise DevicePathInvalidError("Cannot pull from an empty device path")
        self._note(f"pull {remote} -> {local}")
        with open(local, "wb") as f:
            f.write(b"x")

    def shell(self, command, **kwargs):
        self._note(f"shell {command}")
        return ""

    def reboot(self):
        pass

    def close(self):
        pass
'''
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"


def stub_tree(base):
    """A package tree that answers tv.py's three adb_shell imports."""
    (base / "adb_shell" / "auth").mkdir(parents=True)
    (base / "adb_shell" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "adb_device.py").write_text(STUB_DEVICE, encoding="utf-8")
    (base / "adb_shell" / "auth" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "auth" / "keygen.py").write_text(STUB_KEYGEN, encoding="utf-8")
    (base / "adb_shell" / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER, encoding="utf-8")
    return base


class TransferPathsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.base = stub_tree(Path(cls._tmp.name) / "stub")
        # A stock ADB key in the temporary HOME: tv.py reuses it, so neither the checkout's tools/adbkey
        # nor the real ~/.android is touched by these runs.
        home = Path(cls._tmp.name) / "home"
        (home / ".android").mkdir(parents=True)
        (home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.home = home
        # A file and a folder of one file, for the runs that must still go through.
        cls.files = Path(cls._tmp.name) / "files"
        cls.files.mkdir()
        cls.apk = cls.files / "app-release.apk"
        cls.apk.write_bytes(b"PK\x03\x04" + b"0" * 64)
        cls.folder = cls.files / "assets"
        cls.folder.mkdir()
        (cls.folder / "one.txt").write_text("one", encoding="utf-8")

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def run_tv(self, args):
        """(exit status, stdout, stderr, the device that was opened or None, the calls it was given)."""
        work = Path(tempfile.mkdtemp(dir=Path(self._tmp.name)))
        mark, calls = work / "mark", work / "calls"
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env["HOME"] = str(self.home)
        # Windows expands `~` from USERPROFILE, not HOME, so tv.py would read the real ~/.android.
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["STUB_MARK"], env["STUB_CMDS"] = str(mark), str(calls)
        env["PYTHONPATH"] = str(self.base)
        env["TV_HOST"] = "192.0.2.1"     # TEST-NET-1: the stub answers, so no socket is opened
        r = subprocess.run([sys.executable, str(SCRIPT)] + [str(a) for a in args], cwd=str(ROOT), env=env,
                           capture_output=True, text=True)
        opened = mark.read_text(encoding="utf-8") if mark.exists() else None
        sent = calls.read_text(encoding="utf-8").splitlines() if calls.exists() else []
        return r.returncode, r.stdout, r.stderr, opened, sent

    def assertRefused(self, args, *needles):
        """Exit 2, the needles in stderr, no traceback, nothing printed, and the TV never opened."""
        status, out, err, opened, sent = self.run_tv(args)
        self.assertEqual(2, status, err)
        self.assertEqual("", out)
        self.assertNotIn("Traceback", err)
        for needle in needles:
            self.assertIn(needle, err)
        self.assertIsNone(opened, "a path that cannot be used must not open the TV")
        self.assertEqual([], sent)
        return err

    def test_a_push_of_a_file_that_is_not_there_is_refused(self):
        # The add-on READMEs' command before the build has run, or typed from the wrong folder.
        self.assertRefused(["--push", "no-such.apk", "/data/local/tmp/app.apk"],
                           "cannot read no-such.apk", "No such file or directory",
                           "Nothing was sent to the TV.")

    def test_a_push_of_a_path_through_a_file_is_refused(self):
        self.assertRefused(["--push", str(self.apk) + "/inner.apk", "/data/local/tmp/app.apk"],
                           str(self.apk) + "/inner.apk", "Nothing was sent to the TV.")

    def test_a_push_with_no_local_path_is_refused(self):
        err = self.assertRefused(["--push", "", "/data/local/tmp/app.apk"],
                                 "--push needs the local file to send")
        self.assertIn("tv.py --push", err, "a wrong count or an empty argument still carries the usage")

    def test_a_push_with_no_path_on_the_tv_is_refused(self):
        # adb_shell answers this one with DevicePathInvalidError, after the connection was made.
        self.assertRefused(["--push", str(self.apk), ""], "--push needs the path on the TV to write to")

    def test_a_pull_into_a_folder_that_is_not_there_is_refused(self):
        target = Path(self._tmp.name) / "gone" / "s.png"
        err = self.assertRefused(["--pull", "/sdcard/s.png", str(target)],
                                 str(target.parent), "Nothing was pulled.")
        self.assertIn(str(target), err, "the message names the file that was asked for")

    def test_a_pull_onto_a_folder_is_refused(self):
        # adb_shell writes to the name it is given: a folder there was IsADirectoryError, after the TV
        # had already been asked for the file.
        self.assertRefused(["--pull", "/sdcard/s.png", str(self.files)],
                           f"{self.files} is a folder", "give a file name", "Nothing was pulled.")

    def test_a_pull_with_no_local_path_is_refused(self):
        self.assertRefused(["--pull", "/sdcard/s.png", ""], "--pull needs the local file to save to")

    def test_a_pull_with_no_path_on_the_tv_is_refused(self):
        self.assertRefused(["--pull", "", str(self.files / "s.png")],
                           "--pull needs the path on the TV to read")

    def test_a_pull_that_cannot_write_the_file_is_refused(self):
        if os.name == "nt":
            self.skipTest("chmod does not make a file unwritable for its owner on Windows")
        if hasattr(os, "geteuid") and os.geteuid() == 0:
            self.skipTest("root writes a read-only file, so the permission check cannot be observed here")
        target = self.files / "readonly.png"
        target.write_bytes(b"x")
        target.chmod(0o444)
        try:
            self.assertRefused(["--pull", "/sdcard/s.png", str(target)],
                               f"cannot write {target}", "Nothing was pulled.")
        finally:
            target.chmod(0o644)

    def test_a_file_is_still_pushed(self):
        status, out, err, opened, sent = self.run_tv(["--push", str(self.apk), "/data/local/tmp/app.apk"])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertEqual([f"push {self.apk} -> /data/local/tmp/app.apk"], sent)
        self.assertIn(f"pushed {self.apk} -> /data/local/tmp/app.apk", out)

    def test_a_folder_is_still_pushed(self):
        # adb_shell sends the files inside a folder, so this stays a valid argument.
        status, out, err, opened, sent = self.run_tv(["--push", str(self.folder), "/data/local/tmp/assets"])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertEqual([f"push {self.folder} -> /data/local/tmp/assets"], sent)

    def test_a_pull_to_a_name_in_a_folder_that_is_there_still_runs(self):
        target = Path(tempfile.mkdtemp(dir=Path(self._tmp.name))) / "s.png"
        status, out, err, opened, sent = self.run_tv(["--pull", "/sdcard/s.png", str(target)])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertEqual([f"pull /sdcard/s.png -> {target}"], sent)
        self.assertTrue(target.exists(), "the stub wrote the file where the run was told to")

    def test_the_judgement_runs_before_the_connection_is_made(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertLess(source.index("transfer_args(args)"), source.index("dev = connect()"),
                        "the two paths have to be judged in check_arity, above connect()")
        self.assertLess(source.index("def transfer_args"), source.index("dev = connect()"))

    def test_the_help_states_the_exit_status_the_refusals_use(self):
        # The refusals above are exit 2; the usage has to say what 2 means, since 1 is a screenshot
        # that came back empty and the two were the same code before.
        status, out, err, opened, sent = self.run_tv(["--help"])
        self.assertEqual(0, status, err)
        self.assertIn("Exit status", out)
        self.assertIn("2 it was refused before anything ran on the TV", out)
        self.assertIsNone(opened)
        self.assertEqual([], sent)


if __name__ == "__main__":
    unittest.main()

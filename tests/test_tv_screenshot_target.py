"""tools/tv.py --screenshot judges the file it saves to before it opens the TV.

PROMPT.md, AGENTS.md and CONTRIBUTING.md all hand out `--screenshot shots/01.png`, and #364 taught the
command to make the folder that is not there yet. The other shapes a destination takes were judged
nowhere, so they all came back as the answer this client keeps for the TV: `mkdir shots` followed by
`--screenshot shots` - or a name typed with its trailing separator - reached adb_shell's pull, which
writes to the name it is given, as an IsADirectoryError *after* the TV had run `screencap`: exit 1, no
PNG, and the capture left on the TV because the cleanup after the pull never ran. A path through a file
was the same shape one step earlier out of os.makedirs, and `--screenshot ""` - what
`--screenshot "$SHOT"` sends when SHOT was never set - was FileNotFoundError: ''.

All of them are wrong before anything can be captured, so they are answered now in screenshot_args(),
which check_arity() runs before connect(): exit 2, the path named, nothing on stdout, no traceback, and
no capture started for a file that cannot be written. The runs here use a stub adb_shell (as
tests/test_tv_transfer_paths.py does) that writes like the library does and records whether a device was
opened, so "nothing was captured" is observed rather than assumed.

A folder that is not there is still made (#364) and an existing PNG is still overwritten; both are
pinned here so the refusal cannot start refusing the runs the pages describe.

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
SOURCE = SCRIPT.read_text(encoding="utf-8")
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 32

STUB_DEVICE = '''\
import os


class DevicePathInvalidError(Exception):
    pass


class AdbDeviceTcp:
    """Stands in for adb_shell's client: pull writes the name it is given, as the library does."""

    def __init__(self, host, port, **kwargs):
        with open(os.environ["STUB_MARK"], "w") as f:
            f.write(f"{host}:{port}")

    def connect(self, **kwargs):
        pass

    def _note(self, line):
        with open(os.environ["STUB_CMDS"], "a") as f:
            f.write(line + "\\n")

    def push(self, local, remote, **kwargs):
        raise AssertionError("--screenshot never pushes")

    def pull(self, remote, local, **kwargs):
        if not remote:
            raise DevicePathInvalidError("Cannot pull from an empty device path")
        self._note(f"pull {remote} -> {local}")
        with open(local, "wb") as f:
            f.write(b"\\x89PNG\\r\\n\\x1a\\n" + b"0" * 32)

    def shell(self, command, read_timeout_s=None, **kwargs):
        self._note(f"shell {command}")
        return "rc=0\\n" if "screencap" in command else ""

    def reboot(self, **kwargs):
        pass

    def close(self):
        self._note("close")
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


class ScreenshotTargetTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.base = stub_tree(Path(cls._tmp.name) / "stub")
        # A stock ADB key in a temporary HOME: neither the checkout's tools/adbkey nor the real
        # ~/.android is touched by these runs.
        home = Path(cls._tmp.name) / "home"
        (home / ".android").mkdir(parents=True)
        (home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.home = home
        # A folder standing where a file name goes, and a file standing where a folder goes.
        cls.files = Path(cls._tmp.name) / "files"
        cls.files.mkdir()
        cls.shots = cls.files / "shots"
        cls.shots.mkdir()
        cls.afile = cls.files / "README.md"
        cls.afile.write_text("x", encoding="utf-8")

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def work(self):
        return Path(tempfile.mkdtemp(dir=Path(self._tmp.name)))

    def run_tv(self, args, work=None):
        """(exit status, stdout, stderr, the device that was opened or None, the calls it was given)."""
        work = work or self.work()
        mark, calls = work / "mark", work / "calls"
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env["HOME"] = str(self.home)
        # Windows expands `~` from USERPROFILE, not HOME, so tv.py would read the real ~/.android.
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["STUB_MARK"], env["STUB_CMDS"] = str(mark), str(calls)
        env["PYTHONPATH"] = str(self.base)
        env["TV_HOST"] = "192.0.2.1"     # TEST-NET-1: the stub answers, so no socket is opened
        r = subprocess.run([sys.executable, str(SCRIPT)] + [str(a) for a in args], cwd=str(work), env=env,
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
        self.assertIsNone(opened, "a destination that cannot be written must not open the TV")
        self.assertEqual([], sent, "nothing may be captured for a destination that cannot be written")
        return err

    def test_a_folder_in_place_of_the_file_is_refused(self):
        # `mkdir shots` and then `--screenshot shots`, the two steps an owner takes in that order.
        self.assertRefused(["--screenshot", str(self.shots)],
                           f"{self.shots} is a folder", "give a file name",
                           str(self.shots / "01-home.png"), "Nothing was captured.")

    def test_a_folder_with_its_trailing_separator_is_refused(self):
        self.assertRefused(["--screenshot", str(self.shots) + os.sep], f"{self.shots}{os.sep} is a folder")

    def test_an_empty_destination_is_a_usage_error(self):
        # `--screenshot "$SHOT"` with SHOT never set: adb_shell's pull answered FileNotFoundError: ''.
        err = self.assertRefused(["--screenshot", ""], "--screenshot needs the file to save to")
        self.assertIn("tv.py --screenshot", err, "a missing argument still carries the usage")

    def test_a_path_through_a_file_is_refused(self):
        target = Path(str(self.afile)) / "inner.png"
        self.assertRefused(["--screenshot", str(target)], f"{self.afile} is a file, not a folder",
                           str(target), "Nothing was captured.")

    def test_a_folder_that_cannot_be_created_is_refused(self):
        if os.name == "nt":
            self.skipTest("chmod does not make a folder unwritable for its owner on Windows")
        if hasattr(os, "geteuid") and os.geteuid() == 0:
            self.skipTest("root writes into a read-only folder, so the refusal cannot be observed here")
        blocked = self.files / "blocked"
        blocked.mkdir()
        os.chmod(blocked, 0o500)
        try:
            target = blocked / "new" / "01-home.png"
            self.assertRefused(["--screenshot", str(target)], str(blocked / "new"), "Nothing was captured.")
        finally:
            os.chmod(blocked, 0o700)

    def test_a_folder_that_is_not_there_is_still_created(self):
        # #364: this is the shape the command has to fix for itself, not refuse.
        work = self.work()
        local = work / "shots" / "00-stock-home.png"
        status, out, err, opened, sent = self.run_tv(["--screenshot", str(local)], work)
        self.assertEqual(0, status, err)
        self.assertEqual(PNG, local.read_bytes())
        self.assertIn(f"screenshot {local} ({len(PNG)} bytes)", out)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertTrue(any("screencap" in call for call in sent), "the capture still runs")

    def test_an_existing_png_is_still_overwritten(self):
        work = self.work()
        local = work / "before.png"
        local.write_bytes(b"old")
        status, out, err, opened, sent = self.run_tv(["--screenshot", str(local)], work)
        self.assertEqual(0, status, err)
        self.assertEqual(PNG, local.read_bytes())

    def test_the_keys_are_still_pressed_into_a_folder_that_is_created(self):
        work = self.work()
        local = work / "deep" / "after.png"
        status, out, err, opened, sent = self.run_tv(["--screenshot", str(local), "DPAD_DOWN"], work)
        self.assertEqual(0, status, err)
        self.assertIn("shell input keyevent KEYCODE_DPAD_DOWN", sent)
        self.assertTrue(local.exists())

    def test_the_refusal_sits_where_the_tv_has_not_been_opened(self):
        # check_arity() judges --screenshot's arguments, and main() calls it before dev = connect().
        self.assertIn("screenshot_args(args[1:])", SOURCE)
        args_body = SOURCE.split("def screenshot_args(rest):", 1)[1].split("\ndef ", 1)[0]
        self.assertIn("screenshot_target(local)", args_body,
                      "screenshot_args() has to judge the destination it returns")
        target_body = SOURCE.split("def screenshot_target(local):", 1)[1].split("\ndef ", 1)[0]
        self.assertIn("os.path.isdir(local)", target_body, "a folder in place of the file name")
        self.assertIn("os.makedirs(folder, exist_ok=True)", target_body,
                      "a folder that is not there is still made (#364)")
        self.assertLess(SOURCE.index("    check_arity(args)"), SOURCE.index("        dev = connect()"),
                        "the arguments have to be judged before the TV is opened")


if __name__ == "__main__":
    unittest.main()

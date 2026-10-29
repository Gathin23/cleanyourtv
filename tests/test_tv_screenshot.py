"""tools/tv.py --screenshot: capture on the TV, pull the file, and say when it is empty.

PROMPT.md tells the agent to look at a screenshot after every change, and CONTRIBUTING.md asks for
one in a launcher PR, but tools/tv.py had no screenshot command, so every agent wrote its own. In
#364 (CHiQ AI PONT, Android 11) that hand-rolled helper crashed on a `shots/` folder that did not
exist yet, and the capture step stopped the whole prompt. The command therefore has to create the
folder, run `screencap` on the TV and pull the PNG (never `exec-out`, which returns a broken file on
some MediaTek sets), leave nothing behind on the TV, and name the 0-byte case for what it is: Live
TV and HDMI inputs capture empty.

It can also press remote keys first (#560), so one command shows what a key did: key names are checked
before anything reaches the TV, since they end up in a shell command there.

The TV itself is faked, so these run anywhere adb_shell is installed or not.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 32


class FakeDevice:
    """Stands in for adb_shell's AdbDeviceTcp and records what tv.py asked it to do."""

    def __init__(self, capture=PNG, screencap_says="rc=0\n"):
        self.capture = capture
        self.screencap_says = screencap_says
        self.calls = []
        self.closed = False

    def shell(self, command, read_timeout_s=None):
        self.calls.append(("shell", command))
        return self.screencap_says if "screencap" in command else ""

    def pull(self, remote, local, transport_timeout_s=None, read_timeout_s=None):
        self.calls.append(("pull", remote, local))
        Path(local).write_bytes(self.capture)

    def push(self, local, remote, transport_timeout_s=None, read_timeout_s=None):
        self.calls.append(("push", local, remote))

    def connect(self, **kwargs):
        self.calls.append(("connect",))

    def close(self):
        self.closed = True


def load_tv():
    """Import tools/tv.py with adb_shell stubbed: these tests never open a socket."""
    stubs = {}
    for name in ("adb_shell", "adb_shell.auth"):
        stubs[name] = types.ModuleType(name)
    device = types.ModuleType("adb_shell.adb_device")
    device.AdbDeviceTcp = object
    keygen = types.ModuleType("adb_shell.auth.keygen")
    keygen.keygen = lambda path: None
    signer = types.ModuleType("adb_shell.auth.sign_pythonrsa")
    signer.PythonRSASigner = lambda pub, priv: None
    stubs.update({"adb_shell.adb_device": device, "adb_shell.auth.keygen": keygen,
                  "adb_shell.auth.sign_pythonrsa": signer})
    spec = importlib.util.spec_from_file_location("cyt_tv", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    os.environ.setdefault("TV_HOST", "192.0.2.1")   # TEST-NET-1: nothing ever answers there
    with mock.patch.dict(sys.modules, stubs):
        spec.loader.exec_module(module)
    return module


TV = load_tv()


class ScreenshotTest(unittest.TestCase):
    def run_tv(self, args, capture=PNG, screencap_says="rc=0\n"):
        device = FakeDevice(capture, screencap_says)
        self.sleeps = []
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(TV, "AdbDeviceTcp", return_value=device), \
                mock.patch.object(TV, "get_signer", return_value=None), \
                mock.patch.object(TV.time, "sleep", side_effect=self.sleeps.append), \
                mock.patch.object(sys, "argv", ["tv.py", *args]), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                code = TV.main()
            except SystemExit as e:
                code = e.code
        return code, out.getvalue(), err.getvalue(), device

    def test_creates_the_folder_and_pulls_the_png(self):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "shots" / "00-stock-home.png"   # the folder does not exist yet
            code, out, err, device = self.run_tv(["--screenshot", str(local)])
            self.assertEqual(code, 0, err)
            self.assertEqual(local.read_bytes(), PNG)
            self.assertIn(f"screenshot {local} ({len(PNG)} bytes)", out)

    def test_capture_runs_on_the_tv_then_pull_then_cleanup(self):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "home.png"
            _, _, err, device = self.run_tv(["--screenshot", str(local)])
            self.assertEqual(err, "")
            steps = [c for c in device.calls if c[0] != "connect"]
            self.assertEqual([c[0] for c in steps], ["shell", "pull", "shell"])
            self.assertEqual(steps[0][1], f"rm -f {TV.SHOT_REMOTE}; screencap -p {TV.SHOT_REMOTE}; echo rc=$?")
            self.assertEqual(steps[1][1], TV.SHOT_REMOTE)
            self.assertEqual(steps[2][1], f"rm -f {TV.SHOT_REMOTE}")
            self.assertTrue(device.closed)

    def test_empty_capture_names_the_input_and_fails(self):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "live-tv.png"
            code, out, err, _ = self.run_tv(["--screenshot", str(local)], capture=b"")
            self.assertEqual(code, 1)
            self.assertIn("0 bytes", err)
            self.assertIn("Live TV and HDMI", err)
            self.assertTrue(local.exists(), "the empty file stays for the user to look at")

    def test_netflix_style_capture_exit_1_with_no_bytes_is_the_empty_case(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, _, err, _ = self.run_tv(["--screenshot", str(Path(tmp) / "netflix.png")],
                                          capture=b"", screencap_says="rc=1\n")
            self.assertEqual(code, 1)
            self.assertIn("0 bytes", err)
            self.assertIn("Netflix", err)

    def test_failed_screencap_says_so_and_pulls_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "home.png"
            code, _, err, device = self.run_tv(["--screenshot", str(local)],
                                               screencap_says="screencap: not found\nrc=127\n")
            self.assertEqual(code, 1)
            self.assertIn("screencap failed on the TV (exit status 127): screencap: not found", err)
            self.assertNotIn("pull", [c[0] for c in device.calls])
            self.assertEqual(device.calls[-1], ("shell", f"rm -f {TV.SHOT_REMOTE}"))

    def test_a_damaged_pull_is_not_passed_off_as_a_png(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, _, err, _ = self.run_tv(["--screenshot", str(Path(tmp) / "home.png")], capture=b"garbage")
            self.assertEqual(code, 1)
            self.assertIn("is not a PNG", err)

    def test_keys_are_pressed_in_order_before_the_capture(self):
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "right.png"
            code, _, err, device = self.run_tv(["--screenshot", str(local), "HOME", "KEYCODE_DPAD_RIGHT", "83"])
            self.assertEqual(code, 0, err)
            shells = [c[1] for c in device.calls if c[0] == "shell"]
            self.assertEqual(shells[:3], ["input keyevent KEYCODE_HOME", "input keyevent KEYCODE_DPAD_RIGHT",
                                          "input keyevent 83"])
            self.assertIn("screencap", shells[3])
            self.assertEqual(self.sleeps, [TV.KEY_GAP_S, TV.KEY_GAP_S, 1.0])

    def test_wait_sets_the_pause_after_the_last_key(self):
        with tempfile.TemporaryDirectory() as tmp:
            code, _, err, _ = self.run_tv(["--screenshot", str(Path(tmp) / "a.png"), "BACK", "--wait", "2.5"])
            self.assertEqual(code, 0, err)
            self.assertEqual(self.sleeps, [2.5])

    def test_no_keys_means_no_pause(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.run_tv(["--screenshot", str(Path(tmp) / "a.png")])
            self.assertEqual(self.sleeps, [])

    def test_bad_key_names_and_waits_never_reach_the_tv(self):
        for args in (["--screenshot", "a.png", "home"], ["--screenshot", "a.png", "HOME;reboot"],
                     ["--screenshot", "a.png", "--wait", "-1"], ["--screenshot", "a.png", "--wait", "soon"]):
            with self.subTest(args=args):
                code, _, err, device = self.run_tv(args)
                self.assertEqual(code, 2)
                self.assertIn("usage:", err)
                self.assertEqual(device.calls, [], "nothing may reach the TV")

    def test_a_flag_without_its_argument_is_a_usage_error_not_a_tv_command(self):
        for args in (["--screenshot"], ["--push", "only-one"], ["--pull"]):
            with self.subTest(args=args):
                code, _, err, device = self.run_tv(args)
                self.assertEqual(code, 2)
                self.assertIn("usage:", err)
                self.assertEqual(device.calls, [], "nothing may reach the TV")

    def test_plain_shell_command_is_unchanged(self):
        code, _, _, device = self.run_tv(["input keyevent KEYCODE_DPAD_DOWN"])
        self.assertEqual(code, 0)
        self.assertEqual([c for c in device.calls if c[0] != "connect"],
                         [("shell", "input keyevent KEYCODE_DPAD_DOWN")])


if __name__ == "__main__":
    unittest.main()

"""tools/tv.py when the reader stops reading the TV's answer: a pager, `head`, `less` quit at the first screen.

The answer a command prints here is the TV's own output, and for `dumpsys` or `logcat -d` it is longer
than the pipe the reader and this tool share, so the write fails with BrokenPipeError once the reader
has gone. That used to leave the reader a traceback and exit 1 - the status the docstring keeps for a
command whose answer was lost when the TV dropped the connection, or a screenshot, pull or push that
failed, none of which happened: the command had already run on the TV. The run now ends at 0, the
status a command that ran is given, and the interpreter's last flush at exit goes to /dev/null so the
stderr of a run that went fine does not carry "Exception ignored in: ... BrokenPipeError".

`tv.py --help` is the one command here that needs neither the venv nor a TV, which is what makes the
closed pipe measurable without one: the first of these cases drives main() with a writer that raises,
the rest run the script as a subprocess whose stdout is a pipe with its read end already closed.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import errno
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import types
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"
# What `dumpsys package` answers on a set with a few hundred packages: more than the 64 KiB a pipe holds,
# which is the size at which the reader has to have gone before the write can fail.
LONG_ANSWER = "Package [com.example.app] (a1b2c3):\n" * 4000


class ClosedReader(io.StringIO):
    """Stand-in for stdout after the reader went away: the first write is the pager's BrokenPipeError."""

    def write(self, text):
        raise BrokenPipeError(32, "Broken pipe")

    def fileno(self):
        # A pipe whose read end is closed has no descriptor this process can flush to; the tool's guard
        # has to cope with that rather than raise out of the run.
        raise io.UnsupportedOperation("fileno")


class FlushFails(io.StringIO):
    """Stand-in for stdout whose buffered answer is only refused at the last flush."""

    def __init__(self, error):
        super().__init__()
        self.error = error

    def flush(self):
        raise self.error

    def fileno(self):
        raise io.UnsupportedOperation("fileno")


class FakeDevice:
    """Stands in for adb_shell's AdbDeviceTcp and answers one shell command with a long answer."""

    def __init__(self, answer):
        self.answer = answer
        self.closed = False

    def shell(self, command, read_timeout_s=None):
        return self.answer

    def connect(self, **kwargs):
        pass

    def close(self):
        self.closed = True


def load_tv():
    """Import tools/tv.py with adb_shell stubbed: these cases never open a socket."""
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
    spec = importlib.util.spec_from_file_location("cyt_tv_reader", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    os.environ.setdefault("TV_HOST", "192.0.2.1")   # TEST-NET-1: nothing ever answers there
    with mock.patch.dict(sys.modules, stubs):
        spec.loader.exec_module(module)
    return module


TV = load_tv()


class TheAnswerTest(unittest.TestCase):
    def run_tv(self, args, stdout):
        device = FakeDevice(LONG_ANSWER)
        err = io.StringIO()
        with mock.patch.object(TV, "AdbDeviceTcp", return_value=device), \
                mock.patch.object(TV, "get_signer", return_value=None), \
                mock.patch.object(sys, "argv", ["tv.py", *args]), \
                mock.patch.object(sys, "stdout", stdout), \
                contextlib.redirect_stderr(err):
            try:
                code = TV.main()
            except SystemExit as e:
                code = e.code
        return code, err.getvalue(), device

    def test_a_reader_who_stops_reading_the_answer_gets_the_status_of_a_run_that_worked(self):
        code, err, device = self.run_tv(["dumpsys package com.example.app"], ClosedReader())
        self.assertEqual(code, 0, f"a closed pipe answered {code} with {err!r} on stderr")
        self.assertEqual(err, "")
        self.assertEqual(device.answer, LONG_ANSWER)

    def test_a_refusal_is_still_a_refusal_whatever_the_reader_did(self):
        # The guard around the printing may not swallow the statuses the docstring keeps for a run that
        # was refused or failed: --push with too few words is 2, and that is answered before any write.
        code, err, _ = self.run_tv(["--push", "only-one-word"], ClosedReader())
        self.assertEqual(code, 2)
        self.assertIn("--push", err)


class TheFinalFlushTest(unittest.TestCase):
    """main() with _run patched: what the last flush does to the status the run itself earned."""

    def run_main(self, stdout, run_result=0, windows=False):
        err = io.StringIO()
        stopped = mock.Mock(return_value=0)
        run = mock.Mock(side_effect=run_result) if isinstance(run_result, BaseException)             else mock.Mock(return_value=run_result)
        with mock.patch.object(TV, "_run", run),                 mock.patch.object(TV, "WINDOWS", windows),                 mock.patch.object(TV, "stopped_reading", stopped),                 mock.patch.object(sys, "stdout", stdout),                 contextlib.redirect_stderr(err):
            code = TV.main()
        return code, err.getvalue(), stopped

    def test_a_failed_run_keeps_its_status_when_the_final_flush_finds_the_reader_gone(self):
        code, err, stopped = self.run_main(FlushFails(BrokenPipeError(32, "Broken pipe")), run_result=1)
        self.assertEqual(code, 1)
        self.assertEqual(err, "")
        stopped.assert_called_once_with()

    def test_on_windows_an_einval_at_the_final_flush_is_the_reader_gone(self):
        code, err, stopped = self.run_main(FlushFails(OSError(errno.EINVAL, "Invalid argument")),
                                           windows=True)
        self.assertEqual(code, 0)
        self.assertNotIn("Traceback", err)
        stopped.assert_called_once_with()

    def test_on_windows_an_einval_from_a_command_is_not_turned_into_0(self):
        with self.assertRaises(OSError) as raised:
            self.run_main(io.StringIO(), run_result=OSError(errno.EINVAL, "Invalid argument"), windows=True)
        self.assertEqual(raised.exception.errno, errno.EINVAL)

    def test_off_windows_an_einval_at_the_final_flush_is_not_swallowed(self):
        with self.assertRaises(OSError) as raised:
            self.run_main(FlushFails(OSError(errno.EINVAL, "Invalid argument")), windows=False)
        self.assertEqual(raised.exception.errno, errno.EINVAL)


class TheUsageTest(unittest.TestCase):
    """The one command that needs neither the venv nor a TV, run as the script a reader types."""

    def run_tv(self, stdout):
        return subprocess.run([sys.executable, "-I", str(SCRIPT), "--help"],
                              stdout=stdout, stderr=subprocess.PIPE, timeout=120)

    def test_the_usage_ends_the_run_at_0_when_the_reader_is_gone(self):
        read_fd, write_fd = os.pipe()
        os.close(read_fd)      # the reader is gone before the first byte
        try:
            proc = self.run_tv(write_fd)
        finally:
            os.close(write_fd)
        said = proc.stderr.decode("utf-8", "replace")
        self.assertEqual(proc.returncode, 0, f"a closed pipe answered {proc.returncode} with {said}")
        self.assertNotIn("BrokenPipeError", said)
        self.assertNotIn("Traceback", said)

    def test_the_usage_is_still_printed_to_a_reader_who_stays(self):
        proc = self.run_tv(subprocess.PIPE)
        self.assertEqual(proc.returncode, 0, proc.stderr.decode("utf-8", "replace"))
        self.assertIn(b"tv.py --probe", proc.stdout)
        self.assertIn(b"Exit status: 0 the command ran", proc.stdout)


if __name__ == "__main__":
    unittest.main()

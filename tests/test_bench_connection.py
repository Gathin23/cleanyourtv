"""tools/bench.py keeps one ADB connection for the whole run, and asks the TV only for what it needs.

It used to start tools/tv.py for every shell command: a new Python, a new TCP connection and a new
ADB handshake, several times per key, plus the whole main log pulled back for every key (about
100 MB on a TCL C755, 41 s a read). Through a
relay one full run took over 45 minutes. Now the TV class connects once with tools/tv.py's client
and reuses it, reopening it once if it drops; only the log's newest lines are read, filtered on the
TV, and not at all once the first keys show the TV doesn't log them; and the key loop looks up Home once. --host also has to reach the client: the old subprocesses only read TV_HOST. It reaches it as TV_HOST/TV_PORT, and the target the run keeps is the one tools/tv.py made of those two (it splits `host:port` and checks the range), not the arguments again.

A TV that cannot be reached is the other half, and the run's first connect is also the one it reopens
after a drop: tools/tv.py words a refused port, a Wireless debugging port and an address nothing answers
on in connect_error(), and bench.py let the exception out as a traceback with exit 1 - naming neither the
address nor that no result.json was written, and 1 is also the status a finished run whose result.json
could not be written returns, so nothing told the two apart. The sentence is printed now, and a failure
tv.py has nothing to add to (a reset in the handshake, a key file it cannot read) keeps its traceback,
which names the file.

The TV and tools/tv.py are faked, so these run without either; one test runs the real pair against a port
on 127.0.0.1 that nothing listens on.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_tv_connect_errors as connect_errors  # noqa: E402  (a module, so its tests do not run twice)

from tools import bench

ROOT = Path(__file__).resolve().parents[1]


class FakeDevice:
    def __init__(self, answers=None, fail_first=False):
        self.answers = answers or {}
        self.fail_first = fail_first
        self.commands = []
        self.closed = False

    def shell(self, command, read_timeout_s=None, decode=True):
        if self.fail_first:
            self.fail_first = False
            raise ConnectionResetError("TV went to standby")
        self.commands.append(command)
        return next((v for k, v in self.answers.items() if k in command), b"")

    def close(self):
        self.closed = True


def fake_client(devices):
    """A stand-in for tools/tv.py: connect() hands out the given devices in turn.

    HOST and PORT stand for the target the real client resolves from TV_HOST/TV_PORT when bench.py
    imports it; this one has no parser, so the environment bench.py sets for it is what a test reads.
    connect_error() is the real client's too: None for an error it has nothing to add to, which is
    what keeps such an error's traceback.
    """
    module = types.ModuleType("tv")
    module.HOST, module.PORT = None, None
    module.connects = 0

    def connect():
        module.connects += 1
        return devices[module.connects - 1]
    module.connect = connect
    setattr(module, "connect_error", lambda error: None)
    return module


class OneConnectionTest(unittest.TestCase):
    def tv_with(self, *devices):
        client = fake_client(list(devices))
        patcher = mock.patch.dict(sys.modules, {"tv": client})
        patcher.start()
        self.addCleanup(patcher.stop)
        return bench.TV("192.0.2.7", 15555), client

    def test_many_commands_share_one_connection(self):
        tv, client = self.tv_with(FakeDevice())
        for _ in range(10):
            tv.shell("input keyevent 22")
        self.assertEqual(client.connects, 1)

    def test_host_and_port_from_the_command_line_reach_the_client(self):
        tv, client = self.tv_with(FakeDevice())
        tv.shell("true")
        # bench.py hands the command line to the client through TV_HOST/TV_PORT, the two the real
        # tools/tv.py reads when it is imported. The fake client resolves neither, so the pair is
        # what this can check; the read-back is the test below.
        self.assertEqual((os.environ["TV_HOST"], os.environ["TV_PORT"]), ("192.0.2.7", "15555"))

    def test_the_target_the_client_resolved_is_the_one_the_run_keeps(self):
        # tools/tv.py splits `host:port` out of the address and refuses a port outside 1..65535.
        # bench.py used to put the arguments back over that (tv_client.HOST, PORT = self.host,
        # int(self.port)), so `--host <ip>:<port>` reached the client as a machine of that name.
        tv, client = self.tv_with(FakeDevice())
        client.HOST, client.PORT = "192.0.2.20", 5877
        tv.shell("true")
        self.assertEqual((tv.host, tv.port, tv.serial), ("192.0.2.20", 5877, "192.0.2.20:5877"))

    def test_a_dropped_connection_is_reopened_once(self):
        first, second = FakeDevice(fail_first=True), FakeDevice({"echo": b"ok"})
        tv, client = self.tv_with(first, second)
        self.assertEqual(tv.shell("echo ok"), "ok")
        self.assertEqual(client.connects, 2)
        self.assertTrue(first.closed)

    def test_output_that_is_not_utf8_does_not_stop_the_run(self):
        tv, _ = self.tv_with(FakeDevice({"dumpsys": b"Uptime: 5 \xff\xfe"}))
        self.assertEqual(tv.shell("dumpsys gfxinfo"), "Uptime: 5 ��")

    def test_close_closes_the_connection(self):
        device = FakeDevice()
        tv, _ = self.tv_with(device)
        tv.shell("true")
        tv.close()
        self.assertTrue(device.closed)


class FakeTV:
    """Records commands; answers the two dumpsys calls and the filtered log."""

    def __init__(self, log=""):
        self.log = log
        self.commands = []

    def shell(self, command):
        self.commands.append(command)
        if "framestats" in command:
            return "Uptime: 2000\n"
        if "gfxinfo" in command:
            return "Uptime: 1000\n"
        if command.startswith("logcat"):
            return self.log
        return ""

    def package_of(self, component):
        return component.split("/", 1)[0]

    def home_component(self):
        self.commands.append("HOME LOOKUP")
        return "com.gathin.tvhome/.MainActivity"


class KeyTest(unittest.TestCase):
    def test_the_log_is_filtered_on_the_tv(self):
        line = "10-22 I Input: injectKeyEvent: KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_DPAD_RIGHT, eventTime=1500 }"
        tv = FakeTV(line)
        self.assertEqual(bench.key_event_time(tv, 22), 1500)
        self.assertEqual(tv.commands, ["logcat -d -b main -t 2000 | grep injectKeyEvent"])

    def test_a_tv_that_never_logs_keys_stops_being_asked(self):
        tv = FakeTV("")
        for _ in range(10):
            self.assertIsNone(bench.key_event_time(tv, 22))
        self.assertEqual(len(tv.commands), bench.KEY_LOG_PROBES)

    def test_a_tv_that_logs_keys_keeps_being_asked(self):
        tv = FakeTV("injectKeyEvent action=ACTION_DOWN KEYCODE_DPAD_RIGHT eventTime=7")
        for _ in range(10):
            bench.key_event_time(tv, 22)
        self.assertEqual(len(tv.commands), 10)

    def test_the_key_loop_does_not_look_up_home_for_every_key(self):
        tv = FakeTV()
        with mock.patch.object(bench.time, "sleep"):
            bench.measure_key(tv, 22, package="com.gathin.tvhome")
        self.assertNotIn("HOME LOOKUP", tv.commands)
        self.assertIn("dumpsys gfxinfo com.gathin.tvhome reset", tv.commands)


class UnreachableTVTest(unittest.TestCase):
    """A TV that cannot be reached ends the run with tools/tv.py's sentence, not a traceback.

    tv.py words the failures that mean the TV was not reached (a refused port, a Wireless debugging port,
    an address nothing answers on) in connect_error(), and check.py, b64push.py and privacy.py print that
    instead of a traceback. bench.py let the exception out: a TV that was asleep, or off, or had its
    debugging switched off, ended a run in a ConnectionRefusedError/TimeoutError traceback with exit 1 -
    naming neither the address nor that no file was written, and 1 is also the code a finished run whose
    result.json could not be written returns, so a run that never reached the TV looked like one that did.
    """

    def client_that_raises(self, error, why):
        module = types.ModuleType("tv")
        module.HOST, module.PORT = "192.0.2.9", 5555
        module.connects = 0

        def connect():
            module.connects += 1
            raise error

        module.connect = connect
        setattr(module, "connect_error", lambda raised: why)
        patcher = mock.patch.dict(sys.modules, {"tv": module})
        patcher.start()
        self.addCleanup(patcher.stop)
        return module

    def test_tv_pys_sentence_is_printed_and_the_run_stops_at_2(self):
        sentence = ("could not reach 192.0.2.9:5555 (timed out). Is the TV on and on this network? "
                    "See docs/TROUBLESHOOTING.md#connecting.")
        client = self.client_that_raises(TimeoutError("timed out"), sentence)
        tv = bench.TV("192.0.2.9", 5555)
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            with self.assertRaises(SystemExit) as caught:
                tv.shell("true")
        self.assertEqual(2, caught.exception.code)
        self.assertIn(sentence, err.getvalue())
        self.assertIn("Nothing was written", err.getvalue())
        self.assertEqual("", out.getvalue())
        # The run ends here: shell() reopens a dropped connection once, and this SystemExit is not a drop.
        self.assertEqual(1, client.connects)
        self.assertIsNone(tv.client)

    def test_an_error_tv_py_has_nothing_to_add_to_keeps_its_traceback(self):
        # A reset in the handshake reached the TV, and a key file tv.py cannot read carries the file's
        # name; connect_error() returns None for both, and neither may read as "the TV was off".
        client = self.client_that_raises(ConnectionResetError("connection reset by peer"), None)
        tv = bench.TV("192.0.2.9", 5555)
        with self.assertRaises(ConnectionResetError):
            tv.shell("true")
        # One reopen (shell()'s second attempt), then the exception: no third try.
        self.assertEqual(2, client.connects)


class BenchAgainstARefusingPortTest(unittest.TestCase):
    """The real tools/tv.py and a port on 127.0.0.1 nothing listens on: what a reader actually sees.

    Run from a copy of the two scripts in a temporary folder (bench.py imports tv.py from beside itself),
    with HOME there too so the ADB key tv.py creates stays out of the checkout.
    """

    @classmethod
    def setUpClass(cls):
        cls.python = connect_errors.client_python()
        if not cls.python:
            raise unittest.SkipTest("no interpreter with adb_shell to run bench.py")

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self._tmp.name)
        shutil.copyfile(ROOT / "tools" / "bench.py", self.folder / "bench.py")
        shutil.copyfile(ROOT / "tools" / "tv.py", self.folder / "tv.py")
        self.home = self.folder / "home"
        self.home.mkdir()

    def tearDown(self):
        self._tmp.cleanup()

    def test_the_run_names_the_port_and_writes_nothing(self):
        clean = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        clean.update(HOME=str(self.home), USERPROFILE=str(self.home),
                     TV_HOST=f"127.0.0.1:{connect_errors.refusing_port()}")
        result = subprocess.run([self.python, "-I", str(self.folder / "bench.py"),
                                 "--out-dir", "results"],
                                cwd=str(self.folder), env=clean, capture_output=True, text=True, timeout=180)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("refused the connection: nothing listens for ADB there", result.stderr)
        self.assertIn("docs/TROUBLESHOOTING.md#connecting", result.stderr)
        self.assertIn("Nothing was written", result.stderr)
        # Nothing was asked of the TV and no results folder was made for a run that did not happen.
        self.assertFalse((self.folder / "results").exists())
        self.assertFalse(list(self.folder.glob("**/result.json")))


if __name__ == "__main__":
    unittest.main()

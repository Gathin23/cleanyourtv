"""tools/bench.py keeps one ADB connection for the whole run, and asks the TV only for what it needs.

It used to start tools/tv.py for every shell command: a new Python, a new TCP connection and a new
ADB handshake, several times per key, plus the whole main log pulled back for every key (about
100 MB on a TCL C755, 41 s a read). Through a
relay one full run took over 45 minutes. Now the TV class connects once with tools/tv.py's client
and reuses it, reopening it once if it drops; only the log's newest lines are read, filtered on the
TV, and not at all once the first keys show the TV doesn't log them; and the key loop looks up Home once. --host also has to reach the client: the old subprocesses only read TV_HOST. It reaches it as TV_HOST/TV_PORT, and the target the run keeps is the one tools/tv.py made of those two (it splits `host:port` and checks the range), not the arguments again.

The TV and tools/tv.py are faked, so these run without either.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import sys
import types
import unittest
from unittest import mock

from tools import bench


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
    """
    module = types.ModuleType("tv")
    module.HOST, module.PORT = None, None
    module.connects = 0

    def connect():
        module.connects += 1
        return devices[module.connects - 1]
    module.connect = connect
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


if __name__ == "__main__":
    unittest.main()

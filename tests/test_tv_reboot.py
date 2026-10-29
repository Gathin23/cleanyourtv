"""The reboot CLI uses ADB's native service rather than waiting for a shell to finish.

These tests run the real adb_shell service code over an in-memory transport. An accepted reboot
has an OKAY packet but no shell output or CLSE: the connection ends as the TV restarts (#995).
No socket, ADB key or TV is used, and the timeout cases advance a fake clock instead of waiting.
"""
import contextlib
import importlib.util
import io
import itertools
import os
from pathlib import Path
import sys
import unittest
from unittest import mock

try:
    from adb_shell import constants, exceptions
    from adb_shell.adb_device import AdbDevice
    from adb_shell.adb_message import AdbMessage
    from adb_shell.transport.base_transport import BaseTransport
except ImportError:
    raise unittest.SkipTest("protocol tests need adb_shell (tools/requirements.txt)")


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("cyt_tv_reboot", ROOT / "tools" / "tv.py")
TV = importlib.util.module_from_spec(spec)
with mock.patch.dict(os.environ, {"TV_HOST": "192.0.2.1", "TV_PORT": "5555"}):
    spec.loader.exec_module(TV)


def packet(command, remote=77, local=1, data=b""):
    message = AdbMessage(command, remote, local, data)
    return message.pack() + message.data


class MemoryTransport(BaseTransport):
    """A normal ADB handshake, supplied command replies, then immediate EOF."""

    def __init__(self, replies):
        handshake = packet(constants.CNXN, constants.VERSION, constants.MAX_ADB_DATA, b"device::\0")
        self.incoming = bytearray(handshake + replies)
        self.writes = []
        self.eof_reads = 0
        self.closed = False

    def connect(self, transport_timeout_s):
        self.closed = False

    def close(self):
        self.closed = True

    def bulk_read(self, numbytes, transport_timeout_s):
        if not self.incoming:
            self.eof_reads += 1
        data = bytes(self.incoming[:numbytes])
        del self.incoming[:numbytes]
        return data

    def bulk_write(self, data, transport_timeout_s):
        self.writes.append(bytes(data))
        return len(data)


class RebootTest(unittest.TestCase):
    def run_tv(self, args, replies):
        self.transport = MemoryTransport(replies)
        self.device = AdbDevice(self.transport, default_transport_timeout_s=120.0, banner=b"test")
        self.device.connect()  # The in-memory CNXN handshake; no key or socket.
        self.out, self.err = io.StringIO(), io.StringIO()
        with mock.patch.object(TV, "connect", return_value=self.device), \
                mock.patch.object(sys, "argv", ["tv.py", *args]), \
                mock.patch("adb_shell.adb_device.time.time", side_effect=itertools.count()), \
                contextlib.redirect_stdout(self.out), contextlib.redirect_stderr(self.err):
            return TV.main()

    def test_an_accepted_reboot_needs_no_shell_close_reply(self):
        self.assertEqual(self.run_tv(["--reboot"], packet(constants.OKAY)), 0)
        self.assertIn(b"reboot:\0", self.transport.writes)
        self.assertNotIn(b"shell:--reboot\0", self.transport.writes)
        self.assertEqual(self.transport.incoming, b"")
        self.assertEqual(self.transport.eof_reads, 0, "do not read for a reply after the acceptance")
        self.assertIn("Reboot request accepted", self.out.getvalue())
        self.assertEqual(self.err.getvalue(), "")
        self.assertTrue(self.transport.closed)

    def test_missing_acceptance_fails_and_closes_without_claiming_success(self):
        with self.assertRaises(exceptions.AdbTimeoutError):
            self.run_tv(["--reboot"], b"")
        self.assertTrue(self.transport.closed)
        self.assertIn(b"reboot:\0", self.transport.writes)
        self.assertGreater(self.transport.eof_reads, 0)
        self.assertEqual(self.out.getvalue(), "")

    def test_plain_commands_including_raw_reboot_keep_the_shell_protocol(self):
        for args, command in ((["getprop", "ro.product.model"], b"getprop ro.product.model"),
                              (["reboot"], b"reboot")):
            with self.subTest(args=args):
                replies = packet(constants.OKAY) + packet(constants.WRTE, data=b"shell output\n") \
                          + packet(constants.CLSE)
                self.assertEqual(self.run_tv(args, replies), 0)
                self.assertIn(b"shell:" + command + b"\0", self.transport.writes)
                self.assertNotIn(b"reboot:\0", self.transport.writes)
                self.assertEqual(self.out.getvalue(), "shell output\n")
                self.assertTrue(self.transport.closed)

    def test_reboot_with_extra_arguments_is_rejected_before_connecting(self):
        for args in (["--reboot", "now"], ["--reboot", "bootloader"]):
            with self.subTest(args=args), \
                    mock.patch.object(TV, "connect", side_effect=AssertionError("must not connect")) as connect, \
                    mock.patch.object(sys, "argv", ["tv.py", *args]), \
                    contextlib.redirect_stderr(io.StringIO()) as err:
                with self.assertRaises(SystemExit) as raised:
                    TV.main()
                self.assertEqual(raised.exception.code, 2)
                self.assertIn("usage:", err.getvalue())
                connect.assert_not_called()


if __name__ == "__main__":
    unittest.main()

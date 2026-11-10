"""A path must fit the verification command as well as the base64 chunk commands."""
import contextlib
import hashlib
import importlib.util
import io
import os
from pathlib import Path
import re
import sys
import tempfile
import types
import unittest
from unittest import mock

from tests.test_native_adb import load_tv

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("b64push_payload", ROOT / "tools" / "b64push.py")
push = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(push)


class BoundedDevice:
    def __init__(self, limit, data, direct=False):
        self.payload_limit = limit
        self.max_chunk_size = limit // 2 if direct else limit
        if not direct:
            self.max_shell_command_size = limit - len(b"shell:\0")
        self.checksum = hashlib.md5(data).hexdigest()
        self.commands = []
        self.closed = False

    def shell(self, command, **kwargs):
        self.commands.append(command)
        # adb_shell opens b'shell:' + command.encode('utf8') + b'\0'.
        if len(b"shell:" + command.encode("utf-8") + b"\0") > self.payload_limit:
            raise OSError("shell command too long")
        if command.startswith("echo '"):
            return re.search(r"echo (ok\d+)$", command)[1] + "\n"
        if command.startswith("base64 -d"):
            return self.checksum + "  staged-file\n"
        if command.startswith("mv -f"):
            return "moved\n"
        return ""

    def close(self):
        self.closed = True


class VerificationPayloadTest(unittest.TestCase):
    REMOTE = "/data/local/tmp/" + "/".join(["a" * 180] * 6) + "/file.bin"

    def run_push(self, data, limit, direct=False, device=None):
        device = device if device is not None else BoundedDevice(limit, data, direct)
        out, err = io.StringIO(), io.StringIO()
        with tempfile.TemporaryDirectory() as tmp:
            local = Path(tmp) / "source.bin"
            local.write_bytes(data)
            with mock.patch.object(sys, "argv", ["b64push.py", str(local), self.REMOTE]), \
                    mock.patch.dict(sys.modules, {"tv": types.SimpleNamespace(connect=lambda: device)}), \
                    contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = push.main()
        return code, device, out.getvalue(), err.getvalue()

    def verification_payload(self):
        b64, part = self.REMOTE + ".b64.part", self.REMOTE + ".part"
        command = f"base64 -d < '{b64}' > '{part}' && md5sum '{part}'; rm -f '{b64}'"
        return len(b"shell:" + command.encode("utf-8") + b"\0")

    def test_oversized_verification_is_refused_before_any_shell_command(self):
        # The ~1.1 KB path passes validation, and its data chunks fit a 4 KB transport. The verification
        # command repeats it four times, so it cannot be sent even for an empty upload.
        for data in (b"", b"new contents" * 1000):
            with self.subTest(empty=not data):
                code, device, out, err = self.run_push(data, 4096)
                self.assertEqual(code, 2, out + err)
                self.assertEqual(device.commands, [], "not even staging or cleanup may be sent")
                self.assertTrue(device.closed)
                self.assertIn("verification", err)
                self.assertIn("Nothing was sent to the TV.", err)
                self.assertNotIn("Traceback", err)

    def test_a_verification_payload_that_exactly_fits_still_transfers(self):
        limit = self.verification_payload()
        code, device, out, err = self.run_push(b"new contents" * 1000, limit)
        self.assertEqual(code, 0, out + err)
        self.assertTrue(device.closed)
        self.assertTrue(any(c.startswith("mv -f") for c in device.commands))
        self.assertEqual(max(len(b"shell:" + c.encode("utf-8") + b"\0") for c in device.commands), limit)

    def test_one_byte_less_than_the_verification_payload_is_refused(self):
        code, device, out, err = self.run_push(b"new contents", self.verification_payload() - 1)
        self.assertEqual(code, 2, out + err)
        self.assertEqual(device.commands, [])
        self.assertTrue(device.closed)

    def test_adb_shell_filesync_chunks_are_not_the_shell_command_limit(self):
        # adb_shell exposes half its negotiated payload as max_chunk_size. Verification fits the
        # shell OPEN even though it is longer than a filesync chunk.
        code, device, out, err = self.run_push(b"new contents" * 1000, 8192, direct=True)
        self.assertEqual(code, 0, out + err)
        self.assertTrue(device.closed)
        self.assertTrue(any(c.startswith("mv -f") for c in device.commands))

    def test_the_native_adapter_supplies_its_own_command_limit(self):
        with mock.patch.dict(os.environ, {"TV_HOST": "tv.example"}):
            client = load_tv()
        with mock.patch.object(client.NativeAdbDevice, "_run", return_value="device") as run:
            device = client.NativeAdbDevice("fake-adb", "tv.example", 5555, 5)
            run.reset_mock()   # Only the constructor's get-state was answered.
            code, _, out, err = self.run_push(b"new contents", 4096, device=device)
        self.assertEqual(code, 2, out + err)
        run.assert_not_called()
        self.assertIn("verification", err)

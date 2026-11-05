"""The opt-in Google ADB backend must keep every operation on the selected transport."""
import importlib.util
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]


def load_tv():
    spec = importlib.util.spec_from_file_location("tv_native_test", ROOT / "tools" / "tv.py")
    tv = importlib.util.module_from_spec(spec)
    # Native commands do not use Python ADB keys or sockets. Stub those imports so
    # these tests also run under a Python which has no adb_shell installed.
    stubs = {name: types.ModuleType(name) for name in (
        "adb_shell", "adb_shell.auth", "adb_shell.adb_device",
        "adb_shell.auth.keygen", "adb_shell.auth.sign_pythonrsa")}
    stubs["adb_shell.adb_device"].AdbDeviceTcp = object
    stubs["adb_shell.auth.keygen"].keygen = lambda path: None
    stubs["adb_shell.auth.sign_pythonrsa"].PythonRSASigner = lambda pub, priv: None
    with mock.patch.dict(sys.modules, stubs):
        spec.loader.exec_module(tv)
    return tv


class NativeAdbEncodingTest(unittest.TestCase):
    def setUp(self):
        with mock.patch.dict(os.environ, {"TV_HOST": "192.0.2.8:37123"}):
            self.tv = load_tv()

    @contextlib.contextmanager
    def device_output(self, stdout=b"", stderr=b"", status=0):
        run = subprocess.run

        def fake_adb(args, **kwargs):
            # Use a real child process and pipe decoding, without an ADB binary or TV.
            # The Python child writes bytes so its own console encoding cannot affect them.
            out, err, code = (b"device\n", b"", 0) if args[3] == "get-state" else (stdout, stderr, status)
            script = (f"import sys; sys.stdout.buffer.write({out!r}); "
                      f"sys.stderr.buffer.write({err!r}); sys.exit({code})")
            return run([sys.executable, "-c", script], **kwargs)

        # Emulate the locale default on Windows even on CI's UTF-8 Linux runner.
        with mock.patch.object(subprocess, "_text_encoding", return_value="cp1252"), \
                mock.patch.object(self.tv.subprocess, "run", side_effect=fake_adb):
            yield self.tv.NativeAdbDevice("fake-adb", "192.0.2.8", 37123, 5)

    def test_utf8_shell_output_survives_a_non_utf8_host_locale(self):
        text = "Sala de estar: A\u00e7\u00e3o, \u65e5\u672c\u8a9e, \u0422\u0412\n"
        with self.device_output(stdout=text.encode("utf-8")) as device:
            self.assertEqual(device.shell("getprop ro.product.model"), text)

    def test_utf8_transport_errors_survive_a_non_utf8_host_locale(self):
        text = "n\u00e3o foi poss\u00edvel abrir /sdcard/\u65e5\u672c\u8a9e.png"
        for stream in ("stdout", "stderr"):
            with self.subTest(stream=stream), \
                    self.device_output(**{stream: text.encode("utf-8")}, status=1) as device:
                with self.assertRaises(self.tv.NativeAdbError) as caught:
                    device.shell("getprop ro.product.model")
                self.assertIn(text, str(caught.exception))

    def test_invalid_utf8_is_replaced_without_losing_the_response(self):
        with self.device_output(stdout=b"before\xffafter\n") as device:
            self.assertEqual(device.shell("getprop ro.product.model"), "before\ufffdafter\n")


@unittest.skipIf(os.name == "nt", "fake adb executable uses a POSIX shebang")
class NativeAdbTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="native adb ")
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)
        self.log = self.dir / "calls.jsonl"
        self.binary = self.dir / "fake adb"
        # A shebang is split at its first space, so a Python under "Application Support",
        # "Program Files" or a user name with a space cannot be named there: exec then fails
        # with ENOENT ("bad interpreter"), which showed up here as five errors and one
        # failure. This helper imports the standard library only, so env is enough.
        interp = sys.executable if not any(c in " \t" for c in sys.executable) else "/usr/bin/env python3"
        self.binary.write_text(f"#!{interp}\n" + r'''
import json, os, pathlib, shutil, subprocess, sys
args = sys.argv[1:]
with open(os.environ['FAKE_ADB_LOG'], 'a') as f:
    f.write(json.dumps(args) + '\n')
if os.environ.get('FAKE_ADB_FAIL'):
    print('transport unavailable', file=sys.stderr)
    sys.exit(1)
operation = args[2]
if operation == 'get-state':
    print(os.environ.get('FAKE_ADB_STATE', 'device'))
elif operation == 'shell':
    if os.environ.get('FAKE_ADB_RUN_SHELL'):
        result = subprocess.run(args[-1], shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        sys.stdout.buffer.write(result.stdout)
    else:
        print('remote output\nremote stderr')
elif operation == 'pull':
    shutil.copyfile(os.environ['FAKE_ADB_REMOTE_FILE'], args[4])
''')
        self.binary.chmod(0o755)
        self.environment = mock.patch.dict(os.environ, {
            "TV_HOST": "192.0.2.8:37123", "TV_ADB_BINARY": str(self.binary),
            "FAKE_ADB_LOG": str(self.log),
        })
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.tv = load_tv()

    def calls(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_shell_does_not_execute_local_shell_syntax_and_keeps_remote_stderr(self):
        marker = self.dir / "must-not-exist"
        command = f"printf hello; touch '{marker}'"
        with mock.patch.object(self.tv, "get_signer", side_effect=AssertionError("no key read")):
            device = self.tv.connect(timeout=2)
        self.assertEqual(device.shell(command), "remote output\nremote stderr\n")
        device.close()
        self.assertFalse(marker.exists())
        self.assertEqual(self.calls(), [
            ["-s", "192.0.2.8:37123", "get-state"],
            ["-s", "192.0.2.8:37123", "shell", "-T", "-x", command],
        ])

    def test_binary_pull_push_and_reboot_stay_on_the_same_transport(self):
        remote = self.dir / "remote bytes"
        remote.write_bytes(bytes(range(256)))
        local = self.dir / "pulled bytes"
        with mock.patch.dict(os.environ, {"FAKE_ADB_REMOTE_FILE": str(remote)}):
            device = self.tv.connect()
            device.pull("/data/local/tmp/picture.png", local, read_timeout_s=3)
            device.push(local, "/data/local/tmp/file with spaces", read_timeout_s=3)
            device.reboot()
            device.close()
        self.assertEqual(local.read_bytes(), remote.read_bytes())
        calls = self.calls()
        self.assertTrue(all(c[:2] == ["-s", "192.0.2.8:37123"] for c in calls))
        self.assertEqual([c[2] for c in calls], ["get-state", "pull", "push", "reboot"])
        self.assertEqual(calls[2][-1], "/data/local/tmp/file with spaces")

    def test_installer_base64_uploader_uses_native_transport_and_verifies_bytes(self):
        # The installer uses b64push, not Device.push. Run its real chunking, shell
        # checksums and rename through the fake ADB executable against temporary files.
        with tempfile.TemporaryDirectory(prefix="native-b64-") as folder:
            source = Path(folder) / "source.apk"
            target = Path(folder) / "target.apk"
            data = bytes(range(256)) * 40
            source.write_bytes(data)
            target.write_bytes(b"old content")
            spec = importlib.util.spec_from_file_location("native_b64_test", ROOT / "tools" / "b64push.py")
            uploader = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(uploader)
            with mock.patch.dict(sys.modules, {"tv": self.tv}), \
                    mock.patch.dict(os.environ, {"FAKE_ADB_RUN_SHELL": "1"}), \
                    mock.patch.object(sys, "argv", ["b64push.py", str(source), str(target)]), \
                    mock.patch.object(sys, "path", list(sys.path)), \
                    contextlib.redirect_stdout(io.StringIO()) as output:
                self.assertEqual(uploader.main(), 0)
            self.assertEqual(target.read_bytes(), data)
            self.assertIn("MATCH", output.getvalue())
            self.assertFalse(Path(str(target) + ".part").exists())
            chunks = [c for c in self.calls() if c[2] == "shell" and "&& echo ok" in c[-1]]
            self.assertGreater(len(chunks), 1)
            self.assertTrue(all(len(c[-1]) < 8192 for c in chunks))

    def test_unauthorized_or_failed_transport_never_falls_back(self):
        for env in ({"FAKE_ADB_STATE": "unauthorized"}, {"FAKE_ADB_FAIL": "1"}):
            with self.subTest(env=env), mock.patch.dict(os.environ, env), \
                    mock.patch.object(self.tv, "AdbDeviceTcp", side_effect=AssertionError("no fallback")):
                with self.assertRaises(self.tv.NativeAdbError):
                    self.tv.connect(timeout=2)
        self.assertEqual([c[2] for c in self.calls()], ["get-state", "get-state"])

    def test_subprocess_timeout_is_bounded_and_reported(self):
        device = self.tv.connect(timeout=7)
        with mock.patch.object(self.tv.subprocess, "run",
                               side_effect=subprocess.TimeoutExpired("adb", 3)) as run:
            with self.assertRaisesRegex(self.tv.NativeAdbError, "timed out after 3s"):
                device.shell("getprop ro.product.model", read_timeout_s=3)
        self.assertEqual(run.call_args.kwargs["timeout"], 3)

    def test_ipv6_endpoint_is_explicit_and_bracketed(self):
        device = self.tv.NativeAdbDevice(str(self.binary), "2001:db8::8", 37123, 2)
        device.shell("getprop ro.product.model")
        self.assertTrue(all(c[:2] == ["-s", "[2001:db8::8]:37123"] for c in self.calls()))

    def test_missing_executable_is_a_clear_connection_error(self):
        with mock.patch.dict(os.environ, {"TV_ADB_BINARY": str(self.dir / "absent")}):
            with self.assertRaises(self.tv.NativeAdbError) as raised:
                self.tv.connect(timeout=2)
        self.assertIn("Could not run Google ADB", self.tv.connect_error(raised.exception))


if __name__ == "__main__":
    unittest.main()

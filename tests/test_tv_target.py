"""tools/tv.py accepts host:port endpoints in TV_HOST and respects TV_PORT.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import os
from pathlib import Path
import sys
import types
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"


def load_tv(env):
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
    spec = importlib.util.spec_from_file_location("cyt_tv_target_test", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    clean_env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    clean_env.update(env)
    with mock.patch.dict(os.environ, clean_env, clear=True), mock.patch.dict(sys.modules, stubs):
        spec.loader.exec_module(module)
    return module


class TvTargetTest(unittest.TestCase):
    def test_split_host_port_function(self):
        tv = load_tv({"TV_HOST": "192.0.2.1"})
        self.assertEqual(tv.split_host_port("192.0.2.1"), ("192.0.2.1", None))
        self.assertEqual(tv.split_host_port("192.0.2.1:5555"), ("192.0.2.1", "5555"))
        self.assertEqual(tv.split_host_port("[::1]:5555"), ("::1", "5555"))
        self.assertEqual(tv.split_host_port("[::1]"), ("::1", None))
        self.assertEqual(tv.split_host_port("::1"), ("::1", None))

    def test_default_port(self):
        tv = load_tv({"TV_HOST": "192.0.2.1"})
        self.assertEqual(tv.HOST, "192.0.2.1")
        self.assertEqual(tv.PORT, 5555)

    def test_endpoint_with_port(self):
        tv = load_tv({"TV_HOST": "192.0.2.1:5555"})
        self.assertEqual(tv.HOST, "192.0.2.1")
        self.assertEqual(tv.PORT, 5555)

    def test_endpoint_with_custom_port(self):
        tv = load_tv({"TV_HOST": "192.0.2.1:39482"})
        self.assertEqual(tv.HOST, "192.0.2.1")
        self.assertEqual(tv.PORT, 39482)

    def test_endpoint_port_overrides_tv_port_env(self):
        tv = load_tv({"TV_HOST": "192.0.2.1:39482", "TV_PORT": "15555"})
        self.assertEqual(tv.HOST, "192.0.2.1")
        self.assertEqual(tv.PORT, 39482)

    def test_tv_port_env_used_when_endpoint_has_no_port(self):
        tv = load_tv({"TV_HOST": "192.0.2.1", "TV_PORT": "15555"})
        self.assertEqual(tv.HOST, "192.0.2.1")
        self.assertEqual(tv.PORT, 15555)

    def test_ipv6_bracketed_endpoint(self):
        tv = load_tv({"TV_HOST": "[::1]:5555"})
        self.assertEqual(tv.HOST, "::1")
        self.assertEqual(tv.PORT, 5555)

    def test_ipv6_bracketed_without_port(self):
        tv = load_tv({"TV_HOST": "[::1]"})
        self.assertEqual(tv.HOST, "::1")
        self.assertEqual(tv.PORT, 5555)

    def test_missing_host_raises_system_exit(self):
        with self.assertRaises(SystemExit) as cm:
            load_tv({})
        self.assertIn("Set TV_HOST to your TV's IP address", str(cm.exception))

    def test_bad_port_raises_system_exit(self):
        with self.assertRaises(SystemExit) as cm:
            load_tv({"TV_HOST": "192.0.2.1:not-a-port"})
        self.assertIn("not a port number: 'not-a-port'", str(cm.exception))

    def test_out_of_range_port_raises_system_exit(self):
        with self.assertRaises(SystemExit) as cm:
            load_tv({"TV_HOST": "192.0.2.1:70000"})
        self.assertIn("not a port number: '70000'", str(cm.exception))


    def test_connect_passes_host_and_port(self):
        created_devices = []

        class StubAdbDeviceTcp:
            def __init__(self, host, port, **kwargs):
                created_devices.append((host, port))

            def connect(self, **kwargs):
                pass

        tv = load_tv({"TV_HOST": "192.0.2.1:39482"})
        with mock.patch.object(tv, "AdbDeviceTcp", StubAdbDeviceTcp), \
                mock.patch.object(tv, "get_signer", return_value=None):
            tv.connect()
        self.assertEqual(created_devices, [("192.0.2.1", 39482)])


if __name__ == "__main__":
    unittest.main()

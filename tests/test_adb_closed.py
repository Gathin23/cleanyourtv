"""tools/adb-closed.py tells an open port from a closed one, on 127.0.0.1.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import socket
import subprocess
import sys
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"


def check(*args, env=None):
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args],
                          capture_output=True, text=True, timeout=30, env=env)


class AdbClosedTest(unittest.TestCase):
    def test_open_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            result = check("127.0.0.1", str(server.getsockname()[1]))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("still open", result.stderr)

    def test_closed_port(self):
        # Take a free port and let it go again: nothing listens there, so the connection is refused.
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        result = check("127.0.0.1", str(port))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("is closed: the TV refused", result.stdout)

    def test_endpoint_with_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check(f"127.0.0.1:{port}")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_closed_endpoint_with_port(self):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        result = check(f"127.0.0.1:{port}")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is closed: the TV refused", result.stdout)

    def test_endpoint_from_tv_host_env(self):
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 0))
            port = probe.getsockname()[1]
        env = dict(os.environ, TV_HOST=f"127.0.0.1:{port}")
        result = check(env=env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is closed", result.stdout)

    def test_cli_port_overrides_endpoint_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check("127.0.0.1:1", str(port))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_endpoint_port_in_tv_host_env_overrides_tv_port_env(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            open_port = server.getsockname()[1]
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                closed_port = probe.getsockname()[1]
            env = dict(os.environ, TV_HOST=f"127.0.0.1:{open_port}", TV_PORT=str(closed_port))
            result = check(env=env)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{open_port} is still open", result.stderr)

    def test_tv_port_env_used_when_tv_host_has_no_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            env = dict(os.environ, TV_HOST="127.0.0.1", TV_PORT=str(port))
            result = check(env=env)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_bad_arguments(self):
        self.assertEqual(check("127.0.0.1", "not-a-port").returncode, 2)
        self.assertEqual(check("127.0.0.1:not-a-port").returncode, 2)
        self.assertIn("not a port number: 'not-a-port'", check("127.0.0.1:not-a-port").stderr)
        self.assertEqual(check("[::1]:not-a-port").returncode, 2)
        self.assertEqual(check("127.0.0.1:99999").returncode, 2)
        self.assertEqual(check("a", "b", "c").returncode, 2)


if __name__ == "__main__":
    unittest.main()

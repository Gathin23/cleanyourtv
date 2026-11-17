"""tools/adb-closed.py reads its address and port, and finds an open port, on 127.0.0.1.

A refusal on 127.0.0.1 is this computer's, not the TV's, so the closed cases run against a stand-in TV
in tests/test_adb_closed_only_a_refusal.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import socket
import subprocess
import sys
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "adb-closed.py"


def env_with(**overrides):
    """This environment without the shell's own TV_HOST/TV_PORT, plus the given ones.

    adb-closed.py reads both (tools/relay.md tells the reader to `export TV_HOST=127.0.0.1
    TV_PORT=15555` before running the tools through a relay), so a caller with them exported changed
    what these tests asserted: five of them answered "TV_PORT=15555 is a relay's port" instead of the
    address they typed. tests/test_adb_closed_only_a_refusal.py and
    tests/test_adb_closed_macos_permission.py already run in an environment they own, by this same
    rule; this file passed env=None and inherited whatever the shell had.
    """
    env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    env.update(overrides)
    return env


def check(*args, env=None):
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args],
                          capture_output=True, text=True, timeout=30,
                          env=env if env is not None else env_with())


class AdbClosedTest(unittest.TestCase):
    def test_open_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            result = check("127.0.0.1", str(server.getsockname()[1]))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("still open", result.stderr)

    def test_endpoint_with_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            result = check(f"127.0.0.1:{port}")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

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
            env = env_with(TV_HOST=f"127.0.0.1:{open_port}", TV_PORT=str(closed_port))
            result = check(env=env)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{open_port} is still open", result.stderr)

    def test_tv_port_env_used_when_tv_host_has_no_port(self):
        with socket.socket() as server:
            server.bind(("127.0.0.1", 0))
            server.listen(1)
            port = server.getsockname()[1]
            env = env_with(TV_HOST="127.0.0.1", TV_PORT=str(port))
            result = check(env=env)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", result.stderr)

    def test_a_name_that_is_not_a_hostname_is_not_an_open_port(self):
        # getaddrinfo runs the name through the idna codec, which raises UnicodeError on an empty or
        # over-long label (a doubled dot, a 70-character label) rather than the gaierror an unresolvable
        # name gives. Uncaught, that was a traceback that ended in exit 1 - the code this tool reserves
        # for "the port is still open" - so a typo in the address read as an open port at the closing
        # step the prompt sends an assistant to.
        for address in ("192.168.1..50", ".", "192.168.1.50..", "a" * 70 + ".com", "192.168.1..50:5555"):
            with self.subTest(address=address):
                result = check(address)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn("not a hostname", result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                self.assertNotIn("still open", result.stderr)
                self.assertEqual(result.stdout, "")

    def test_a_refused_check_names_the_address_it_was_given(self):
        # Whatever the reason, the message has to name the address the owner typed: the one reading
        # "could not check" is the one who can see the typo.
        result = check("192.168.1..50")
        self.assertIn("could not check 192.168.1..50:5555", result.stderr)

    def test_a_relay_shell_does_not_change_what_these_tests_ask(self):
        # tools/relay.md's line 13 is `export TV_HOST=127.0.0.1 TV_PORT=15555`, and CONTRIBUTING asks a
        # contributor to run this suite before opening a PR. In that shell the five tests above failed
        # on addresses they never typed, because adb-closed.py reads those two variables and, rightly,
        # refuses a relay's port beside this computer's address: a red suite that said nothing about
        # the change under test. The tests now hand the script an environment of their own.
        with mock.patch.dict(os.environ, {"TV_HOST": "127.0.0.1", "TV_PORT": "15555"}):
            refused = check("192.168.1..50")
        self.assertEqual(refused.returncode, 2, refused.stderr)
        self.assertIn("could not check 192.168.1..50:5555", refused.stderr)
        self.assertNotIn("is a relay's port", refused.stderr)
        with mock.patch.dict(os.environ, {"TV_HOST": "127.0.0.1", "TV_PORT": "15555"}):
            with socket.socket() as server:
                server.bind(("127.0.0.1", 0))
                server.listen(1)
                port = server.getsockname()[1]
                open_port = check(f"127.0.0.1:{port}")
        self.assertEqual(open_port.returncode, 1, open_port.stderr)
        self.assertIn(f"127.0.0.1:{port} is still open", open_port.stderr)

    def test_bad_arguments(self):
        self.assertEqual(check("127.0.0.1", "not-a-port").returncode, 2)
        self.assertEqual(check("127.0.0.1:not-a-port").returncode, 2)
        self.assertIn("not a port number: 'not-a-port'", check("127.0.0.1:not-a-port").stderr)
        self.assertEqual(check("[::1]:not-a-port").returncode, 2)
        self.assertEqual(check("127.0.0.1:99999").returncode, 2)
        self.assertEqual(check("a", "b", "c").returncode, 2)


if __name__ == "__main__":
    unittest.main()

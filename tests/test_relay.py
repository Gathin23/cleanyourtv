"""tools/relay.py forwards bytes both ways, listens on loopback only, and says so when the TV is out of reach.

A small echo server on this machine stands in for the TV, so the test needs no TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import os
import socket
import subprocess
import sys
import threading
import unittest


ROOT = Path(__file__).resolve().parents[1]


def echo_server():
    """A stand-in TV on 127.0.0.1: echoes whatever it receives. Returns its port."""
    srv = socket.socket()
    srv.bind(("127.0.0.1", 0))
    srv.listen(4)

    def run():
        while True:
            try:
                conn, _ = srv.accept()
            except OSError:
                return
            with conn:
                while True:
                    data = conn.recv(65536)
                    if not data:
                        break
                    conn.sendall(data)

    threading.Thread(target=run, daemon=True).start()
    return srv


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def env_with(**values):
    """This environment without the shell's own TV_HOST/TV_PORT, plus the given ones."""
    env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
    env.update(values)
    return env


HELLO = b"CNXN\x00\x00\x00\x01"


def round_trip(port):
    """Send a few bytes through the relay on PORT and return what comes back."""
    with socket.create_connection(("127.0.0.1", port), timeout=10) as c:
        c.settimeout(15)
        c.sendall(HELLO)
        got = b""
        while len(got) < len(HELLO):
            chunk = c.recv(65536)
            if not chunk:
                break
            got += chunk
    return got


class RelayTest(unittest.TestCase):
    def start_relay(self, tv_port, *extra):
        proc, port, _ = self.launch("127.0.0.1", "--tv-port", str(tv_port), "--port", "0", *extra)
        return proc, port

    def launch(self, *args, env=None):
        """Start the relay with ARGS; return it, its local port and the line that names where it forwards to."""
        proc = subprocess.Popen([sys.executable, str(ROOT / "tools" / "relay.py"), *args], env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        # Cleanups run last-in first-out: kill, wait, then close the pipes.
        self.addCleanup(proc.stderr.close)
        self.addCleanup(proc.stdout.close)
        self.addCleanup(proc.wait, 10)
        self.addCleanup(proc.kill)
        first = proc.stdout.readline()
        if not first.startswith("relay 127.0.0.1:"):
            proc.kill()   # stderr is only complete once the relay has exited
            self.fail(first + proc.stderr.read())
        port = int(first.split()[1].split(":")[1])
        self.assertIn(f"TV_HOST=127.0.0.1 TV_PORT={port}", proc.stdout.readline())
        return proc, port, first

    def run_relay(self, *args, timeout=10):
        """Run the relay with ARGS to the end; a relay that starts instead of refusing an argument is a failure."""
        try:
            return subprocess.run([sys.executable, str(ROOT / "tools" / "relay.py"), *args],
                                  env=env_with(), capture_output=True, text=True, timeout=timeout)
        except subprocess.TimeoutExpired:
            self.fail(f"relay.py {' '.join(args)} started a relay instead of refusing the argument")

    def test_bytes_go_both_ways(self):
        tv = echo_server()
        self.addCleanup(tv.close)
        _, port = self.start_relay(tv.getsockname()[1])
        payload = b"CNXN\x00\x00\x00\x01" + bytes(range(256)) * 400   # bigger than one recv
        with socket.create_connection(("127.0.0.1", port), timeout=10) as c:
            # Send from a thread while reading here: the echo comes back while we are still sending, and a
            # single sendall would fill the buffers on the way back and never finish.
            threading.Thread(target=c.sendall, args=(payload,), daemon=True).start()
            got = b""
            while len(got) < len(payload):
                chunk = c.recv(65536)
                if not chunk:
                    break
                got += chunk
        self.assertEqual(got, payload)

    def test_listens_on_loopback_only(self):
        tv = echo_server()
        self.addCleanup(tv.close)
        _, port = self.start_relay(tv.getsockname()[1])
        # This machine's address on the network (no packet is sent to 192.0.2.1, a documentation address).
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            try:
                probe.connect(("192.0.2.1", 9))
                lan = probe.getsockname()[0]
            except OSError:
                self.skipTest("no network interface besides loopback")
        if lan.startswith("127."):
            self.skipTest("no network interface besides loopback")
        with self.assertRaises(OSError):
            socket.create_connection((lan, port), timeout=3).close()

    def test_an_unreachable_tv_closes_the_client_and_says_why(self):
        proc, port = self.start_relay(free_port())   # nothing listens there
        with socket.create_connection(("127.0.0.1", port), timeout=10) as c:
            c.settimeout(15)
            self.assertEqual(c.recv(16), b"")
        self.assertIn("cannot reach 127.0.0.1:", proc.stdout.readline())

    def test_a_port_already_in_use_is_reported(self):
        with socket.socket() as taken:
            taken.bind(("127.0.0.1", 0))
            taken.listen(1)
            result = subprocess.run([sys.executable, str(ROOT / "tools" / "relay.py"), "192.0.2.1",
                                     "--port", str(taken.getsockname()[1])],
                                    capture_output=True, text=True, timeout=30)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Is a relay already running?", result.stderr)

    def test_pointing_the_relay_at_itself_is_refused(self):
        result = subprocess.run([sys.executable, str(ROOT / "tools" / "relay.py"), "127.0.0.1",
                                 "--tv-port", "15555", "--port", "15555"], capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 2)
        self.assertIn("the relay itself", result.stderr)

    # The TV's port comes from where tv.py and adb-closed.py take it (#997): an explicit --tv-port, else a
    # port in the address, else TV_PORT, else 5555. The relay used to ignore the address and TV_PORT, so a
    # TV whose adbd has a port of its own (5877 on the Wanbo T6R Max) was relayed to 5555.
    def stand_in_tv(self):
        tv = echo_server()
        self.addCleanup(tv.close)
        return tv.getsockname()[1]

    def assert_reaches(self, tv_port, *args, env):
        _, port, first = self.launch(*args, "--port", "0", env=env)
        self.assertIn(f"-> 127.0.0.1:{tv_port}\n", first)
        self.assertEqual(round_trip(port), HELLO)

    def test_tv_port_from_the_environment_is_used(self):
        tv_port = self.stand_in_tv()
        self.assert_reaches(tv_port, env=env_with(TV_HOST="127.0.0.1", TV_PORT=str(tv_port)))

    def test_a_port_in_tv_host_is_used_ahead_of_tv_port(self):
        tv_port = self.stand_in_tv()
        self.assert_reaches(tv_port, env=env_with(TV_HOST=f"127.0.0.1:{tv_port}", TV_PORT=str(free_port())))

    def test_a_port_in_the_address_argument_is_used(self):
        tv_port = self.stand_in_tv()
        self.assert_reaches(tv_port, f"127.0.0.1:{tv_port}", env=env_with(TV_PORT=str(free_port())))

    def test_an_explicit_tv_port_wins(self):
        tv_port = self.stand_in_tv()
        env = env_with(TV_HOST=f"127.0.0.1:{free_port()}", TV_PORT=str(free_port()))
        self.assert_reaches(tv_port, "--tv-port", str(tv_port), env=env)

    def test_the_tools_own_settings_are_refused(self):
        # The TV_HOST/TV_PORT exported for the tools, and then the relay started in that same terminal. With the
        # TV's IP given too, that TV_PORT would send the relay to the TV's IP on a relay's port (15555).
        tools = env_with(TV_HOST="127.0.0.1", TV_PORT="15555")
        for args, env, said in (([], tools, "TV_PORT=15555 is a relay's port"),
                                (["192.0.2.1"], tools, "TV_PORT=15555 is a relay's port"),
                                (["192.0.2.1", "--port", "15556"], tools, "TV_PORT=15555 is a relay's port"),
                                (["192.0.2.1", "--port", "15556"], env_with(TV_PORT="15556"),
                                 "TV_PORT=15556 is a relay's port"),
                                ([], env_with(TV_HOST="127.0.0.1:15555"), "the relay itself")):
            with self.subTest(args=args, TV_HOST=env.get("TV_HOST"), TV_PORT=env.get("TV_PORT")):
                result = subprocess.run([sys.executable, str(ROOT / "tools" / "relay.py"), *args], env=env,
                                        capture_output=True, text=True, timeout=30)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn(said, result.stderr)
                self.assertEqual(result.stdout, "")

    def test_tv_port_still_applies_to_the_tvs_ip_given(self):
        # A TV_PORT that is not a relay's is the TV's, as for tv.py: TV_PORT=5877 relay.py <tv ip>.
        tv_port = self.stand_in_tv()
        self.assert_reaches(tv_port, "127.0.0.1", env=env_with(TV_PORT=str(tv_port)))

    def test_a_local_port_that_is_not_a_port_is_refused(self):
        # --port went to bind() as typed, and bind() answers a port above 65535 (or a negative one) with an
        # OverflowError, which is not an OSError: the "cannot listen on 127.0.0.1:PORT" line below never saw
        # it, so choosing a port by mistake was a traceback with exit 1.
        for value in ("65536", "70000", "-1"):
            with self.subTest(port=value):
                result = self.run_relay("192.0.2.1", "--port", value)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn(f"--port: not a port number: {value}", result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                self.assertEqual(result.stdout, "")

    def test_a_tv_port_that_is_not_a_port_is_refused(self):
        # --tv-port skipped the 1..65535 check the address and TV_PORT get (a few lines above in relay.py), so
        # it reached socket.create_connection, which cuts a servname to 16 bits: --tv-port 70000 printed
        # "-> <tv>:70000" and forwarded to <tv>:4464, some other service on the TV, and 0 or 65536 became port
        # 0. A port the relay cannot forward to is refused before it listens, as the address's port is.
        for value in ("0", "-1", "65536", "70000", "131072"):
            with self.subTest(port=value):
                result = self.run_relay("192.0.2.1", "--tv-port", value)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn(f"--tv-port: not a port number: {value}", result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                self.assertEqual(result.stdout, "")

    def test_the_ends_of_the_port_range_are_still_ports(self):
        # 1 and 65535 are ports and neither option may refuse them; --port keeps 0, which picks a free one and
        # is what every other test here starts a relay with.
        for value in ("1", "65535"):
            with self.subTest(port=value):
                _, _, first = self.launch("192.0.2.1", "--tv-port", value, "--port", "0", env=env_with())
                self.assertIn(f"-> 192.0.2.1:{value}\n", first)

    def test_a_port_that_is_not_a_number_is_refused(self):
        for args, env in ((["127.0.0.1:55a5"], env_with()), (["127.0.0.1:70000"], env_with()),
                          ([], env_with(TV_HOST="127.0.0.1", TV_PORT="x"))):
            with self.subTest(args=args, TV_PORT=env.get("TV_PORT")):
                result = subprocess.run([sys.executable, str(ROOT / "tools" / "relay.py"), *args, "--port", "0"],
                                        env=env, capture_output=True, text=True, timeout=30)
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn("not a port number", result.stderr)
                self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()

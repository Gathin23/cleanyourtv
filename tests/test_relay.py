"""tools/relay.py forwards bytes both ways, listens on loopback only, and says so when the TV is out of reach.

A small echo server on this machine stands in for the TV, so the test needs no TV.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
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


class RelayTest(unittest.TestCase):
    def start_relay(self, tv_port, *extra):
        proc = subprocess.Popen([sys.executable, str(ROOT / "tools" / "relay.py"), "127.0.0.1",
                                 "--tv-port", str(tv_port), "--port", "0", *extra],
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
        return proc, port

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


if __name__ == "__main__":
    unittest.main()

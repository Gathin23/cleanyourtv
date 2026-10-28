"""tools/tv.py says what a refused port or a Wireless debugging port means, instead of a traceback (#1578).

The reporter's Haier H50H5F (Android 14 / Google TV) had only Wireless debugging, whose port changes at every
restart, while tv.py connects to 5555 unless TV_HOST or TV_PORT names another port. On a TV like that, port
5555 refuses and tv.py ended with a ConnectionRefusedError traceback. Pointed at the Wireless debugging port,
it ended with adb_shell's `InvalidCommandError: Unknown command: ... 'STLS'`: that port asks for TLS, and
adb_shell 0.4.4 (tools/requirements.txt) has none. Neither says which port to use instead. tv.py now prints
what happened and the way to port 5555 (pair once with the stock adb, then `adb tcpip 5555`), and exits 2.
A key file it cannot read and a connection reset in the handshake keep their traceback: neither means the TV
could not be reached. tv.connect() itself still raises, because check.py (through recovery.connect()) and
privacy.py catch its errors themselves; recovery.py --save still lets them through.

These runs use the real adb_shell and real sockets on 127.0.0.1: a port nothing listens on, a server that
answers the handshake with the STLS packet a Chromecast with Google TV 4K (Google TV 14) sent back on its
Wireless debugging port, and one that resets the connection instead. A handshake timeout takes tv.py's 120
seconds on a socket, so those errors are raised in place of connect() instead, with the wording
tests/test_drift_check_adb_errors.py pins. tv.py runs from a copy in a temporary folder, with HOME there
too, so the ADB key it creates stays out of the checkout and away from ~/.android.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_drift_check_adb_errors as adb_errors  # noqa: E402  (a module, so its tests do not run twice)

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"
# STLS, version 0x01000000, no payload: byte for byte what the Chromecast's Wireless debugging port
# answered a CNXN with.
STLS = struct.unpack("<I", b"STLS")[0]
STLS_PACKET = struct.pack("<6I", STLS, 0x01000000, 0, 0, 0, STLS ^ 0xFFFFFFFF)

# Loads the copy of tv.py named on the command line and either calls connect() the way check.py and
# privacy.py do, printing the error it raises, or puts the error named next, with the message after it (and
# for PermissionError the file's name, as open() gives it), in place of connect() and runs main() as
# `tv.py --probe` would.
RUNNER = r'''
import importlib.util
import sys

from adb_shell import exceptions

spec = importlib.util.spec_from_file_location("tv", sys.argv[1])
tv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tv)
name = sys.argv[2]
if name == "connect":
    try:
        tv.connect()
    except Exception as e:
        print(type(e).__name__)
    sys.exit(0)
if name == "OSError":
    error = OSError(65, sys.argv[3])
elif name == "PermissionError":
    error = PermissionError(13, sys.argv[3], sys.argv[4])
else:
    error = getattr(exceptions, name)(sys.argv[3])


def connect():
    raise error


tv.connect = connect
sys.argv = ["tv.py", "--probe"]
sys.exit(tv.main())
'''


def client_python():
    """An interpreter with adb_shell: the tools venv that CI builds before the tests, or this one."""
    for candidate in (ROOT / "tools" / "venv" / "bin" / "python",
                      ROOT / "tools" / "venv" / "Scripts" / "python.exe", Path(sys.executable)):
        if candidate.exists() and subprocess.run([str(candidate), "-I", "-c", "import adb_shell"],
                                                 capture_output=True).returncode == 0:
            return str(candidate)
    return None


def refusing_port():
    """A port on 127.0.0.1 that refuses: one the system just handed out and nothing listens on any more.
    (A socket kept bound without listen() is no good: macOS lets the connection time out instead.)"""
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class StlsPort:
    """Answers one connection's first packet with STLS_PACKET, then waits for the client to hang up, as a
    Wireless debugging port waits for the TLS handshake."""

    def __init__(self):
        self.sock = socket.socket()
        self.sock.bind(("127.0.0.1", 0))
        self.sock.listen(1)
        self.sock.settimeout(60)
        self.port = self.sock.getsockname()[1]
        self.first = b""
        self.thread = threading.Thread(target=self.serve, daemon=True)
        self.thread.start()

    def read(self, conn, size):
        data = b""
        while len(data) < size:
            chunk = conn.recv(size - len(data))
            if not chunk:
                break
            data += chunk
        return data

    def serve(self):
        try:
            conn, _ = self.sock.accept()
        except OSError:
            return
        with conn:
            conn.settimeout(60)
            header = self.read(conn, 24)
            if len(header) == 24:
                self.read(conn, struct.unpack("<6I", header)[3])   # the payload, so closing sends no RST
            self.first = header
            self.answer(conn)

    def answer(self, conn):
        conn.sendall(STLS_PACKET)
        try:
            while conn.recv(4096):
                pass
        except OSError:
            pass

    def close(self):
        self.sock.close()
        self.thread.join(timeout=60)


class ResetPort(StlsPort):
    """Reads one connection's first packet, then drops the connection with a reset instead of answering:
    the port took the connection, so the TV was reached."""

    def answer(self, conn):
        # l_onoff 1, l_linger 0, so closing sends RST. Windows takes two u_shorts, the others two ints.
        linger = struct.pack("HH" if os.name == "nt" else "ii", 1, 0)
        conn.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, linger)


class TvConnectErrorTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.python = client_python()
        if not cls.python:
            raise unittest.SkipTest("these runs need adb_shell (tools/requirements.txt)")
        cls._tmp = tempfile.TemporaryDirectory()
        base = Path(cls._tmp.name)
        cls.tv = base / "tools" / "tv.py"
        cls.tv.parent.mkdir()
        shutil.copy(SCRIPT, cls.tv)
        cls.home = base / "home"
        cls.home.mkdir()

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def run_tv(self, args, **env):
        clean = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        clean.update(HOME=str(self.home), USERPROFILE=str(self.home), **env)
        return subprocess.run([self.python, "-I", *args], cwd=str(ROOT), env=clean, capture_output=True,
                              text=True, timeout=120)

    def assertExplained(self, result):
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("See docs/TROUBLESHOOTING.md#connecting.", result.stderr)

    def assertKeptTraceback(self, result, error):
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Traceback", result.stderr)
        self.assertIn(error, result.stderr)
        self.assertNotIn("could not reach", result.stderr)
        self.assertNotIn("docs/TROUBLESHOOTING.md", result.stderr)

    def test_a_refused_port_names_the_way_to_port_5555(self):
        port = refusing_port()
        for env in ({"TV_HOST": f"127.0.0.1:{port}"}, {"TV_HOST": "127.0.0.1", "TV_PORT": str(port)}):
            with self.subTest(env=env):
                result = self.run_tv([str(self.tv), "--probe"], **env)
                self.assertExplained(result)
                self.assertIn(f"127.0.0.1:{port} refused the connection", result.stderr)
                self.assertIn("If the TV has Wireless debugging, pair the TV once with the stock adb",
                              result.stderr)
                self.assertIn("`adb tcpip 5555`", result.stderr)
                self.assertIn("takes TV_HOST=<tv ip>:<port>", result.stderr)
                self.assertNotIn("ConnectionRefusedError", result.stderr)

    def test_a_wireless_debugging_port_is_named_as_one(self):
        server = StlsPort()
        self.addCleanup(server.close)
        result = self.run_tv([str(self.tv), "--probe"], TV_HOST=f"127.0.0.1:{server.port}")
        self.assertExplained(result)
        self.assertEqual(b"CNXN", server.first[:4], "tv.py opened the ADB handshake that STLS answers")
        self.assertIn(f"127.0.0.1:{server.port} is a Wireless debugging port: it asks for TLS, which tv.py "
                      "cannot use.", result.stderr)
        self.assertIn("`adb pair`", result.stderr)
        self.assertIn("`adb tcpip 5555`", result.stderr)
        self.assertIn("set TV_HOST to the TV's IP alone and leave TV_PORT unset", result.stderr)
        self.assertNotIn("InvalidCommandError", result.stderr)

    def test_a_handshake_that_times_out_points_at_the_prompt(self):
        for error in ("TcpTimeoutException", "AdbTimeoutError"):
            with self.subTest(error=error):
                message = adb_errors.AT_CONNECT[error]
                result = self.run_tv(["-c", RUNNER, str(self.tv), error, message], TV_HOST="192.0.2.1")
                self.assertExplained(result)
                self.assertIn("192.0.2.1:5555 took the connection, but ADB did not finish the handshake "
                              f"({error}: {message}).", result.stderr)
                self.assertIn('If the TV shows "Allow USB debugging?", accept it', result.stderr)

    def test_a_network_error_says_the_tv_could_not_be_reached(self):
        result = self.run_tv(["-c", RUNNER, str(self.tv), "OSError", "No route to host"], TV_HOST="192.0.2.1")
        self.assertExplained(result)
        self.assertIn("could not reach 192.0.2.1:5555 ([Errno 65] No route to host). Is the TV on and on "
                      "this network?", result.stderr)

    # RUNNER raises OSError(65, ...), and 65 is EHOSTUNREACH on macOS but ENOPKG on Linux, so this is the
    # platform tv.py adds the Local Network note on. Skipped elsewhere rather than faked: the note exists to
    # be read on a Mac, and a test that passed on Linux CI while failing on a contributor's Mac is worse
    # than no test. The sentence above stays in both messages, so the test before this one covers both.
    @unittest.skipUnless(sys.platform == "darwin", "the Local Network note is macOS-only")
    def test_macos_adds_the_local_network_privacy_note_to_an_unreachable_host(self):
        result = self.run_tv(["-c", RUNNER, str(self.tv), "OSError", "No route to host"], TV_HOST="192.0.2.1")
        self.assertExplained(result)
        self.assertIn("On macOS this is also what Local Network privacy looks like.", result.stderr)
        # The two things a reader gets wrong: which app to allow, and whether the network is at fault.
        self.assertIn("granting it to python or adb does nothing", result.stderr)
        self.assertIn("try the default gateway", result.stderr)

    @unittest.skipIf(sys.platform == "darwin", "the Local Network note belongs on macOS")
    def test_other_platforms_keep_the_plain_unreachable_message(self):
        result = self.run_tv(["-c", RUNNER, str(self.tv), "OSError", "No route to host"], TV_HOST="192.0.2.1")
        self.assertExplained(result)
        self.assertNotIn("Local Network privacy", result.stderr)

    def test_an_error_it_has_nothing_to_add_to_keeps_its_traceback(self):
        # Only the STLS answer is a Wireless debugging port; any other unknown command is left as it was.
        for error, message in (("InvalidChecksumError", "Received checksum 1 != 2"),
                               ("InvalidCommandError", "Unknown command: 1 = 'b'\\x01\\x00\\x00\\x00''")):
            with self.subTest(error=error):
                result = self.run_tv(["-c", RUNNER, str(self.tv), error, message], TV_HOST="192.0.2.1")
                self.assertKeptTraceback(result, error)

    def test_a_key_file_error_keeps_its_traceback(self):
        # get_signer() runs inside connect(), before any socket opens, so "could not reach" would send the
        # reader to the TV and the network over a file on this computer. The traceback names the file.
        with self.subTest(key="adbkey.pub missing"):
            tmp = tempfile.TemporaryDirectory()
            self.addCleanup(tmp.cleanup)
            tv = Path(tmp.name) / "tools" / "tv.py"
            (tv.parent / "adbkey").mkdir(parents=True)
            shutil.copy(SCRIPT, tv)
            (tv.parent / "adbkey" / "adbkey").write_text("a private key whose adbkey.pub is gone\n")
            result = self.run_tv([str(tv), "--probe"], TV_HOST=f"127.0.0.1:{refusing_port()}")
            self.assertKeptTraceback(result, "FileNotFoundError")
            self.assertIn("adbkey.pub", result.stderr)
        with self.subTest(key="not readable, as a stock key a `sudo adb` run left owned by root"):
            key = str(self.home / ".android" / "adbkey")
            result = self.run_tv(["-c", RUNNER, str(self.tv), "PermissionError", "Permission denied", key],
                                 TV_HOST="192.0.2.1")
            self.assertKeptTraceback(result, "PermissionError")
            self.assertIn(key, result.stderr)

    def test_a_connection_reset_in_the_handshake_keeps_its_traceback(self):
        server = ResetPort()
        self.addCleanup(server.close)
        result = self.run_tv([str(self.tv), "--probe"], TV_HOST=f"127.0.0.1:{server.port}")
        self.assertEqual(b"CNXN", server.first[:4], "tv.py opened the ADB handshake the reset drops")
        self.assertKeptTraceback(result, "ConnectionResetError")

    def test_connect_itself_still_raises(self):
        # check.py (through recovery.connect()) and privacy.py call tv.connect() and catch what it raises.
        result = self.run_tv(["-c", RUNNER, str(self.tv), "connect"], TV_HOST=f"127.0.0.1:{refusing_port()}")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("ConnectionRefusedError", result.stdout.strip())


if __name__ == "__main__":
    unittest.main()

"""bench.py hands tools/tv.py the TV's address and port as tools/tv.py reads them.

docs/TROUBLESHOOTING.md says "Every script here honours `TV_PORT`", tools/relay.md gives the TV's port
with the address (`192.168.1.50:5555`), and tools/tv.py's own usage line 4 is "Set TV_HOST to the TV's IP
(or IP:port)". bench.py - the script docs/workflows/perf-ab.md measures a launcher change with - read
both itself, and neither as tools/tv.py does:

- `--port` had `type=int` with `default=int(os.environ.get("TV_PORT", "5555"))`, so a `TV_PORT` that is
  not a number (`TV_PORT=abc`, or `TV_PORT=` exported empty, which tools/tv.py reads as 5555) ended in a
  `ValueError: invalid literal for int()` traceback from that line, exit 1, before anything was read.
- `--host 192.0.2.20:5877` (or `TV_HOST` in that form) kept its `:port` in the host: TV._connect set
  `tv_client.HOST, tv_client.PORT = self.host, int(self.port)` after importing tools/tv.py, overwriting
  the split tv.py had just made from the environment bench.py gave it, so the client was built for a
  machine literally named `192.0.2.20:5877`, on the default port 5555.

bench.py now leaves both to tools/tv.py: `--port`/`TV_PORT` is passed on as text, and TV._connect reads
back the HOST and PORT tv.py parsed out of the environment bench.py set for it - where the split and the
1..65535 refusal already are, in tv.py's own words (`not a port number: '70000'`, which bench.py already
reached for an out-of-range port, because importing tools/tv.py checks it).

The tests run the real tools/tv.py against a stub adb_shell that records the host and port
`AdbDeviceTcp` was built with, so the target is observed rather than assumed; HOME points at a temporary
folder so no ADB key is written into the checkout.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "bench.py"
STUB_DEVICE = '''\
import os


class AdbDeviceTcp:
    """Stands in for adb_shell's client: records the host and port it was built with."""

    def __init__(self, host, port, **kwargs):
        with open(os.environ["STUB_MARK"], "w") as f:
            f.write(f"{host}:{port}")

    def connect(self, **kwargs):
        pass

    def shell(self, command, **kwargs):
        return b""

    def push(self, local, remote, **kwargs):
        pass

    def pull(self, remote, local, **kwargs):
        pass

    def close(self):
        pass
'''
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"


def stub_tree(base):
    """A package tree that answers tools/tv.py's three adb_shell imports."""
    (base / "adb_shell" / "auth").mkdir(parents=True)
    (base / "adb_shell" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "adb_device.py").write_text(STUB_DEVICE, encoding="utf-8")
    (base / "adb_shell" / "auth" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "auth" / "keygen.py").write_text(STUB_KEYGEN, encoding="utf-8")
    (base / "adb_shell" / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER, encoding="utf-8")
    return base


class BenchTargetAddressTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.base = stub_tree(Path(cls._tmp.name) / "stub")
        # A stock ADB key in the temporary HOME: tools/tv.py reuses it, so neither tools/adbkey in the
        # checkout nor the real ~/.android is touched by these runs.
        home = Path(cls._tmp.name) / "home"
        (home / ".android").mkdir(parents=True)
        (home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.home = home

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def run_bench(self, args, tv_host="192.0.2.20", tv_port=None):
        """(exit status, stdout, stderr, the "host:port" the client was built with or None)."""
        work = Path(tempfile.mkdtemp(dir=Path(self._tmp.name)))
        mark = work / "mark"
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env["HOME"] = str(self.home)
        if os.name == "nt":
            # Windows expands `~` from USERPROFILE, not HOME, so tools/tv.py would make a key in the
            # real ~/.android instead of reusing the one this run put in the temporary home.
            env["USERPROFILE"] = str(self.home)
        env["STUB_MARK"] = str(mark)
        env["PYTHONPATH"] = str(self.base)
        if tv_host is not None:
            env["TV_HOST"] = tv_host
        if tv_port is not None:
            env["TV_PORT"] = tv_port
        r = subprocess.run([sys.executable, str(SCRIPT), *args], cwd=str(ROOT), env=env,
                           capture_output=True, text=True, timeout=60)
        opened = mark.read_text(encoding="utf-8") if mark.exists() else None
        return r.returncode, r.stdout, r.stderr, opened

    def refused(self, args, tv_host="192.0.2.20", tv_port=None, value=None):
        """A port bench.py will not use: a sentence naming it, no traceback, nothing opened."""
        status, out, err, opened = self.run_bench(args, tv_host=tv_host, tv_port=tv_port)
        self.assertIsNone(opened, f"nothing should have been opened: {out}{err}")
        self.assertNotIn("Traceback", err, err)
        self.assertIn("not a port number", err, err)
        if value is not None:
            self.assertIn(value, err, err)
        self.assertEqual(status, 1, f"{out}{err}")

    def test_a_host_with_a_port_is_split(self):
        # tools/relay.md gives the TV's port with the address. This reached the client as a machine
        # called "192.0.2.20:5877" on the default port, so nothing could connect.
        status, out, err, opened = self.run_bench(["--host", "192.0.2.20:5877"])
        self.assertEqual(opened, "192.0.2.20:5877", out + err)

    def test_the_environment_may_carry_the_port_in_the_address(self):
        status, out, err, opened = self.run_bench([], tv_host="192.0.2.20:5877")
        self.assertEqual(opened, "192.0.2.20:5877", out + err)

    def test_the_port_in_the_address_wins_over_the_port_option(self):
        # tools/tv.py's own rule: TV_HOST's port is used ahead of TV_PORT, and --port sets TV_PORT.
        status, out, err, opened = self.run_bench(["--host", "192.0.2.20:5877", "--port", "15555"])
        self.assertEqual(opened, "192.0.2.20:5877", out + err)

    def test_the_port_option_and_the_environment_still_choose_the_port(self):
        for args, tv_port, expected in ((["--port", "15555"], None, "15555"),
                                        ([], "15555", "15555")):
            with self.subTest(args=args, tv_port=tv_port):
                status, out, err, opened = self.run_bench(args, tv_host="192.0.2.20", tv_port=tv_port)
                self.assertEqual(opened, f"192.0.2.20:{expected}", out + err)

    def test_an_empty_port_means_5555_as_it_does_for_the_other_tools(self):
        # `TV_PORT=` exported empty was the same ValueError traceback; tools/tv.py reads it as 5555.
        status, out, err, opened = self.run_bench([], tv_port="")
        self.assertEqual(opened, "192.0.2.20:5555", out + err)

    def test_a_port_that_is_not_a_number_is_refused(self):
        # This was a ValueError traceback from the argparse default, before anything was read.
        self.refused([], tv_port="abc", value="abc")

    def test_a_port_outside_one_to_65535_is_refused(self):
        # tools/tv.py refuses these when it is imported; the arguments must keep reaching it.
        for args, tv_port in ((["--port", "70000"], None), ([], "70000"),
                              (["--port", "0"], None), (["--port", "-1"], None)):
            with self.subTest(args=args, tv_port=tv_port):
                self.refused(args, tv_port=tv_port)

    def test_a_bad_port_in_the_address_is_refused(self):
        self.refused(["--host", "192.0.2.20:70000"], tv_host=None, value="70000")

    def test_a_plain_host_still_reaches_the_tv_on_5555(self):
        # The guard: a run that is not refused still opens the connection it did before.
        status, out, err, opened = self.run_bench(["--host", "192.0.2.20"])
        self.assertEqual(opened, "192.0.2.20:5555", out + err)
        self.assertIn("no component answers HOME on this TV", err, err)


if __name__ == "__main__":
    unittest.main()

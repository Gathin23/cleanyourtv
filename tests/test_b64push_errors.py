"""tools/b64push.py says why it stopped on stderr, and a TV it cannot connect to gets tv.py's message.

Several add-on scripts run b64push.py with its stdout thrown away (`>/dev/null`, `| Out-Null`,
`| grep -q md5`), and theme.ps1 keeps it in a variable it does not print when the push fails. A chunk the TV
refused was reported only on stdout ("chunk 0 failed: ..."), and so was the md5 line of a copy that did not
match, so those scripts showed nothing, or only "... was left as it was.". Both now go to stderr. The md5
line of a copy that matched stays on stdout, where the photo and news screensaver scripts grep for "md5".

A refused port, a Wireless debugging port or a TV that cannot be reached ended in a traceback from
tv.connect(), where tools/tv.py itself explains each one (#1578, tests/test_tv_connect_errors.py). b64push.py
now prints tv.py's explanation and exits 2, since nothing was sent. An error tv.py has nothing to add to,
such as a key file it cannot read, keeps its traceback.

The first runs use a fake tools/tv.py whose "TV shell" is /bin/sh on this machine. The connection runs use
the real tools/tv.py with adb_shell and real sockets on 127.0.0.1, from a copy in a temporary folder with HOME
there too, so the ADB key it creates stays out of the checkout and away from ~/.android.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_tv_connect_errors as connect_errors  # noqa: E402  (a module, so its tests do not run twice)

ROOT = Path(__file__).resolve().parents[1]

# A tools/tv.py whose shell is /bin/sh. adb_shell's shell() hands back what the TV's shell wrote to stderr
# mixed into its output, so this one does too.
FAKE_SHELL_TV = r'''
import os
import subprocess


class Device:
    max_chunk_size = 4096

    def shell(self, command, read_timeout_s=None):
        # Every chunk becomes "ABC", so the copy that arrives has another md5.
        if os.environ.get("FAKE_CORRUPT") and command.startswith("echo '"):
            command = "echo 'QUJD' >>" + command.split(">>", 1)[1]
        return subprocess.run(["/bin/sh", "-c", command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True).stdout

    def close(self):
        pass


def connect():
    return Device()
'''


@unittest.skipUnless(os.name == "posix" and shutil.which("md5sum") and shutil.which("base64"),
                     "the fake TV's shell is /bin/sh, and md5sum and base64 are needed")
class TransferErrorsTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        tools = self.base / "tools"
        tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", tools / "b64push.py")
        (tools / "tv.py").write_text(FAKE_SHELL_TV)
        self.script = tools / "b64push.py"
        self.remote_dir = self.base / "remote"
        self.remote_dir.mkdir()
        self.data = os.urandom(10000)   # three chunks at a 4096-byte payload
        self.local = self.base / "app.apk"
        self.local.write_bytes(self.data)

    def push(self, remote, **env):
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update(env)
        return subprocess.run([sys.executable, str(self.script), str(self.local), str(remote)], env=full,
                              capture_output=True, text=True, timeout=120)

    def test_a_chunk_the_tv_refuses_is_explained_on_stderr(self):
        missing = self.remote_dir / "no-such-folder" / "app.apk"
        read_only = self.remote_dir / "read-only" / "app.apk"
        read_only.parent.mkdir()
        read_only.write_bytes(b"old contents")
        read_only.parent.chmod(0o555)
        self.addCleanup(read_only.parent.chmod, 0o755)
        for target in (missing, read_only):
            with self.subTest(target=target.parent.name):
                if target == read_only and os.geteuid() == 0:
                    self.skipTest("root writes into a read-only folder")
                result = self.push(target)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertIn("chunk 0 failed: ", result.stderr)
                # The shell's own words, which name the file it could not write.
                self.assertIn(f"{target}.b64.part", result.stderr)
                self.assertIn(f"{target} was left as it was.", result.stderr)
                self.assertNotIn("failed", result.stdout)
                self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(read_only.read_bytes(), b"old contents")

    def test_a_checksum_that_does_not_match_is_explained_on_stderr(self):
        target = self.remote_dir / "app.apk"
        target.write_bytes(b"old contents")
        result = self.push(target, FAKE_CORRUPT="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn(f"local: {hashlib.md5(self.data).hexdigest()} MISMATCH", result.stderr)
        self.assertIn(f"{target} was left as it was.", result.stderr)
        self.assertNotIn("md5", result.stdout)
        self.assertEqual(target.read_bytes(), b"old contents")

    def test_a_reason_starts_its_own_line_when_both_streams_go_to_one_log(self):
        # 2>&1 into a log, or an agent reading both streams: the progress line is ended before the
        # reason, so the MISMATCH line is not glued to "chunk 3/3".
        target = self.remote_dir / "app.apk"
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full["FAKE_CORRUPT"] = "1"
        result = subprocess.run([sys.executable, str(self.script), str(self.local), str(target)], env=full,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
        lines = result.stdout.replace("\r", "\n").splitlines()
        self.assertTrue(any(line.startswith("md5 remote: ") for line in lines), result.stdout)
        self.assertEqual(lines[-1], f"{target} was left as it was.", result.stdout)

    def test_a_copy_that_matches_keeps_its_md5_line_on_stdout(self):
        # addons/photo-screensaver/sync.sh and addons/news-screensaver/setup.sh grep stdout for "md5".
        target = self.remote_dir / "app.apk"
        result = self.push(target)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"local: {hashlib.md5(self.data).hexdigest()} MATCH", result.stdout)
        self.assertEqual(result.stderr, "")
        self.assertEqual(target.read_bytes(), self.data)


class ConnectErrorsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.python = connect_errors.client_python()
        if not cls.python:
            raise unittest.SkipTest("these runs need adb_shell (tools/requirements.txt)")
        cls._tmp = tempfile.TemporaryDirectory()
        base = Path(cls._tmp.name)
        tools = base / "tools"
        tools.mkdir()
        for name in ("b64push.py", "tv.py"):
            shutil.copy(ROOT / "tools" / name, tools / name)
        cls.script = tools / "b64push.py"
        cls.home = base / "home"
        cls.home.mkdir()
        cls.local = base / "app.apk"
        cls.local.write_bytes(b"an apk")

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def push(self, script, **env):
        clean = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        clean.update(HOME=str(self.home), USERPROFILE=str(self.home), **env)
        return subprocess.run([self.python, "-I", str(script), str(self.local), "/data/local/tmp/app.apk"],
                              cwd=str(ROOT), env=clean, capture_output=True, text=True, timeout=120)

    def assertExplained(self, result):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertIn("See docs/TROUBLESHOOTING.md#connecting.\nNothing was sent to the TV.", result.stderr)

    def test_a_refused_port_gets_the_message_tv_py_gives(self):
        port = connect_errors.refusing_port()
        result = self.push(self.script, TV_HOST=f"127.0.0.1:{port}")
        self.assertExplained(result)
        self.assertIn(f"127.0.0.1:{port} refused the connection: nothing listens for ADB there",
                      result.stderr)
        self.assertNotIn("ConnectionRefusedError", result.stderr)

    def test_a_wireless_debugging_port_gets_the_message_tv_py_gives(self):
        server = connect_errors.StlsPort()
        self.addCleanup(server.close)
        result = self.push(self.script, TV_HOST=f"127.0.0.1:{server.port}")
        self.assertExplained(result)
        self.assertIn(f"127.0.0.1:{server.port} is a Wireless debugging port", result.stderr)
        self.assertNotIn("InvalidCommandError", result.stderr)

    def test_a_key_file_error_keeps_its_traceback(self):
        # tv.py has nothing to add to it: the traceback names the file, which is on this computer.
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        tools = Path(tmp.name) / "tools"
        (tools / "adbkey").mkdir(parents=True)
        for name in ("b64push.py", "tv.py"):
            shutil.copy(ROOT / "tools" / name, tools / name)
        (tools / "adbkey" / "adbkey").write_text("a private key whose adbkey.pub is gone\n")
        result = self.push(tools / "b64push.py", TV_HOST=f"127.0.0.1:{connect_errors.refusing_port()}")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("Traceback", result.stderr)
        self.assertIn("FileNotFoundError", result.stderr)
        self.assertIn("adbkey.pub", result.stderr)
        self.assertNotIn("Nothing was sent to the TV.", result.stderr)


if __name__ == "__main__":
    unittest.main()

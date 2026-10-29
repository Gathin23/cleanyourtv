"""Device-free regression checks using isolated transport stubs."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

# A tools/tv.py whose "TV shell" is /bin/sh on this machine, so b64push.py's real commands run.
FAKE_SHELL_TV = r'''
import json
import os
import subprocess

LOG = os.environ["FAKE_LOG"]


class Device:
    max_chunk_size = int(os.environ.get("FAKE_MAX", "4096"))

    def shell(self, command, read_timeout_s=None):
        with open(LOG, "a") as f:
            f.write(json.dumps(command) + "\n")
        # Inject transport exceptions after staging exists, to verify cleanup on every failure phase.
        if os.environ.get("FAKE_RAISE") and command.startswith(os.environ["FAKE_RAISE"]):
            raise OSError("simulated transport failure")
        if os.environ.get("FAKE_CORRUPT") and command.startswith("echo '"):
            command = "echo 'QUJD' >>" + command.split(">>", 1)[1]
        return subprocess.run(["/bin/sh", "-c", command], capture_output=True, text=True).stdout

    def close(self):
        pass


def connect():
    if os.environ.get("FAKE_NO_CONNECT"):
        raise RuntimeError("fake: connect() was called")
    return Device()
'''


class TempDirTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)


@unittest.skipUnless(shutil.which("md5sum") and shutil.which("base64"), "needs md5sum and base64")
class B64PushTest(TempDirTest):
    """M4 (safe replace of the remote file) and L3 (usage) in tools/b64push.py."""

    def setUp(self):
        super().setUp()
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", self.tools / "b64push.py")
        (self.tools / "tv.py").write_text(FAKE_SHELL_TV)
        self.remote_dir = self.base / "remote"
        self.remote_dir.mkdir()
        self.log = self.base / "log.jsonl"

    def push(self, *args, **env):
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update(FAKE_LOG=str(self.log), **env)
        return subprocess.run([sys.executable, str(self.tools / "b64push.py"), *args], env=full,
                              capture_output=True, text=True, timeout=120)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()] if self.log.exists() else []

    def local(self, data):
        path = self.base / "local.bin"
        path.write_bytes(data)
        return path

    def test_missing_arguments_print_usage_without_tv_host(self):
        for args in ([], ["only-one"], ["a", "b", "c"]):
            with self.subTest(args=args):
                result = self.push(*args, FAKE_NO_CONNECT="1")
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertIn("usage", result.stderr.lower())
                self.assertNotIn("Traceback", result.stderr)

    def test_unsafe_remote_path_is_refused_before_connecting(self):
        src = self.local(b"x")
        for remote in ("relative/path", "/data/local/tmp/a b", "/data/local/tmp/$(id)",
                       "/data/local/tmp/a;rm -rf /", "/data/local/../tmp/x", "/data/'q'", ""):
            with self.subTest(remote=remote):
                result = self.push(str(src), remote, FAKE_NO_CONNECT="1")
                self.assertEqual(result.returncode, 2, result.stderr)
                self.assertNotIn("connect() was called", result.stderr)
                self.assertEqual(self.commands(), [])

    def test_transfer_replaces_the_target_only_after_the_checksum_matches(self):
        data = os.urandom(10000)   # several chunks at a 4096-byte payload
        target = self.remote_dir / "file.apk"
        target.write_bytes(b"old contents")
        result = self.push(str(self.local(data)), str(target))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(target.read_bytes(), data)
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"],
                         "temporary files are cleaned up")
        first = self.commands()[0]
        self.assertNotIn(f"rm -f '{target}' ", first + " ", "the target must not be deleted up front")
        for command in self.commands():
            self.assertIn(f"'{target}", command, "every remote path is single-quoted")

    def test_failed_checksum_leaves_the_original_intact(self):
        target = self.remote_dir / "file.apk"
        target.write_bytes(b"old contents")
        result = self.push(str(self.local(os.urandom(5000))), str(target), FAKE_CORRUPT="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("MISMATCH", result.stdout)
        self.assertEqual(target.read_bytes(), b"old contents")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])

    def test_transport_exceptions_remove_staging_and_keep_original(self):
        for phase in ("echo '", "base64 -d", "mv -f"):
            with self.subTest(phase=phase):
                target = self.remote_dir / "file.apk"
                target.write_bytes(b"old contents")
                result = self.push(str(self.local(b"new contents")), str(target), FAKE_RAISE=phase)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(target.read_bytes(), b"old contents")
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])

    def test_dot_dot_anywhere_is_refused(self):
        result = self.push(str(self.local(b"x")), str(self.remote_dir / "a..b"), FAKE_NO_CONNECT="1")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("connect() was called", result.stderr)

    def test_empty_file(self):
        target = self.remote_dir / "empty.txt"
        target.write_bytes(b"not empty yet")
        result = self.push(str(self.local(b"")), str(target))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(target.read_bytes(), b"")
        self.assertIn(hashlib.md5(b"").hexdigest(), result.stdout)
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["empty.txt"])


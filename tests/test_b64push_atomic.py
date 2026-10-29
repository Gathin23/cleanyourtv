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
        # SELinux refusing rename(2) in /data/local/tmp while cp works, in toybox's words (#1588).
        if os.environ.get("FAKE_NO_RENAME") and command.startswith("mv "):
            return "mv: bad '%s': Permission denied\n" % command.split("'")[1]
        if os.environ.get("FAKE_NO_COPY") and command.startswith("cp "):
            return "cp: %s: Permission denied\n" % command.split("'")[3]
        # A connection that drops while cp is writing: the target is cut short, then the transport raises.
        if os.environ.get("FAKE_PARTIAL_COPY") and command.startswith("cp "):
            with open(command.split("'")[1], "rb") as src, open(command.split("'")[3], "wb") as dst:
                dst.write(src.read(int(os.environ["FAKE_PARTIAL_COPY"])))
            raise OSError("simulated connection drop mid-copy")
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


@unittest.skipUnless(os.name == "posix" and shutil.which("md5sum") and shutil.which("base64"),
                     "the fake TV's shell is /bin/sh, and md5sum and base64 are needed")
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
        self.assertIn("MISMATCH", result.stderr)
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

    def test_a_refused_move_copies_the_verified_file_and_checks_it_again(self):
        # TCL ak30a5 on 20250610 refuses mv in /data/local/tmp, while cp works there (#1588).
        data = os.urandom(10000)
        target = self.remote_dir / "file.apk"
        for before in (b"old contents", None):
            with self.subTest(target_existed=before is not None):
                if before is None:
                    target.unlink()
                else:
                    target.write_bytes(before)
                self.log.unlink(missing_ok=True)
                result = self.push(str(self.local(data)), str(target), FAKE_NO_RENAME="1")
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(target.read_bytes(), data)
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"],
                                 "temporary files are cleaned up")
                copies = [c for c in self.commands() if c.startswith("cp ")]
                self.assertEqual(len(copies), 1, self.commands())
                self.assertIn(f"md5sum '{target}'", copies[0], "the copy on the target is checked again")
                self.assertIn("Permission denied", result.stdout, "the output says why it copied")

    def test_a_move_that_works_is_not_followed_by_a_copy(self):
        target = self.remote_dir / "file.apk"
        result = self.push(str(self.local(b"new contents")), str(target))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual([c for c in self.commands() if c.startswith("cp ")], [])

    def test_a_refused_move_and_a_failed_copy_exit_1_and_clear_staging(self):
        for env in ({"FAKE_NO_COPY": "1"}, {"FAKE_RAISE": "cp -f"}):
            with self.subTest(env=env):
                target = self.remote_dir / "file.apk"
                target.write_bytes(b"old contents")
                result = self.push(str(self.local(b"new contents")), str(target),
                                   FAKE_NO_RENAME="1", **env)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertEqual(target.read_bytes(), b"old contents", "the fake refused before writing")
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])
                self.assertIn(f"bad '{target}.part': Permission denied", result.stderr)
                self.assertIn(f"{target} may be incomplete", result.stderr)
                if "FAKE_NO_COPY" in env:
                    self.assertIn(f"cp: {target}: Permission denied", result.stderr)

    def test_a_copy_interrupted_part_way_says_the_target_may_be_incomplete(self):
        # The connection drops after cp has written half the file: the target is cut short.
        data = os.urandom(10000)
        target = self.remote_dir / "file.apk"
        target.write_bytes(b"old contents")
        result = self.push(str(self.local(data)), str(target), FAKE_NO_RENAME="1", FAKE_PARTIAL_COPY="5000")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(target.read_bytes(), data[:5000], "the fake cut the copy short")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])
        self.assertIn(f"bad '{target}.part': Permission denied", result.stderr)
        self.assertIn(f"{target} may be incomplete", result.stderr)
        self.assertIn("simulated connection drop mid-copy", result.stderr, "the transport error still shows")

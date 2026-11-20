"""Device-free regression checks using isolated transport stubs.

The stand-in TV below is this machine's /bin/sh, so the folder tools/b64push.py writes into has to be
one this machine can write. It cannot be the test's temp folder itself: b64push.py refuses a remote
path outside `[A-Za-z0-9._/-]`, because that path is pasted into the TV's shell, and TMPDIR may name
a folder with a space or a character outside that set - which turned every case here red for a
reason about the temp folder rather than about the tool. So the fake maps the TV's own folder
(/data/local/tmp, the path the tool and the scripts that call it use) onto a folder under the temp
dir, and the tests name their target the way a TV does.
"""
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

# The folder the TV's side names, and the local folder the fake stands in for it with (FAKE_TV_ROOT).
TV_DIR = "/data/local/tmp"

# A tools/tv.py whose "TV shell" is /bin/sh on this machine, so b64push.py's real commands run.
FAKE_SHELL_TV = r'''
import json
import os
import subprocess

LOG = os.environ["FAKE_LOG"]


def here(command):
    """The TV's folder is a folder on this machine: /data/local/tmp/x -> <FAKE_TV_ROOT>/x."""
    return command.replace(os.environ["FAKE_TV_DIR"], os.environ["FAKE_TV_ROOT"])


class Device:
    max_chunk_size = int(os.environ.get("FAKE_MAX", "4096"))

    def shell(self, command, read_timeout_s=None):
        # Logged as the TV receives it, which is what the cases here read back; only then is it run
        # against the folder standing in for the TV's own.
        with open(LOG, "a") as f:
            f.write(json.dumps(command) + "\n")
        on_tv, command = command, here(command)
        # Inject transport exceptions after staging exists, to verify cleanup on every failure phase.
        if os.environ.get("FAKE_RAISE") and command.startswith(os.environ["FAKE_RAISE"]):
            raise OSError("simulated transport failure")
        if os.environ.get("FAKE_CORRUPT") and command.startswith("echo '"):
            command = "echo 'QUJD' >>" + command.split(">>", 1)[1]
        # SELinux refusing rename(2) in /data/local/tmp while cp works, in toybox's words (#1588).
        if os.environ.get("FAKE_NO_RENAME") and command.startswith("mv "):
            return os.environ.get("FAKE_MOVE_REFUSAL") or "mv: bad '%s': Permission denied\n" % on_tv.split("'")[1]
        if os.environ.get("FAKE_NO_COPY") and command.startswith("cp "):
            return "cp: %s: Permission denied\n" % on_tv.split("'")[3]
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

    def on_the_tv(self, name):
        """The path the TV is given, where the stand-in writes it, and the copy to read back."""
        return f"{TV_DIR}/{name}"

    def write(self, name, data):
        (self.remote_dir / name).write_bytes(data)

    def read(self, name):
        return (self.remote_dir / name).read_bytes()

    def push(self, *args, **env):
        full = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        full.update(FAKE_LOG=str(self.log), FAKE_TV_DIR=TV_DIR, FAKE_TV_ROOT=str(self.remote_dir),
                    **env)
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
        target = self.on_the_tv("file.apk")
        self.write("file.apk", b"old contents")
        result = self.push(str(self.local(data)), target)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.read("file.apk"), data)
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"],
                         "temporary files are cleaned up")
        first = self.commands()[0]
        self.assertNotIn(f"rm -f '{target}' ", first + " ", "the target must not be deleted up front")
        for command in self.commands():
            self.assertIn(f"'{target}", command, "every remote path is single-quoted")

    def test_a_local_file_in_a_folder_with_a_space_and_a_non_ascii_name_is_pushed(self):
        # The shape a photo folder has: a local path with a space and a name outside ASCII. The local
        # side is opened by b64push.py itself rather than by the TV's shell, so any name is fine there;
        # the TV's side stays the plain path the tool documents.
        data = os.urandom(9000)
        folder = self.base / "my photos" / "성수동"
        folder.mkdir(parents=True)
        source = folder / "holiday picture.jpg"
        source.write_bytes(data)
        result = self.push(str(source), self.on_the_tv("picture.jpg"))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.read("picture.jpg"), data)
        self.assertIn(hashlib.md5(data).hexdigest(), result.stdout)

    def test_every_command_fits_one_payload_with_a_long_remote_path(self):
        # tools/theme.sh stages a wallpaper at /data/local/tmp/cyt-theme-wallpapers-<name>.jpg. Over native adb
        # (a 4096-byte payload) a chunk sized without that path made `echo '<chunk>' >> <path>` too long:
        # "shell command too long", and no picture of the theme reached the TV.
        data = os.urandom(20000)
        name = "x" * 40 + ".jpg"
        result = self.push(str(self.local(data)), self.on_the_tv(name))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.read(name), data)
        self.assertLessEqual(max(len(c) for c in self.commands()), 4096)

    def test_a_transport_too_small_for_the_staging_path_is_refused_without_sending(self):
        # The floor on the chunk from #2614: a staging name that leaves under 256 bytes per chunk,
        # or none at all, is an argument error (exit 2, nothing sent), not a push spread over one-
        # and two-byte commands, and not an empty transfer that ends in a MISMATCH either.
        target = self.on_the_tv("file.jpg")
        for fake_max in ("300", "100"):
            with self.subTest(fake_max=fake_max):
                self.log.unlink(missing_ok=True)
                result = self.push(str(self.local(b"x" * 4096)), target, FAKE_MAX=fake_max)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(f"refusing remote path '{target}'", result.stderr)
                self.assertIn("Nothing was sent to the TV.", result.stderr)
                self.assertEqual(self.commands(), [], "not even the staging cleanup ran")
                self.assertFalse((self.remote_dir / "file.jpg").exists())

    def test_failed_checksum_leaves_the_original_intact(self):
        self.write("file.apk", b"old contents")
        result = self.push(str(self.local(os.urandom(5000))), self.on_the_tv("file.apk"),
                           FAKE_CORRUPT="1")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("MISMATCH", result.stderr)
        self.assertEqual(self.read("file.apk"), b"old contents")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])

    def test_transport_exceptions_remove_staging_and_keep_original(self):
        for phase in ("echo '", "base64 -d", "mv -f"):
            with self.subTest(phase=phase):
                self.write("file.apk", b"old contents")
                result = self.push(str(self.local(b"new contents")), self.on_the_tv("file.apk"),
                                   FAKE_RAISE=phase)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.read("file.apk"), b"old contents")
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])

    def test_dot_dot_anywhere_is_refused(self):
        result = self.push(str(self.local(b"x")), self.on_the_tv("a..b"), FAKE_NO_CONNECT="1")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("connect() was called", result.stderr)

    def test_empty_file(self):
        self.write("empty.txt", b"not empty yet")
        result = self.push(str(self.local(b"")), self.on_the_tv("empty.txt"))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.read("empty.txt"), b"")
        self.assertIn(hashlib.md5(b"").hexdigest(), result.stdout)
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["empty.txt"])

    def test_a_verified_copy_stays_successful_when_the_console_cannot_print_the_move_refusal(self):
        data = b"the complete verified upload"
        target = self.on_the_tv("file.apk")
        refusal = "mv: \u62d2\u7edd\u8bbf\u95ee\n"
        for encoding in ("utf-8:strict", "cp1252:strict", "ascii:strict"):
            with self.subTest(encoding=encoding):
                self.log.unlink(missing_ok=True)
                result = self.push(str(self.local(data)), target, FAKE_NO_RENAME="1",
                                   FAKE_MOVE_REFUSAL=refusal, PYTHONIOENCODING=encoding)
                self.assertEqual(data, self.read("file.apk"))
                self.assertEqual(["file.apk"], sorted(p.name for p in self.remote_dir.iterdir()))
                self.assertTrue(any(c.startswith("cp -f ") for c in self.commands()))
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                self.assertIn(hashlib.md5(data).hexdigest(), result.stdout)
                self.assertNotIn("Traceback", result.stderr)
                if encoding == "utf-8:strict":
                    self.assertIn(refusal.strip(), result.stdout)

    def test_a_refused_move_copies_the_verified_file_and_checks_it_again(self):
        # TCL ak30a5 on 20250610 refuses mv in /data/local/tmp, while cp works there (#1588).
        data = os.urandom(10000)
        target = self.on_the_tv("file.apk")
        for before in (b"old contents", None):
            with self.subTest(target_existed=before is not None):
                if before is None:
                    (self.remote_dir / "file.apk").unlink(missing_ok=True)
                else:
                    self.write("file.apk", before)
                self.log.unlink(missing_ok=True)
                result = self.push(str(self.local(data)), target, FAKE_NO_RENAME="1")
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertEqual(self.read("file.apk"), data)
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"],
                                 "temporary files are cleaned up")
                copies = [c for c in self.commands() if c.startswith("cp ")]
                self.assertEqual(len(copies), 1, self.commands())
                self.assertIn(f"md5sum '{target}'", copies[0], "the copy on the target is checked again")
                self.assertIn("Permission denied", result.stdout, "the output says why it copied")

    def test_a_move_that_works_is_not_followed_by_a_copy(self):
        result = self.push(str(self.local(b"new contents")), self.on_the_tv("file.apk"))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual([c for c in self.commands() if c.startswith("cp ")], [])

    def test_a_refused_move_and_a_failed_copy_exit_1_and_clear_staging(self):
        target = self.on_the_tv("file.apk")
        for env in ({"FAKE_NO_COPY": "1"}, {"FAKE_RAISE": "cp -f"}):
            with self.subTest(env=env):
                self.write("file.apk", b"old contents")
                result = self.push(str(self.local(b"new contents")), target,
                                   FAKE_NO_RENAME="1", **env)
                self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
                self.assertEqual(self.read("file.apk"), b"old contents",
                                 "the fake refused before writing")
                self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])
                self.assertIn(f"bad '{target}.part': Permission denied", result.stderr)
                self.assertIn(f"{target} may be incomplete", result.stderr)
                if "FAKE_NO_COPY" in env:
                    self.assertIn(f"cp: {target}: Permission denied", result.stderr)

    def test_a_copy_interrupted_part_way_says_the_target_may_be_incomplete(self):
        # The connection drops after cp has written half the file: the target is cut short.
        data = os.urandom(10000)
        target = self.on_the_tv("file.apk")
        self.write("file.apk", b"old contents")
        result = self.push(str(self.local(data)), target, FAKE_NO_RENAME="1", FAKE_PARTIAL_COPY="5000")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertEqual(self.read("file.apk"), data[:5000], "the fake cut the copy short")
        self.assertEqual(sorted(p.name for p in self.remote_dir.iterdir()), ["file.apk"])
        self.assertIn(f"bad '{target}.part': Permission denied", result.stderr)
        self.assertIn(f"{target} may be incomplete", result.stderr)
        self.assertIn("simulated connection drop mid-copy", result.stderr, "the transport error still shows")

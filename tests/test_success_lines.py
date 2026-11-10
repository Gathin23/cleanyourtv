"""b64push.py and manage.py take the TV's success marker only as a line of its own.

b64push.py moves the verified copy with `mv -f <part> <target> && echo moved` and counted the move as done
when "moved" appeared anywhere in the answer. A refused move names its paths, so a target such as
/data/local/tmp/removed.jpg made the refusal itself read as success: the copy fallback never ran and the
run exited 0 with the target unchanged. manage.py read pm's answer the same way: a failed install whose
message names a package with "Success" in it counted as installed.

The fake tools/tv.py answers in Python, with no shell, so these cases also run on Windows.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

# Answers b64push.py's commands the way toybox does, without running them; the move is refused.
FAKE_TV = r'''
import json
import os

LOG = os.environ["FAKE_LOG"]


class Device:
    max_chunk_size = 4096

    def shell(self, command, read_timeout_s=None):
        with open(LOG, "a") as f:
            f.write(json.dumps(command) + "\n")
        if command.startswith("echo '"):
            return "ok" + command.rsplit("echo ok", 1)[1] + "\n"
        if command.startswith("base64 -d"):
            return os.environ["FAKE_MD5"] + "  " + command.split("'")[3] + "\n"
        if command.startswith("mv "):
            return "mv: bad '%s': Permission denied\n" % command.split("'")[1]
        if command.startswith("cp "):
            return "cp: %s: Permission denied\n" % command.split("'")[3]
        return ""

    def close(self):
        pass


def connect():
    return Device()
'''


class B64PushMovedLineTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(ROOT / "tools" / "b64push.py", self.tools / "b64push.py")
        (self.tools / "tv.py").write_text(FAKE_TV)
        self.log = self.base / "log.jsonl"
        self.local = self.base / "local.jpg"
        self.local.write_bytes(b"picture bytes")

    def push(self, remote):
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update(FAKE_LOG=str(self.log), FAKE_MD5=hashlib.md5(self.local.read_bytes()).hexdigest())
        return subprocess.run([sys.executable, str(self.tools / "b64push.py"), str(self.local), remote],
                              env=env, capture_output=True, text=True, timeout=60)

    def commands(self):
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def test_a_refused_move_that_names_moved_is_not_a_move(self):
        result = self.push("/data/local/tmp/removed.jpg")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertTrue(any(c.startswith("cp ") for c in self.commands()),
                        "a refused move goes on to the copy fallback")
        self.assertIn("may be incomplete", result.stderr)


class ManagePmSuccessLineTest(unittest.TestCase):
    def call(self, operation, *args):
        out = io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(io.StringIO()):
            return operation(*args)

    def install(self, answer):
        item = {"id": "jewish-calendar", "script": None}
        with mock.patch.object(manage, "run", return_value=0), \
                mock.patch.object(manage, "tv_python", return_value="fake-python"), \
                mock.patch.object(manage, "tv", return_value=answer):
            return self.call(manage.install_addon, item, [])

    def test_a_failure_that_names_a_success_package_is_a_failure(self):
        answer = ("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package com.example.Success signatures do "
                  "not match previously installed version; ignoring!]\n")
        self.assertEqual(self.install(answer), 1)

    def test_pm_success_line_still_counts(self):
        self.assertEqual(self.install("Success\n"), 0)
        self.assertEqual(self.install("\tpkg: /data/local/tmp/addon.apk\r\nSuccess\r\n"), 0)

    def test_the_success_line_is_trimmed_of_the_whitespace_around_it(self):
        self.assertEqual(self.install("  Success \t\n"), 0)

    def remove(self, answer):
        item = {"id": "x", "kind": "add-on", "package": "com.example.addon"}
        with mock.patch.object(manage, "find", return_value=item), \
                mock.patch.object(manage, "tv", return_value=answer):
            return self.call(manage.cmd_remove, "x")

    def test_remove_reads_the_same_line(self):
        self.assertEqual(self.remove("Failure [DELETE_FAILED_INTERNAL_ERROR]\n"), 1)
        self.assertEqual(self.remove("Success\n"), 0)

    def test_a_removal_failure_that_names_a_success_package_is_a_failure(self):
        self.assertEqual(self.remove("Failure [DELETE_FAILED_DEVICE_POLICY_MANAGER: com.example.Success]\n"), 1)


if __name__ == "__main__":
    unittest.main()

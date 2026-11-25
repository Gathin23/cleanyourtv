"""disable.sh names the list when the folder in its path is not there, as restore.sh does.

A list path one folder short - examples/my-tv where the folder is examples/my-tv-android-11 - stopped
at the line that resolves the list against the caller's directory: bash printed "cd: examples/my-tv: No
such file or directory" with the script's own line number, a folder where the reader had typed a list,
and the "cannot read the list" message written for exactly this was never reached. restore.sh had the
same line and keeps the path as typed when the folder is missing; disable.sh now does too.

tools/tv.py is a fake that records every call, so "nothing was sent" means it was never run.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

FAKE_TV = r'''
import os
import sys

with open(os.environ["FAKE_TV_LOG"], "a") as f:
    f.write(" ".join(sys.argv[1:]) + "\n")
'''


@unittest.skipUnless(os.name == "posix" and shutil.which("bash"), "disable.sh is a POSIX script; it needs bash")
class DisableListFolderMissingTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        tools = self.base / "repo" / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
        (tools / "tv.py").write_text(FAKE_TV)
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        self.script = tools / "disable.sh"
        self.log = self.base / "tv.log"

    def run_disable(self, list_path):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.base), FAKE_TV_LOG=str(self.log))
        return subprocess.run(["bash", str(self.script), list_path], cwd=self.base, env=env,
                              capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)

    def test_a_missing_folder_names_the_list(self):
        result = self.run_disable("examples/my-tv/debloat.txt")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("cannot read the list", result.stderr)
        self.assertIn("my-tv/debloat.txt", result.stderr)
        self.assertNotIn("cd:", result.stderr)
        self.assertFalse(self.log.exists(), "nothing is sent to the TV")

    def test_a_missing_folder_does_not_fall_back_to_the_checkout_list(self):
        checkout_lists = self.base / "repo" / "lists"
        checkout_lists.mkdir()
        (checkout_lists / "a.txt").write_text("com.example.fromcheckout\n")
        result = self.run_disable("lists/a.txt")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("cannot read the list", result.stderr)
        self.assertIn(str(self.base.resolve() / "lists" / "a.txt"), result.stderr)
        self.assertFalse(self.log.exists(), "nothing is sent to the TV")

    def test_a_list_in_a_folder_that_is_there_still_runs(self):
        folder = self.base / "examples" / "my-tv"
        folder.mkdir(parents=True)
        (folder / "debloat.txt").write_text("com.example.ads\n")
        result = self.run_disable("examples/my-tv/debloat.txt")
        self.assertNotIn("cannot read the list", result.stderr)
        self.assertTrue(self.log.exists(), result.stderr)
        # The list was found and read: its package reached the TV checks.
        self.assertIn("for p in com.example.ads", self.log.read_text())


@unittest.skipUnless(os.name == "posix" and shutil.which("bash"), "restore.sh is a POSIX script; it needs bash")
class RestoreListFolderMissingTest(unittest.TestCase):
    def test_a_missing_folder_does_not_hand_recovery_the_checkout_list(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp).resolve()
            tools = base / "repo" / "tools"
            tools.mkdir(parents=True)
            shutil.copy2(ROOT / "tools" / "restore.sh", tools / "restore.sh")
            client = tools / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            client.write_text('#!/bin/sh\necho FAKE-PY "$@" | tee -a "$FAKE_PY_LOG"\n')
            client.chmod(0o755)
            (base / "repo" / "lists").mkdir()
            (base / "repo" / "lists" / "a.txt").write_text("com.example.fromcheckout\n")
            caller = base / "caller"
            caller.mkdir()
            log = base / "py.log"
            env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(base), FAKE_PY_LOG=str(log))
            subprocess.run(["bash", str(tools / "restore.sh"), "lists/a.txt"], cwd=caller, env=env,
                           capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)
            calls = log.read_text()
            self.assertIn(f"--commands {caller}/lists/a.txt ", calls)
            self.assertNotIn("--commands lists/a.txt", calls)


if __name__ == "__main__":
    unittest.main()

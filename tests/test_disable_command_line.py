"""disable.sh refuses a flag or an extra word on its command line instead of reading it as the list.

disable.sh takes one word, the debloat list. `tools/disable.sh --help` handed --help to dirname, which
printed its own coreutils help inside a bash "cd: ...: No such file or directory" error, and exit 1. A
word after the list - a second list, `FORCE_HOME=1` typed after the path instead of before it - was
dropped without a word while the first list went to the TV. restore.sh refuses both shapes with a
usage line and exit 2 before anything reaches the TV; disable.sh now does the same.

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
USAGE = "usage: TV_HOST=<tv ip> tools/disable.sh <debloat list>"

FAKE_TV = r'''
import os
import sys

with open(os.environ["FAKE_TV_LOG"], "a") as f:
    f.write(" ".join(sys.argv[1:]) + "\n")
'''


@unittest.skipUnless(os.name == "posix" and shutil.which("bash"), "disable.sh is a POSIX script; it needs bash")
class DisableCommandLineTest(unittest.TestCase):
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
        (self.base / "debloat.txt").write_text("com.example.ads\n")

    def run_disable(self, *words):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.base), FAKE_TV_LOG=str(self.log))
        return subprocess.run(["bash", str(self.script), *words], cwd=self.base, env=env,
                              capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=60)

    def assert_refused(self, result, word):
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(word, result.stderr)
        self.assertIn(USAGE, result.stderr)
        self.assertIn("Nothing was sent to the TV", result.stderr)
        self.assertNotIn("dirname", result.stderr)
        self.assertFalse(self.log.exists(), "nothing is sent to the TV")

    def test_a_flag_is_not_read_as_the_list(self):
        for flag in ("--help", "-h", "--reset"):
            with self.subTest(flag=flag):
                self.assert_refused(self.run_disable(flag), flag)

    def test_a_word_after_the_list_is_not_dropped(self):
        self.assert_refused(self.run_disable("debloat.txt", "FORCE_HOME=1"), "FORCE_HOME=1")

    def test_one_list_still_reaches_the_tv(self):
        result = self.run_disable("debloat.txt")
        self.assertTrue(self.log.exists(), result.stderr)
        self.assertIn("for p in com.example.ads", self.log.read_text())


if __name__ == "__main__":
    unittest.main()

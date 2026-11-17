"""A '#' straight after a package name starts a comment in disable.sh too, as in disable.ps1 and recovery.py.

disable.sh only cut a comment that had a space before it, so `com.b.two#note` reached the name check whole
and the run was refused, while disable.ps1 and recovery.read_list (behind restore.sh and check.py) cut the
line at the first '#' and used com.b.two. The same list now reads the same way in all three, which is what
tests/test_examples.py already assumes. A core package written that way is refused as a core package.

Run from the repository root: python3 -m unittest discover -s tests -v
The disable.sh case needs bash and skips on Windows; CI runs it.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import recovery  # noqa: E402

LIST = "# My TV\ncom.example.ads\ncom.android.systemui#kept by mistake\n"


class ListInlineCommentTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text(LIST)

    def test_recovery_reads_the_name_before_the_hash(self):
        self.assertEqual(recovery.read_list(str(self.list_path)), ["com.example.ads", "com.android.systemui"])

    @unittest.skipIf(os.name != "posix" or not shutil.which("bash"), "disable.sh needs bash")
    def test_disable_sh_reads_the_same_name(self):
        tools = self.base / "repo" / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "disable.sh", tools / "disable.sh")
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        # The core-package refusal comes before the TV is asked anything, so no TV stands in here.
        env = dict(os.environ, TV_HOST="192.0.2.1", HOME=str(self.base))
        result = subprocess.run(["bash", str(tools / "disable.sh"), str(self.list_path)],
                                capture_output=True, text=True, env=env, timeout=60)
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertNotIn("not a package name", result.stderr)
        self.assertIn("core Android", result.stderr)
        self.assertIn("  com.android.systemui\n", result.stderr)


if __name__ == "__main__":
    unittest.main()

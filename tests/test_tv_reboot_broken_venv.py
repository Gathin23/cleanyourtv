"""tests/test_tv_reboot.py with a tools venv whose adb_shell imports but lacks what the tests use.

Where python3 has no adb_shell, test_tv_reboot.py runs itself again with the venv's Python, which
test_tv_connect_errors.client_python() picks by `import adb_shell` alone. A partial or different adb_shell
there passes that check but not the tests' own imports. The child must then fail with that ImportError,
not take the same branch and start a child of its own, and that one another, without end.

This copies tests/ to a temporary folder beside a venv made there without pip, whose adb_shell is an empty
package that counts the runs and stops the fourth. test_tv_reboot.py runs with -S, so this Python's own
adb_shell, if it has one, is not seen. No socket, ADB key or TV is used.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import venv

ROOT = Path(__file__).resolve().parents[1]

# The venv's whole adb_shell: no constants, adb_device or transport. Each run of test_tv_reboot.py that
# imports it adds a line to the file RUNS_LOG names, and the fourth stops there, so a chain ends.
STUB = r'''
import os
import sys

if sys.argv[0].endswith("test_tv_reboot.py"):
    with open(os.environ["RUNS_LOG"], "a") as log:
        log.write(" ".join(sys.argv) + "\n")
    with open(os.environ["RUNS_LOG"]) as log:
        if len(log.readlines()) > 3:
            raise SystemExit("stub adb_shell: test_tv_reboot.py ran four times")
'''


class BrokenVenvTest(unittest.TestCase):
    def test_one_child_fails_with_the_import_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            shutil.copytree(ROOT / "tests", tmp / "tests", ignore=shutil.ignore_patterns("__pycache__"))
            env_dir = tmp / "tools" / "venv"
            venv.create(env_dir, with_pip=False, symlinks=os.name != "nt")
            python = env_dir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
            purelib = "import sysconfig; print(sysconfig.get_path('purelib'))"
            site = Path(subprocess.run([str(python), "-I", "-c", purelib], capture_output=True, text=True,
                                       check=True).stdout.strip())
            (site / "adb_shell").mkdir()
            (site / "adb_shell" / "__init__.py").write_text(STUB)
            runs = tmp / "runs.log"
            result = subprocess.run([sys.executable, "-I", "-S", "-m", "unittest", "discover", "-s", "tests",
                                     "-p", "test_tv_reboot.py"], cwd=str(tmp),
                                    env={**os.environ, "RUNS_LOG": str(runs)}, capture_output=True, text=True,
                                    timeout=120)
            output = result.stdout + result.stderr
            self.assertTrue(runs.exists(), "test_tv_reboot.py never ran with the venv's Python:\n" + output)
            self.assertEqual(1, len(runs.read_text().splitlines()), runs.read_text() + output)
            self.assertNotEqual(0, result.returncode, output)
            self.assertIn("cannot import name 'constants' from 'adb_shell'", output)


if __name__ == "__main__":
    unittest.main()

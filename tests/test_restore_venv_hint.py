"""restore.sh and restore.ps1 say how to make the tools venv when it is missing, as disable does.

`disable.sh` and `disable.ps1` check for the venv's Python before anything else and print the command
that makes it. The undo did not: `restore.sh` fell back to `tools/venv/Scripts/python.exe`, the
Windows path, on every machine, so on macOS or Linux bash said twice that this file was not there,
once for each side of its pipe. `restore.ps1` ran the missing interpreter and PowerShell answered
that the term was not recognized as a cmdlet. Neither said what to run. The undo is the script an
owner reaches for when something went wrong, often on a fresh clone or another computer, so it gets
the same one-line hint as disable.

tools/tv.py is a fake that records the batch, so a venv that is there is checked to still restore:
both the Unix layout and the Windows one Git Bash finds, which is what keeps the fix from being a
script that refuses every machine.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PWSH = shutil.which("pwsh")
SH_HINT = "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"
PS1_HINT = "run: python -m venv tools\\venv"

# Stands in for tools/tv.py: records what a batch sends. recovery.py --commands needs no TV.
FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]

if "--batch" in sys.argv[1:]:
    with open(STATE_PATH) as f:
        state = json.load(f)
    state["commands"] += [line.strip() for line in sys.stdin if line.strip()]
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)
'''


class RestoreVenvHintTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("restore.sh", "restore.ps1", "disable.sh", "disable.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.state_path.write_text(json.dumps({"commands": []}))
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("# My TV\ncom.example.ads\n")

    def venv(self, *parts):
        # The real interpreter behind the path the scripts look for.
        client = self.tools.joinpath("venv", *parts)
        client.parent.mkdir(parents=True, exist_ok=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

    def run_script(self, script):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        if script.endswith(".sh"):
            if os.name != "posix" or not shutil.which("bash"):
                raise unittest.SkipTest(f"{script} is a POSIX script; it needs bash")
            argv = ["bash", str(self.tools / script), str(self.list_path)]
        else:
            if not PWSH:
                raise unittest.SkipTest(f"{script} needs pwsh on PATH")
            argv = [PWSH, "-NoProfile", "-File", str(self.tools / script),
                    "-TvHost", "192.168.1.50", str(self.list_path)]
        result = subprocess.run(argv, cwd=self.repo, env=env, capture_output=True, text=True,
                                timeout=180)
        return result, result.stdout + result.stderr

    def sent(self):
        return json.loads(self.state_path.read_text())["commands"]

    def test_restore_sh_without_a_venv_says_how_to_make_one(self):
        result, output = self.run_script("restore.sh")
        self.assertEqual(result.returncode, 1, output)
        self.assertIn(SH_HINT, result.stderr, "the hint is an error, so it goes to stderr")
        self.assertNotIn("No such file or directory", output)
        self.assertNotIn("Scripts/python.exe", output,
                         "a Mac or Linux owner is not told about the Windows interpreter")
        self.assertEqual(self.sent(), [])

    def test_restore_sh_gives_the_hint_disable_sh_gives(self):
        # The two scripts are run one after the other from the same clone: the same missing venv must
        # read the same way in both, so the owner who ran one has already seen the fix for the other.
        _, disable = self.run_script("disable.sh")
        self.assertIn(SH_HINT, disable)
        _, restore = self.run_script("restore.sh")
        self.assertIn(SH_HINT, restore)

    def test_restore_ps1_without_a_venv_says_how_to_make_one(self):
        for script in ("disable.ps1", "restore.ps1"):
            with self.subTest(script=script):
                result, output = self.run_script(script)
                self.assertNotEqual(result.returncode, 0, output)
                self.assertIn(PS1_HINT, output)
                self.assertNotIn("is not recognized", output)
        self.assertEqual(self.sent(), [])

    def test_a_venv_that_is_there_still_restores(self):
        cases = [("restore.sh", ("bin", "python")),
                 # Git Bash on Windows: only the Windows layout exists, and restore.sh falls back to it.
                 ("restore.sh", ("Scripts", "python.exe")),
                 ("restore.ps1", ("Scripts", "python.exe"))]
        for script, layout in cases:
            with self.subTest(script=script, venv="/".join(layout)):
                shutil.rmtree(self.tools / "venv", ignore_errors=True)
                self.state_path.write_text(json.dumps({"commands": []}))
                self.venv(*layout)
                result, output = self.run_script(script)
                self.assertEqual(result.returncode, 0, output)
                self.assertNotIn("-m venv", output)
                self.assertIn("pm enable com.example.ads", self.sent())

    def test_a_clone_in_a_folder_with_brackets_still_restores(self):
        # Test-Path reads [1] as a wildcard: the venv check takes the path as written, so it does not
        # tell an owner whose venv is there to make one.
        moved = self.base / "a[1]" / "repo"
        moved.parent.mkdir()
        self.repo.rename(moved)
        self.repo, self.tools = moved, moved / "tools"
        self.venv("Scripts", "python.exe")
        result, output = self.run_script("restore.ps1")
        self.assertEqual(result.returncode, 0, output)
        self.assertIn("pm enable com.example.ads", self.sent())


if __name__ == "__main__":
    unittest.main()

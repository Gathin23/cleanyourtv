"""restore.sh reads its own command line before it moves: a list that is not there, and a third word.

The undo is the script an owner reaches for when the TV already looks wrong, and it read its first two
words only:

- `restore.sh examples/xiaomi-mi-tv-4/debloat.txt` - one character short of a folder that exists -
  ended inside the script's folder resolution: bash printed `cd: examples/xiaomi-mi-tv-4: No such
  file or directory` with restore.sh's own line number, named a folder where the reader had typed a
  list, and exited 1. recovery.py's "could not read <list> (No such file or directory). Nothing was
  sent to the TV", written for exactly this, was never reached, so the one message that says which
  file was missing never appeared. restore.ps1's Resolve-Path names the list.
- A third word (`restore.sh <list> com.google.android.tvlauncher <stock again>`, a second list path,
  `--reset` copied over from theme.sh) was dropped without a word and the TV was restored anyway, while
  recovery.py refuses three words with its usage line and exit 2 when a caller hands them over.
  restore.ps1 refuses them on its own: PowerShell binds two positional arguments.

Both now answer before anything is sent: the folder that is not there keeps the path as typed, so
recovery.py's refusal is what the reader reads, and a word left over exits 2 saying it would not have
been used. What the script does with the two words it takes is unchanged - the list is still resolved
against the caller's directory in the `pwd -W` form Git Bash needs for the record's key, and the batch
still goes through recovery.py and tv.py --batch.

tools/tv.py is a fake that records the batch, so "nothing was sent" is asserted and the documented
command lines are still shown to restore. The restore.ps1 cases need `pwsh` on PATH and skip without
it; CI's Ubuntu runner has it.

Run from the repository root: python3 -m unittest discover -s tests -v
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


class RestoreCommandLineTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("restore.sh", "restore.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.state_path.write_text(json.dumps({"commands": []}))
        (self.repo / "examples" / "my-tv").mkdir(parents=True)
        self.list_path = self.repo / "examples" / "my-tv" / "debloat.txt"
        self.list_path.write_text("# My TV\ncom.example.ads\n")
        # The venv the script insists on before it does anything.
        client = self.tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True, exist_ok=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

    def run_script(self, script, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        if script.endswith(".sh"):
            if os.name != "posix" or not shutil.which("bash"):
                raise unittest.SkipTest(f"{script} is a POSIX script; it needs bash")
            argv = ["bash", str(self.tools / script), *args]
        else:
            if not PWSH:
                raise unittest.SkipTest(f"{script} needs pwsh on PATH")
            argv = [PWSH, "-NoProfile", "-File", str(self.tools / script),
                    "-TvHost", "192.168.1.50", *args]
        result = subprocess.run(argv, cwd=self.repo, env=env, capture_output=True, text=True,
                                timeout=180)
        return result, result.stdout + result.stderr

    def sent(self):
        return json.loads(self.state_path.read_text())["commands"]

    def test_a_list_under_a_folder_that_is_not_there_names_the_list(self):
        # Relative on purpose: that is how a page writes it, and the folder is what is wrong.
        typed = "examples/my-tv-android-11/debloat.txt"
        result, output = self.run_script("restore.sh", typed)
        self.assertEqual(result.returncode, 1, output)
        self.assertIn("could not read " + typed, result.stderr,
                      "recovery.py's refusal, which names the file the reader typed")
        self.assertIn("Nothing was sent to the TV", result.stderr)
        self.assertNotIn("cd:", output, "bash's own folder error is not an answer about the list")
        self.assertNotIn("restore.sh: line", output)
        self.assertEqual(result.stdout, "")
        self.assertEqual(self.sent(), [], "a list that could not be read sends nothing")

    def test_a_third_word_is_refused(self):
        extra = "com.google.android.tvlauncher"
        result, output = self.run_script("restore.sh", str(self.list_path), extra, extra)
        self.assertEqual(result.returncode, 2, output)
        self.assertIn(extra + " would not have been used", result.stderr)
        self.assertIn("Nothing was sent to the TV", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(self.sent(), [])

    def test_the_documented_command_lines_still_restore(self):
        for args, why in ([ (str(self.list_path),), "the list alone"],
                          [(str(self.list_path), "com.google.android.tvlauncher"),
                           "the list and the stock launcher Google TV needs"]):
            with self.subTest(why):
                self.state_path.write_text(json.dumps({"commands": []}))
                result, output = self.run_script("restore.sh", *args)
                self.assertEqual(result.returncode, 0, output)
                self.assertNotIn("would not have been used", output)
                self.assertIn("pm enable com.example.ads", self.sent())

    def test_restore_ps1_already_answers_both_shapes(self):
        extra = "com.google.android.tvlauncher"
        cases = [((str(self.list_path), extra, extra), extra),
                 (("examples/my-tv-android-11/debloat.txt",), "debloat.txt")]
        for args, named in cases:
            with self.subTest(args=args):
                result, output = self.run_script("restore.ps1", *args)
                self.assertNotEqual(result.returncode, 0, output)
                self.assertIn(named, output, "PowerShell's own refusal names what was typed")
                self.assertEqual(self.sent(), [])


if __name__ == "__main__":
    unittest.main()

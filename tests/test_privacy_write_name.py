"""tools/privacy.py --write is given a file name, and everything it is given is judged before the audit.

`--write` was read as a path under a folder without ever asking whether the argument named a file:

  * `--write ""` is what `--write "$OUT"` sends when OUT was never set. os.path.exists("") is False and
    os.path.dirname("") is "", so the folder check looked at "." and passed, the whole audit ran - two
    shell calls for each of up to 25 packages, `dumpsys package` slow on old firmware - and then
    open("", "w") was `FileNotFoundError: [Errno 2] No such file or directory: ''`, a traceback with exit
    1, the code every refusal in this script uses.
  * `--write lists/` or `--write` a folder that is there answered "lists already exists; move it aside or
    choose another name", which is about a file the owner never meant to write.

Both are answered before connect(): an empty name is the same mistake as leaving the name out, so it gets
the usage block and exit 2, and a folder is named as a folder with the file name to pass instead, with
"Nothing was asked of the TV." like the checks beside it. A folder with a trailing separator follows the
path it is: refused when it is there, "there is no folder" when it is not.

The write itself is guarded too, because the audit is the expensive half: a file that cannot be created
(a symlink pointing into a folder that is not there is the one that can be written in a test) answered
"could not be written" with the path and the reason, and the list that was to go into it is printed on
stdout first, so the audited TV does not have to be audited again.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated. An
unreachable fake TV is how a test sees that the TV was not asked: connecting to it fails.

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
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(ROOT / "tools"))
from test_privacy_audit import FAKE_TV, OWN_DUMP  # noqa: E402
import recovery  # noqa: E402

KATNISS = "com.google.android.katniss"
USAGE = "usage: privacy.py"


class PrivacyWriteNameTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        (self.repo / "tools").mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", self.repo / "tools" / "privacy.py")
        (self.repo / "tools" / "tv.py").write_text(FAKE_TV)
        self.state_path = self.base / "tv-state.json"
        self.tv()

    def tv(self, **overrides):
        state = {
            "installed": [KATNISS, "com.example.plain"],
            "disabled": [],
            "voice": {"voice_interaction_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"},
            "dumps": {KATNISS: OWN_DUMP},
            "appops": {},
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def write(self, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.repo / "tools" / "privacy.py"), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True,
                              stdin=subprocess.DEVNULL, timeout=120)

    def listing(self):
        return sorted(p.name for p in self.repo.iterdir())

    # The name it was given

    def test_an_empty_name_is_the_usage_and_not_a_traceback(self):
        self.tv(unreachable=True)
        before = self.listing()
        result = self.write("--write", "")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(USAGE, result.stderr)
        self.assertIn("--write <file>", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(self.listing(), before)

    def test_an_empty_name_asks_nothing_of_a_tv_that_would_answer(self):
        # Not only a fast exit: the audit must not have run at all, which is the whole cost of this path.
        result = self.write("--write", "")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("wrote", result.stdout)
        self.assertEqual(self.listing(), ["tools"])

    def test_a_folder_is_named_as_a_folder_before_the_tv_is_asked(self):
        (self.repo / "lists").mkdir()
        self.tv(unreachable=True)
        result = self.write("--write", "lists")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("lists is a folder", result.stderr)
        self.assertIn("lists/privacy.txt", result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertNotIn("already exists", result.stderr)
        self.assertEqual(list((self.repo / "lists").iterdir()), [])

    def test_a_folder_with_a_trailing_separator_is_the_folder(self):
        (self.repo / "lists").mkdir()
        self.tv(unreachable=True)
        result = self.write("--write", "lists/")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("lists/ is a folder", result.stderr)
        self.assertIn("lists/privacy.txt", result.stderr)
        self.assertNotIn("already exists", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(list((self.repo / "lists").iterdir()), [])

    def test_a_folder_that_is_not_there_keeps_its_own_message(self):
        self.tv(unreachable=True)
        result = self.write("--write", "no-such-folder/privacy.txt")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("there is no folder no-such-folder", result.stderr)
        self.assertNotIn("is a folder", result.stderr)

    def test_an_existing_file_still_says_it_exists(self):
        (self.repo / "privacy.txt").write_text("# hand edits\n")
        self.tv(unreachable=True)
        result = self.write("--write", "privacy.txt")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("privacy.txt already exists", result.stderr)
        self.assertEqual((self.repo / "privacy.txt").read_text(), "# hand edits\n")

    # The write, when the folder took the check and not the file

    @unittest.skipUnless(hasattr(os, "symlink"), "no symlinks on this platform")
    def test_a_file_that_cannot_be_created_keeps_the_audit_on_stdout(self):
        try:
            os.symlink("no-such-folder/privacy.txt", self.repo / "privacy.txt")
        except (OSError, NotImplementedError) as error:  # Windows without the symlink privilege
            self.skipTest(f"cannot make a symlink here: {error}")
        result = self.write("--write", "privacy.txt")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("privacy.txt could not be written", result.stderr)
        self.assertIn("No such file or directory", result.stderr)
        # The audit cost the TV two shell calls per package: the list it produced is not lost.
        self.assertIn("Privacy audit written by tools/privacy.py", result.stdout)
        self.assertIn(KATNISS, result.stdout)
        copy = self.base / "saved.txt"
        copy.write_text(result.stdout)
        self.assertEqual(recovery.read_list(copy), [KATNISS])
        self.assertNotIn("wrote", result.stderr)


if __name__ == "__main__":
    unittest.main()

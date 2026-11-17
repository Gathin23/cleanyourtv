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
(a controlled filesystem failure after the audit is used in the test) answered
"could not be written" with the path and the reason, and the list that was to go into it is printed on
stdout first, so the audited TV does not have to be audited again.

A name the filesystem will not look at is not a folder that is not there yet. os.path.isdir() answers
EACCES, ENAMETOOLONG, ELOOP and ENOTDIR with False, so `--write <a 300-character name>` passed the check,
the whole audit ran - two shell calls for each package - and the write then failed as "could not be
written: File name too long", exit 1, the status this file keeps for an audit that ran, while its own
Exit status paragraph promises 2 for "the `--write` target cannot be written". `--write notes.txt/x.txt`,
with notes.txt a file, was answered "there is no folder notes.txt ... create it", an instruction nobody
can carry out. Both are judged before connect() now, in the tree's own words: tools/gen_tables.py's
--root and tools/trace.py's --out say "cannot be looked at (...)", and tools/flamechart.py and
tools/screendiff.py name the path that runs through a file ("is a file, not a folder"). LookedAtTest holds
the errno table itself, so the branch is judged on a filesystem that would accept a 300-character name.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated. An
unreachable fake TV is how a test sees that the TV was not asked: connecting to it fails.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import errno
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(ROOT / "tools"))
from test_privacy_audit import FAKE_TV, OWN_DUMP  # noqa: E402
from test_privacy_write_target_first import SENTINEL, WRITE_HOOK  # noqa: E402
import privacy  # noqa: E402
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

    def write(self, *args, hook=None):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        command = [sys.executable, str(self.repo / "tools" / "privacy.py"), *args]
        if hook:
            env.update(PRIVACY_WRITE_HOOK=hook, PRIVACY_SENTINEL=SENTINEL.hex())
            command = [sys.executable, "-c", WRITE_HOOK, *command[1:]]
        return subprocess.run(command,
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
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("lists is a folder", result.stderr)
        self.assertIn(os.path.join("lists", "privacy.txt"), result.stderr)  # lists\privacy.txt on Windows
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertNotIn("already exists", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(list((self.repo / "lists").iterdir()), [])

    def test_a_folder_with_a_trailing_separator_is_the_folder(self):
        (self.repo / "lists").mkdir()
        self.tv(unreachable=True)
        result = self.write("--write", "lists/")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("lists/ is a folder", result.stderr)
        self.assertIn("lists/privacy.txt", result.stderr)
        self.assertNotIn("already exists", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(list((self.repo / "lists").iterdir()), [])

    def test_a_folder_that_is_not_there_keeps_its_own_message(self):
        self.tv(unreachable=True)
        result = self.write("--write", "no-such-folder/privacy.txt")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("there is no folder no-such-folder", result.stderr)
        self.assertNotIn("is a folder", result.stderr)

    def test_an_existing_file_still_says_it_exists(self):
        (self.repo / "privacy.txt").write_text("# hand edits\n")
        self.tv(unreachable=True)
        result = self.write("--write", "privacy.txt")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("privacy.txt already exists", result.stderr)
        self.assertEqual((self.repo / "privacy.txt").read_text(), "# hand edits\n")

    def test_a_folder_the_path_runs_through_is_a_file_not_a_folder(self):
        (self.repo / "notes.txt").write_text("a file where a folder was meant\n")
        self.tv(unreachable=True)
        result = self.write("--write", os.path.join("notes.txt", "privacy.txt"))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("notes.txt is a file, not a folder", result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("there is no folder", result.stderr)

    def test_a_target_name_the_filesystem_cannot_hold_is_named_before_the_tv(self):
        name = self.a_name_the_filesystem_refuses()
        result = self.write("--write", name)
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("cannot be looked at", result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        # The audit is the expensive half, and this is what says it did not run: its list would be on
        # stdout, and the write's own failure line would carry "could not be written" and exit 1.
        self.assertEqual(result.stdout, "")
        self.assertNotIn("could not be written", result.stderr)
        self.assertEqual(self.listing(), ["tools"])

    def test_a_folder_name_the_filesystem_cannot_hold_is_named(self):
        name = self.a_name_the_filesystem_refuses()
        result = self.write("--write", os.path.join(name, "privacy.txt"))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("cannot be looked at", result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("create it", result.stderr)

    def a_name_the_filesystem_refuses(self):
        """A name longer than NAME_MAX, or a skip on a filesystem that would take it."""
        name = "a" * 300
        try:
            os.stat(name)
        except OSError:
            return name
        self.skipTest("this filesystem accepts a 300-character name")

    # The write, when the folder took the check and not the file

    def test_a_file_that_cannot_be_created_keeps_the_audit_on_stdout(self):
        result = self.write("--write", "privacy.txt", hook="creation_error")
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
        self.assertNotIn("wrote", result.stdout)
        self.assertFalse((self.repo / "privacy.txt").exists())
        self.assertEqual(list(self.repo.glob(".privacy-*")), [])


class LookedAtTest(unittest.TestCase):
    """The errno table behind those messages, judged where the filesystem takes a 300-character name.

    The cases above are the shapes a reader meets, and the long-name ones skip on a filesystem that would
    accept the name - so the branch is held here as well: only ENOENT is "not there yet", ENOTDIR is the
    state the caller answers one level up, and every other OSError is a refusal that keeps the system's
    own words rather than being read as a folder that is missing.
    """

    def state(self, error):
        with mock.patch("os.stat", side_effect=error):
            return privacy.looked_at("x")

    def test_only_a_missing_name_is_not_there_yet(self):
        self.assertEqual(("missing", None),
                         self.state(OSError(errno.ENOENT, "No such file or directory")))

    def test_a_path_that_runs_through_a_file_has_its_own_state(self):
        self.assertEqual(("runs-through", "Not a directory"),
                         self.state(OSError(errno.ENOTDIR, "Not a directory")))

    def test_every_other_refusal_keeps_the_systems_words(self):
        for number, words in ((errno.ENAMETOOLONG, "File name too long"),
                              (errno.EACCES, "Permission denied"),
                              (errno.ELOOP, "Too many levels of symbolic links")):
            with self.subTest(errno=number):
                self.assertEqual(("refused", words), self.state(OSError(number, words)))

    def test_a_folder_a_file_and_a_name_that_is_not_there(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        folder = Path(tmp.name)
        (folder / "notes.txt").write_text("x")
        self.assertEqual(("folder", None), privacy.looked_at(str(folder)))
        self.assertEqual(("file", None), privacy.looked_at(str(folder / "notes.txt")))
        self.assertEqual(("missing", None), privacy.looked_at(str(folder / "gone.txt")))


if __name__ == "__main__":
    unittest.main()

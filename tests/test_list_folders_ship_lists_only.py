"""A folder under `examples/` ships a list and its notes, never a script of its own.

`docs/workflows/tv-list.md` step 6 is the recipe a list PR follows, and it says: "Do not add install or
restore scripts, Private DNS or AdGuard setup, or `pm uninstall` steps: the folder only disables, and
`tools/disable.sh` and `tools/restore.sh` do the rest." `AGENTS.md`'s hard rules ("no install or restore
scripts, inside a list folder") and `docs/DOS-AND-DONTS.md`'s Don't column say the same, and
`.github/maintainer/MAINTAINER.md` names `pm uninstall` steps in a list folder as rework.

`examples/epson-ls800` was the one folder that did not keep to it. Its `restore.sh` re-enabled the list,
reset the theme and rewrote the Inputs labels in one command, and it also ran
`pm uninstall com.gathin.tvhome` behind `--uninstall-launcher` - an uninstall, in a script, in a list
folder. The folder's own `debloat.txt` already named `tools/restore.sh examples/epson-ls800/debloat.txt
com.google.android.tvlauncher` as its undo, so the script was a second, drifting answer to a question the
shared tool answers, and the only executable file in `examples/` (`git ls-files -s examples` has it as
100755 and nothing else). Two folders were trimmed the same way before - `examples/tcl-43c645` in #1717 and
`examples/benq-gv32` - and no check read a folder for a script, so this one stayed.

So the rule is checked here instead of trusted: no file under `examples/` that a shell or PowerShell on
the reader's computer runs, at any depth, and no file in `examples/` committed executable. A stand-in tree
written in the test holds the walk itself: a folder with `restore.sh`, a script one level down, a Windows
`.ps1` and a script beside the list are each reported, and a folder carrying only what a list folder ships
is quiet.

The same step's last clause, `pm uninstall` steps, was named in that sentence and in `MAINTAINER.md`
("`pm uninstall` steps in a list folder -> rework: keep the folder to disabling only") without a check:
the walk above reads file suffixes, and a command sits inside the list's own comments. Two lists handed
one out - `examples/xiaomi-mitv-mssp3` (`pm uninstall --user 0 com.mstar.netflixobserver`, in a header
that also said the list only disables) and `examples/xiaomi-mitv-mssp3-android9` (the same for
`com.google.android.katniss`, in a five-line removal recipe with its `cmd package install-existing`
undo). A list's Undo line is `tools/restore.sh`, which runs `pm enable`: it cannot put back what an
uninstall removes, so the step and the undo do not belong in one file. `uninstall_steps()` reads every
`debloat*.txt` in the tree for the command shape - the line, once its `#` and indentation are gone, is
`pm uninstall` with a package name - and leaves prose alone (`pm uninstall fails with ...`, "even after
`pm uninstall --user 0`", `pm uninstall-updates`).

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

# The suffixes a shell on the reader's computer runs without being asked twice.
SCRIPT_SUFFIXES = (".sh", ".bash", ".zsh", ".fish", ".ps1", ".bat", ".cmd")

# A `pm uninstall <package>` step, read once the line's `#` and indentation are gone: the command itself,
# with a package name as the last word of the line. `pm uninstall-updates` is a different command and a
# sentence that mentions the command without a package (`pm uninstall fails with ...`, "even after
# `pm uninstall --user 0`.") hands nothing to a reader, so neither is a step.
UNINSTALL = re.compile(r"(?:adb\s+shell\s+)?pm\s+uninstall(?:\s+\S+)*?\s+[A-Za-z][\w]*\.[\w.]+$")

# What step 6 tells a list author, and the check it is now held to.


def uninstall_steps(examples):
    """`folder/file:line: text` for every `pm uninstall <package>` step a list under `examples/` hands out."""
    found = []
    for path in sorted(examples.rglob("debloat*.txt")):
        for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            text = line.strip().lstrip("#").strip()
            if UNINSTALL.match(text):
                found.append(f"{path.relative_to(examples).as_posix()}:{number}: {text}")
    return found


def scripts(examples):
    """`folder/file` for every script a folder under `examples/` ships, at any depth."""
    return sorted(path.relative_to(examples).as_posix() for path in examples.rglob("*")
                  if path.is_file() and path.suffix.lower() in SCRIPT_SUFFIXES)


def executable_files(root, folder="examples"):
    """`mode path` for every file under `folder` git holds with an executable bit, or None without git."""
    try:
        result = subprocess.run(["git", "ls-files", "-s", folder], cwd=root, capture_output=True,
                                text=True, check=True)
    except (OSError, subprocess.CalledProcessError):
        return None
    return [" ".join(line.split("\t")[0].split()[:1] + [line.split("\t")[-1]])
            for line in result.stdout.splitlines() if line.startswith("100755 ")]


def folders(examples):
    """Every list folder under `examples/`: a directory carrying a debloat list."""
    return sorted(path for path in examples.iterdir() if path.is_dir() and any(path.glob("debloat*.txt")))


class ListFoldersTest(unittest.TestCase):
    def test_no_folder_ships_a_script_of_its_own(self):
        found = scripts(EXAMPLES)
        self.assertEqual([], found,
                         "a list folder ships a list, not a script: docs/workflows/tv-list.md step 6 says "
                         "'Do not add install or restore scripts, Private DNS or AdGuard setup, or `pm "
                         "uninstall` steps', and tools/disable.sh, tools/restore.sh and the launcher's own "
                         "install script do everything a folder needs. Found: " + ", ".join(found))

    def test_no_list_hands_out_an_uninstall_step(self):
        found = uninstall_steps(EXAMPLES)
        self.assertEqual([], found,
                         "a list disables and re-enables, so its Undo line (tools/restore.sh) cannot put "
                         "back what a `pm uninstall` removes: docs/workflows/tv-list.md step 6 asks for no "
                         "`pm uninstall` steps and MAINTAINER.md marks them rework ('keep the folder to "
                         "disabling only'). Found: " + ", ".join(found))

    def test_the_walk_reads_every_folder(self):
        # Without this, a walk that stopped finding folders would pass the test above by finding nothing.
        found = folders(EXAMPLES)
        self.assertGreaterEqual(len(found), 350, "expected every list folder under examples/")
        self.assertIn(EXAMPLES / "epson-ls800", found)
        self.assertTrue((EXAMPLES / "epson-ls800" / "tv.json").is_file(),
                        "the LS800 folder still ships its list, its tv.json and a config.json")

    def test_no_file_in_a_list_folder_is_committed_executable(self):
        found = executable_files(ROOT)
        if found is None:
            self.skipTest("git is not installed here; the walk above still reads the suffixes")
        self.assertEqual([], found,
                         "a list folder holds no file the shell runs, so nothing in examples/ is committed "
                         "executable - a list's commands go through tools/venv/bin/python -I tools/<x>.py, "
                         "or the tools own script")


class TheWalkTest(unittest.TestCase):
    """The same reader over a tree written here, so a walk that stops reading fails loudly."""

    def tree(self, tmp, files):
        examples = Path(tmp) / "examples"
        for name, text in files.items():
            path = examples / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        examples.mkdir(parents=True, exist_ok=True)
        return examples

    def test_a_folder_with_a_restore_script_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "# TV\ncom.example.one\n",
                                       "x/tv.json": "{}\n",
                                       "x/restore.sh": "#!/bin/sh\npm enable com.example.one\n"})
            self.assertEqual(["x/restore.sh"], scripts(examples))

    def test_a_script_below_the_folder_is_reported(self):
        # A folder a list PR builds by hand can put a script anywhere; the rule is about the folder, not its root.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "# TV\ncom.example.one\n",
                                       "x/tools/undo.sh": "#!/bin/sh\n"})
            self.assertEqual(["x/tools/undo.sh"], scripts(examples))

    def test_a_windows_script_beside_the_list_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "# TV\ncom.example.one\n",
                                       "x/debloat.ps1": "pm enable com.example.one\n",
                                       "x/install.cmd": "@echo off\n"})
            self.assertEqual(["x/debloat.ps1", "x/install.cmd"], scripts(examples))

    def test_a_folder_carrying_only_what_a_list_folder_ships_is_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "# TV\ncom.example.one\n",
                                       "x/debloat-keep-cast.txt": "# TV\ncom.example.two\n",
                                       "x/tv.json": "{}\n",
                                       "x/config.json": "{\"dock_apps\": []}\n",
                                       "x/README.md": "# TV\n\nUndo: tools/restore.sh x/debloat.txt\n",
                                       "x/home.jpg": ""})
            self.assertEqual([], scripts(examples))

    def test_a_shipped_script_was_the_whole_rule_in_this_tree(self):
        # The rule the test above reads today, kept honest: no folder's notes name a script of its own.
        for folder in folders(EXAMPLES):
            for page in folder.glob("*.md"):
                for line in page.read_text(encoding="utf-8").splitlines():
                    self.assertNotIn(f"examples/{folder.name}/restore.sh", line,
                                     f"{page.relative_to(ROOT)} tells a reader to run a script inside the "
                                     f"list folder; the undo is tools/restore.sh {page.relative_to(ROOT).parent}/debloat.txt")

    def test_a_commented_uninstall_command_is_reported(self):
        # The shape both lists had: the command indented under a `#` bullet, in the header's notes.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "# TV\n#   Beyond this repo:\n"
                                                      "#     pm uninstall --user 0 com.example.one\n"})
            self.assertEqual(["x/debloat.txt:3: pm uninstall --user 0 com.example.one"],
                             uninstall_steps(examples))

    def test_an_active_uninstall_line_is_reported_too(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "# TV\ncom.example.one\n",
                                       "x/debloat-keep-cast.txt": "pm uninstall com.example.two\n"})
            self.assertEqual(["x/debloat-keep-cast.txt:1: pm uninstall com.example.two"],
                             uninstall_steps(examples))

    def test_an_adb_shell_wrapper_is_the_same_step(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": "#   adb shell pm uninstall --user 0 com.example.one\n"})
            self.assertEqual(1, len(uninstall_steps(examples)))

    def test_a_sentence_that_mentions_the_command_is_not_a_step(self):
        # Every one of these is on main: a failure the firmware answers, a history line, and a command
        # whose name only starts the same way. None of them hands a reader a package to remove.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/debloat.txt": (
                "# - pm uninstall fails with DELETE_FAILED_INTERNAL_ERROR; the commented-out line\n"
                "#   and it re-enabled them even after `pm uninstall --user 0`.\n"
                "# - `pm uninstall-updates` does not exist on this Android 11 build\n"
                "#   pm uninstall --user 0, as the owner did before this list\n"
                "com.example.one\n")})
            self.assertEqual([], uninstall_steps(examples))

    def test_the_walk_reads_a_list_at_any_depth(self):
        # A folder can ship more than one list (a keep-cast twin); the walk covers the folder, not one name.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x/lists/debloat.txt": "#     pm uninstall com.example.one\n"})
            self.assertEqual(["x/lists/debloat.txt:1: pm uninstall com.example.one"],
                             uninstall_steps(examples))


if __name__ == "__main__":
    unittest.main()

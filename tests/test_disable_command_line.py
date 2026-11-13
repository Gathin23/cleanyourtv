"""disable.sh's command line is judged before the list is read or the TV is asked anything.

`disable.sh` is the script that changes a TV, and its one argument is the list. It was read with
`LIST="${1:?...}"` and nothing else, so four command lines this script cannot use ran to completion as
if they had been understood:

- no word at all: bash answered `${1:?}` itself, so the whole output was
  `tools/disable.sh: line 11: 1: usage: disable.sh <debloat list>` - a line number and a `1:` where the
  usage belongs - with status 1, which the script keeps for a run that started and did not finish;
- `--help` (or `-h`, or `--reset` pasted over from theme.sh, whose undo hint people copy): the word was
  resolved as a folder, so `dirname` printed its own --help text (844 bytes of coreutils) inside a
  `cd: $'...'` line;
- a list path one folder short (`examples/my-tv/debloat.txt`): the run died in that same `cd` with this
  script's line number, naming a folder where the reader had typed a list - the
  "cannot read the list: <path>" sentence written for exactly that case was never reached;
- a word appended after the list (a package name someone expected this script to take, a second path,
  a flag): it was dropped without a word and the whole list went through anyway, so a reader who added
  a package name read the clean run as "that package is disabled now".

restore.sh and theme.sh answer all four with status 2, a usage line and "Nothing was sent to the TV",
and tools/recovery.py answers the same words the same way. `disable.ps1` refuses them on its own -
PowerShell binds one positional argument, `-h`/`--help` are parameter names it does not have, and
$List is Mandatory - so only the POSIX twin is exercised here; the suite has no PowerShell on CI and
that script needs none.

The properties checked are the status, the words, and the silence around them: nothing is read, no
recovery record is written and no command reaches the TV. tools/tv.py is a fake that records every
command it is asked to run, so "nothing reached the TV" is checked rather than assumed, and a real
list still runs - which is what keeps the fix from being a script that refuses everything. No guard
line, no protected-package rule and no HOME check is touched by this file.

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
import venv


ROOT = Path(__file__).resolve().parents[1]

# Stands in for tools/tv.py: answers the queries disable.sh and recovery.py make, records every command
# it was asked to run in state["calls"], and records a --batch instead of sending it.
FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


state = load()


def save():
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def answer(command):
    if command == "pm list packages":
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command == "pm list packages -d":
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if command.startswith("cmd package query-activities"):
        return "".join(f"  {c}\n" for c in state["claims"])
    if command.startswith("cmd package resolve-activity"):
        return (state["home"] or "") + "\n"
    if command.startswith("dumpsys activity"):
        return state.get("home_process", "")
    if command.startswith("settings get global "):
        return "null\n"
    if command.startswith("getprop ro.adb.secure"):
        return "1\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    # recovery.py imports this module and calls connect() directly.
    return Device()


def main():
    args = sys.argv[1:]
    state["calls"].append(args)
    if "--batch" in args:
        for line in sys.stdin:
            line = line.strip()
            if line and not line.startswith("#"):
                state["commands"].append(line)
        save()
        return 0
    save()
    print(answer(args[-1]), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''

USAGE = "usage: TV_HOST=<tv ip> tools/disable.sh <debloat list>"


class DisableCommandLineTest(unittest.TestCase):
    def setUp(self):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("disable.sh is a POSIX script; it needs bash")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("disable.sh", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        # The script calls the tools venv's Python: run the real interpreter, which imports the fake
        # tv.py for the transport and the real recovery.py for the record.
        for client in (self.tools / "venv" / "bin" / "python",
                       self.tools / "venv" / "Scripts" / "python.exe"):
            client.parent.mkdir(parents=True, exist_ok=True)
            client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
            client.chmod(0o755)
        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("# My TV\ncom.example.before\n")

    def tv(self):
        state = {
            "installed": ["com.example.before", "com.example.after",
                          "com.google.android.tvlauncher"],
            "disabled": [],
            "home": "com.google.android.tvlauncher/.MainActivity",
            "claims": ["com.google.android.tvlauncher/.MainActivity"],
            "home_process": "",
            "calls": [],
            "commands": [],
        }
        self.state_path.write_text(json.dumps(state))
        return state

    def disable(self, *argv):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        result = subprocess.run(["bash", str(self.tools / "disable.sh"), *argv],
                                cwd=self.repo, env=env, capture_output=True, text=True,
                                timeout=180)
        return result, json.loads(self.state_path.read_text())

    def output(self, result):
        return result.stdout + result.stderr

    def test_no_word_is_refused_with_the_usage_and_status_2(self):
        self.tv()
        result, state = self.disable()
        self.assertEqual(result.returncode, 2, self.output(result))
        self.assertIn(USAGE, self.output(result))
        self.assertIn("Nothing was sent to the TV", self.output(result))
        self.assertEqual(result.stdout, "", "a refusal is a message on stderr, not a run's output")
        self.assertNotIn("line 1", self.output(result),
                         "the answer has to be this script's own sentence, not bash naming a line")
        self.assertEqual(state["calls"], [])
        self.assertFalse((self.home / ".cleanyourtv").exists(), "no list was read, so no record")

    def test_an_empty_word_reads_as_no_word(self):
        self.tv()
        result, state = self.disable("")
        self.assertEqual(result.returncode, 2, self.output(result))
        self.assertIn(USAGE, self.output(result))
        self.assertEqual(state["calls"], [])

    def test_a_flag_is_refused_rather_than_read_as_a_list_path(self):
        # -h and --help are what a reader types first; --reset is pasted from theme.sh's undo hint. All
        # three used to be resolved as a folder, so dirname/basename answered about the flag itself.
        for flag in ("--help", "-h", "--reset"):
            with self.subTest(flag=flag):
                self.tv()
                result, state = self.disable(flag)
                self.assertEqual(result.returncode, 2, self.output(result))
                self.assertIn(flag, self.output(result),
                              "the refusal has to name the word the reader typed")
                self.assertIn(USAGE, self.output(result))
                self.assertIn("Nothing was sent to the TV", self.output(result))
                self.assertNotIn("dirname", self.output(result))
                self.assertNotIn("cd:", self.output(result))
                self.assertEqual(state["calls"], [])
                self.assertFalse((self.home / ".cleanyourtv").exists())

    def test_a_word_after_the_list_is_refused_and_not_dropped(self):
        # The one that could change a TV: the list ran, the word was thrown away, and the run read clean.
        self.tv()
        result, state = self.disable(str(self.list_path), "com.example.after")
        self.assertEqual(result.returncode, 2, self.output(result))
        self.assertIn("com.example.after", self.output(result))
        self.assertIn("would not have been used", self.output(result))
        self.assertIn("Nothing was sent to the TV", self.output(result))
        self.assertEqual(state["calls"], [], "the TV is not asked anything for a command line it cannot read")
        self.assertEqual(state["commands"], [])
        self.assertFalse((self.home / ".cleanyourtv").exists())
        self.assertNotIn("pm disable-user --user 0 com.example.before", self.output(result))

    def test_a_path_one_folder_short_is_named_as_a_list_not_as_a_cd_failure(self):
        # The status is the one the "cannot read the list" line already answers; what changes is that the
        # sentence is reached at all, and that it names the path the reader typed.
        self.tv()
        typed = "examples/my-tv/debloat.txt"
        result, state = self.disable(typed)
        self.assertEqual(result.returncode, 1, self.output(result))
        self.assertIn("cannot read the list: " + typed, self.output(result))
        self.assertNotIn("cd:", self.output(result))
        self.assertNotIn("No such file or directory", self.output(result))
        self.assertEqual(state["calls"], [])

    def test_a_list_that_is_there_still_runs(self):
        # The guard on the fix: a list this script can read must not have become a list it refuses.
        self.tv()
        result, state = self.disable(str(self.list_path))
        self.assertEqual(result.returncode, 0, self.output(result))
        self.assertEqual(state["commands"],
                         ["pm disable-user --user 0 com.example.before", "pm list packages -d"],
                         "the batch is the list's own lines, plus the read-back it ends with")
        self.assertTrue((self.home / ".cleanyourtv").exists(),
                        "a list that disables something still records the undo")


if __name__ == "__main__":
    unittest.main()

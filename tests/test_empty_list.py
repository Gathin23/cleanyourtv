"""A debloat list with no package names in it must say so, rather than exit 1 in silence.

CONTRIBUTING asks for an entry nobody has lived with yet to be commented out, so an all-comments list
is a normal contribution: the device note went in, the packages are still `#` lines. `disable.sh`
read it with a pipeline (`awk | grep | sed | awk`) whose last stage is the one that prints nothing,
and under `set -euo pipefail` the failing `grep` ended the script with status 1 and not one line of
output - the same shape as a run that died halfway through, which is the one thing the owner of a TV
must be able to tell apart. `disable.ps1` said something, but only after two queries had gone to the
TV, and its message was about the recovery record.

The property here is the message and the silence around it, not the wording: a list of comments or
blank lines is refused in words that name the file, nothing is sent to the TV, and no recovery record
is written. tools/tv.py is a fake that records every command it is asked to run, so "nothing reached
the TV" is checked rather than assumed. A real list still runs, which is what keeps the fix from
being a script that refuses everything.

Run from the repository root: python3 -m unittest discover -s tests -v
The PowerShell cases need `pwsh` on PATH and skip without it; CI has no PowerShell.
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

# Stands in for tools/tv.py: answers the queries both scripts make, records every command it was
# asked to run in state["calls"], and records a --batch instead of sending it.
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


class EmptyListTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("disable.sh", "disable.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        # The scripts call the tools venv's Python: run the real interpreter, which imports the fake
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

    def write_list(self, text):
        self.list_path.write_text(text)

    def tv(self, **overrides):
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
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))
        return state

    def disable(self, entry):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        if entry == "sh":
            argv = ["bash", str(self.tools / "disable.sh"), str(self.list_path)]
        else:
            argv = [PWSH, "-NoProfile", "-File", str(self.tools / "disable.ps1"),
                    "-TvHost", "192.168.1.50", str(self.list_path)]
        result = subprocess.run(argv, cwd=self.repo, env=env, capture_output=True, text=True,
                                timeout=180)
        return result, json.loads(self.state_path.read_text())

    def entries(self):
        return ("sh", "ps1") if PWSH else ("sh",)

    def output(self, result):
        return result.stdout + result.stderr

    def test_a_list_whose_entries_are_all_comments_is_refused_in_words(self):
        self.write_list("# TCL 50P616, Android TV 9\n# Every entry below is still untested:\n"
                        "# com.example.before\n\n  # com.example.after   # untested\n")
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, state = self.disable(entry)
                self.assertNotEqual(result.returncode, 0, self.output(result))
                self.assertIn("no package names", self.output(result))
                self.assertIn(str(self.list_path), self.output(result),
                              "the message has to name the file, or an owner with two lists "
                              "cannot tell which one it read")
                self.assertEqual(state["calls"], [],
                                 "the list is refused before anything is asked of the TV")
                self.assertEqual(state["commands"], [])

    def test_a_list_of_blank_lines_is_refused_the_same_way(self):
        self.write_list("\n   \n\n")
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, state = self.disable(entry)
                self.assertNotEqual(result.returncode, 0, self.output(result))
                self.assertIn("no package names", self.output(result))
                self.assertEqual(state["calls"], [])

    def test_the_refusal_is_not_the_bad_package_name_one(self):
        # Both messages end the run with status 1, so the words are what separates "your list is
        # empty" from "your list has junk in it": an empty list must not read as a typo.
        self.write_list("# com.example.before\n")
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, _ = self.disable(entry)
                self.assertNotIn("not a package name", self.output(result))

    def test_no_recovery_record_is_written_for_an_empty_list(self):
        # The record is the only copy of the state undo replays: writing one for a list that disables
        # nothing would still be a change to the machine, and restore.sh would then report it.
        self.write_list("# com.example.before\n")
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                self.disable(entry)
                self.assertFalse((self.home / ".cleanyourtv").exists())

    def test_a_real_list_still_runs(self):
        # The guard on the fix: the pipeline now tolerates an empty result, and a list with entries
        # must not have become a list that is refused.
        self.write_list("# My TV\ncom.example.before\ncom.example.after  # note\n")
        for entry in self.entries():
            with self.subTest(entry=entry):
                self.tv()
                result, state = self.disable(entry)
                self.assertEqual(result.returncode, 0, self.output(result))
                sent = state["commands"]
                self.assertIn("pm disable-user --user 0 com.example.before", sent)
                self.assertIn("pm disable-user --user 0 com.example.after", sent)
                self.assertTrue((self.home / ".cleanyourtv").exists(),
                                "a list that disables something still records the undo")

    def test_a_list_that_cannot_be_read_is_named(self):
        # A path that is not a file (a typo, a folder) used to reach awk, whose line was the only
        # thing on stderr while the script exited 1.
        self.tv()
        result, state = self.disable("sh")
        self.assertNotEqual(result.returncode, 0, self.output(result))
        self.assertIn("cannot read the list", self.output(result))
        self.assertIn(str(self.list_path), self.output(result))
        self.assertEqual(state["calls"], [])


if __name__ == "__main__":
    unittest.main()

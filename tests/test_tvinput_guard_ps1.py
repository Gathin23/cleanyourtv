"""tools/disable.ps1 must run the input-driver check through tv.py --batch on stdin (#3186).

Windows PowerShell 5.1 drops every double quote from a native program's arguments. The loop that
asks the TV which listed packages drive the HDMI/AV inputs is full of them, so passing it as one
argument left the TV's shell with a command it could not parse, tv.py still exited 0, and the guard
passed in silence: an input driver on the list was disabled with no warning (#321). The loop now goes
to `tv.py --batch` on stdin, as launcher/build-and-install.ps1 does (#263), and ends with a marker the
script only accepts as a line of its own, so a check that did not run is reported with the existing
"Could not check" warning instead of passing in silence. As in disable.sh, that warning does not stop the
run: the listed packages are still disabled, and the person is told to check every input afterwards.

The fake tv.py echoes each command as `$ <command>` before its answer, as tv.py --batch does. The
PowerShell cases need `pwsh` on PATH and skip without it; CI has it.

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
BEFORE = "com.example.before"
AFTER = "com.example.after"
# On MediaTek sets this is the package behind every HDMI, AV and tuner input.
MEDIATEK = "com.mediatek.tvinput"
HOME = "com.google.android.tvlauncher"

# Stands in for tools/tv.py: answers the queries the disable script makes, records how it was called,
# and for --batch echoes every command as `$ <command>` the way the real client does.
FAKE_TV = r'''
import json
import os
import re
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]
LOG_PATH = os.environ["FAKE_TV_LOG"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


def save(state):
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def log(argv, stdin_text):
    with open(LOG_PATH, "a") as f:
        f.write(json.dumps({"argv": argv, "stdin": stdin_text}) + "\n")


def answer(command, state):
    if command.startswith("pm list packages -e"):
        return "".join(f"package:{name}\n" for name in state["enabled"])
    if command.startswith("pm list packages -d"):
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if command.startswith("pm list packages"):
        return "".join(f"package:{name}\n" for name in state["installed"])
    if command.startswith("cmd package resolve-activity"):
        return state["home"] + "\n"
    if command.startswith("cmd package query-activities"):
        return ""
    if command.startswith("dumpsys activity"):
        return ""
    if command.startswith("settings get global "):
        return "null\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        # recovery.py imports this module and reads the TV through connect().
        return answer(command, load())

    def close(self):
        pass


def connect():
    return Device()


def guard_answer(line):
    """What the TV's dumpsys finds, then the loop's own completion marker when the check ran."""
    state = load()
    if not state["guard_runs"]:
        return ""
    out = "".join(f"{name}\n" for name in state["guard_packages"])
    marker = re.search(r"echo (\S+)\s*$", line)
    if marker:
        out += marker.group(1) + "\n"
    return out


def main():
    argv = sys.argv[1:]
    if "--batch" in argv:
        text = sys.stdin.read()
        log(argv, text)
        state = load()
        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            # The real --batch prints `$ <command>` before the command's answer.
            print(f"$ {line}")
            if "dumpsys package" in line:
                out = guard_answer(line)
            elif line.startswith("pm disable-user --user 0 "):
                name = line.split()[-1]
                state["enabled"] = [p for p in state["enabled"] if p != name]
                if name not in state["disabled"]:
                    state["disabled"].append(name)
                out = ""
            else:
                out = ""
            if out:
                sys.stdout.write(out)
        save(state)
        return 0
    log(argv, None)
    print(answer(argv[-1], load()), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


class TvInputGuardPs1Test(unittest.TestCase):
    def setUp(self):
        if not PWSH:
            raise unittest.SkipTest("the PowerShell input-driver guard needs pwsh on PATH")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        for name in ("disable.ps1", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        # disable.ps1 asks for the Windows path; PowerShell on this machine resolves it to this shim,
        # which runs the interpreter the tests already use.
        client = self.tools / "venv" / "Scripts" / "python.exe"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

        self.home_dir = self.base / "home"
        self.home_dir.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.log_path = self.base / "invocations.jsonl"
        self.list_path = self.base / "debloat.txt"

    def write_list(self, names):
        self.list_path.write_text("# My TV\n" + "".join(f"{name}\n" for name in names))

    def tv(self, **overrides):
        state = {
            "installed": [BEFORE, MEDIATEK, AFTER, HOME],
            "enabled": [BEFORE, MEDIATEK, AFTER, HOME],
            "disabled": [],
            "home": f"{HOME}/.MainActivity",
            "guard_runs": True,
            "guard_packages": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def disable(self):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home_dir),
                   USERPROFILE=str(self.home_dir), FAKE_TV_STATE=str(self.state_path),
                   FAKE_TV_LOG=str(self.log_path))
        result = subprocess.run(
            [PWSH, "-NoProfile", "-File", str(self.tools / "disable.ps1"),
             "-TvHost", "192.168.1.50", str(self.list_path)],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=180)
        self.assertEqual(result.returncode, 0, self.output(result))
        return result

    def output(self, result):
        return result.stdout + result.stderr

    def invocations(self):
        return [json.loads(line) for line in self.log_path.read_text().splitlines() if line]

    def guard_call(self):
        """The tv.py --batch call that carried the input-driver loop."""
        for call in self.invocations():
            if "--batch" in call["argv"] and "dumpsys package" in (call["stdin"] or ""):
                return call
        self.fail("the input-driver check never reached tv.py --batch on stdin")

    def sent_commands(self):
        """The commands of the last --batch that was not the guard: the disable list itself."""
        batches = [call["stdin"] for call in self.invocations()
                   if "--batch" in call["argv"] and "dumpsys package" not in (call["stdin"] or "")]
        self.assertTrue(batches, "the list was never sent to tv.py --batch")
        return [line.strip() for line in batches[-1].splitlines() if line.strip()]

    def test_the_check_goes_to_tv_py_batch_on_stdin(self):
        # 5.1 drops the loop's double quotes from an argument; on stdin the bytes are untouched.
        self.write_list([BEFORE, MEDIATEK, AFTER])
        self.tv(guard_packages=[MEDIATEK])
        self.disable()
        call = self.guard_call()
        self.assertIn("--batch", call["argv"])
        for entry in call["argv"]:
            self.assertNotIn("dumpsys package", entry,
                             "the loop must not be a single command-line argument")
            self.assertNotIn("TV_INPUT_HARDWARE", entry)
        self.assertIn("for p in ", call["stdin"])
        self.assertIn('*"requested permissions:"*', call["stdin"],
                      "the quotes have to reach the TV's shell")

    def test_an_input_driver_is_skipped_when_the_marker_returns(self):
        self.write_list([BEFORE, MEDIATEK, AFTER])
        self.tv(guard_packages=[MEDIATEK])
        result = self.disable()
        sent = self.sent_commands()
        self.assertNotIn(f"pm disable-user --user 0 {MEDIATEK}", sent)
        self.assertIn(f"pm disable-user --user 0 {BEFORE}", sent)
        self.assertIn(f"Skipping {MEDIATEK}", self.output(result))
        self.assertIn("TV_INPUT_HARDWARE", self.output(result))

    def test_a_missing_marker_warns_and_continues(self):
        # tv.py exits 0 but the TV never echoed the marker, as when its shell did not run the loop:
        # the run must say it could not check instead of claiming a pass. Like disable.sh, it then
        # carries on and disables the unchecked packages (TVINPUT_ALLOW is the explicit bypass).
        self.write_list([BEFORE, MEDIATEK, AFTER])
        self.tv(guard_runs=False, guard_packages=[MEDIATEK])
        result = self.disable()
        sent = self.sent_commands()
        self.assertIn(f"pm disable-user --user 0 {MEDIATEK}", sent)
        self.assertIn("Could not check which listed packages drive the HDMI/AV inputs",
                      self.output(result))
        self.assertNotIn("Skipping", self.output(result))


if __name__ == "__main__":
    unittest.main()

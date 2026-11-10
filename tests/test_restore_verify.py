"""restore.sh and restore.ps1 read the TV back after the batch and exit 1 when the plan did not take.

tv.py --batch exits 0 whatever the TV answers each line, so a `pm enable` the firmware refused, or a
`cmd package set-home-activity` it did not take, ended the undo with status 0. disable.sh and disable.ps1
already read the TV back after their batch. The undo now does the same: a package the plan enables that
is still disabled for user 0, or a Home that resolves to something other than the recorded component,
fails the run and is named. A read the TV does not answer only says the check did not run.

The fake tools/tv.py applies the batch to a stored state and answers the reads from it. The restore.sh
cases need bash and the restore.ps1 cases `pwsh` on PATH; each skips without its shell.

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

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_recovery_commands_needs_host as records  # noqa: E402  (its record helpers, not its tests)

ROOT = Path(__file__).resolve().parents[1]
PWSH = shutil.which("pwsh")
ADDRESS = records.ADDRESS
STOCK_HOME = "com.google.android.tvlauncher/.MainActivity"

FAKE_TV = r'''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]
with open(STATE_PATH) as f:
    state = json.load(f)


def apply(line):
    words = line.split()
    if line.startswith("pm enable ") and words[-1] not in state["refuse"]:
        state["disabled"] = [p for p in state["disabled"] if p != words[-1]]
    elif line.startswith("cmd package set-home-activity ") and not state["home_refused"]:
        state["home"] = words[-1]


def answer(command):
    if command.startswith("pm list packages -d"):
        return "".join(f"package:{name}\n" for name in state["disabled"])
    if "resolve-activity" in command:
        home = state["home"]
        if state.get("short_home"):
            package = home.split("/")[0]
            home = home.replace(f"{package}/{package}.", f"{package}/.", 1)
        return home + "\n"
    return ""


if "--batch" in sys.argv[1:]:
    for line in sys.stdin:
        if line.strip():
            print(f"$ {line.strip()}")
            apply(line.strip())
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)
else:
    sys.stdout.write(answer(sys.argv[-1]))
'''


class RestoreVerifyTest(unittest.TestCase):
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
        self.venv()
        self.home = self.base / "home"
        self.home.mkdir()
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("# My TV\ncom.example.enabled\ncom.example.already_off\n")
        records_dir = self.home / ".cleanyourtv"
        records_dir.mkdir()
        self.record = records.record_for(self.list_path, ADDRESS)
        self.record["home"] = STOCK_HOME
        self.record_path = records_dir / f"recovery-{ADDRESS}-{records.digest(self.list_path)}.json"
        self.record_path.write_text(json.dumps(self.record))
        self.state_path = self.base / "tv-state.json"

    def venv(self):
        # Both layouts run the tests' interpreter: restore.sh asks for bin/python, restore.ps1 for the Windows path.
        for parts in (("bin", "python"), ("Scripts", "python.exe")):
            client = self.tools.joinpath("venv", *parts)
            client.parent.mkdir(parents=True, exist_ok=True)
            client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
            client.chmod(0o755)

    def tv(self, refuse=(), home_refused=False, extra_disabled=(), short_home=False):
        self.state_path.write_text(json.dumps({
            "disabled": ["com.example.enabled", "com.example.already_off", *extra_disabled],
            "home": "com.gathin.tvhome/com.gathin.tvhome.MainActivity",
            "refuse": list(refuse), "home_refused": home_refused, "short_home": short_home}))

    def restore(self, script):
        env = dict(os.environ, TV_HOST=ADDRESS, HOME=str(self.home), USERPROFILE=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        if script.endswith(".sh"):
            if os.name != "posix" or not shutil.which("bash"):
                self.skipTest("restore.sh is a POSIX script; it needs bash")
            argv = ["bash", str(self.tools / script), str(self.list_path)]
        else:
            if not PWSH or os.name != "posix":
                self.skipTest("restore.ps1 needs pwsh, and this fake venv's python.exe is a /bin/sh script")
            argv = [PWSH, "-NoProfile", "-File", str(self.tools / script), "-TvHost", ADDRESS, str(self.list_path)]
        result = subprocess.run(argv, cwd=self.repo, env=env, capture_output=True, text=True,
                                stdin=subprocess.DEVNULL, timeout=180)
        return result, result.stdout + result.stderr

    def test_a_restore_the_tv_took_exits_0(self):
        for script in ("restore.sh", "restore.ps1"):
            with self.subTest(script=script):
                self.tv()
                result, output = self.restore(script)
                self.assertEqual(result.returncode, 0, output)

    def test_a_refused_pm_enable_fails_the_run_and_is_named(self):
        for script in ("restore.sh", "restore.ps1"):
            with self.subTest(script=script):
                self.tv(refuse=["com.example.enabled"])
                result, output = self.restore(script)
                self.assertEqual(result.returncode, 1, output)
                named = output.split("Still disabled", 1)[-1].split("The TV did not take", 1)[0]
                self.assertIn("com.example.enabled", named)
                # Left off on purpose by the record: never named as a failure.
                self.assertNotIn("com.example.already_off", named)

    def test_a_home_the_tv_did_not_take_fails_the_run(self):
        for script in ("restore.sh", "restore.ps1"):
            with self.subTest(script=script):
                self.tv(home_refused=True)
                result, output = self.restore(script)
                self.assertEqual(result.returncode, 1, output)
                self.assertIn("Home did not go back", output)
                self.assertIn(STOCK_HOME, output)

    def test_a_home_the_tv_names_in_its_short_form_is_the_same_home(self):
        self.record["home"] = "com.example.home/com.example.home.MainActivity"
        self.record_path.write_text(json.dumps(self.record))
        for script in ("restore.sh", "restore.ps1"):
            with self.subTest(script=script):
                self.tv(short_home=True)
                result, output = self.restore(script)
                self.assertEqual(result.returncode, 0, output)

    def test_a_package_that_differs_only_by_case_is_not_the_planned_one(self):
        for script in ("restore.sh", "restore.ps1"):
            with self.subTest(script=script):
                self.tv(extra_disabled=["com.example.Enabled"])
                result, output = self.restore(script)
                self.assertEqual(result.returncode, 0, output)


if __name__ == "__main__":
    unittest.main()

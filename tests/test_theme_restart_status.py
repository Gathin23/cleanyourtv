"""theme.sh answers 1, not 2, when Home does not come back after the files reached the TV.

Its header keeps 2 for a run that stopped before the theme was up and sent nothing, and 1 for a run that
reached the set: "the theme could not be pushed, or Home did not restart after it". Both paths end with
theme_restart.py, and that helper has its own 2 for a TV it never reached ("nothing was asked of the TV") -
true of the restart, but the push has already written the config.json by then, or the reset has already
removed it. Under `set -e` the helper's status left this script unchanged, so a set that went to sleep, or
dropped Wi-Fi, between the push and the restart answered 2: "the run stopped before the theme was up and
nothing was sent". An owner reading that re-runs a push that already happened, and the shell's own message
("Nothing was sent to the TV.") is printed nowhere to contradict it. theme.ps1 throws for the same failure
and an uncaught throw leaves it with 1, so the twins disagreed on one condition.

theme_restart.py itself is left alone: 2 is right for it, the helper's job is the restart. The translation
belongs in the script that knows the files are already on the TV.

These tests run the real tools/theme.sh against a stub tools/tv.py that logs every call and exits 0 on the
command line, and that answers the helper's import two ways: a TV that stopped answering (connect raises,
theme_restart.py's exit 2) and a TV that ran the restart and refused it (its shell calls raise, exit 1). A
theme the script cannot read is still 2, and that is pinned here too: nothing was sent for it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

TOOLS = Path(__file__).resolve().parents[1] / "tools"
NOTHING_SENT = "Nothing was sent to the TV."

# Run as a script, this is tools/tv.py for the push: every call is logged and nothing is sent anywhere.
# Imported, it is the client theme_restart.py loads: FAKE_RESTART=unreached is a TV that stopped answering
# (connect() raises and connect_error() words it, which is theme_restart.py's exit 2), FAKE_RESTART=failed is
# a TV that took the restart and refused a command (the device's shell raises, which is its exit 1).
FAKE_TV = '''\
import os
import sys


def log():
    with open(os.environ["FAKE_LOG"], "a") as handle:
        handle.write(" ".join(sys.argv[1:]) + "\\n")


class Device:
    def shell(self, command, read_timeout_s=None):
        raise RuntimeError("TV command failed or returned an incomplete response")

    def close(self):
        pass


def connect():
    if os.environ.get("FAKE_RESTART") == "unreached":
        raise OSError("Connection refused")
    return Device()


def connect_error(error):
    if os.environ.get("FAKE_RESTART") == "unreached":
        return "192.0.2.10:5555 did not answer: the TV is asleep, or ADB over the network is off."
    return None


if __name__ == "__main__":
    log()
    sys.exit(0)
'''

# What an owner is told when the restart did not happen, and what this script's header keeps for a run that
# sent nothing. The two must not be able to appear together.
HOME_DID_NOT_RESTART = "Home did not restart after the theme was"


class ThemeRestartStatusTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        for name in ("theme.sh", "theme_restart.py"):
            shutil.copy2(TOOLS / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        venv = self.tools / "venv" / "bin"
        venv.mkdir(parents=True)
        try:
            (venv / "python").symlink_to(sys.executable)
        except OSError:
            # Windows without the symlink privilege (WinError 1314). Only the bash runs use this python,
            # and they skip there.
            if os.name != "nt":
                raise
        self.theme = self.base / "my-theme"
        self.theme.mkdir()
        (self.theme / "config.json").write_text('{"background_color": "#000000"}\n', encoding="utf-8")
        self.log = self.base / "tv-calls.log"

    def run_theme(self, *args, **env_overrides):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log)})
        env.update(env_overrides)
        for key, value in env_overrides.items():
            if value is None:
                env.pop(key, None)
        return subprocess.run(["bash", str(self.tools / "theme.sh"), *args], env=env,
                              capture_output=True, text=True, timeout=120)

    def tv_calls(self):
        if not self.log.exists():
            return []
        return [line for line in self.log.read_text().splitlines() if line.strip()]

    def assert_reached_the_tv(self, result):
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn(HOME_DID_NOT_RESTART, result.stderr, result.stderr)
        self.assertIn("The TV is already in the state you asked for", result.stderr, result.stderr)
        self.assertNotIn("Traceback", result.stderr, result.stderr)
        # 2 is the status for "nothing was sent", and this run sent everything it had: a reader told that
        # would push the same theme again.
        self.assertNotIn(NOTHING_SENT, result.stderr, "a run that reached the TV is not one that sent nothing")
        self.assertNotIn("applied", result.stdout)
        self.assertNotIn("is on the TV", result.stdout)
        self.assertNotEqual([], self.tv_calls(), "the files went through before the restart")

    def test_a_tv_that_stops_answering_after_the_push_answers_1(self):
        # The defect: this answered 2, the status the header keeps for a run that sent nothing, while the
        # config.json was already on the set. Shell: FAKE_RESTART=unreached makes theme_restart.py answer 2.
        result = self.run_theme(str(self.theme), FAKE_RESTART="unreached")
        self.assert_reached_the_tv(result)
        self.assertIn("the theme was pushed", result.stderr)
        pushes = [line for line in self.tv_calls() if line.startswith("--push")]
        self.assertTrue(any(str(self.theme / "config.json") in line for line in pushes), pushes)
        self.assertIn("did not answer", result.stderr, "the helper's own words are kept")

    def test_a_reset_that_cannot_restart_answers_1(self):
        # --reset ends the same way, and its files are off the TV by then: "nothing was sent" would be wrong
        # the other way round here - the removal happened.
        result = self.run_theme("--reset", FAKE_RESTART="unreached")
        self.assert_reached_the_tv(result)
        self.assertIn("the theme was removed", result.stderr)
        self.assertTrue(any("rm -f" in line and "config.json" in line for line in self.tv_calls()),
                        "the reset removed the overlay before the restart failed")

    def test_a_restart_the_tv_refuses_answers_1(self):
        # The other way the helper fails (its exit 1): already 1 before this change, pinned so the clamp
        # cannot turn a refused restart into something else.
        result = self.run_theme(str(self.theme), FAKE_RESTART="failed")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Theme restart failed", result.stderr)
        self.assertIn(HOME_DID_NOT_RESTART, result.stderr, result.stderr)
        self.assertNotIn("applied", result.stdout)

    def test_a_theme_that_never_reached_the_tv_still_answers_2(self):
        # The clamp starts only after the push: a theme this script cannot read sent nothing, and 2 says so.
        shutil.rmtree(self.theme)
        self.theme.mkdir()
        result = self.run_theme(str(self.theme))
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn(NOTHING_SENT, result.stderr)
        self.assertEqual([], self.tv_calls())



if __name__ == "__main__":
    unittest.main()

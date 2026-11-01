"""theme.sh answers the two shapes its header gives exit 2 for with 2, not with 1.

The header says: "0 the theme is on the TV, or the reset ran; 1 the theme could not be pushed, or Home did not
restart after it; 2 the run stopped before the theme was up - the command line is not one theme folder or
--reset, ... the TV was not reached". Two shapes that never reached the TV answered 1 instead, the status a
reader (and an assistant following PROMPT.md) reads as "the theme was tried and the TV refused it":

  * TV_HOST unset or empty: bash's own `${TV_HOST:?}` expansion wrote "tools/theme.sh: line 44: TV_HOST: set
    TV_HOST ..." and exited 1 - bash answers a failed `:?` with 1 - so a run that sent nothing looked like a
    push that had run. An empty TV_HOST (TV_HOST= ...) took the same path.
  * a word starting with a dash: `--help`, or a `--resett` one letter too many, was resolved against the
    caller's directory like a folder name and the run stopped at "no config.json in <cwd>/--help", exit 1.
    Nothing was sent either, and the message named a file nobody wrote instead of the command line.
  * the checkout has no tools/venv: the reader was given the venv line, then exit 1 for a run that never left
    this computer - while theme_restart.py, the helper the push ends with, answers that with 2 (#2763).

All three are judged before anything is asked of the TV now, in this script's own words, on exit 2. The usage
line in the header is unchanged: a theme folder or --reset, one argument.

These tests run the real tools/theme.sh against a stub tools/tv.py that appends every call to a log and exits 0,
so "nothing was sent" means the log is missing and "the theme is still pushed" means the log holds the
config.json push. A theme folder with a config.json is written in the test.

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
USAGE = "usage: theme.sh <theme folder> | --reset"
NOTHING_SENT = "Nothing was sent to the TV."

# Every call theme.sh (and theme_restart.py) makes reaches this file, so a test can tell whether anything was
# asked of the TV. The real client is never imported: the tools are copied into a folder of the test's own.
FAKE_TV = '''\
import os
import sys
with open(os.environ["FAKE_LOG"], "a") as log:
    log.write(" ".join(sys.argv[1:]) + "\\n")
sys.exit(0)
'''


class ThemeExitStatusTest(unittest.TestCase):
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
        (venv / "python").symlink_to(sys.executable)
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

    def assert_stopped(self, result, word=None):
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout, "a run that stopped before the theme was up writes nothing on stdout")
        self.assertIn(NOTHING_SENT, result.stderr, result.stderr)
        self.assertNotIn("Traceback", result.stderr, result.stderr)
        if word is not None:
            self.assertIn(word, result.stderr, "the message has to name what it could not read")
        self.assertEqual([], self.tv_calls(), "a run that sent nothing asked the TV for something")

    def test_no_tv_host_stops_with_2(self):
        # The defect: this answered 1, the code for "the theme could not be pushed", and the message was bash's
        # own expansion error naming a line number.
        result = self.run_theme(str(self.theme), TV_HOST=None)
        self.assert_stopped(result, "set TV_HOST to the TV IP address")
        self.assertNotIn("theme.sh: line", result.stderr,
                         "bash's own expansion error is not this script's message")
        self.assertNotIn("TV_HOST: set TV_HOST", result.stderr)

    def test_an_empty_tv_host_is_the_same_mistake(self):
        # `TV_HOST= tools/theme.sh themes/aurora`, or a variable that came out empty: `${TV_HOST:?}` treats an
        # empty value as unset too, so this reached the same exit 1.
        result = self.run_theme(str(self.theme), TV_HOST="")
        self.assert_stopped(result, "set TV_HOST to the TV IP address")

    def test_reset_without_a_host_sends_nothing(self):
        result = self.run_theme("--reset", TV_HOST=None)
        self.assert_stopped(result, "set TV_HOST to the TV IP address")

    def test_help_is_a_command_line_the_script_cannot_read(self):
        # The defect: resolved as a folder beside the caller, so the reader was told their theme had no
        # config.json at a path they never typed.
        result = self.run_theme("--help")
        self.assert_stopped(result, "--help")
        self.assertIn(USAGE, result.stderr, result.stderr)
        self.assertNotIn("no config.json", result.stderr,
                         "the theme folder was never read: the command line was")

    def test_a_misspelt_reset_is_not_read_as_a_folder(self):
        result = self.run_theme("--resett")
        self.assert_stopped(result, "--resett")
        self.assertIn(USAGE, result.stderr, result.stderr)

    def test_a_checkout_without_the_venv_stops_with_2(self):
        # theme_restart.py already answers its missing venv with 2 (#2763); theme.sh answered 1 - the code for
        # "the theme could not be pushed" - for a run that never left this computer.
        shutil.rmtree(self.tools / "venv")
        result = self.run_theme(str(self.theme))
        self.assert_stopped(result, "python3 -m venv tools/venv")

    def test_a_theme_folder_still_pushes_with_a_host(self):
        # The other direction: the refusal above must not catch a folder, and the harness has to reach the TV.
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("is on the TV", result.stdout)
        pushes = [line for line in self.tv_calls() if line.startswith("--push")]
        self.assertTrue(pushes, "the theme's config.json is pushed")
        self.assertTrue(any(str(self.theme / "config.json") in line for line in pushes), pushes)


if __name__ == "__main__":
    unittest.main()

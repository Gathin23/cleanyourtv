"""tools/theme.sh reads exactly one argument: a theme folder, or --reset.

The first word alone decided everything the script did: `if [ "${1:-}" = --reset ]` took the reset branch and
nothing else was read. So the undo hint the script prints, "Undo with: tools/theme.sh --reset", with the
folder appended - `theme.sh --reset themes/aurora` - matched that branch on the first word and threw the
folder away: the theme the reader named was removed from the TV instead of pushed, and the run answered
"reset" with exit 0, so nothing in it said the folder had not been used. The same line the other way round,
`theme.sh themes/aurora --reset`, pushed the theme and ignored the --reset, which is the one word that asked
for it to be removed. A second folder, `theme.sh themes/a themes/b`, pushed only a. All three end with
"Nothing was sent to the TV." now, on exit 2, before TV_HOST is required; theme.ps1 refuses -Reset with a
folder the same way, and a command line with no argument gets the usage line instead of bash's own
"THEME_ARG: usage: ..." out of the `${THEME_ARG:?}` expansion.

These tests run the real tools/theme.sh against a stub tools/tv.py that appends every call to a log and exits
0, so "nothing was sent to the TV" means the log is not there, and "the theme is still pushed" means the log
holds the config.json push. A theme folder with a config.json is written in the test.

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
PWSH = shutil.which("pwsh")
USAGE = "usage: theme.sh <theme folder> | --reset"
REFUSAL = "cannot share a command line"

# Every call theme.sh (and theme_restart.py) makes reaches this file, so a test can tell whether anything was
# asked of the TV. The real client is never imported: the tools are copied into a folder of the test's own.
FAKE_TV = '''\
import os
import sys
with open(os.environ["FAKE_LOG"], "a") as log:
    log.write(" ".join(sys.argv[1:]) + "\\n")
sys.exit(0)
'''


class ThemeOneArgumentTest(unittest.TestCase):
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
            # Windows without the symlink privilege (WinError 1314). Only the theme.sh runs use this
            # python, and they skip there; the checks of theme.ps1 still run.
            if os.name != "nt":
                raise
        self.theme = self.base / "my-theme"
        self.theme.mkdir()
        (self.theme / "config.json").write_text('{"background_color": "#000000"}\n', encoding="utf-8")
        self.other = self.base / "another-theme"
        self.other.mkdir()
        (self.other / "config.json").write_text('{"background_color": "#ffffff"}\n', encoding="utf-8")
        self.log = self.base / "tv-calls.log"

    def run_theme(self, *args):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        env = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        env.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log)})
        return subprocess.run(["bash", str(self.tools / "theme.sh"), *args], env=env,
                              capture_output=True, text=True, timeout=120)

    def tv_calls(self):
        if not self.log.exists():
            return []
        return [line for line in self.log.read_text().splitlines() if line.strip()]

    def assert_refused(self, result, word):
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout, "a refusal writes nothing on stdout")
        self.assertIn(word, result.stderr, "the message has to name the word that was not used")
        self.assertIn(REFUSAL, result.stderr, result.stderr)
        self.assertIn("Nothing was sent to the TV.", result.stderr, result.stderr)
        self.assertNotIn("Traceback", result.stderr, result.stderr)
        self.assertEqual([], self.tv_calls(), "a refused command line asked the TV for something")

    def test_reset_with_a_theme_folder_is_refused(self):
        # The defect: this line removes the theme the TV has and never pushes the folder it was given.
        self.assert_refused(self.run_theme("--reset", str(self.theme)), str(self.theme))

    def test_a_flag_after_the_folder_is_refused(self):
        # The other order: the theme is pushed and the --reset is dropped, so the theme it asked to remove stays.
        self.assert_refused(self.run_theme(str(self.theme), "--reset"), "--reset")

    def test_a_second_theme_folder_is_refused(self):
        self.assert_refused(self.run_theme(str(self.theme), str(self.other)), str(self.other))

    def test_no_argument_gets_the_usage_line(self):
        result = self.run_theme()
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn(USAGE, result.stderr, result.stderr)
        self.assertNotIn("THEME_ARG", result.stderr, "bash's own expansion error is not the usage line")
        self.assertEqual([], self.tv_calls())

    def test_reset_alone_still_resets(self):
        result = self.run_theme("--reset")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("reset", result.stdout)
        calls = self.tv_calls()
        self.assertTrue(calls, "the reset asks the TV to remove the overlay")
        self.assertTrue(any("config.json" in line and "rm -f" in line for line in calls),
                        "the reset has to remove the theme's config.json")

    def test_a_folder_alone_still_pushes(self):
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("is on the TV", result.stdout)
        pushes = [line for line in self.tv_calls() if line.startswith("--push")]
        self.assertTrue(pushes, "the theme's config.json is pushed")
        self.assertTrue(any(str(self.theme / "config.json") in line for line in pushes), pushes)


class ThemeArgumentSourceTest(unittest.TestCase):
    """The judgement sits where a command line cannot reach the TV, and the Windows twin carries it too."""

    def setUp(self):
        self.shell = (TOOLS / "theme.sh").read_text(encoding="utf-8")
        self.ps = (TOOLS / "theme.ps1").read_text(encoding="utf-8")

    def test_the_count_is_judged_before_the_reset_branch_and_before_a_send(self):
        judged = self.shell.index('if [ "$#" -gt 1 ]')
        self.assertLess(judged, self.shell.index('if [ "${1:-}" = --reset ]'),
                        "the reset branch is what dropped the folder, so the count is read first")
        self.assertLess(judged, self.shell.index('tv "mkdir -p $DIR'),
                        "nothing can be sent before the command line is read")

    def test_the_usage_line_is_ours_not_bash(self):
        self.assertIn('if [ -z "$THEME_ARG" ]', self.shell)
        self.assertLess(self.shell.index('if [ -z "$THEME_ARG" ]'), self.shell.index('${THEME_ARG:?'))

    def test_the_windows_twin_refuses_both(self):
        self.assertIn("if ($Theme -and $Reset)", self.ps)
        self.assertIn("Nothing was sent to the TV.", self.ps)
        judged = self.ps.index("if ($Theme -and $Reset)")
        self.assertLess(judged, self.ps.index("if ($Reset) {"), "the reset branch is judged before it runs")
        self.assertIn("exit 2", self.ps[judged:])

    def test_the_windows_twin_keeps_the_words_it_would_not_use(self):
        # Without a parameter that catches them, PowerShell's own binder refuses the command line before the
        # script runs: "A positional parameter cannot be found that accepts argument 'x'", exit 1 - the status
        # the header keeps for a push that ran and failed. theme.sh answers the same line at 2 with its own
        # sentence, so the twin has to take the words first and refuse them itself.
        self.assertIn("ValueFromRemainingArguments", self.ps)
        judged = self.ps.index("if ($Rest")
        self.assertLess(judged, self.ps.index("if ($Reset) {"), "the reset branch is judged before it runs")
        self.assertLess(judged, self.ps.index("Invoke-Tv tv.py"), "nothing can be sent before it is refused")
        self.assertIn("exit 2", self.ps[judged:])
        # The usage line a dash-led word gets, as theme.sh's `-*` branch answers it.
        self.assertIn("usage: theme.ps1 <theme folder> | -Reset", self.ps)


# The twin's own words, run for real: one theme folder pushes, a word it would not use is refused at 2.
FAKE_PY = '''\
import os
import sys
with open(os.environ["FAKE_LOG"], "a") as log:
    log.write(" ".join(sys.argv[1:]) + "\\n")
sys.exit(0)
'''


class ThemePs1CommandLineTest(unittest.TestCase):
    """theme.ps1 refuses the words it would drop, in its own words, at the status its header gives them.

    PowerShell's binder refuses them first, and at 1: `theme.ps1 themes\\aurora other` (a second folder, a
    word pasted from theme.sh), `theme.ps1 themes\\aurora --resett` (the flag one letter too long) and
    `theme.ps1 -h` all ended in "A positional parameter cannot be found ..." / "A parameter cannot be found
    ..." with exit 1, while theme.sh answers each at 2 with its own sentence and says nothing was sent. 1 is
    the status the header keeps for a theme that could not be pushed, so an assistant reading the status was
    told the push reached the TV when the command line never did.

    tools/tv.py, b64push.py and theme_restart.py are stand-ins that record their calls, and the venv is a shim,
    so a run that is not refused goes on to push. The cases need `pwsh` on PATH and skip without it; CI has it.
    """

    def setUp(self):
        if not PWSH:
            raise unittest.SkipTest("theme.ps1 needs pwsh on PATH")
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        self.tools = self.repo / "tools"
        self.tools.mkdir(parents=True)
        shutil.copy2(TOOLS / "theme.ps1", self.tools / "theme.ps1")
        for name in ("tv.py", "b64push.py", "theme_restart.py"):
            (self.tools / name).write_text(FAKE_PY)
        # The script asks for the Windows path; on Linux and macOS this shim runs the tests' interpreter.
        client = self.tools / "venv" / "Scripts" / "python.exe"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)
        self.theme = self.base / "my-theme"
        self.theme.mkdir()
        (self.theme / "config.json").write_text('{"background_color": "#000000"}\n', encoding="utf-8")
        self.log = self.base / "tv-calls.log"

    def run_theme(self, *args):
        env = dict(os.environ, TV_HOST="192.0.2.10", FAKE_LOG=str(self.log))
        return subprocess.run([PWSH, "-NoProfile", "-File", str(self.tools / "theme.ps1"), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True, timeout=180)

    def tv_calls(self):
        if not self.log.exists():
            return []
        return [line for line in self.log.read_text().splitlines() if line.strip()]

    def assert_refused(self, result, word):
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout, "a refusal writes nothing on stdout")
        self.assertIn(word, result.stderr, "the message has to name the word that was not used")
        self.assertIn("Nothing was sent to the TV.", result.stderr, result.stderr)
        self.assertNotIn("cannot be found", result.stderr,
                         "PowerShell's own binder answered, not the script")
        self.assertNotIn("Traceback", result.stderr, result.stderr)
        self.assertEqual([], self.tv_calls(), "a refused command line asked the TV for something")

    def test_a_second_word_is_refused(self):
        # `theme.ps1 themes\aurora extra` is the line theme.sh refuses with "would not have been used".
        self.assert_refused(self.run_theme(str(self.theme), "extra"), "extra")
        self.assertIn("cannot share a command line", self.run_theme(str(self.theme), "extra").stderr)

    def test_a_misspelt_switch_is_refused_with_the_usage_line(self):
        # One letter too many: PowerShell would have refused it as an unknown parameter, at 1.
        result = self.run_theme(str(self.theme), "--resett")
        self.assert_refused(result, "--resett")
        self.assertIn("usage: theme.ps1 <theme folder> | -Reset", result.stderr, result.stderr)

    def test_the_help_flag_answers_the_usage_line(self):
        result = self.run_theme("-h")
        self.assert_refused(result, "-h")
        self.assertIn("usage: theme.ps1 <theme folder> | -Reset", result.stderr, result.stderr)

    def test_one_folder_still_pushes(self):
        result = self.run_theme(str(self.theme))
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("is on the TV", result.stdout)
        pushes = [line for line in self.tv_calls() if line.startswith("--push")]
        self.assertTrue(pushes, "the theme's config.json is pushed")
        self.assertTrue(any(str(self.theme / "config.json") in line for line in pushes), pushes)

    def test_reset_alone_still_resets(self):
        result = self.run_theme("-Reset")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("reset", result.stdout)
        self.assertTrue(self.tv_calls(), "the reset asks the TV to remove the overlay")


if __name__ == "__main__":
    unittest.main()

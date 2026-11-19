"""tools/theme.sh pushes to the launcher that actually owns Home, not to a hardcoded package.

The launcher is usually com.gathin.tvhome, but some firmware only hands HOME to a launcher installed under
the applicationId on its allowlist: Xiaomi China sets on Android 14 want com.ts.launcher
(docs/devices/xiaomi-mitv-mftr0-debloat-home.md). With the package hardcoded, the files went into a
directory that launcher never reads and the restart named a package that is not installed: the push
reported success while the TV showed the old theme.

Resolution has to stay lazy. A command line theme.sh refuses (`--help`, a folder with no config.json, a
config.json the launcher would drop) sends nothing to the TV, and asking the TV which package owns Home is
still asking the TV.

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

# What `cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME`
# answers on the TV the bug was found on: the launcher answers HOME, under the allowlisted applicationId.
XIAOMI_HOME = "com.ts.launcher/com.gathin.tvhome.MainActivity\n"
DEFAULT_HOME = "com.gathin.tvhome/com.gathin.tvhome.MainActivity\n"


class LauncherPackageTest(unittest.TestCase):
    def setUp(self):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")

    def prepare(self, repo, home_answer):
        """Copy theme.sh and stand in for tools/venv/bin/python.

        The fake answers the HOME query with `home_answer` (None: the TV answers nothing, as when it is
        asleep) and records every call, so a test can see which package the push and the restart address.
        """
        tools = repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "theme.sh", tools / "theme.sh")
        client = tools / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        python = Path(sys.executable).as_posix()
        answer = tools / "home_answer.txt"
        answer.write_text("" if home_answer is None else home_answer)
        client.write_text(
            '#!/bin/sh\n'
            'if [ "$1" = "-I" ] && [ "$2" = "-c" ]; then\n'
            f'  exec "{python}" "$@"\n'
            'fi\n'
            'shift\n'
            f'printf "%s\\n" "$*" >> "{tools}/calls.txt"\n'
            # The HOME query is the only call whose answer matters; everything else only needs to pass.
            'case "$*" in\n'
            '  *resolve-activity*) cat ' f'"{answer}"' ' ; exit 0 ;;\n'
            'esac\n'
            'exit 0\n'
        )
        client.chmod(0o755)
        return tools

    def run_theme(self, tools, argument, cwd, extra_env=None):
        env = dict(os.environ, TV_HOST="192.0.2.1")
        env.pop("LAUNCHER_PACKAGE", None)
        env.update(extra_env or {})
        result = subprocess.run(["bash", str(tools / "theme.sh"), argument], cwd=cwd, env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL)
        return result, self.calls(tools)

    def calls(self, tools):
        log = tools / "calls.txt"
        return log.read_text().splitlines() if log.exists() else []

    def make_theme(self, base):
        theme = base / "theme"
        theme.mkdir()
        (theme / "config.json").write_text(json.dumps({"columns": 5}))
        return theme

    def push_lines(self, calls):
        """The calls that write to a package's directory: mkdir, the provider URI, the staging push."""
        return [line for line in calls
                if "/Android/data/" in line or "content://" in line or "tv.py --push" in line]

    def test_the_push_follows_the_launcher_that_owns_home(self):
        """The case the bug was reported on: HOME is com.ts.launcher, so the theme goes there."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo", XIAOMI_HOME)
            theme = self.make_theme(base)
            result, calls = self.run_theme(tools, str(theme), cwd=base)
            self.assertEqual(result.returncode, 0, result.stderr)
            push = "\n".join(self.push_lines(calls))
            self.assertIn("/Android/data/com.ts.launcher/files", push, push)
            self.assertIn("content://com.ts.launcher.theme", push, push)
            self.assertNotIn("com.gathin.tvhome/files", push, push)
            self.assertNotIn("content://com.gathin.tvhome.theme", push, push)
            # The restart names the same package, or Home is not the launcher that just got the theme.
            restart = [line for line in calls if "theme_restart.py" in line]
            self.assertTrue(restart, calls)

    def test_the_default_launcher_is_still_the_default(self):
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo", DEFAULT_HOME)
            theme = self.make_theme(base)
            result, calls = self.run_theme(tools, str(theme), cwd=base)
            self.assertEqual(result.returncode, 0, result.stderr)
            push = "\n".join(self.push_lines(calls))
            self.assertIn("/Android/data/com.gathin.tvhome/files", push, push)
            self.assertNotIn("com.ts.launcher", push, push)

    def test_a_tv_that_cannot_name_home_keeps_the_default(self):
        """A set that does not answer, or answers the chooser, still gets a push at the usual package."""
        for answer in (None, "", "android\n", "com.example.otherlauncher/.Home\n"):
            with self.subTest(answer=answer), tempfile.TemporaryDirectory() as tmp:
                base = Path(tmp)
                tools = self.prepare(base / "repo", answer)
                theme = self.make_theme(base)
                result, calls = self.run_theme(tools, str(theme), cwd=base)
                self.assertEqual(result.returncode, 0, result.stderr)
                push = "\n".join(self.push_lines(calls))
                self.assertIn("/Android/data/com.gathin.tvhome/files", push, push)

    def test_launcher_package_is_taken_as_given(self):
        """LAUNCHER_PACKAGE names it outright, without asking the TV (the theme script's own override)."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo", DEFAULT_HOME)
            theme = self.make_theme(base)
            result, calls = self.run_theme(tools, str(theme), cwd=base,
                                           extra_env={"LAUNCHER_PACKAGE": "com.ts.launcher"})
            self.assertEqual(result.returncode, 0, result.stderr)
            push = "\n".join(self.push_lines(calls))
            self.assertIn("/Android/data/com.ts.launcher/files", push, push)
            self.assertFalse([line for line in calls if "resolve-activity" in line], calls)

    def test_a_refused_command_line_sends_nothing(self):
        """The reason resolution is lazy: a run that stops on its own must not have asked the TV anything."""
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp)
            tools = self.prepare(base / "repo", XIAOMI_HOME)
            empty = base / "not-a-theme"
            empty.mkdir()
            result, calls = self.run_theme(tools, str(empty), cwd=base)
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertEqual([], calls, "a run that sent nothing asked the TV for something")


if __name__ == "__main__":
    unittest.main()

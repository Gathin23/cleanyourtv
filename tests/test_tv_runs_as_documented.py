"""`tools/tv.py ...` runs as the docs type it, with no interpreter in front.

README.md ("Stuck already?"), docs/TROUBLESHOOTING.md, tv.py's own TV_HOST hint and the rescue file that
launcher/build-and-install.sh writes when the Home handover fails all give the bare form. It failed twice
over. git had the file as 100644, so the shell refused it ("permission denied", exit 126). And once it is
executable, its `#!/usr/bin/env python3` starts the first python3 on PATH, which seldom has adb_shell (the
README installs it into tools/venv only), so the run ended in a ModuleNotFoundError traceback.

Now the file is 100755, and a run without adb_shell starts again, once, under the tools/venv Python beside
it, with -I and the same arguments, or says how to make that venv. Imported as a module (recovery.py,
privacy.py, bench.py and the stub tests) it still raises the ImportError, which those callers turn into
messages of their own.

The runs copy tv.py into a temporary tools/ folder, with or without a stand-in venv, and start it with -S,
which leaves site-packages out: the interpreter running the suite then stands for a python3 without
adb_shell whether it has one or not, and no run can reach the network. The stand-in venv Pythons are shell
scripts, so those cases need a POSIX shell; CI runs them.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"
# tv.py sets this before it starts the venv's Python, so a venv without adb_shell does not start itself again.
MARK = "CLEANYOURTV_TV_PY_VENV"
MAKE_VENV = (r"python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt"
             if os.name == "nt" else
             "python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt")


class TvIsExecutableTest(unittest.TestCase):
    def test_git_has_tv_py_as_executable(self):
        # What a clone gets: the mode in git, not the mode of this checkout's file.
        if shutil.which("git") is None:
            self.skipTest("git is not installed")
        r = subprocess.run(["git", "ls-files", "-s", "tools/tv.py"], cwd=str(ROOT), capture_output=True,
                           text=True)
        if r.returncode != 0 or not r.stdout:
            self.skipTest("not a git checkout")
        self.assertTrue(r.stdout.startswith("100755 "),
                        f"tools/tv.py must be executable in git, as disable.sh and theme.sh are: {r.stdout}")

    @unittest.skipIf(os.name == "nt", "a script started by its shebang needs a POSIX system")
    def test_the_bare_command_starts(self):
        # --help needs neither adb_shell nor a TV, so this is the shebang and the mode alone.
        if shutil.which("python3") is None:
            self.skipTest("no python3 on PATH for the shebang")
        try:
            r = subprocess.run([str(SCRIPT), "--help"], cwd=str(ROOT), capture_output=True, text=True,
                               timeout=60)
        except PermissionError:
            self.fail("`tools/tv.py ...` gives 'permission denied': the file is not executable")
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertIn("tv.py --probe", r.stdout)


class TvWithoutAdbShellTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        base = Path(self._tmp.name)
        self.tools = base / "tools"
        self.tools.mkdir()
        self.script = self.tools / "tv.py"
        shutil.copyfile(SCRIPT, self.script)
        self.home = base / "home"
        self.home.mkdir()
        self.record = base / "venv-ran"

    def env(self):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH", MARK)}
        # The documentation address, so the runs look like the docs; -S keeps every run away from a client.
        env["TV_HOST"] = "192.0.2.1"
        env["HOME"] = str(self.home)
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["VENV_RAN"] = str(self.record)
        return env

    def run_tv(self, args):
        return subprocess.run([sys.executable, "-S", "-I", str(self.script), *args], cwd=str(self.tools.parent),
                              env=self.env(), capture_output=True, text=True, timeout=60)

    def fake_venv(self, body):
        """tools/venv/bin/python as a shell script: it records how it was started, then runs `body`."""
        python = self.tools / "venv" / "bin" / "python"
        python.parent.mkdir(parents=True)
        python.write_text('#!/bin/sh\nprintf \'%s\\n\' "--- $' + MARK + '" "$@" >> "$VENV_RAN"\n' + body + "\n",
                          encoding="utf-8")
        python.chmod(0o755)

    def venv_runs(self):
        """[(the marker it saw, [its arguments])] for each time the stand-in venv Python was started."""
        if not self.record.exists():
            return []
        runs = []
        for line in self.record.read_text(encoding="utf-8").splitlines():
            if line.startswith("--- "):
                runs.append((line[4:], []))
            else:
                runs[-1][1].append(line)
        return runs

    def assert_says_how_to_make_the_venv(self, r):
        self.assertEqual(1, r.returncode, r.stderr)
        self.assertNotIn("Traceback", r.stderr)
        self.assertIn("tools/tv.py needs the packages in tools/requirements.txt", r.stderr)
        self.assertIn("No module named 'adb_shell'", r.stderr)
        self.assertIn(MAKE_VENV, r.stderr)
        self.assertEqual("", r.stdout)

    def test_without_a_venv_it_says_how_to_make_one(self):
        # The defect: a ModuleNotFoundError traceback, which names no venv and no requirements file.
        self.assert_says_how_to_make_the_venv(self.run_tv(["--probe"]))

    @unittest.skipIf(os.name == "nt", "the stand-in venv Python is a shell script")
    def test_it_runs_again_under_the_venv_with_the_same_arguments(self):
        self.fake_venv('echo "venv python ran"\nexit 3')
        for args in (["--probe"], ["pm list packages"], ["--screenshot", "shots/a b.png", "HOME"]):
            with self.subTest(args=args):
                self.record.unlink(missing_ok=True)
                r = self.run_tv(args)
                self.assertEqual(3, r.returncode, "the venv run's exit status is tv.py's: " + r.stderr)
                self.assertEqual("venv python ran\n", r.stdout)
                self.assertNotIn("Traceback", r.stderr)
                self.assertEqual([("1", ["-I", os.path.abspath(str(self.script)), *args])], self.venv_runs())

    @unittest.skipIf(os.name == "nt", "the stand-in venv Python is a shell script")
    def test_a_venv_without_adb_shell_gets_the_message_not_another_run(self):
        # A venv made without `pip install -r tools/requirements.txt`: the second run has no adb_shell
        # either, and must stop with the message rather than start the venv again and again.
        self.fake_venv(f'exec "{sys.executable}" -S "$@"')
        r = self.run_tv(["--probe"])
        self.assert_says_how_to_make_the_venv(r)
        self.assertEqual(1, len(self.venv_runs()), self.venv_runs())

    def test_imported_it_still_raises_and_starts_nothing(self):
        # recovery.py and privacy.py catch ImportError, bench.py ModuleNotFoundError: neither may find the
        # process replaced by the venv's Python, or a message and an exit in the middle of an import.
        if os.name != "nt":
            self.fake_venv("exit 3")
        code = ("import sys\n"
                f"sys.path.insert(0, {str(self.tools)!r})\n"
                "try:\n"
                "    import tv\n"
                "except ModuleNotFoundError as e:\n"
                "    print('raised', e.name)\n")
        r = subprocess.run([sys.executable, "-S", "-I", "-c", code], cwd=str(self.tools.parent), env=self.env(),
                           capture_output=True, text=True, timeout=60)
        self.assertEqual(0, r.returncode, r.stderr)
        self.assertEqual("raised adb_shell\n", r.stdout)
        self.assertEqual([], self.venv_runs())


if __name__ == "__main__":
    unittest.main()

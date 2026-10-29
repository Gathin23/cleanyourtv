"""tools/trace.py started with a Python that has no adb_shell: the venv's Python, or the line to run.

The pages print `tools/venv/bin/python -I tools/trace.py home --runs 5 --out traces/before`, and a
reader who has just cloned the repository types `python3 tools/trace.py home` instead - the shape the
file's own usage block shows. That second run died at `import tv`, whose top-level imports are

    tools/tv.py:42: from adb_shell.adb_device import AdbDeviceTcp
    ModuleNotFoundError: No module named 'adb_shell'

a traceback with exit 1 (the code a run that recorded nothing returns), naming neither
tools/requirements.txt nor the tools/venv/bin/python that was sitting next to it with the package
installed. tv.py re-execs itself in that position, bench.py re-execs itself before it connects, and
privacy.py, recovery.py and check.py each answer with the line to run; trace.py was the one script that
imports the client and answered with a traceback.

It now does what bench.py does: once, marked in the environment as tv.py marks its own re-exec, it
starts again under tools/venv's Python with the arguments it was given, and a venv that lacks the
package as well gets the line saying how to make one. The marker is what keeps a broken venv from
starting a third process; comparing the interpreters' paths is not enough, because tools/venv/bin/python
is often a symlink to the very interpreter that started the run.

These tests run the real script in a folder of its own (with -S, so adb_shell cannot be imported
however this suite is run) beside a stub tools/venv/bin/python that records the arguments it is started
with: no TV, no network and no ADB key is needed.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
MARKER = "CLEANYOURTV_TRACE_PY_VENV"

# tools/venv/bin/python, as the tests make it: it records what it was started with and stops there. A
# re-exec that never happened leaves no file, which is how "the venv was not started" is checked.
STUB_VENV_PYTHON = """#!/bin/sh
printf '%s\\n' "$@" > "$TRACE_STUB_LOG"
exit 0
"""


class TraceVenvTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self._tmp.name).resolve()
        self.tools = self.folder / "tools"
        self.tools.mkdir()
        shutil.copyfile(ROOT / "tools" / "trace.py", self.tools / "trace.py")
        self.script = self.tools / "trace.py"
        self.log = self.folder / "venv-python-args.txt"

    def tearDown(self):
        self._tmp.cleanup()

    def stub_venv(self):
        binary = self.tools / "venv" / "bin"
        binary.mkdir(parents=True)
        stub = binary / "python"
        stub.write_text(STUB_VENV_PYTHON, encoding="utf-8")
        stub.chmod(0o755)
        return stub

    def started_with(self):
        if not self.log.exists():
            return None
        return self.log.read_text(encoding="utf-8").splitlines()

    def run_trace(self, *args, env_extra=None):
        env = os.environ.copy()
        for key in ("TV_HOST", "TV_PORT", MARKER):
            env.pop(key, None)
        env["TRACE_STUB_LOG"] = str(self.log)
        env.update(env_extra or {})
        # -S: no site-packages, so adb_shell is not importable whatever Python runs this suite.
        return subprocess.run([sys.executable, "-S", str(self.script), *args],
                              cwd=self.folder, env=env, capture_output=True, text=True, timeout=60)

    # ---- what a checkout without a venv answers ------------------------------------------------------

    def test_a_python_without_the_packages_is_told_how_to_make_the_venv(self):
        result = self.run_trace("home", "--runs", "1", "--out", "traces")
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("tools/trace.py needs the packages in tools/requirements.txt", result.stderr)
        self.assertIn("No module named 'adb_shell'", result.stderr)
        self.assertIn("python3 -m venv tools/venv && tools/venv/bin/pip install -r "
                      "tools/requirements.txt", result.stderr)
        self.assertEqual("", result.stdout)
        self.assertEqual(None, self.started_with(), "no venv is there, and one was started anyway")

    def test_the_line_is_the_one_tv_py_prints(self):
        """The hint names the same command the client itself prints, read out of tv.py rather than copied."""
        client = (ROOT / "tools" / "tv.py").read_text(encoding="utf-8")
        match = re.search(r'"(python3 -m venv tools/venv && tools/venv/bin/pip install -r '
                          r'tools/requirements\.txt)"', client)
        if match is None:
            self.fail("tools/tv.py no longer carries the venv command this check reads")
        command = match.group(1)
        result = self.run_trace("home")
        self.assertIn(f"run: {command}", result.stderr)

    def test_help_is_answered_without_the_packages(self):
        result = self.run_trace("--help")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("usage:", result.stdout)
        self.assertNotIn("Traceback", result.stderr)
        self.assertNotIn("requirements.txt", result.stderr)

    # ---- what a checkout with a venv does ------------------------------------------------------------

    def test_the_venv_python_is_started_with_the_arguments_and_isolated(self):
        self.stub_venv()
        result = self.run_trace("home", "--runs", "1", "--out", "traces")
        self.assertEqual(["-I", str(self.script), "home", "--runs", "1", "--out", "traces"],
                         self.started_with(),
                         "tools/venv/bin/python was not started with -I and the script's own arguments")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual("", result.stdout)
        self.assertEqual("", result.stderr)

    def test_an_argument_error_is_still_answered_before_the_venv_is_started(self):
        self.stub_venv()
        result = self.run_trace("home", "--runs", "0", "--out", "traces")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("--runs must be between 1 and 50", result.stderr)
        self.assertEqual(None, self.started_with(),
                         "a command line that cannot run started the venv's Python")

    def test_a_venv_without_the_packages_is_not_started_twice(self):
        """The marker tv.py sets for the same reason: the run that already tried the venv gets the line."""
        self.stub_venv()
        result = self.run_trace("home", env_extra={MARKER: "1"})
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertIn("tools/trace.py needs the packages in tools/requirements.txt", result.stderr)
        self.assertEqual(None, self.started_with(),
                         "the marker was set and the venv was started again")

    # ---- the script, and the same file called as a library -------------------------------------------

    def load_script(self):
        """The copy beside the stub venv, imported as a module (tests/test_trace.py does the same)."""
        spec = importlib.util.spec_from_file_location("cyt_trace_venv", self.script)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_main_given_its_own_arguments_never_starts_another_python(self):
        """main(argv) is called by tests/test_trace.py; sys.argv there is the test runner's command line.

        Starting the venv from that call handed `discover -s tests` to trace.py's own parser, which
        answered 'argument scenario: invalid choice: discover' from a suite that had not asked for a
        recording at all.
        """
        self.stub_venv()
        module = self.load_script()
        fake_tv = types.ModuleType("tv")
        fake_tv.HOST, fake_tv.PORT = "192.0.2.7", "5555"

        def connect():
            raise SystemExit("reached connect()")
        fake_tv.connect = connect
        fake_bench = types.ModuleType("bench")
        fake_bench.accessibility_services = lambda *a: []
        fake_bench.restore_accessibility = lambda *a: None
        # A Python that has no adb_shell, whatever interpreter this suite runs under.
        no_packages = types.SimpleNamespace(util=types.SimpleNamespace(find_spec=lambda name: None))
        out = self.folder / "traces"
        with mock.patch.object(module, "importlib", no_packages), \
             mock.patch("os.execv") as execv, \
             mock.patch.dict(sys.modules, {"tv": fake_tv, "bench": fake_bench}), \
             contextlib.redirect_stderr(io.StringIO()), contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit) as caught:
                module.main(["coldsys", "--out", str(out)])
        self.assertEqual("reached connect()", str(caught.exception.code),
                         "main(argv) did not reach the TV")
        execv.assert_not_called()
        self.assertEqual(None, self.started_with(), "a library call started the venv's Python")

    def test_a_run_of_the_script_still_starts_the_venv_once(self):
        """The same file started as a script (argv stays None) does start it, with -I and its arguments."""
        self.stub_venv()
        module = self.load_script()
        no_packages = types.SimpleNamespace(util=types.SimpleNamespace(find_spec=lambda name: None))
        out = self.folder / "traces"
        with mock.patch.object(module, "importlib", no_packages), \
             mock.patch("os.execv") as execv, \
             mock.patch.object(sys, "argv", ["trace.py", "coldsys", "--out", str(out)]), \
             mock.patch.dict(os.environ), \
             contextlib.redirect_stderr(io.StringIO()):
            # The stub is replaced by a recorder, so the call is answered rather than made; what a real
            # execv would do is what the subprocess tests above check.
            with self.assertRaises(SystemExit) as caught:
                module.main()
        venv = str(self.tools / "venv" / "bin" / "python")
        self.assertEqual([venv, "-I", str(self.script), "coldsys", "--out", str(out)],
                         execv.call_args.args[1])
        # A mocked execv returns, so the run goes on to the hint it gives a venv without the package;
        # sys.exit(str) is status 1 at the process level, which the subprocess tests above check.
        self.assertIn("tools/trace.py needs the packages", str(caught.exception.code))

    # ---- the order the script works in ---------------------------------------------------------------

    def test_the_check_sits_before_the_client_is_imported(self):
        source = (ROOT / "tools" / "trace.py").read_text(encoding="utf-8")
        check = source.find('find_spec("adb_shell")')
        self.assertGreater(check, -1, "tools/trace.py no longer asks whether adb_shell is importable")
        for later in ("import bench  # noqa: E402", "import tv  # noqa: E402", "tv.connect()"):
            self.assertGreater(source.find(later), check,
                               f"{later!r} is reached before the check, so a missing package is a "
                               f"traceback again")
        self.assertIn("client_python_or_exit()", source)
        self.assertIn(f'VENV_MARKER = "{MARKER}"', source)

    def test_the_docstring_says_so(self):
        source = (ROOT / "tools" / "trace.py").read_text(encoding="utf-8")
        doc = source.split('"""')[1]
        self.assertIn("tools/venv's Python", doc,
                      "the usage block does not say the script runs again under the venv's Python")


if __name__ == "__main__":
    unittest.main()

"""A bench.py run that never got as far as the TV answers 2, and says nothing was measured.

tools/bench.py's exit status is the reader's answer to "did the numbers disappear, or was there never a
run": 0 the run is measured and written, 1 anything else including a result.json that could not be
written after the run, and 2 a run that never reached the TV. Three shapes still answered 1, all before a
connection existed:

- no `--host` and no `TV_HOST` - a bare `SystemExit("pass --host or set TV_HOST")`, whose text the
  interpreter prints and whose status is 1;
- a `--port` (or `TV_PORT`, or a `:port` in the address) that tools/tv.py cannot read - tv.py refuses it
  while it is imported, so `import tv` leaves a SystemExit carrying "not a port number: 'abc'", again 1;
- a checkout a reader has not built yet, with no adb_shell and no tools/venv for main() to start again
  under - the ModuleNotFoundError handler raised a SystemExit with its sentence, again 1.

1 is also what a finished run keeps when its result.json could not be written, so a run that measured
nothing read as a run whose numbers were lost - the one thing perf-ab.md's author has to tell apart.

The reader here is the checkout's own tools/tv.py, copied beside a copy of bench.py (that is where bench
imports it from), and the stub cases pin the handler around it: a client module that stops with its own
text (2, that text printed), one that stops with a number (2, since nothing was measured) and one that
exits 0 (its status stands - the branch tools/theme_restart.py keeps). A TV that answered but named no
HOME activity keeps 1: it was reached, and the docstring's 1 covers it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
NOTHING_MEASURED = "Nothing was measured, and nothing was written."


def packages_installed():
    """True when this interpreter can load adb_shell, which the checkout's tv.py needs to be imported."""
    return subprocess.run([sys.executable, "-c", "import adb_shell"],
                          capture_output=True, timeout=120).returncode == 0


class Base(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.base = Path(self._tmp.name)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(TOOLS / "bench.py", self.tools / "bench.py")
        self.script = self.tools / "bench.py"
        self.home = self.base / "home"
        (self.home / ".android").mkdir(parents=True)

    def run_bench(self, *args, python_flags=(), **environment):
        """bench.py's own command line in the copied folder; no TV_HOST or TV_PORT from this machine."""
        env = {k: v for k, v in os.environ.items()
               if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env.update(HOME=str(self.home), USERPROFILE=str(self.home))
        env.update(environment)
        return subprocess.run([sys.executable, *python_flags, "-I", str(self.script), *args],
                              cwd=str(self.tools), env=env, capture_output=True, text=True, timeout=120)

    def assert_nothing_measured(self, result):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn(NOTHING_MEASURED, result.stderr,
                      "the reader is told in one line that nothing was measured")
        self.assertEqual(result.stdout, "", "a run that measured nothing prints no numbers")
        self.assertFalse((self.tools / "results").exists(), "no results folder for a run that did not happen")

    def stub(self, body):
        """A stand-in tools/tv.py beside the copy of bench.py, which imports it from there."""
        (self.tools / "tv.py").write_text(body, encoding="utf-8")


class RealClientTest(Base):
    """The checkout's own tools/tv.py beside a copy of bench.py: what a reader's run does."""

    @classmethod
    def setUpClass(cls):
        if not packages_installed():
            raise unittest.SkipTest("needs the packages tools/venv installs (pip install -r "
                                    "tools/requirements.txt)")

    def setUp(self):
        super().setUp()
        shutil.copy2(TOOLS / "tv.py", self.tools / "tv.py")

    def test_a_run_with_no_address_says_nothing_was_measured(self):
        result = self.run_bench("--out-dir", "results")
        self.assert_nothing_measured(result)
        self.assertIn("--host", result.stderr, "the line names the option")
        self.assertIn("TV_HOST", result.stderr, "and the environment variable")

    def test_a_tv_port_that_is_not_a_number_says_nothing_was_measured(self):
        result = self.run_bench("--out-dir", "results", TV_HOST="192.0.2.9", TV_PORT="abc")
        self.assert_nothing_measured(result)
        self.assertIn("not a port number: 'abc'", result.stderr, "tv.py's own line is printed")

    def test_a_port_outside_the_range_says_nothing_was_measured(self):
        for args, environment, named in ((["--port", "70000"], {"TV_HOST": "192.0.2.9"}, "'70000'"),
                                        ([], {"TV_HOST": "192.0.2.9", "TV_PORT": "0"}, "'0'"),
                                        (["--host", "192.0.2.9:70000"], {}, "'70000'")):
            with self.subTest(args=args, environment=environment):
                result = self.run_bench("--out-dir", "results", *args, **environment)
                self.assert_nothing_measured(result)
                self.assertIn(f"not a port number: {named}", result.stderr)

    def test_a_checkout_without_adb_shell_says_nothing_was_measured(self):
        # -S leaves site-packages out, so adb_shell is missing as it is in a fresh clone; there is no
        # tools/venv beside the copied script for main() to start again under, so this is the branch a
        # reader who never ran the pip line actually reaches.
        result = self.run_bench("--host", "192.0.2.9", "--out-dir", "results", python_flags=("-S",))
        self.assert_nothing_measured(result)
        self.assertIn("bench.py needs adb_shell", result.stderr)
        self.assertIn("tools/venv", result.stderr, "the line that builds the venv is still given")


class StubClientTest(Base):
    """The handler around `import tv` itself: a client module that decides to stop while imported."""

    def test_a_client_that_stops_with_its_own_words_prints_them_and_answers_2(self):
        self.stub('raise SystemExit("the client refuses this TV")\n')
        result = self.run_bench("--host", "192.0.2.9", "--out-dir", "results")
        self.assert_nothing_measured(result)
        self.assertIn("the client refuses this TV", result.stderr, "tv.py's text is printed, not swallowed")

    def test_a_client_that_stops_with_a_number_answers_2(self):
        self.stub("raise SystemExit(3)\n")
        result = self.run_bench("--host", "192.0.2.9", "--out-dir", "results")
        self.assert_nothing_measured(result)
        self.assertNotIn("3", result.stderr.replace("Nothing was measured", ""),
                         "a number is a status, not a sentence to print")

    def test_a_client_that_exits_0_keeps_its_status(self):
        self.stub("raise SystemExit(0)\n")
        result = self.run_bench("--host", "192.0.2.9", "--out-dir", "results")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn(NOTHING_MEASURED, result.stderr, "nothing is added to a client that answered")

    def test_a_tv_that_answered_but_names_no_home_keeps_1(self):
        # Reached and read: this is the docstring's "anything else", not a run that never got there.
        self.stub("HOST, PORT = '192.0.2.9', 5555\n"
                  "\n"
                  "class Device:\n"
                  "    def shell(self, command, **kwargs):\n"
                  "        return b''\n"
                  "\n"
                  "    def close(self):\n"
                  "        pass\n"
                  "\n"
                  "def connect():\n"
                  "    return Device()\n"
                  "\n"
                  "def connect_error(error):\n"
                  "    return None\n")
        result = self.run_bench("--host", "192.0.2.9", "--out-dir", "results")
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("no component answers HOME on this TV", result.stderr)


if __name__ == "__main__":
    unittest.main()

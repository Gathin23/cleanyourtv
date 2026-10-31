"""A trace.py run that never got as far as the TV answers 2, and says nothing was recorded.

tools/trace.py's exit status is the reader's answer to "is the trace in the folder, or was there never a
recording": 0 every run is recorded and written, 1 a recording that started and then failed, 2 an
argument it cannot use and a TV that cannot be reached. The connect() handler was brought to that in
#2683, and two shapes still answered 1, both before a connection existed:

- no `TV_HOST` - tools/tv.py refuses it while it is imported ("Set TV_HOST to your TV's IP address"),
  and `import tv` in main() leaves a SystemExit carrying that text, which the interpreter answers with 1;
- a `TV_PORT` (or a `:port` in the address) tools/tv.py cannot read - the same import-time refusal, with
  "not a port number: 'abc'".

1 is also what a recording that started and then failed keeps, so a run that never reached the TV read
as one whose trace was lost - the one thing [docs/workflows/perf-ab.md]'s author has to tell apart.

The reader here is the checkout's own tools/tv.py, copied beside a copy of trace.py (that is where trace
imports it from), and the stub cases pin the handler around it: a client module that stops with its own
text (2, that text printed), one that stops with a number (2, since nothing was recorded) and one that
exits 0 (its status stands - the branch tools/theme_restart.py keeps). A TV that answered keeps 1: it was
reached, and the docstring's 1 covers what happens next.

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
NOTHING_RECORDED = "Nothing was recorded"


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
        # trace.py imports tv for the connection and bench for the accessibility helpers, from its own
        # folder, so both travel with the copy.
        for name in ("trace.py", "bench.py"):
            shutil.copy2(TOOLS / name, self.tools / name)
        self.script = self.tools / "trace.py"
        self.out = self.base / "traces"
        self.home = self.base / "home"
        (self.home / ".android").mkdir(parents=True)

    def run_trace(self, *args, scenario="coldsys", **environment):
        """trace.py's own command line in the copied folder; no TV_HOST or TV_PORT from this machine."""
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env.update(HOME=str(self.home), USERPROFILE=str(self.home))
        env.update(environment)
        return subprocess.run([sys.executable, "-I", str(self.script), scenario, "--out", str(self.out),
                               *args], cwd=str(self.tools), env=env, capture_output=True, text=True,
                              timeout=120)

    def assert_nothing_recorded(self, result):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn(NOTHING_RECORDED, result.stderr, "the reader is told in one line that nothing was recorded")
        self.assertEqual(result.stdout, "", "a run that recorded nothing prints no trace")
        self.assertFalse(self.out.exists(), "the folder was made for a run that never started")

    def stub(self, body):
        """A stand-in tools/tv.py beside the copy of trace.py, which imports it from there."""
        (self.tools / "tv.py").write_text(body, encoding="utf-8")


class RealClientTest(Base):
    """The checkout's own tools/tv.py beside a copy of trace.py: what a reader's run does."""

    @classmethod
    def setUpClass(cls):
        if not packages_installed():
            raise unittest.SkipTest("needs the packages tools/venv installs (pip install -r "
                                    "tools/requirements.txt)")

    def setUp(self):
        super().setUp()
        shutil.copy2(TOOLS / "tv.py", self.tools / "tv.py")

    def test_a_run_with_no_address_says_nothing_was_recorded(self):
        result = self.run_trace()
        self.assert_nothing_recorded(result)
        self.assertIn("TV_HOST", result.stderr, "tools/tv.py's own line is printed")
        self.assertIn("192.168.1.50", result.stderr, "with the example address it gives")

    def test_a_tv_port_that_is_not_a_number_says_nothing_was_recorded(self):
        result = self.run_trace(TV_HOST="192.0.2.9", TV_PORT="abc")
        self.assert_nothing_recorded(result)
        self.assertIn("not a port number: 'abc'", result.stderr, "tv.py's own line is printed")

    def test_a_port_outside_the_range_says_nothing_was_recorded(self):
        for environment, named in (({"TV_HOST": "192.0.2.9", "TV_PORT": "0"}, "'0'"),
                                   ({"TV_HOST": "192.0.2.9:70000"}, "'70000'"),
                                   ({"TV_HOST": "192.0.2.9:abc"}, "'abc'")):
            with self.subTest(environment=environment):
                result = self.run_trace(**environment)
                self.assert_nothing_recorded(result)
                self.assertIn(f"not a port number: {named}", result.stderr)


class StubClientTest(Base):
    """The handler around `import tv` itself: a client module that decides to stop while imported."""

    def test_a_client_that_stops_with_its_own_words_prints_them_and_answers_2(self):
        self.stub('raise SystemExit("the client refuses this TV")\n')
        result = self.run_trace(TV_HOST="192.0.2.9")
        self.assert_nothing_recorded(result)
        self.assertIn("the client refuses this TV", result.stderr, "tv.py's text is printed, not swallowed")

    def test_a_client_that_stops_with_a_number_answers_2(self):
        self.stub("raise SystemExit(3)\n")
        result = self.run_trace(TV_HOST="192.0.2.9")
        self.assert_nothing_recorded(result)
        self.assertNotIn("3", [line.strip() for line in result.stderr.splitlines()],
                         "a number is a status, not a sentence to print")

    def test_a_client_that_exits_0_keeps_its_status(self):
        self.stub("raise SystemExit(0)\n")
        result = self.run_trace(TV_HOST="192.0.2.9")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertNotIn(NOTHING_RECORDED, result.stderr, "nothing is added to a client that answered")

    def test_a_tv_that_answered_keeps_1(self):
        # Reached and read: this is the docstring's 1, not a run that never got there. `cold` asks the
        # TV for another app to leave Home for, and this one names none.
        self.stub("HOST, PORT = '192.0.2.9', 5555\n"
                  "\n"
                  "class Device:\n"
                  "    def shell(self, command, read_timeout_s=None):\n"
                  "        return ''\n"
                  "\n"
                  "    def close(self):\n"
                  "        pass\n"
                  "\n"
                  "def connect():\n"
                  "    return Device()\n"
                  "\n"
                  "def connect_error(error):\n"
                  "    return None\n")
        result = self.run_trace(scenario="cold", TV_HOST="192.0.2.9")
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        self.assertIn("could not find another app to switch to", result.stderr)


if __name__ == "__main__":
    unittest.main()

"""A push that never got as far as the TV answers 2, in tv.py's own words.

tools/b64push.py keeps 1 for "a transfer failed (the target is as it was, unless that copy failed or
was interrupted, which the message says)" and 2 for "nothing was sent". It caught ImportError around
`import tv` - a checkout with no tools/venv - but tv.py refuses two more shapes at import time, before
it has a connection: no TV_HOST ("Set TV_HOST to your TV's IP address ...", with the `arp -a` hint) and
a TV_PORT it cannot read ("not a port number: 'abc'", which is also what `TV_HOST=<ip>:70000` gets).
Both came out of `import tv` as a SystemExit whose code is the text, and the interpreter answered that
with 1: a push that never started read as one that broke halfway, which is the one thing the reader of
this status has to tell apart - the fallback exists for an install that went wrong, and the message
they got named neither that nothing was sent nor that the TV was never asked anything.

The reader is the checkout's own tools/tv.py, not a copy of its words, and the stub cases pin the
handler around it: a module that stops with its own text (2, that text printed), one that stops with a
number (2, since nothing was sent), and one that exits 0 (its status stands - the branch
tools/theme_restart.py keeps).

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
NOTHING_SENT = "Nothing was sent to the TV."


def packages_installed():
    """True when this interpreter can load adb_shell, which the checkout's tv.py needs to be imported."""
    return subprocess.run([sys.executable, "-c", "import adb_shell"],
                          capture_output=True, timeout=120).returncode == 0


class Base(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(TOOLS / "b64push.py", self.tools / "b64push.py")
        self.script = self.tools / "b64push.py"
        self.local = self.base / "app.apk"
        self.local.write_bytes(b"an apk")

    def run_b64push(self, **environment):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
        env.update(environment)
        return subprocess.run([sys.executable, str(self.script), str(self.local),
                               "/data/local/tmp/app.apk"],
                              env=env, capture_output=True, text=True, timeout=120)

    def assert_nothing_sent(self, result):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn(NOTHING_SENT, result.stderr, "the reader is told in one line that nothing was sent")
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "", "nothing is reported as transferred")


@unittest.skipUnless(packages_installed(), "needs the packages tools/venv installs (pip install -r "
                                          "tools/requirements.txt)")
class RealClientTest(Base):
    """The checkout's own tools/tv.py beside a copy of the script: the two refusals it makes by itself."""

    def setUp(self):
        super().setUp()
        shutil.copy2(TOOLS / "tv.py", self.tools / "tv.py")

    def test_a_run_without_tv_host_says_nothing_was_sent(self):
        result = self.run_b64push()
        self.assert_nothing_sent(result)
        self.assertIn("Set TV_HOST to your TV's IP address", result.stderr, "tv.py's own line is printed")
        self.assertIn("arp -a", result.stderr, "with the hint the address is found with")

    def test_a_tv_port_that_is_not_a_number_says_nothing_was_sent(self):
        result = self.run_b64push(TV_HOST="192.168.1.50", TV_PORT="abc")
        self.assert_nothing_sent(result)
        self.assertIn("not a port number: 'abc'", result.stderr)

    def test_a_port_in_the_address_that_is_not_a_port_says_nothing_was_sent(self):
        result = self.run_b64push(TV_HOST="192.168.1.50:70000")
        self.assert_nothing_sent(result)
        self.assertIn("not a port number: '70000'", result.stderr)


class StubClientTest(Base):
    """A tools/tv.py written here, so each shape of the import-time exit is pinned without a TV."""

    def stub(self, body):
        (self.tools / "tv.py").write_text(body, encoding="utf-8")

    def test_a_stub_that_stops_with_its_own_words_prints_them_and_answers_2(self):
        self.stub('import sys\nsys.exit("Set TV_HOST to your TV\'s IP address, e.g. 192.168.1.50")\n')
        result = self.run_b64push()
        self.assert_nothing_sent(result)
        self.assertIn("Set TV_HOST to your TV's IP address", result.stderr)

    def test_a_stub_that_stops_with_a_number_answers_2(self):
        # Nothing was sent either way, and 3 is a status this file's docstring does not declare.
        self.stub("import sys\nsys.exit(3)\n")
        result = self.run_b64push()
        self.assert_nothing_sent(result)

    def test_a_stub_that_exits_0_keeps_its_status(self):
        self.stub("import sys\nsys.exit(0)\n")
        result = self.run_b64push()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(result.stderr, "")


if __name__ == "__main__":
    unittest.main()

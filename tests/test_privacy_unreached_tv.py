"""tools/privacy.py answers a run that never got as far as the TV with 2, and says nothing was asked.

The docstring gives 1 to a run that reached the TV and then failed and 2 to one that never got as far
as the TV, and check.py (#2405), b64push.py (#2426), manage.py (#2692), trace.py (#2683),
theme_restart.py (#2763) and recovery.py (#2767) all answer an import-time refusal from tools/tv.py
with 2 and a line saying what they did not do. privacy.py's connect() caught only ImportError, so the
two refusals tv.py makes while it is being imported - no TV_HOST ("Set TV_HOST to your TV's IP
address ...") and a TV_PORT that is not a port number ("not a port number: 'abc'") - came out of
`import tv` as a SystemExit the interpreter answered with exit 1, the status this file keeps for an
audit that ran and then failed. An owner whose wrapper reads the status, or whose shell stops on it,
was told the audit had reached the TV when no connection was ever opened and nothing was sent.

Reproduced on main (57c00a40): `env -u TV_HOST tools/venv/bin/python -I tools/privacy.py` printed the
two Set TV_HOST lines and exited 1, and `TV_HOST=192.168.1.50 TV_PORT=abc ...` printed "not a port
number: 'abc'" and exited 1. Both exit 2 with tv.py's own text, a "Nothing was asked of the TV." line
and an empty stdout now, and a `--write` target is still not created.

The last test is the branch that was already right: a checkout with no tools/tv.py at all still says
it could not load it, with 2.

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

NOTHING_ASKED = "Nothing was asked of the TV."
SET_HOST = "Set TV_HOST to your TV's IP address"
BAD_PORT = "not a port number: 'abc'"
CANNOT_LOAD = "cannot load tools/tv.py"


class PrivacyNeedsHostTest(unittest.TestCase):
    """The checkout's own tools/tv.py: the two refusals it makes while it is being imported."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        (self.repo / "tools").mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", self.repo / "tools" / "privacy.py")
        shutil.copy2(ROOT / "tools" / "tv.py", self.repo / "tools" / "tv.py")
        self.script = self.repo / "tools" / "privacy.py"

    def run_privacy(self, *args, **env_overrides):
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT")}
        env.update(env_overrides)
        return subprocess.run([sys.executable, str(self.script), *args], cwd=self.repo, env=env,
                              capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)

    def test_no_tv_host_is_2_with_tv_pys_own_line(self):
        result = self.run_privacy()
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(SET_HOST, result.stderr)
        self.assertIn(NOTHING_ASKED, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertNotIn("Traceback", result.stderr)

    def test_a_write_target_is_not_created_when_no_host_was_given(self):
        result = self.run_privacy("--write", "privacy.txt")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(SET_HOST, result.stderr)
        self.assertIn(NOTHING_ASKED, result.stderr)
        self.assertFalse((self.repo / "privacy.txt").exists(),
                         "a run that never reached the TV wrote a list")

    def test_a_tv_port_that_is_not_a_number_is_2_too(self):
        # Same import, one step further: tv.py prints the number it could not read and exits 1, which
        # read as an audit that ran and failed.
        result = self.run_privacy(TV_HOST="192.168.1.50", TV_PORT="abc")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(BAD_PORT, result.stderr)
        self.assertIn(NOTHING_ASKED, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertNotIn("Traceback", result.stderr)

    def test_the_address_tv_py_refused_is_the_one_it_prints(self):
        # The line printed is tv.py's, not a rewrite of it: a reader is sent to the tool that judged it.
        result = self.run_privacy()
        self.assertIn("tools/tv.py --probe", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)


class PrivacyImportExitTest(unittest.TestCase):
    """A tools/tv.py that exits as it is imported, which is all the two refusals above are."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        (self.repo / "tools").mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", self.repo / "tools" / "privacy.py")
        self.script = self.repo / "tools" / "privacy.py"

    def with_tv(self, source):
        (self.repo / "tools" / "tv.py").write_text(source, encoding="utf-8")
        env = dict(os.environ, TV_HOST="192.168.1.50")
        return subprocess.run([sys.executable, str(self.script)], cwd=self.repo, env=env,
                              capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)

    def test_a_text_exit_is_printed_and_answered_with_2(self):
        result = self.with_tv('import sys\nsys.exit("the address tv.py read is not one it can use")\n')
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("the address tv.py read is not one it can use", result.stderr)
        self.assertIn(NOTHING_ASKED, result.stderr)
        self.assertEqual(result.stdout, "")

    def test_a_numeric_exit_is_answered_with_2_as_well(self):
        # tv.py keeps 2 for a command line it cannot read and 1 for the rest of its own refusals; this
        # file's next step looks at whether the TV was asked, not at which number tv.py chose.
        result = self.with_tv("import sys\nsys.exit(3)\n")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(NOTHING_ASKED, result.stderr)

    def test_a_module_that_exited_0_keeps_its_status(self):
        # An import that answered the call itself (its own --help) is not a refusal, and is not
        # rewritten: theme_restart.py's import keeps that status too.
        result = self.with_tv("import sys\nsys.exit(0)\n")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn(NOTHING_ASKED, result.stderr)

    def test_a_checkout_without_tv_py_still_says_it_could_not_load_it(self):
        env = dict(os.environ, TV_HOST="192.168.1.50")
        result = subprocess.run([sys.executable, str(self.script)], cwd=self.repo, env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(CANNOT_LOAD, result.stderr)
        self.assertIn(NOTHING_ASKED, result.stderr)
        self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()

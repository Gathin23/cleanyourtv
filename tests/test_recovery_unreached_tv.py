"""recovery.py tells a TV it never reached from a save that read the TV and failed.

`disable.sh` writes the state undo needs before the first command reaches the TV, and a save that
fails stops the run with the TV untouched. tools/recovery.py answered every one of those failures the
same way, exit 1: a port that refused the connection ended in a raw ConnectionRefusedError traceback
naming neither the port to use instead nor the page for a set with no ADB, and a run with no TV_HOST
or no tools/venv stopped with tv.py's own words and 1. 1 is the status this script keeps for a save
that read the TV and then failed (a record that cannot be read or updated, a TV that answered no
packages), which is the opposite diagnosis: nothing was asked of the TV at all.

tools/tv.py's connect_error() already words those failures - check.py, privacy.py, manage.py,
bench.py, trace.py and theme_restart.py print that sentence and exit 2 - and connect_error()'s own
docstring carried recovery.py --save as the one caller still letting them through. reach() is that
caller, and the shapes below are what it answers, end to end: the real client against a port on this
computer that refuses, the real tv.py with no TV_HOST, a checkout without adb_shell, and - through a
fake tv.py, the way tests/test_recovery_record.py does it - a TV that answered no packages, an error
tv.py has nothing to add to, and a record that has to be extended when the TV is gone.

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
NOTHING = "Nothing was recorded and nothing was sent to the TV."

# Stands in for tools/tv.py when the shape needs a TV that answers, or one that answers nothing: the
# real recovery.py logic runs, only the transport is simulated.
FAKE_TV = '''
import json
import os
import sys

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


state = load()


def answer(command):
    if state["mode"] == "nothing":
        return ""
    if command == "pm list packages --user 0":
        return "".join(f"package:{name}\\n" for name in state["installed"])
    if "pm list packages -d" in command:
        return "".join(f"package:{name}\\n" for name in state["disabled"])
    if "resolve-activity" in command:
        return "com.google.android.tvlauncher/.MainActivity\\n"
    if command.startswith("settings get global "):
        return "1.0\\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    if state["mode"] == "refused":
        raise ConnectionRefusedError(111, "Connection refused")
    if state["mode"] == "keyfile":
        raise PermissionError(13, "Permission denied", "/home/me/.android/adbkey")
    return Device()


def connect_error(error):
    if state["mode"] == "refused":
        return "192.168.1.50:5555 refused the connection: nothing listens for ADB there."
    return None


def main():
    return 0


if __name__ == "__main__":
    sys.exit(main())
'''


def adb_shell_installed():
    """The real client needs the venv; CI runs the suite without it (test_check_exit_codes.py)."""
    try:
        import adb_shell  # noqa: F401
    except ImportError:
        return False
    return True


class Tree:
    """A copy of tools/recovery.py with a stand-in client beside it, and a HOME of its own."""

    def __init__(self, base, tv_source):
        self.base = Path(base)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "recovery.py", tools / "recovery.py")
        if tv_source is not None:
            (tools / "tv.py").write_text(tv_source)
        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.write_list(["com.example.one", "com.example.two"])

    def write_list(self, names):
        self.list_path.write_text("# My TV\n" + "".join(f"{name}\n" for name in names))

    def tv(self, **state):
        base = {"mode": "ok", "installed": ["com.example.one", "com.example.two",
                                           "com.google.android.tvlauncher"],
                "disabled": []}
        base.update(state)
        self.state_path.write_text(json.dumps(base))

    def run(self, *args, python_args=(), host="192.168.1.50", port=None, cwd=None):
        env = dict(os.environ, HOME=str(self.home), FAKE_TV_STATE=str(self.state_path))
        env.pop("TV_ADB_BINARY", None)
        env.pop("TV_PORT", None)
        if host is None:
            env.pop("TV_HOST", None)
        else:
            env["TV_HOST"] = host
        if port is not None:
            env["TV_PORT"] = str(port)
        return subprocess.run([sys.executable, "-I", *python_args,
                               str(self.repo / "tools" / "recovery.py"), *args],
                              cwd=str(cwd or self.repo), env=env, capture_output=True, text=True,
                              timeout=120)

    def record(self):
        files = list((self.home / ".cleanyourtv").glob("recovery-*.json"))
        return json.loads(files[0].read_text()) if files else None


class UnreachedTvTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)

    def real_tree(self):
        """recovery.py and the real tools/tv.py, so tv.py's own words are what comes back."""
        tree = Tree(self.base, None)
        shutil.copy2(ROOT / "tools" / "tv.py", tree.repo / "tools" / "tv.py")
        return tree

    def fake_tree(self):
        tree = Tree(self.base, FAKE_TV)
        tree.tv()
        return tree

    @unittest.skipUnless(adb_shell_installed(), "the real client needs tools/venv")
    def test_a_refused_port_answers_tv_py_s_sentence_and_2(self):
        tree = self.real_tree()
        result = tree.run("--save", str(tree.list_path), host="127.0.0.1", port=1)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("refused the connection: nothing listens for ADB there", result.stderr)
        self.assertIn("cleanyour.tv/not-android", result.stderr,
                      "the page for a set with no ADB is part of tv.py's sentence")
        self.assertIn(NOTHING, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIsNone(tree.record(), "a TV that was never reached has nothing to record")

    def test_a_run_without_tv_host_is_2(self):
        # tv.py stops at import time with its own message, which is a process exit and not an
        # exception: caught, that text is still the answer the owner needs.
        tree = self.real_tree()
        result = tree.run("--save", str(tree.list_path), host=None)
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertTrue("Set TV_HOST to your TV's IP address" in result.stderr
                        or "cannot load tools/tv.py" in result.stderr, result.stderr)
        self.assertIn(NOTHING, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIsNone(tree.record())

    def test_a_run_without_the_adb_client_is_2(self):
        # -S drops site-packages, so adb_shell cannot be imported even under tools/venv's Python:
        # the same shape on a computer where the venv is gone.
        tree = self.real_tree()
        result = tree.run("--save", str(tree.list_path), python_args=("-S",))
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("cannot load tools/tv.py", result.stderr)
        self.assertIn("python3 -m venv tools/venv", result.stderr)
        self.assertIn(NOTHING, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIsNone(tree.record())

    def test_a_tv_that_answered_no_packages_keeps_1(self):
        # Reached and asked, and it said nothing: that is the save that ran and failed, which is what
        # 1 is kept for, and the record is still not written.
        tree = self.fake_tree()
        tree.tv(mode="nothing")
        result = tree.run("--save", str(tree.list_path))
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("the TV returned no packages", result.stderr)
        self.assertNotIn(NOTHING, result.stderr)
        self.assertIsNone(tree.record())

    def test_an_error_tv_py_has_nothing_to_add_to_keeps_its_traceback(self):
        # An ADB key file this account cannot read: tv.py says None, and the traceback names the file,
        # which no sentence of ours would.
        tree = self.fake_tree()
        tree.tv(mode="keyfile")
        result = tree.run("--save", str(tree.list_path))
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn("Traceback", result.stderr)
        self.assertIn("/home/me/.android/adbkey", result.stderr)
        self.assertNotIn(NOTHING, result.stderr)
        self.assertIsNone(tree.record())

    def test_a_record_that_has_to_be_extended_and_a_tv_that_is_gone_is_2(self):
        # The second connect() in save(): the record is there, the list gained a name, and the TV is
        # not answering. The record must be left exactly as it was, and the status must not be the 1
        # of a save that read the TV.
        tree = self.fake_tree()
        self.assertEqual(0, tree.run("--save", str(tree.list_path)).returncode)
        first = tree.record()
        self.assertIsNotNone(first)
        tree.write_list(["com.example.one", "com.example.two", "com.example.three"])
        tree.tv(mode="refused")
        result = tree.run("--save", str(tree.list_path))
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("refused the connection", result.stderr)
        self.assertIn(NOTHING, result.stderr)
        self.assertEqual(first, tree.record(), "the record from before the TV went away is kept")


class ReachTest(unittest.TestCase):
    """reach() itself, without a subprocess: the three shapes and the one it must not swallow."""

    def setUp(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("recovery_under_test",
                                                      ROOT / "tools" / "recovery.py")
        self.recovery = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.recovery)
        self.addCleanup(sys.modules.pop, "recovery_under_test", None)

    def run_reach(self, client):
        import contextlib
        import io
        from unittest.mock import patch
        stderr = io.StringIO()
        with patch.object(self.recovery, "client", lambda: client):
            with contextlib.redirect_stderr(stderr):
                with self.assertRaises(SystemExit) as raised:
                    self.recovery.reach()
        return raised.exception.code, stderr.getvalue()

    def test_a_worded_error_is_the_sentence_and_2(self):
        class Client:
            @staticmethod
            def connect():
                raise ConnectionRefusedError(111, "Connection refused")

            @staticmethod
            def connect_error(error):
                return "192.168.1.50:5555 refused the connection."

        code, stderr = self.run_reach(Client)
        self.assertEqual(2, code)
        self.assertIn("192.168.1.50:5555 refused the connection.", stderr)
        self.assertIn(NOTHING, stderr)

    def test_tv_py_s_own_exit_keeps_its_words_and_is_2(self):
        class Client:
            @staticmethod
            def connect():
                raise SystemExit("Set TV_HOST to your TV's IP address")

        code, stderr = self.run_reach(Client)
        self.assertEqual(2, code)
        self.assertIn("Set TV_HOST to your TV's IP address", stderr)
        self.assertIn(NOTHING, stderr)

    def test_a_numeric_exit_is_not_ours_to_rewrite(self):
        class Client:
            @staticmethod
            def connect():
                raise SystemExit(3)

        import contextlib
        import io
        from unittest.mock import patch
        with patch.object(self.recovery, "client", lambda: Client):
            with contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as raised:
                    self.recovery.reach()
        # reach() re-raises it: a status that came from somewhere else keeps its own meaning.
        self.assertEqual(3, raised.exception.code)

    def test_an_error_connect_error_has_nothing_to_add_to_is_raised(self):
        class Client:
            @staticmethod
            def connect():
                raise PermissionError(13, "Permission denied", "/home/me/.android/adbkey")

            @staticmethod
            def connect_error(error):
                return None

        import contextlib
        import io
        from unittest.mock import patch
        stderr = io.StringIO()
        with patch.object(self.recovery, "client", lambda: Client):
            with contextlib.redirect_stderr(stderr):
                with self.assertRaises(PermissionError):
                    self.recovery.reach()
        self.assertNotIn(NOTHING, stderr.getvalue())


if __name__ == "__main__":
    unittest.main()

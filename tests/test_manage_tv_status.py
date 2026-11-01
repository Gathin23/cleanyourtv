"""tools/manage.py's exit status when the TV was never asked anything.

`tv()` ran `tools/tv.py` and turned any non-zero exit into `sys.exit(message)`, which is 1 - the status the
docstring keeps for "a command on the TV or a script this checkout runs failed". tools/tv.py answers 2 for
a TV it never reached (a refused port, a Wireless debugging port that answers TLS, an address with no route
to it, a Mac never granted Local Network), so `manage.py status` against a set that was asleep exited 1: a
status/install/remove that ran and failed. Reproduced on 10d76503 with `TV_HOST=127.0.0.1 TV_PORT=1`:
`tools/tv.py 'pm list packages'` exits 2, `tools/manage.py status` exits 1 with the same message. With no
TV_HOST at all, and in a clone that has no tools/venv, `tv()` and `tv_python()` answered 1 as well, while
check.py's own fixes (#2405) put both at 2 and b64push.py's (#2426) the missing ADB client.

The rule the tests below hold: nothing was asked of the TV -> 2, the TV ran the command and failed -> 1, and
the message is still on stderr either way. A status tools/tv.py does not document (it answers 0, 1 and 2) comes
from a crash of its own, so it is a command that failed: 1, and this script's three statuses stay true.
"""
import contextlib
import io
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402

# tools/tv.py's own words for a port that refuses, the sentence manage.py re-emits.
REFUSED = ("127.0.0.1:1 refused the connection: nothing listens for ADB there, so network ADB is off or on "
           "another port.")
VENV_LINE = "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"


def stop(call, *args, **kwargs):
    """The SystemExit `call` raises, and what it wrote to stderr."""
    err = io.StringIO()
    with contextlib.redirect_stderr(err):
        with contextlib.redirect_stdout(io.StringIO()):
            with unittest.TestCase().assertRaises(SystemExit) as raised:
                call(*args, **kwargs)
    return raised.exception.code, err.getvalue()


class TvStatusTest(unittest.TestCase):
    """`tv()` keeps tools/tv.py's status, and answers 2 before a TV was asked anything."""

    def test_no_tv_host_answers_2_and_names_the_setting(self):
        environ = {k: v for k, v in os.environ.items() if k != "TV_HOST"}
        with mock.patch.dict(os.environ, environ, clear=True), \
                mock.patch.object(manage.subprocess, "run") as run:
            code, err = stop(manage.tv, "pm list packages")
        self.assertEqual(2, code)
        self.assertIn("set TV_HOST to the TV IP address", err)
        # tools/tv.py's own hint, which this line dropped when it was a one-line sys.exit
        self.assertIn("Settings → Network", err)
        self.assertIn("Nothing was asked of the TV.", err)
        run.assert_not_called()

    def test_a_refused_port_keeps_tools_tv_py_status_2(self):
        done = mock.Mock(returncode=2, stderr=REFUSED + "\n", stdout="")
        with mock.patch.dict(os.environ, {"TV_HOST": "127.0.0.1", "TV_PORT": "1"}), \
                mock.patch.object(manage, "tv_python", return_value="python"), \
                mock.patch.object(manage.subprocess, "run", return_value=done):
            code, err = stop(manage.tv, "pm list packages")
        self.assertEqual(2, code)
        self.assertIn(REFUSED, err)

    def test_a_command_the_tv_failed_keeps_1(self):
        done = mock.Mock(returncode=1, stderr="Error: java.lang.SecurityException\n", stdout="")
        with mock.patch.dict(os.environ, {"TV_HOST": "192.168.1.50"}), \
                mock.patch.object(manage, "tv_python", return_value="python"), \
                mock.patch.object(manage.subprocess, "run", return_value=done):
            code, err = stop(manage.tv, "pm uninstall com.example.addon")
        self.assertEqual(1, code)
        self.assertIn("SecurityException", err)

    def test_a_tv_py_that_said_nothing_still_gives_its_status_and_a_line(self):
        done = mock.Mock(returncode=2, stderr="", stdout="")
        with mock.patch.dict(os.environ, {"TV_HOST": "192.168.1.50"}), \
                mock.patch.object(manage, "tv_python", return_value="python"), \
                mock.patch.object(manage.subprocess, "run", return_value=done):
            code, err = stop(manage.tv, "pm list packages")
        self.assertEqual(2, code)
        self.assertIn("tv.py exited with status 2", err)

    def test_a_status_tv_py_does_not_document_is_a_failed_command(self):
        """tools/tv.py documents 0, 1 and 2; anything else comes from a crash of its own, which is a command
        that failed - and this script's docstring still promises no status but 0, 1 and 2."""
        for returncode in (3, 130, 255):
            with self.subTest(returncode=returncode):
                done = mock.Mock(returncode=returncode, stderr="", stdout="")
                with mock.patch.dict(os.environ, {"TV_HOST": "192.168.1.50"}), \
                        mock.patch.object(manage, "tv_python", return_value="python"), \
                        mock.patch.object(manage.subprocess, "run", return_value=done):
                    code, err = stop(manage.tv, "pm list packages")
                self.assertEqual(1, code)
                self.assertIn(f"tv.py exited with status {returncode}", err)

    def test_a_tv_that_answered_returns_its_output(self):
        done = mock.Mock(returncode=0, stderr="", stdout="package:com.example.a\n")
        with mock.patch.dict(os.environ, {"TV_HOST": "192.168.1.50"}), \
                mock.patch.object(manage, "tv_python", return_value="python"), \
                mock.patch.object(manage.subprocess, "run", return_value=done):
            self.assertEqual("package:com.example.a\n", manage.tv("pm list packages"))


class NoVenvTest(unittest.TestCase):
    """A clone that has no tools/venv answers 2, as b64push.py does for the same shape."""

    def test_no_venv_answers_2_and_says_how_to_make_one(self):
        with tempfile.TemporaryDirectory() as empty:
            with mock.patch.object(manage, "TOOLS", Path(empty)):
                code, err = stop(manage.tv_python)
        self.assertEqual(2, code)
        self.assertIn("cannot load tools/tv.py", err)
        self.assertIn(VENV_LINE, err)
        self.assertIn("Nothing was asked of the TV.", err)

    def test_the_venv_is_still_found_when_it_is_there(self):
        with tempfile.TemporaryDirectory() as tree:
            python = Path(tree) / "venv" / "bin" / "python"
            python.parent.mkdir(parents=True)
            python.write_text("#!/bin/sh\n", encoding="utf-8")
            with mock.patch.object(manage, "TOOLS", Path(tree)):
                self.assertEqual(str(python), manage.tv_python())


class InstallPushTest(unittest.TestCase):
    """The other path to tools/tv.py, `install`'s --push, already gave its status back."""

    def test_a_push_that_never_reached_the_tv_keeps_2(self):
        item = {"id": "frigate-glance", "kind": "add-on", "script": "install.sh", "package": "com.example.a"}
        with mock.patch.object(manage, "run", return_value=2) as run:
            self.assertEqual(2, manage.install_addon(item, []))
        self.assertEqual("bash", run.call_args[0][0][0])


if __name__ == "__main__":
    unittest.main()

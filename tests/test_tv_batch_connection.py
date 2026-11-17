"""tools/tv.py --batch names the command whose answer the TV dropped, instead of a traceback (#2663).

`--batch` sends one shell command per stdin line and prints each answer as it comes, which is how
`tools/disable.sh` and `tools/restore.sh` send a whole list. When the TV resets the TCP connection
between two of those commands, adb_shell's socket call raises ConnectionResetError and the run ended
with a traceback: `disable.sh` under `set -euo pipefail` exited there, and the reader could not tell
from the traceback which command had been running, nor whether the TV had run it. On the SHIELD in
the report it had: `pm list packages -d` showed the package the traceback named as disabled, and only
its answer was lost.

So a lost connection is now answered in words, naming the command and its place in the batch, and
saying that the TV may or may not have run it and that the commands after it were not sent. A single
command that loses its answer is answered the same way, without a position. The exit status stays 1,
the one a run that reached the TV and failed already answered. An error that is not a lost connection
keeps its traceback, which is where a bug in this script or in adb_shell shows.

These runs use a stub adb_shell (as tests/test_tv_help.py does) whose shell() drops the connection on
the n-th command, so the batch, its answers and the drop are all observed rather than assumed. HOME
points at a temporary folder so the run writes no ADB key into the checkout.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "tv.py"
STUB_DEVICE = '''\
import os

# 1-based: the shell command whose answer the connection drops on, 0 for a batch that runs clean.
DROP_AT = int(os.environ.get("STUB_DROP_AT", "0"))
# "reset" is the SHIELD's ConnectionResetError; "value" is an error no connection explains.
FAILURE = os.environ.get("STUB_FAILURE", "reset")


class AdbDeviceTcp:
    """Stands in for adb_shell's client: answers each command, and drops the connection on DROP_AT."""

    def __init__(self, host, port, **kwargs):
        self.count = 0
        with open(os.environ["STUB_MARK"], "w") as f:
            f.write(f"{host}:{port}")

    def connect(self, **kwargs):
        pass

    def shell(self, command, **kwargs):
        self.count += 1
        with open(os.environ["STUB_CMDS"], "a") as f:
            f.write(command + "\\n")
        if self.count == DROP_AT:
            if FAILURE == "value":
                raise ValueError("this is not a connection error")
            raise ConnectionResetError(54, "Connection reset by peer")
        return f"{command}: done\\n"

    def push(self, local, remote, **kwargs):
        pass

    def pull(self, remote, local, **kwargs):
        with open(local, "wb") as f:
            f.write(b"\\x89PNG\\r\\n\\x1a\\n")

    def reboot(self):
        pass

    def close(self):
        pass
'''
STUB_KEYGEN = "def keygen(path):\n    pass\n"
STUB_SIGNER = "class PythonRSASigner:\n    def __init__(self, pub, priv):\n        pass\n"
# The three names tv.py's lost_connection() reads, with adb_shell's shape: ClientError subclasses that
# are not OSErrors, so a stub without them would be a client tv.py cannot question.
STUB_EXCEPTIONS = """\
class AdbConnectionError(Exception):
    pass


class AdbTimeoutError(Exception):
    pass


class TcpTimeoutException(Exception):
    pass
"""


def stub_tree(base):
    """A package tree that answers tv.py's adb_shell imports."""
    (base / "adb_shell" / "auth").mkdir(parents=True)
    (base / "adb_shell" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "adb_device.py").write_text(STUB_DEVICE, encoding="utf-8")
    (base / "adb_shell" / "exceptions.py").write_text(STUB_EXCEPTIONS, encoding="utf-8")
    (base / "adb_shell" / "auth" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "auth" / "keygen.py").write_text(STUB_KEYGEN, encoding="utf-8")
    (base / "adb_shell" / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER, encoding="utf-8")
    return base


class TvBatchConnectionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.base = stub_tree(Path(cls._tmp.name) / "stub")
        # A stock ADB key in the temporary HOME: tv.py reuses it, so neither the checkout's
        # tools/adbkey nor the real ~/.android is touched by these runs.
        home = Path(cls._tmp.name) / "home"
        (home / ".android").mkdir(parents=True)
        (home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.home = home

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def run_tv(self, args, stdin_text="", drop_at=0, failure="reset", stdin_bytes=None):
        """(exit status, stdout, stderr, the commands that reached the TV)."""
        work = Path(tempfile.mkdtemp(dir=Path(self._tmp.name)))
        mark, cmds = work / "mark", work / "cmds"
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env["HOME"] = str(self.home)
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["PYTHONPATH"] = str(self.base)
        env["TV_HOST"] = "192.0.2.1"
        env["STUB_MARK"], env["STUB_CMDS"] = str(mark), str(cmds)
        env["STUB_DROP_AT"], env["STUB_FAILURE"] = str(drop_at), failure
        r = subprocess.run([sys.executable, str(SCRIPT)] + args, cwd=str(ROOT), env=env,
                           input=stdin_text if stdin_bytes is None else stdin_bytes,
                           capture_output=True, text=stdin_bytes is None)
        if stdin_bytes is not None:
            r.stdout, r.stderr = r.stdout.decode("utf-8"), r.stderr.decode("utf-8")
        sent = cmds.read_text(encoding="utf-8").splitlines() if cmds.exists() else []
        return r.returncode, r.stdout, r.stderr, sent

    def run_batch(self, commands, drop_at=0, failure="reset"):
        return self.run_tv(["--batch"], "\n".join(commands) + "\n", drop_at, failure)

    def test_a_dropped_connection_names_the_command_it_dropped_on(self):
        status, out, err, sent = self.run_batch(
            ["pm list packages -e --user 0",
             "pm disable-user --user 0 com.nvidia.shieldbeta",
             "pm disable-user --user 0 com.nvidia.tegrazone3"], drop_at=2)
        self.assertEqual(1, status, "a run that reached the TV and failed keeps status 1")
        self.assertNotIn("Traceback", err, "a dropped connection is not a crash")
        self.assertIn("pm disable-user --user 0 com.nvidia.shieldbeta", err,
                      "the command the TV dropped the connection on is named")
        self.assertIn("may or may not have run it", err,
                      "the reader cannot tell whether the TV ran it, so the run says so")
        self.assertIn("not sent", err, "and that the rest of the batch was not sent")
        self.assertIn("pm list packages -d", err, "with the command that reads the TV back")
        # The answers that did come back are still on stdout, in order.
        self.assertIn("pm list packages -e --user 0: done", out)
        self.assertNotIn("com.nvidia.shieldbeta: done", out)

    def test_the_dropped_command_is_counted_given_the_batch_and_not_its_lines(self):
        # The batch a list sends carries comment and blank lines; the count is about commands, so the
        # reader can line it up with the `$ command` headings printed above it.
        commands = ["# a comment", "", "pm list packages -d",
                    "pm disable-user --user 0 com.nvidia.shieldbeta", ""]
        status, _, err, sent = self.run_batch(commands, drop_at=2)
        self.assertEqual(1, status, err)
        self.assertIn("command 2 of this batch", err, err)
        self.assertEqual(["pm list packages -d", "pm disable-user --user 0 com.nvidia.shieldbeta"], sent)

    def test_nothing_after_the_dropped_command_is_sent(self):
        commands = ["pm disable-user --user 0 com.nvidia.shieldbeta",
                    "pm disable-user --user 0 com.nvidia.tegrazone3",
                    "pm list packages -e --user 0"]
        status, _, _, sent = self.run_batch(commands, drop_at=1)
        self.assertEqual(1, status)
        self.assertEqual(commands[:1], sent, "the run stops at the command it could not answer")

    def test_a_batch_that_runs_cleanly_is_unchanged(self):
        status, out, err, sent = self.run_batch(["pm list packages -d", "getprop ro.product.model"])
        self.assertEqual(0, status, err)
        self.assertEqual("", err)
        self.assertEqual(["pm list packages -d", "getprop ro.product.model"], sent)
        self.assertIn("$ pm list packages -d\npm list packages -d: done\n", out)
        self.assertIn("$ getprop ro.product.model\ngetprop ro.product.model: done\n", out)

    def test_a_single_command_names_itself_when_the_connection_drops(self):
        # The same words without a batch: one command, so nothing is left unsent and no position is
        # printed - `pm disable-user` run on its own loses its answer the same way.
        status, out, err, sent = self.run_tv(["pm", "disable-user", "--user", "0", "com.nvidia.shieldbeta"],
                                             drop_at=1)
        self.assertEqual(1, status)
        self.assertNotIn("Traceback", err)
        self.assertIn('"pm disable-user --user 0 com.nvidia.shieldbeta"', err)
        self.assertIn("may or may not have run it", err)
        self.assertNotIn("of this batch", err, "there is no batch to give a position in")
        self.assertEqual("", out)
        self.assertEqual(["pm disable-user --user 0 com.nvidia.shieldbeta"], sent)

    def test_a_probe_names_the_property_it_dropped_on(self):
        status, out, err, sent = self.run_tv(["--probe"], drop_at=2)
        self.assertEqual(1, status)
        self.assertNotIn("Traceback", err)
        self.assertIn("the connection to the TV dropped while running", err)
        self.assertIn('"getprop ro.product.device"', err)
        self.assertIn("ro.product.model = ", out, "the answer that did come back is still printed")
        self.assertEqual(2, len(sent))

    def test_a_screenshot_names_itself_when_the_connection_drops(self):
        shot = Path(self._tmp.name) / "shot.png"
        status, out, err, sent = self.run_tv(["--screenshot", str(shot), "HOME"], drop_at=1)
        self.assertEqual(1, status)
        self.assertNotIn("Traceback", err)
        self.assertIn("the connection to the TV dropped while running", err)
        self.assertIn("--screenshot", err)
        self.assertEqual(1, len(sent))

    def test_an_error_a_lost_connection_does_not_explain_keeps_its_traceback(self):
        # Only a dropped connection is answered in words: anything else is a bug, and a traceback is
        # where it shows.
        status, _, err, _ = self.run_batch(["pm list packages -d"], drop_at=1, failure="value")
        self.assertEqual(1, status)
        self.assertIn("Traceback", err)
        self.assertIn("this is not a connection error", err)
        self.assertNotIn("may or may not have run it", err)

    def test_a_batch_that_is_not_utf8_is_refused_before_anything_is_sent(self):
        # stdin is decoded in chunks, so a bad byte late in the list used to stop the run after the
        # commands before it had already gone to the TV.
        lines = [f"pm list packages -d {n}" for n in range(400)]
        lines[350] = "# T\u00e9l\u00e9 du salon"
        data = ("\n".join(lines) + "\n").encode("cp1252")
        status, out, err, sent = self.run_tv(["--batch"], stdin_bytes=data)
        self.assertEqual(2, status, err)
        self.assertEqual([], sent, "nothing reaches the TV")
        self.assertEqual("", out)
        self.assertNotIn("Traceback", err)
        self.assertIn("UTF-8", err)
        self.assertIn("line 351", err)

    def test_the_line_named_counts_a_bom_and_lone_cr_line_ends(self):
        for name, data in (("BOM", b"\xef\xbb\xbfgetprop a\ngetprop b\n# T\xe9l\xe9\n"),
                           ("lone CR", b"getprop a\rgetprop b\r# T\xe9l\xe9\r")):
            with self.subTest(name):
                status, out, err, sent = self.run_tv(["--batch"], stdin_bytes=data)
                self.assertEqual(2, status, err)
                self.assertEqual([], sent)
                self.assertIn("line 3 ", err)

    def test_a_utf16_batch_is_refused_before_anything_is_sent(self):
        # What Windows PowerShell 5.1 writes for `>`.
        data = "pm list packages -d\ngetprop ro.product.model\n".encode("utf-16")
        status, out, err, sent = self.run_tv(["--batch"], stdin_bytes=data)
        self.assertEqual(2, status, err)
        self.assertEqual([], sent)
        self.assertNotIn("Traceback", err)
        self.assertIn("UTF-8", err)

    def test_a_utf8_batch_with_a_bom_still_runs_every_command(self):
        data = "\ufeffpm list packages -d\n# T\u00e9l\u00e9\ngetprop ro.product.model\n".encode("utf-8")
        status, _, err, sent = self.run_tv(["--batch"], stdin_bytes=data)
        self.assertEqual(0, status, err)
        self.assertEqual(["pm list packages -d", "getprop ro.product.model"], sent)

    def test_a_nul_in_a_utf8_batch_is_refused_before_anything_is_sent(self):
        for ending in ("\n", "\r\n", "\r"):
            for bom in ("", "\ufeff"):
                with self.subTest(ending=ending, bom=bool(bom)):
                    data = (bom + ending.join(["getprop ro.product.model", "# comment",
                                              "getprop ro.product.device\0bad", ""])).encode("utf-8")
                    status, out, err, sent = self.run_tv(["--batch"], stdin_bytes=data)
                    self.assertEqual(2, status, err)
                    self.assertEqual([], sent)
                    self.assertEqual("", out)
                    self.assertIn("NUL", err)
                    self.assertIn("line 3 ", err)
                    self.assertNotIn("Traceback", err)

    def test_a_bomless_utf16_batch_with_nuls_is_refused(self):
        for encoding in ("utf-16-le", "utf-16-be"):
            with self.subTest(encoding=encoding):
                data = "getprop ro.product.model\ngetprop ro.product.device\n".encode(encoding)
                status, out, err, sent = self.run_tv(["--batch"], stdin_bytes=data)
                self.assertEqual(2, status, err)
                self.assertEqual([], sent)
                self.assertEqual("", out)
                self.assertIn("NUL", err)
                self.assertIn("line 1 ", err)

    def test_backslash_zero_in_a_shell_command_is_not_a_nul_byte(self):
        commands = [r"printf '\0'", "getprop ro.product.model"]
        status, _, err, sent = self.run_batch(commands)
        self.assertEqual(0, status, err)
        self.assertEqual(commands, sent)


if __name__ == "__main__":
    unittest.main()

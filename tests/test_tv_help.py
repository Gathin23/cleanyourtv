"""A flag is a flag: tools/tv.py answers -h/--help and refuses one it does not have.

`tv.py` takes a shell command as its default form, so anything that is not one of its own flags fell
through to the TV: `tv.py --help` (the first thing a reader types at a script they do not know) ran
`--help` on the set's shell, printed `/system/bin/sh: --help: inaccessible or not found` and exited
0, and a mistyped flag such as `--probb` did the same, so a typo looked like a command that ran.
Without TV_HOST it was worse: the TV_HOST check sits at import time, so `--help` could only ever
print "Set TV_HOST to your TV's IP address". Every other script in tools/ prints its usage.

So -h/--help prints this script's own note, before the adb_shell imports and before TV_HOST is read,
and a leading dash that is not a flag of tv.py is refused with the usage (exit 2), before connecting.
A shell command's first word is never a dash, so the shell branch is unchanged.

The runs use a stub adb_shell (as tests/test_tv_target.py does) that records whether a device was
opened and every command sent, so "refused before connecting" and "the command still reaches the TV"
are both observed rather than assumed. HOME points at a temporary folder so the run does not write an
ADB key into the checkout.

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


class AdbDeviceTcp:
    """Stands in for adb_shell's client: records the connection and the commands it was given."""

    def __init__(self, host, port, **kwargs):
        with open(os.environ["STUB_MARK"], "w") as f:
            f.write(f"{host}:{port}")

    def connect(self, **kwargs):
        pass

    def shell(self, command, **kwargs):
        with open(os.environ["STUB_CMDS"], "a") as f:
            f.write(command + "\\n")
        return f"/system/bin/sh: {command.split()[0]}: inaccessible or not found\\n"

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


def stub_tree(base):
    """A package tree that answers tv.py's three adb_shell imports."""
    (base / "adb_shell" / "auth").mkdir(parents=True)
    (base / "adb_shell" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "adb_device.py").write_text(STUB_DEVICE, encoding="utf-8")
    (base / "adb_shell" / "auth" / "__init__.py").write_text("", encoding="utf-8")
    (base / "adb_shell" / "auth" / "keygen.py").write_text(STUB_KEYGEN, encoding="utf-8")
    (base / "adb_shell" / "auth" / "sign_pythonrsa.py").write_text(STUB_SIGNER, encoding="utf-8")
    return base


class TvHelpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._tmp = tempfile.TemporaryDirectory()
        cls.base = stub_tree(Path(cls._tmp.name) / "stub")
        # A stock ADB key in the temporary HOME: tv.py reuses it, so neither the checkout's tools/adbkey
        # nor the real ~/.android is touched by these runs.
        home = Path(cls._tmp.name) / "home"
        (home / ".android").mkdir(parents=True)
        (home / ".android" / "adbkey").write_text("PRIVATE", encoding="utf-8")
        (home / ".android" / "adbkey.pub").write_text("PUBLIC", encoding="utf-8")
        cls.home = home

    @classmethod
    def tearDownClass(cls):
        cls._tmp.cleanup()

    def run_tv(self, args, tv_host="192.0.2.1", stub=True):
        """(exit status, stdout, stderr, the device that was opened or None, the commands sent).

        tv_host=None leaves TV_HOST out of the child's environment.
        """
        work = Path(tempfile.mkdtemp(dir=Path(self._tmp.name)))
        mark, cmds = work / "mark", work / "cmds"
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "TV_PORT", "PYTHONPATH")}
        env["HOME"] = str(self.home)
        # Windows expands `~` from USERPROFILE, not HOME, so tv.py would read the real ~/.android
        # and make a key of its own instead of the stock one this run put in the temporary home.
        if os.name == "nt":
            env["USERPROFILE"] = str(self.home)
        env["STUB_MARK"], env["STUB_CMDS"] = str(mark), str(cmds)
        if stub:
            env["PYTHONPATH"] = str(self.base)
        if tv_host:
            env["TV_HOST"] = tv_host
        r = subprocess.run([sys.executable, str(SCRIPT)] + args, cwd=str(ROOT), env=env,
                           capture_output=True, text=True)
        opened = mark.read_text(encoding="utf-8") if mark.exists() else None
        sent = cmds.read_text(encoding="utf-8").splitlines() if cmds.exists() else []
        return r.returncode, r.stdout, r.stderr, opened, sent

    def test_help_prints_the_modes_without_a_tv(self):
        # The reader who has not set TV_HOST yet: this used to be the TV_HOST error alone.
        status, out, err, opened, sent = self.run_tv(["--help"], tv_host=None)
        self.assertEqual(0, status, err)
        for needle in ("tv.py --probe", "tv.py --screenshot", "tv.py --push", "tv.py --reboot",
                       "--help", "TV_HOST"):
            self.assertIn(needle, out)
        self.assertEqual("", err)
        self.assertIsNone(opened, "the usage needs no TV")
        self.assertEqual([], sent)

    def test_short_flag_is_the_same_note(self):
        long_status, long_out, _, _, _ = self.run_tv(["--help"], tv_host=None)
        status, out, err, opened, _ = self.run_tv(["-h"], tv_host=None)
        self.assertEqual(0, status, err)
        self.assertEqual(long_out, out)
        self.assertIsNone(opened)

    def test_help_with_a_tv_set_does_not_send_it_to_the_tv(self):
        # The defect: this ran `--help` on the set's shell and exited 0.
        status, out, err, opened, sent = self.run_tv(["--help"])
        self.assertEqual(0, status, err)
        self.assertIn("tv.py --probe", out)
        self.assertNotIn("inaccessible or not found", out)
        self.assertIsNone(opened)
        self.assertEqual([], sent)

    def test_help_wins_over_what_follows_it(self):
        status, out, err, opened, sent = self.run_tv(["--help", "extra", "words"])
        self.assertEqual(0, status, err)
        self.assertIn("tv.py --probe", out)
        self.assertIsNone(opened)
        self.assertEqual([], sent)

    def test_a_flag_it_does_not_have_is_refused(self):
        # The other half: a mistyped flag used to reach the TV and exit 0.
        status, out, err, opened, sent = self.run_tv(["--probb"])
        self.assertEqual(2, status, out)
        self.assertIn("not a flag of tv.py: '--probb'", err)
        self.assertIn("tv.py --probe", err, "the refusal carries the usage")
        self.assertIn("tv.py --help", err, "and so it says how to see the flags it does have")
        self.assertEqual("", out)
        self.assertIsNone(opened, "a typo must not reach the TV")
        self.assertEqual([], sent)

    def test_a_short_flag_it_does_not_have_is_refused_too(self):
        status, _, err, opened, sent = self.run_tv(["-x"])
        self.assertEqual(2, status)
        self.assertIn("not a flag of tv.py: '-x'", err)
        self.assertIsNone(opened)
        self.assertEqual([], sent)

    def test_the_flags_it_does_have_still_reach_the_tv(self):
        status, out, err, opened, sent = self.run_tv(["--probe"])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertEqual(8, len(sent), sent)
        self.assertIn("getprop ro.product.model", sent)
        self.assertIn("ro.product.model = ", out)

    def test_no_arguments_is_still_the_probe(self):
        status, out, err, opened, sent = self.run_tv([])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertIn("getprop ro.product.model", sent)

    def test_no_arguments_without_a_tv_still_says_what_is_missing(self):
        # Not turned into help: the documented behaviour of a bare run is the probe, and without a TV
        # the message has to stay the one that says how to give one.
        status, out, err, _, _ = self.run_tv([], tv_host=None)
        self.assertEqual(1, status)
        self.assertIn("Set TV_HOST", err)
        self.assertEqual("", out)

    def test_a_shell_command_still_goes_to_the_tv(self):
        status, out, err, opened, sent = self.run_tv(["pm", "list", "packages"])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertEqual(["pm list packages"], sent)
        self.assertIn("inaccessible or not found", out, "the TV's own answer is printed as before")

    def test_a_dash_inside_a_command_is_not_a_flag(self):
        # Only the first word decides: `dumpsys -l` is a command with an option, not a flag of tv.py.
        status, _, err, opened, sent = self.run_tv(["dumpsys", "-l"])
        self.assertEqual(0, status, err)
        self.assertEqual("192.0.2.1:5555", opened)
        self.assertEqual(["dumpsys -l"], sent)

    def test_the_guards_before_the_connection_still_hold(self):
        for args in (["--screenshot"], ["--push", "only-two.apk"], ["--batch", "a", "b"]):
            with self.subTest(args=args):
                status, out, err, opened, sent = self.run_tv(args)
                self.assertEqual(2, status, out)
                self.assertIn("usage:", err)
                self.assertIsNone(opened)
                self.assertEqual([], sent)

    def test_the_help_branch_is_read_before_the_client_is_imported(self):
        # The claim in the docstring is that no venv is needed, which only holds while the branch sits
        # above the adb_shell imports (and above the TV_HOST check, which exits at import time).
        lines = SCRIPT.read_text(encoding="utf-8").splitlines()
        guard = next(n for n, l in enumerate(lines) if 'sys.argv[1] in ("-h", "--help")' in l)
        client = next(n for n, l in enumerate(lines) if l.lstrip().startswith("from adb_shell"))
        host = next(n for n, l in enumerate(lines) if 'os.environ.get("TV_HOST")' in l)
        self.assertLess(guard, client, "the help branch must run before adb_shell is imported")
        self.assertLess(guard, host, "the help branch must run before TV_HOST is required")


if __name__ == "__main__":
    unittest.main()

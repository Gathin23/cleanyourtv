"""tools/check.py when ADB fails with one of adb_shell's own errors.

check.py documents exit 2 as "the TV could not be read" and exit 1 as drift, and it is meant for a
cron job that only sees the exit code. adb_shell raises its own errors (TcpTimeoutException when
nobody accepts the "Allow USB debugging?" prompt in time, AdbTimeoutError, DeviceAuthError) as
plain Exception subclasses, not OSError, so check.py used to end with a traceback and exit 1 on
them, which the cron job reads as drift. The same happened when the connection dropped in the
middle of the check or of --fix, whatever the error.

This reuses the drift check's harness and its fake tools/tv.py from tests/test_drift_check.py, with
a few lines added that raise an error at connect, or when a given command is sent.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_drift_check as drift  # noqa: E402  (imported as a module so its tests do not run twice)

STOCK = drift.STOCK


# Added to the drift check's fake tv.py: raises the error the state file names, at connect() or
# when a command containing "on" is sent, before the fake answers or applies it.
FAILING_TV = drift.FAKE_TV + r'''

# adb_shell's own errors are plain Exception subclasses, not OSError, in the 0.4.4 that
# tools/requirements.txt pins. The real ones are raised where adb_shell is installed; elsewhere
# these stand-ins have the same names and the same base.
try:
    from adb_shell.exceptions import AdbTimeoutError, DeviceAuthError, TcpTimeoutException
except ImportError:
    class AdbTimeoutError(Exception):
        pass

    class DeviceAuthError(Exception):
        pass

    class TcpTimeoutException(Exception):
        pass


ERRORS = {error.__name__: error for error in (AdbTimeoutError, DeviceAuthError, TcpTimeoutException,
                                               ConnectionResetError, KeyboardInterrupt, SystemExit)}
plain_connect = connect
plain_shell = Device.shell


def fail(key, command=""):
    failure = state.get(key)
    if failure and failure.get("on", "") in command:
        raise ERRORS[failure["type"]](failure["message"])


def connect():
    fail("connect_raises")
    return plain_connect()


def shell(self, command, read_timeout_s=None):
    fail("shell_raises", command)
    return plain_shell(self, command, read_timeout_s)


Device.shell = shell
'''

# What each error says in adb_shell 0.4.4 when the handshake in tv.connect() raises it.
AT_CONNECT = {
    # The "Allow USB debugging?" prompt was not accepted within tv.py's 120-second auth timeout.
    "TcpTimeoutException": "Reading from 192.168.1.50:5555 timed out (120.0 seconds)",
    "AdbTimeoutError": "Never got one of the expected responses: [b'CNXN'] "
                       "(transport_timeout_s = 120, read_timeout_s = 10.0)",
    "DeviceAuthError": "Device authentication required, no keys available.",
}


class CheckAdbErrorTest(unittest.TestCase):
    # The drift check's own harness: the same temporary repo, list, fake TV state and runner.
    write_list = drift.DriftCheckTest.write_list
    tv = drift.DriftCheckTest.tv
    run_check = drift.DriftCheckTest.run_check
    sent = drift.DriftCheckTest.sent

    def setUp(self):
        drift.DriftCheckTest.setUp(self)
        (self.repo / "tools" / "tv.py").write_text(FAILING_TV)

    def assertCouldNotRead(self, result, error):
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn(f"could not read the TV ({error}: ", result.stderr)
        self.assertIn("tools/tv.py --probe", result.stderr)

    def test_an_adb_error_at_connect_is_not_drift(self):
        for error, message in AT_CONNECT.items():
            with self.subTest(error=error):
                self.tv(connect_raises={"type": error, "message": message})
                result = self.run_check(str(self.list_path))
                self.assertCouldNotRead(result, error)
                self.assertIn(message, result.stderr)
                self.assertIn('"Allow USB debugging?"', result.stderr)
                self.assertEqual(result.stdout, "")
                self.assertEqual(self.sent(), [])

    def test_an_error_in_the_middle_of_the_check_is_not_drift(self):
        # The TV goes to standby or the network drops after the connection was made: a report from
        # half the queries would be wrong either way, so there is none.
        for error, on in (("TcpTimeoutException", "resolve-activity"),
                          ("AdbTimeoutError", "pm list packages -d"),
                          ("ConnectionResetError", "resolve-activity")):
            with self.subTest(error=error, on=on):
                self.tv(disabled=[STOCK], shell_raises={"type": error, "on": on, "message": "gone"})
                result = self.run_check("--fix", str(self.list_path))
                self.assertCouldNotRead(result, error)
                self.assertEqual(result.stdout, "")
                self.assertNotIn("partial", result.stderr)
                self.assertEqual(self.sent(), [])

    def test_an_error_during_fix_says_the_fix_may_be_partial(self):
        # Two listed packages came back; the connection drops while the second one is disabled.
        self.tv(disabled=[STOCK], shell_raises={
            "type": "TcpTimeoutException", "on": "pm disable-user --user 0 com.example.store",
            "message": "Reading from 192.168.1.50:5555 timed out (120.0 seconds)"})
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("back on (2): com.example.music, com.example.store", result.stdout)
        self.assertIn("lost the TV during --fix (TcpTimeoutException: ", result.stderr)
        self.assertIn("may be partial", result.stderr)
        self.assertEqual(self.sent(), ["pm disable-user --user 0 com.example.music"])
        # Checking again once the TV answers shows where the fix stopped, as the message says.
        state = json.loads(self.state_path.read_text())
        del state["shell_raises"]
        self.state_path.write_text(json.dumps(state))
        again = self.run_check(str(self.list_path))
        self.assertEqual(again.returncode, 1, again.stdout + again.stderr)
        self.assertIn("back on (1): com.example.store", again.stdout)

    def test_ctrl_c_still_stops_the_check(self):
        self.tv(shell_raises={"type": "KeyboardInterrupt", "on": "resolve-activity", "message": ""})
        result = self.run_check(str(self.list_path))
        self.assertNotIn(result.returncode, (0, 1, 2), result.stdout + result.stderr)
        self.assertIn("KeyboardInterrupt", result.stderr)
        self.assertNotIn("could not read the TV", result.stderr)

    def test_an_exit_from_tv_py_is_2(self):
        # tv.py and recovery.py stop with SystemExit and their own message (TV_HOST unset, adb_shell
        # missing); that is not an ADB error, so it keeps its own wording, but the TV was never asked
        # and the answer is 2 - the code for a check that never ran - not the 1 Python gives it, which
        # the page tells a weekly job to mail about as drift.
        message = "Set TV_HOST to your TV's IP address"
        self.tv(connect_raises={"type": "SystemExit", "message": message})
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn(message, result.stderr)
        self.assertIn("Nothing was checked or changed.", result.stderr)
        self.assertNotIn("could not read the TV", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual(self.sent(), [], "the TV was asked by a run that could not read it")


if __name__ == "__main__":
    unittest.main()

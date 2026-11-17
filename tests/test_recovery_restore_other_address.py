"""A restore at a new address names the record that is filed under the old one (#2139, undo's side).

The recovery record is one file per list and address, and the setup instructions move that address
mid-session themselves: the setupwraith step asks the owner to forget and re-join the TV's Wi-Fi
network, and the router hands out a new lease. `tools/disable.sh` learned to say so on stderr when it
writes a fresh record under the new address (tests/test_recovery_address_change.py). The other half is
the run that does the damage first: with no record at the new address, `tools/restore.sh` enables every
listed package - including the ones the owner had disabled before cleanup - while the record that says
which of them were on sits under the old address and was not named.

restore_commands() now names every record for the same list under another address, and the two
readings the owner has to choose between: the same TV on a new lease (run the undo at the old address,
or move that record here) or a second set cleaned with the same list (nothing is wrong). Nothing else
changes: the batch is the same one on the same exit status, because `--commands` never reaches the TV
and so cannot tell the two apart from what is disabled now.

The TV is the fake tools/tv.py from tests/test_recovery_record.py, so the real recovery.py and
restore.sh run and only the ADB transport is simulated.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_recovery_record as harness  # noqa: E402

STOCK_HOME = harness.STOCK_HOME
# The TV as the setup instructions leave it: the owner had disabled com.example.already_off himself,
# before the list ran.
BEFORE_CLEANUP = {"disabled": ["com.example.already_off"]}


class RestoreOtherAddressTest(unittest.TestCase):
    # The harness's copy of tools/ with the fake tv.py, HOME and TV state. The methods are borrowed
    # rather than inherited, so that the harness's own tests do not run twice.
    setUp = harness.RecoveryRecordTest.setUp
    write_list = harness.RecoveryRecordTest.write_list
    tv = harness.RecoveryRecordTest.tv
    sent = harness.RecoveryRecordTest.sent

    def run_script(self, script, host, list_path=None):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest(f"{script} is a POSIX script; it needs bash")
        env = dict(os.environ, TV_HOST=host, HOME=str(self.home), FAKE_TV_STATE=str(self.state_path))
        env.pop("TV_PORT", None)
        return subprocess.run(["bash", str(self.repo / "tools" / script), str(list_path or self.list_path)],
                              cwd=self.repo, env=env, capture_output=True, text=True, timeout=120)

    def disable(self, host, **state):
        self.tv(**state)
        result = self.run_script("disable.sh", host)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result

    def restore(self, host, list_path=None):
        state = json.loads(self.state_path.read_text())
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))
        result = self.run_script("restore.sh", host, list_path)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result

    def records(self):
        return sorted(p.name.split("-", 2)[1] for p in (self.home / ".cleanyourtv").glob("recovery-*.json"))

    def test_undo_at_a_new_address_names_the_record_under_the_old_one(self):
        self.disable("192.168.1.50", installed=["com.example.enabled", "com.example.already_off"],
                     disabled=["com.example.already_off"], home=STOCK_HOME, settings={}, commands=[])

        result = self.restore("192.168.1.11")

        [old] = (self.home / ".cleanyourtv").glob("recovery-192.168.1.50-*.json")
        self.assertIn("a recovery record for this same list is under 192.168.1.50", result.stderr)
        self.assertIn(str(old), result.stderr, "the file the owner has to run the undo from")
        self.assertIn("run tools/restore.sh at that address", result.stderr)
        self.assertIn("If this is a second set cleaned with the same list, nothing is wrong", result.stderr)
        # Why the line matters: this run is the one that turns the owner's own disable back on, and
        # the batch is unchanged, so the message is the only thing between the two readings.
        self.assertIn("pm enable com.example.already_off", self.sent())
        self.assertEqual(self.records(), ["192.168.1.50"], "nothing was written at the new address")

    def test_a_run_with_no_record_anywhere_names_no_address(self):
        self.tv(installed=["com.example.enabled"], disabled=[], home=STOCK_HOME, settings={}, commands=[])

        result = self.restore("192.168.1.11")

        self.assertIn("no recovery record for this TV and list", result.stderr)
        self.assertNotIn("a recovery record for this same list is under", result.stderr)

    def test_a_record_for_another_list_is_not_named(self):
        self.disable("192.168.1.50", installed=["com.example.enabled"], disabled=[],
                     home=STOCK_HOME, settings={}, commands=[])
        other = self.base / "second-list.txt"
        other.write_text("# Another TV\ncom.example.enabled\n")

        result = self.restore("192.168.1.11", list_path=other)

        self.assertIn("no recovery record for this TV and list", result.stderr)
        self.assertNotIn("192.168.1.50", result.stderr, "another list's record is another TV's state")

    def test_two_sets_under_two_addresses_are_both_named_and_not_a_lost_cleanup(self):
        self.disable("192.168.1.50", installed=["com.example.enabled"], disabled=[],
                     home=STOCK_HOME, settings={}, commands=[])
        self.disable("192.168.1.11", installed=["com.example.enabled"], disabled=[],
                     home=STOCK_HOME, settings={}, commands=[])

        result = self.restore("192.168.1.12")

        self.assertIn("under 192.168.1.11", result.stderr)
        self.assertIn("under 192.168.1.50", result.stderr)
        self.assertIn("nothing is wrong", result.stderr)
        self.assertEqual(self.records(), ["192.168.1.11", "192.168.1.50"])

    def test_a_record_that_cannot_be_read_is_named_and_does_not_stop_the_run(self):
        self.disable("192.168.1.50", installed=["com.example.enabled"], disabled=[],
                     home=STOCK_HOME, settings={}, commands=[])
        [old] = (self.home / ".cleanyourtv").glob("recovery-192.168.1.50-*.json")
        old.write_text("{not json")

        result = self.restore("192.168.1.11")

        self.assertIn("under 192.168.1.50", result.stderr,
                      "the file is still the only record for this list, damaged or not")
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(old.read_text(), "{not json", "the file is left where it is")


if __name__ == "__main__":
    unittest.main()

"""A recovery record is per address, so a new lease is a new record: save() says so (#2139).

The record is one file per list and address (recovery-<address>-<list hash>.json). The setup
instructions move that address mid-session themselves: the setupwraith step asks the owner to forget
and re-join the TV's Wi-Fi network, and the router hands out a new lease. The next `tools/disable.sh`
run then finds no record for the new address and writes a fresh one, and the fresh one is taken from a
TV that is already partly cleaned: every package the earlier batches disabled reads "already disabled,
left alone by undo", so `tools/restore.sh` at the new address leaves them off for good. Nothing in the
run's output pointed at the address change.

save() now names the record under the other address, the packages the two disagree about, and the two
ways out. It is output on stderr and nothing else: the record is written, the list still reaches the
TV, and a second set cleaned with the same list keeps both records. The TV is the fake tools/tv.py from
tests/test_recovery_record.py.

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
# The TV after a first batch: the package the list disables is off now, as the record predicted.
CLEANED_TV = {"disabled": ["com.example.enabled", "com.example.already_off"]}


class AddressChangeWarningTest(unittest.TestCase):
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

    def disable(self, host, list_path=None):
        result = self.run_script("disable.sh", host, list_path)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result

    def restore(self, host, list_path=None):
        state = json.loads(self.state_path.read_text())
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))
        result = self.run_script("restore.sh", host, list_path)
        self.assertEqual(result.returncode, 0, result.stderr)
        return self.sent()

    def records(self):
        return sorted(p.name.split("-", 2)[1] for p in (self.home / ".cleanyourtv").glob("recovery-*.json"))

    def test_the_first_record_says_nothing_about_another_address(self):
        self.tv()
        result = self.disable("192.168.1.50")
        self.assertNotIn("warning:", result.stderr)
        self.assertEqual(self.records(), ["192.168.1.50"])

    def test_a_second_run_on_the_same_address_says_nothing(self):
        self.tv()
        self.disable("192.168.1.50")
        self.tv(**CLEANED_TV)
        result = self.disable("192.168.1.50")
        self.assertIn("keeping the recovery record", result.stdout)
        self.assertNotIn("warning:", result.stderr, "the record it keeps already says what was on")
        self.assertEqual(self.records(), ["192.168.1.50"])

    def test_a_new_address_after_the_cleanup_names_the_old_record(self):
        self.tv()
        self.disable("192.168.1.50")
        self.tv(**CLEANED_TV)

        result = self.disable("192.168.1.11")

        self.assertEqual(result.returncode, 0, "the record is written and the list still runs")
        self.assertIn("warning:", result.stderr)
        [old] = (self.home / ".cleanyourtv").glob("recovery-192.168.1.50-*.json")
        self.assertIn(str(old), result.stderr, "the record that still describes the TV before cleanup")
        self.assertIn("1 of the packages disabled now are recorded there as enabled", result.stderr)
        self.assertIn("com.example.enabled", result.stderr)
        self.assertIn("tools/restore.sh", result.stderr)
        [new] = (self.home / ".cleanyourtv").glob("recovery-192.168.1.11-*.json")
        self.assertIn(f"~/.cleanyourtv/{new.name}", result.stderr, "where to move it to")
        self.assertIn("both records stay", result.stderr, "a second set on the same list is not an error")
        self.assertEqual(self.records(), ["192.168.1.11", "192.168.1.50"])
        self.assertIn("pm disable-user --user 0 com.example.enabled", self.sent())

    def test_undo_at_the_new_address_is_what_the_warning_is_about(self):
        self.tv()
        self.disable("192.168.1.50")
        self.tv(**CLEANED_TV)
        self.disable("192.168.1.11")

        sent = self.restore("192.168.1.11")

        self.assertNotIn("pm enable com.example.enabled", sent,
                         "the fresh record holds it as already disabled, so undo leaves it off")
        # The record from the old address is the one that would put it back.
        self.assertIn("pm enable com.example.enabled", self.restore("192.168.1.50"))

    def test_a_second_set_under_another_address_says_nothing(self):
        # Both TVs are as they were before any list: the old record agrees with what is disabled now,
        # so there is nothing to warn about even though the package name is in both.
        self.tv()
        self.disable("192.168.1.50")
        self.disable("192.168.1.11")
        result = self.disable("192.168.1.12")
        self.assertNotIn("warning:", result.stderr)
        self.assertEqual(self.records(), ["192.168.1.11", "192.168.1.12", "192.168.1.50"])

    def test_another_list_under_another_address_says_nothing(self):
        self.tv()
        self.disable("192.168.1.50")
        self.tv(**CLEANED_TV)
        other = self.base / "second-list.txt"
        other.write_text("# Another TV\ncom.example.enabled\n")
        result = self.disable("192.168.1.11", list_path=other)
        self.assertNotIn("warning:", result.stderr, "a record for another list is another TV's state")
        self.assertEqual(len(list((self.home / ".cleanyourtv").glob("recovery-*.json"))), 2)

    def test_a_record_that_cannot_be_read_does_not_stop_the_run(self):
        self.tv()
        self.disable("192.168.1.50")
        [old] = (self.home / ".cleanyourtv").glob("recovery-192.168.1.50-*.json")
        old.write_text("{not json")
        self.tv(**CLEANED_TV)
        result = self.disable("192.168.1.11")
        self.assertEqual(result.returncode, 0, "a damaged record under another address is not this run's")
        self.assertNotIn("Traceback", result.stderr)
        self.assertNotIn("warning:", result.stderr)
        self.assertEqual(old.read_text(), "{not json", "the file is left where it is")


class ThePageAndTheCodeAgreeTest(unittest.TestCase):
    """The troubleshooting entry and the warning in tools/recovery.py say the same thing.

    The page is where an owner who already lost the packages looks, and the warning is what the next
    run prints; one of them drifting leaves the other pointing at a file that is not there.
    """
    ROOT = Path(__file__).resolve().parents[1]

    def page_bullet(self):
        text = (self.ROOT / "docs/TROUBLESHOOTING.md").read_text(encoding="utf-8")
        start = text.index("- **`tools/restore.sh` leaves the packages an earlier batch disabled off")
        end = text.index("\n- **", start + 1)
        return " ".join(text[start:end].split())

    def test_the_page_names_the_record_the_fresh_copy_and_the_way_out(self):
        bullet = self.page_bullet()
        for phrase in ("~/.cleanyourtv/recovery-<address>-<list hash>.json",
                       "already disabled, left alone by undo",
                       "`tools/disable.sh` now says so on stderr",
                       "Run `tools/restore.sh` at the old address first",
                       "#2139"):
            self.assertIn(phrase, bullet)
        self.assertIn("not a second one cleaned with the same list", bullet,
                      "a second set is not a lost cleanup, and the page has to say so")

    def test_the_warning_the_page_describes_is_the_one_save_prints(self):
        source = (self.ROOT / "tools/recovery.py").read_text(encoding="utf-8")
        self.assertIn('f"recovery-{tag}-{digest}.json"', source, "the name the page quotes")
        self.assertIn("warning: a new record was written for this list", source)
        self.assertIn("Run tools/restore.sh ", source, "the way out the page names")
        # Nothing else in the file prints this warning, and it is save()'s: the run that writes a
        # record and disables nothing has to say it, not --commands.
        save = source[source.index("def save(list_path, host):"):source.index("def restore_commands(")]
        self.assertIn("warning: a new record was written for this list", save)
        self.assertIn("cleaned_by_another_record(list_path, path, disabled)", save)


if __name__ == "__main__":
    unittest.main()

"""One recovery record per TV on the network, however TV_HOST names it.

tools/recovery.py named a record after TV_HOST exactly as typed and ignored TV_PORT. So the same TV
saved as 192.168.1.50 and restored as 192.168.1.50:5555 found no record, and restore fell back to
enabling every listed package, the ones the owner had disabled before cleanup included. On the
network the record is now named after the TV's IP address alone, records written under the old
name (with the port) are still read, and with records under both names the first one wins. A relay
(a loopback address) and a host name keep TV_HOST as typed, as before. The TV is the fake tools/tv.py
from tests/test_recovery_record.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import test_recovery_record as harness  # noqa: E402

STOCK_HOME = harness.STOCK_HOME
# What restore sends for the harness's usual TV (test_valid_record_restores_the_same_batch).
USUAL_BATCH = [
    "pm enable com.example.enabled",
    "pm enable com.google.android.tvlauncher",
    f"cmd package set-home-activity --user 0 {STOCK_HOME}",
    "settings put global window_animation_scale 0.5",
    "settings delete global transition_animation_scale",
    "settings put global animator_duration_scale 1.0",
    "pm list packages -d",
]
# The same TV after the list was applied: a record of it would leave com.example.enabled off.
CLEANED_TV = {"disabled": ["com.example.enabled", "com.example.already_off"]}


class RecordPerLanAddressTest(unittest.TestCase):
    # The harness's copy of tools/ with the fake tv.py, HOME and TV state. The methods are borrowed
    # rather than inherited, so that the harness's own tests do not run twice.
    setUp = harness.RecoveryRecordTest.setUp
    write_list = harness.RecoveryRecordTest.write_list
    tv = harness.RecoveryRecordTest.tv
    sent = harness.RecoveryRecordTest.sent

    def run_script(self, script, host, port=None):
        env = dict(os.environ, TV_HOST=host, HOME=str(self.home), FAKE_TV_STATE=str(self.state_path))
        env.pop("TV_PORT", None)
        if port:
            env["TV_PORT"] = port
        return subprocess.run(["bash", str(self.repo / "tools" / script), str(self.list_path)],
                              cwd=self.repo, env=env, capture_output=True, text=True, timeout=120)

    def disable(self, host, port=None):
        result = self.run_script("disable.sh", host, port)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def restore(self, host, port=None):
        state = json.loads(self.state_path.read_text())
        state["commands"] = []
        self.state_path.write_text(json.dumps(state))
        result = self.run_script("restore.sh", host, port)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("no recovery record", result.stderr)
        return self.sent(), result.stderr

    def records(self):
        return sorted(p.name.rsplit("-", 1)[0] for p in (self.home / ".cleanyourtv").glob("recovery-*.json"))

    def rename_record(self, old_tag, new_tag):
        [path] = (self.home / ".cleanyourtv").glob(f"recovery-{old_tag}-*.json")
        path.rename(path.with_name(path.name.replace(f"recovery-{old_tag}-", f"recovery-{new_tag}-")))

    def set_created(self, tag, created):
        [path] = (self.home / ".cleanyourtv").glob(f"recovery-{tag}-*.json")
        record = json.loads(path.read_text())
        record["created"] = created
        path.write_text(json.dumps(record))

    def test_the_address_with_and_without_its_port_is_one_record(self):
        forms = [("192.168.1.50", None), ("192.168.1.50:5555", None), ("192.168.1.50", "5555")]
        # Each form saves once, and the next one keeps and restores that record.
        for i, saved in enumerate(forms):
            restored = forms[(i + 1) % len(forms)]
            with self.subTest(saved=saved, restored=restored):
                self.setUp()
                self.tv()
                self.disable(*saved)
                self.assertEqual(self.records(), ["recovery-192.168.1.50"])
                self.tv(**CLEANED_TV)
                self.assertIn("keeping the recovery record", self.disable(*restored))
                self.assertEqual(self.records(), ["recovery-192.168.1.50"])
                self.assertEqual(self.restore(*restored)[0], USUAL_BATCH)

    def test_a_record_under_the_old_name_with_the_port_is_still_used(self):
        for typed, port, old_tag in (("192.168.1.50", None, "192.168.1.50_5555"),
                                     ("192.168.1.50:5877", None, "192.168.1.50_5877"),
                                     ("192.168.1.50", "5877", "192.168.1.50_5877")):
            with self.subTest(typed=typed, port=port):
                self.setUp()
                self.tv()
                self.disable(typed, port)
                self.rename_record("192.168.1.50", old_tag)
                self.tv(**CLEANED_TV)
                out = self.disable(typed, port)
                self.assertIn("keeping the recovery record", out)
                self.assertEqual(self.records(), [f"recovery-{old_tag}"], "no second record of the cleaned TV")
                sent, err = self.restore(typed, port)
                self.assertEqual(sent, USUAL_BATCH)
                self.assertIn("the name an older version gave", err)

    def test_records_under_both_names_stop_before_the_tv_is_changed(self):
        # They may be this TV before and after cleanup, or two TVs that had the address in turn.
        self.tv()
        self.disable("192.168.1.50")
        records = self.home / ".cleanyourtv"
        [first] = records.glob("recovery-192.168.1.50-*.json")
        first.with_name(first.name.replace("192.168.1.50-", "192.168.1.50_5555-")).write_text(first.read_text())
        for script in ("disable.sh", "restore.sh"):
            with self.subTest(script=script):
                state = json.loads(self.state_path.read_text())
                state["commands"] = []
                self.state_path.write_text(json.dumps(state))
                result = self.run_script(script, "192.168.1.50")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("two recovery records", result.stderr)
                self.assertEqual([c for c in self.sent() if not c.startswith(("pm list", "cmd package query",
                                  "cmd package resolve", "dumpsys", "getprop", "settings get"))], [],
                                 "nothing is changed on the TV")

    def test_ipv6_with_and_without_brackets_is_one_record_and_not_another_address(self):
        self.tv()
        self.disable("[fd00::50]:5555")
        self.assertEqual(self.records(), ["recovery-fd00__50"])
        self.assertEqual(self.restore("fd00::50")[0], USUAL_BATCH)
        result = self.run_script("restore.sh", "fd00::5")
        self.assertIn("no recovery record", result.stderr, "fd00::5 is another TV")

    def test_a_relay_and_a_host_name_keep_the_name_as_typed(self):
        for host, port, tag in (("127.0.0.1", "15555", "recovery-127.0.0.1"),
                                ("127.0.0.1:15556", None, "recovery-127.0.0.1_15556"),
                                ("mytv.local:5555", None, "recovery-mytv.local_5555")):
            with self.subTest(host=host, port=port):
                self.setUp()
                self.tv()
                self.disable(host, port)
                self.assertEqual(self.records(), [tag], "unchanged from before this fix")
                self.assertEqual(self.restore(host, port)[0], USUAL_BATCH)


if __name__ == "__main__":
    unittest.main()

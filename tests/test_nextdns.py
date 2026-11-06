"""tools/nextdns.py against a simulated TV.

tools/tv.py is replaced by a fake that keeps the TV's global settings in a JSON file, so the real
script runs and only the ADB transport is simulated: the values it sets, the record it keeps for
--off, the refusal on a TV older than Android 9, and the arguments it refuses before reading TV_HOST.

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
from unittest import mock

from tools import nextdns


ROOT = Path(__file__).resolve().parents[1]

FAKE_TV = r'''
import json
import os

STATE_PATH = os.environ["FAKE_TV_STATE"]


def load():
    with open(STATE_PATH) as f:
        return json.load(f)


def save(state):
    with open(STATE_PATH, "w") as f:
        json.dump(state, f)


def answer(command):
    state = load()
    words = command.split()
    if command == "getprop ro.build.version.sdk":
        return state["sdk"] + "\n"
    if words[:3] == ["settings", "get", "global"]:
        if state.get("reads_fail"):
            raise ConnectionError("connection reset by peer")
        value = state["global"].get(words[3])
        return ("null" if value is None else value) + "\n"
    if words[:3] == ["settings", "put", "global"]:
        if not state.get("read_only") and words[3] not in state.get("refused", []):
            state["global"][words[3]] = words[4]
            state.setdefault("log", []).append(command)
            save(state)
        return ""
    if words[:3] == ["settings", "delete", "global"]:
        if words[3] in state.get("refused", []):
            return ""
        state["global"].pop(words[3], None)
        state.setdefault("log", []).append(command)
        save(state)
        return "Deleted 1 rows\n"
    if command.startswith("dumpsys connectivity"):
        return "NetworkAgentInfo{... Capabilities: INTERNET&VALIDATED ...}\n" if state["validates"] else \
            "NetworkAgentInfo{... Capabilities: INTERNET ...}\n"
    return ""


class Device:
    def shell(self, command, read_timeout_s=None):
        return answer(command)

    def close(self):
        pass


def connect():
    if load().get("unreachable"):
        raise ConnectionError("could not connect to 192.168.1.50:5555")
    return Device()
'''


class NextDnsTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        tools = self.base / "repo" / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "nextdns.py", tools / "nextdns.py")
        (tools / "tv.py").write_text(FAKE_TV)
        self.script = tools / "nextdns.py"
        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.tv()

    def tv(self, **overrides):
        state = {"sdk": "30", "global": {"auto_time": "1"}, "validates": True, "unreachable": False}
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def state(self):
        return json.loads(self.state_path.read_text())

    def record(self):
        return self.home / ".cleanyourtv" / "private-dns-192.168.1.50.json"

    def run_script(self, *args, host="192.168.1.50"):
        env = dict(os.environ, HOME=str(self.home), FAKE_TV_STATE=str(self.state_path))
        env.pop("TV_HOST", None)
        if host:
            env["TV_HOST"] = host
        return subprocess.run([sys.executable, "-I", str(self.script), *args], cwd=self.base / "repo",
                              env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL,
                              timeout=120)

    # Turning it on

    def test_sets_hostname_mode_with_the_profile(self):
        run = self.run_script("abc123")
        self.assertEqual(run.returncode, 0, run.stderr)
        values = self.state()["global"]
        self.assertEqual(values["private_dns_mode"], "hostname")
        self.assertEqual(values["private_dns_specifier"], "abc123.dns.nextdns.io")
        self.assertIn("validated through NextDNS", run.stdout)

    def test_device_name_goes_into_the_first_label(self):
        run = self.run_script("ABC123", "--name", "Living room")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertEqual(self.state()["global"]["private_dns_specifier"],
                         "Living--room-abc123.dns.nextdns.io")

    def test_specifier_is_set_before_the_mode(self):
        # hostname mode with no host, or the old host, for a moment is a TV with no DNS at all.
        self.run_script("abc123")
        log = self.state()["log"]
        self.assertLess(log.index("settings put global private_dns_specifier abc123.dns.nextdns.io"),
                        log.index("settings put global private_dns_mode hostname"))

    def test_first_record_survives_a_second_run(self):
        self.tv(**{"global": {"private_dns_mode": "opportunistic", "auto_time": "1"}})
        self.run_script("abc123")
        self.run_script("def456")
        record = json.loads(self.record().read_text())
        self.assertEqual(record, {"private_dns_mode": "opportunistic", "private_dns_specifier": None})

    def test_android_8_is_refused_without_a_change(self):
        self.tv(sdk="25")
        run = self.run_script("abc123")
        self.assertEqual(run.returncode, 1)
        self.assertIn("Android 9", run.stderr)
        self.assertIn("Linked IP", run.stderr)
        self.assertNotIn("private_dns_mode", self.state()["global"])
        self.assertFalse(self.record().exists())

    def test_values_that_do_not_stick_exit_1(self):
        self.tv(read_only=True)
        run = self.run_script("abc123")
        self.assertEqual(run.returncode, 1)
        self.assertIn("did not keep", run.stderr)
        self.assertIn("--off", run.stderr)

    def test_clock_warning_when_auto_time_is_off(self):
        self.tv(**{"global": {"auto_time": "0"}})
        run = self.run_script("abc123")
        self.assertIn("auto_time is off", run.stdout)

    # The undo

    def test_off_restores_the_recorded_values(self):
        self.tv(**{"global": {"private_dns_mode": "hostname",
                              "private_dns_specifier": "dns.adguard-dns.com", "auto_time": "1"}})
        self.run_script("abc123")
        run = self.run_script("--off")
        self.assertEqual(run.returncode, 0, run.stderr)
        values = self.state()["global"]
        self.assertEqual(values["private_dns_specifier"], "dns.adguard-dns.com")
        self.assertEqual(values["private_dns_mode"], "hostname")
        self.assertFalse(self.record().exists())

    def test_off_deletes_values_the_tv_never_had(self):
        self.run_script("abc123")
        self.run_script("--off")
        values = self.state()["global"]
        self.assertNotIn("private_dns_mode", values)
        self.assertNotIn("private_dns_specifier", values)

    def test_off_without_a_record_is_opportunistic(self):
        self.tv(**{"global": {"private_dns_mode": "hostname",
                              "private_dns_specifier": "abc123.dns.nextdns.io"}})
        run = self.run_script("--off")
        self.assertEqual(run.returncode, 0, run.stderr)
        values = self.state()["global"]
        self.assertEqual(values["private_dns_mode"], "opportunistic")
        self.assertNotIn("private_dns_specifier", values)

    def refuse(self, *keys, **flags):
        state = self.state()
        state["refused"] = list(keys)
        state.update(flags)
        self.state_path.write_text(json.dumps(state))

    def test_off_keeps_the_record_when_a_write_is_refused(self):
        self.tv(**{"global": {"private_dns_mode": "opportunistic", "auto_time": "1"}})
        self.run_script("abc123")
        self.refuse("private_dns_mode")
        run = self.run_script("--off")
        self.assertEqual(run.returncode, 1)
        self.assertIn("did not take back", run.stderr)
        self.assertNotIn("Put back", run.stdout)
        self.assertTrue(self.record().exists())

    def test_off_keeps_the_record_when_a_delete_is_refused(self):
        self.run_script("abc123")
        self.refuse("private_dns_specifier")
        run = self.run_script("--off")
        self.assertEqual(run.returncode, 1)
        self.assertEqual(self.state()["global"]["private_dns_specifier"], "abc123.dns.nextdns.io")
        self.assertTrue(self.record().exists())

    def test_off_again_after_a_partial_restore_puts_the_original_back(self):
        self.tv(**{"global": {"private_dns_mode": "hostname",
                              "private_dns_specifier": "dns.adguard-dns.com", "auto_time": "1"}})
        self.run_script("abc123")
        self.refuse("private_dns_specifier")
        self.assertEqual(self.run_script("--off").returncode, 1)
        self.refuse()
        run = self.run_script("--off")
        self.assertEqual(run.returncode, 0, run.stderr)
        values = self.state()["global"]
        self.assertEqual(values["private_dns_mode"], "hostname")
        self.assertEqual(values["private_dns_specifier"], "dns.adguard-dns.com")
        self.assertFalse(self.record().exists())

    def test_off_keeps_the_record_when_the_readback_fails(self):
        self.run_script("abc123")
        self.refuse(reads_fail=True)
        run = self.run_script("--off")
        self.assertNotEqual(run.returncode, 0)
        self.assertNotIn("Put back", run.stdout)
        self.assertTrue(self.record().exists())

    def test_off_without_a_record_reports_a_refused_write(self):
        self.tv(**{"global": {"private_dns_mode": "hostname",
                              "private_dns_specifier": "abc123.dns.nextdns.io"}})
        self.refuse("private_dns_mode")
        run = self.run_script("--off")
        self.assertEqual(run.returncode, 1)
        self.assertIn("did not take back", run.stderr)
        self.assertNotIn("back to opportunistic", run.stdout)

    def test_status_names_nextdns(self):
        self.run_script("abc123")
        run = self.run_script("--status")
        self.assertEqual(run.returncode, 0, run.stderr)
        self.assertIn("abc123.dns.nextdns.io", run.stdout)
        self.assertIn("no fallback", run.stdout)

    # Refused before the TV

    def test_bad_profile_id_is_refused_before_tv_host(self):
        for bad in (["abc"], ["abc123;reboot"], ["--name", "x"], ["abc123", "--name"],
                    ["abc123", "--name", "a;b"], ["--bogus"]):
            run = self.run_script(*bad, host=None)
            self.assertEqual(run.returncode, 2, bad)
            self.assertIn("usage: nextdns.py", run.stderr, bad)
        self.assertNotIn("log", self.state())

    def test_help_needs_no_tv(self):
        run = self.run_script("--help", host=None)
        self.assertEqual(run.returncode, 0)
        self.assertIn("Private DNS", run.stdout)

    def test_unreachable_tv_exits_2(self):
        self.tv(unreachable=True)
        run = self.run_script("abc123")
        self.assertEqual(run.returncode, 2)
        self.assertIn("Nothing was asked of the TV.", run.stderr)
        self.assertFalse(self.record().exists())


class ValidationTimeoutTest(unittest.TestCase):
    """A network that never validates is reported, without waiting the real 20 seconds."""

    def test_unvalidated_network_is_reported(self):
        dev = mock.Mock()
        dev.shell.return_value = "NetworkAgentInfo{... Capabilities: INTERNET ...}"
        with mock.patch.object(nextdns, "VALIDATE_SECONDS", 0), mock.patch.object(nextdns.time, "sleep"):
            self.assertFalse(nextdns.validated(dev))

    def test_long_device_name_is_refused(self):
        with self.assertRaises(SystemExit) as stop, mock.patch("sys.stderr"):
            nextdns.hostname("abc123", "x" * 60)
        self.assertEqual(stop.exception.code, 2)


if __name__ == "__main__":
    unittest.main()

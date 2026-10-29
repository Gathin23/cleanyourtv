"""tools/trace.py: the scenarios it records, the Perfetto config it sends, and the order it works in.

A trace is only comparable with another if both were recorded the same way: the setup outside the
recording (so opening the other app is not in it), the same steps, a recording long enough for them,
and the trace pulled back with nothing left on the TV. A force-stop can take the launcher's
accessibility helpers off the list, so they are put back after the last run, never before (that
would warm a cold start; see tests/test_bench_accessibility.py).

The TV is faked, so these run without one and without adb_shell.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import base64
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import re
import sys
import tempfile
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("cyt_trace", ROOT / "tools" / "trace.py")
trace = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trace)

LAUNCHER = "com.gathin.tvhome/.MainActivity"
OTHER = "com.android.tv.settings/.MainSettings"

# Encoded by protoc 3.13.0 from the original text config, using the official
# android-9.0.0_r1 schema (protos/perfetto/config/trace_config.proto). These independent
# fixtures cover every buffer/data source, plus multi-byte lengths and duration varints.
CONFIG_9000 = base64.b64decode(
    "CgYIgIAEIAEKBQiAICABEocCCoQCCgxsaW51eC5mdHJhY2UQAKIG8AEKEnNjaGVkL3NjaGVkX3N3aXRj"
    "aAoSc2NoZWQvc2NoZWRfd2FrZXVwChJzY2hlZC9zY2hlZF93YWtpbmcKE3Bvd2VyL2NwdV9mcmVxdWVu"
    "Y3kKDnBvd2VyL2NwdV9pZGxlChF0YXNrL3Rhc2tfbmV3dGFzawoQdGFzay90YXNrX3JlbmFtZQoYc2No"
    "ZWQvc2NoZWRfcHJvY2Vzc19leGl0EgJhbRICd20SBHZpZXcSA2dmeBIFaW5wdXQSBmRhbHZpaxIDcmVzEg1i"
    "aW5kZXJfZHJpdmVyGhFjb20uZ2F0aGluLnR2aG9tZVCAgAFY+gESHgocChNsaW51eC5wcm9jZXNzX3N0"
    "YXRzEAG6BgIQARIoCiYKJGFuZHJvaWQuc3VyZmFjZWZsaW5nZXIuZnJhbWV0aW1lbGluZRIZChcKFWFu"
    "ZHJvaWQucGFja2FnZXNfbGlzdBioRg==")
LONG_PACKAGE = "com.example." + "launcher" * 20
CONFIG_LONG = base64.b64decode(
    "CgYIgIAEIAEKBQiAICABEqMDCqADCgxsaW51eC5mdHJhY2UQAKIGjAMKEnNjaGVkL3NjaGVkX3N3aXRj"
    "aAoSc2NoZWQvc2NoZWRfd2FrZXVwChJzY2hlZC9zY2hlZF93YWtpbmcKE3Bvd2VyL2NwdV9mcmVxdWVu"
    "Y3kKDnBvd2VyL2NwdV9pZGxlChF0YXNrL3Rhc2tfbmV3dGFzawoQdGFzay90YXNrX3JlbmFtZQoYc2No"
    "ZWQvc2NoZWRfcHJvY2Vzc19leGl0EgJhbRICd20SBHZpZXcSA2dmeBIFaW5wdXQSBmRhbHZpaxIDcmVzEg1i"
    "aW5kZXJfZHJpdmVyGqwBY29tLmV4YW1wbGUubGF1bmNoZXJsYXVuY2hlcmxhdW5jaGVybGF1bmNoZXJs"
    "YXVuY2hlcmxhdW5jaGVybGF1bmNoZXJsYXVuY2hlcmxhdW5jaGVybGF1bmNoZXJsYXVuY2hlcmxhdW5j"
    "aGVybGF1bmNoZXJsYXVuY2hlcmxhdW5jaGVybGF1bmNoZXJsYXVuY2hlcmxhdW5jaGVybGF1bmNoZXJs"
    "YXVuY2hlclCAgAFY+gESHgocChNsaW51eC5wcm9jZXNzX3N0YXRzEAG6BgIQARIoCiYKJGFuZHJvaWQu"
    "c3VyZmFjZWZsaW5nZXIuZnJhbWV0aW1lbGluZRIZChcKFWFuZHJvaWQucGFja2FnZXNfbGlzdBiAgAE=")


class FakeDevice:
    """Answers the shell commands trace.py sends and records them; pull writes a stand-in trace."""

    def __init__(self, perfetto_says="1234\n"):
        self.perfetto_says = perfetto_says
        self.calls = []

    def shell(self, command, read_timeout_s=None):
        self.calls.append(("shell", command))
        if "| perfetto " in command:
            return self.perfetto_says
        return ""

    def pull(self, remote, local, transport_timeout_s=None, read_timeout_s=None):
        self.calls.append(("pull", remote, local))
        Path(local).write_bytes(b"trace")


class ScenarioTest(unittest.TestCase):
    def test_setup_stays_out_of_the_recording(self):
        setup, steps = trace.scenario("home", LAUNCHER, OTHER)
        self.assertIn(f"am start --activity-reorder-to-front -n {OTHER}", setup)
        self.assertEqual(steps, ["input keyevent KEYCODE_HOME", "sleep 4"])

    def test_cold_stops_the_launcher_before_recording_and_starts_it_inside(self):
        setup, steps = trace.scenario("cold", LAUNCHER, OTHER)
        self.assertIn(f"am start --activity-reorder-to-front -n {OTHER}", setup)
        self.assertIn("am force-stop com.gathin.tvhome", setup)
        self.assertEqual(steps[0], "am start -W -a android.intent.action.MAIN "
                         f"-c android.intent.category.HOME -n {LAUNCHER}")

    def test_coldsys_leaves_the_restart_to_android(self):
        setup, steps = trace.scenario("coldsys", LAUNCHER, None)
        self.assertEqual(steps[0], "am force-stop com.gathin.tvhome")
        self.assertFalse(any("am start" in s for s in setup + steps))

    def test_nav_is_24_moves(self):
        _, steps = trace.scenario("nav", LAUNCHER, None)
        self.assertEqual(sum(s.startswith("input keyevent KEYCODE_DPAD_") for s in steps), 24)

    def test_custom_uses_the_given_commands(self):
        self.assertEqual(trace.scenario("custom", LAUNCHER, None, ["a"], ["b", "sleep 1"]), (["a"], ["b", "sleep 1"]))

    def test_recording_covers_the_sleeps_plus_margins(self):
        self.assertEqual(trace.recording_ms(["input keyevent KEYCODE_HOME", "sleep 4"]),
                         (trace.START_MARGIN_S + 4 + trace.END_MARGIN_S) * 1000)

    def test_config_traces_the_launcher_for_that_long(self):
        self.assertEqual(trace.perfetto_config("com.gathin.tvhome", 9000), CONFIG_9000)

    def test_config_supports_a_different_package_and_multibyte_lengths(self):
        self.assertEqual(trace.perfetto_config(LONG_PACKAGE, 16384), CONFIG_LONG)


class ArgumentTest(unittest.TestCase):
    def parse_error(self, argv):
        with contextlib.redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit) as caught:
            trace.parse(argv)
        self.assertEqual(caught.exception.code, 2)
        return err.getvalue()

    def test_bad_package_and_component_are_refused_before_connecting(self):
        self.assertIn("not a package name", self.parse_error(["home", "--package", "x; reboot"]))
        self.assertIn("--app needs", self.parse_error(["home", "--app", "com.example"]))

    def test_custom_needs_a_step_and_others_take_none(self):
        self.assertIn("at least one --step", self.parse_error(["custom"]))
        self.assertIn("custom scenario", self.parse_error(["home", "--step", "x"]))

    def test_defaults(self):
        args = trace.parse(["nav"])
        self.assertEqual((args.runs, args.out, args.package), (5, "traces", "com.gathin.tvhome"))

    def test_a_file_where_the_traces_go_is_refused_before_connecting(self):
        # os.makedirs() threw FileExistsError from main() after the TV had answered the Home query, so a
        # --out typo read as a recording that failed.
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "traces"
            target.write_text("not a folder")
            err = self.parse_error(["home", "--out", str(target)])
            self.assertIn(str(target), err)
            self.assertIn("is a file", err)
            err = self.parse_error(["home", "--out", str(target / "before")])
            self.assertIn(str(target), err)
            self.assertIn("passes through the file", err)
        self.assertIn("--out is empty", self.parse_error(["home", "--out", ""]))

    def test_a_folder_or_a_new_folder_is_still_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            existing = Path(tmp) / "before"
            existing.mkdir()
            self.assertEqual(trace.parse(["home", "--out", str(existing)]).out, str(existing))
            new = Path(tmp) / "after"
            self.assertEqual(trace.parse(["home", "--out", str(new)]).out, str(new))   # makedirs() creates it

    def test_nothing_is_asked_of_the_tv_when_the_folder_is_a_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            target = Path(tmp) / "traces"
            target.write_text("not a folder")
            connects = []
            fake_tv = types.ModuleType("tv")
            fake_tv.HOST, fake_tv.PORT = "192.0.2.7", "5555"
            fake_tv.connect = lambda: connects.append(1)
            fake_bench = types.ModuleType("bench")
            fake_bench.accessibility_services = lambda *a: []
            fake_bench.restore_accessibility = lambda *a: None
            with mock.patch.dict(sys.modules, {"tv": fake_tv, "bench": fake_bench}), \
                 contextlib.redirect_stderr(io.StringIO()) as err:
                with self.assertRaises(SystemExit) as caught:
                    trace.main(["coldsys", "--out", str(target)])
            self.assertEqual(caught.exception.code, 2)
            self.assertEqual(connects, [], "the TV was never opened")
            self.assertIn("is a file", err.getvalue())


class RecordTest(unittest.TestCase):
    def record(self, device, setup, steps):
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(trace.time, "sleep") as sleep:
            path = os.path.join(tmp, "home-1.pftrace")
            size = trace.record_one(device, setup, steps, "com.gathin.tvhome", path)
            return size, sleep

    def test_setup_then_perfetto_then_steps_then_pull_and_cleanup(self):
        device = FakeDevice()
        size, _ = self.record(device, ["input keyevent KEYCODE_HOME"], ["input keyevent KEYCODE_DPAD_RIGHT"])
        commands = [c[1] for c in device.calls]
        start = next(i for i, c in enumerate(commands) if "| perfetto " in c)
        self.assertEqual(commands[0], "input keyevent KEYCODE_HOME")
        self.assertGreater(commands.index("input keyevent KEYCODE_DPAD_RIGHT"), start)
        self.assertEqual(device.calls[-2][0], "pull")
        self.assertEqual(commands[-1], f"rm -f {trace.REMOTE}")
        self.assertEqual(size, len(b"trace"))

    def test_android_9_accepts_the_binary_config_without_txt(self):
        class AndroidPDevice(FakeDevice):
            started = False

            def shell(self, command, read_timeout_s=None):
                self.calls.append(("shell", command))
                if "| perfetto " in command:
                    if "--txt" in command:
                        return "perfetto: unrecognized option '--txt'\nUsage: perfetto\n"
                    match = re.search(r"echo (\S+) \| base64 -d \| perfetto -c - -o ", command)
                    if match and base64.b64decode(match.group(1), validate=True) == CONFIG_9000:
                        self.started = True
                    # Android P's --background does not print a PID.
                return ""

            def pull(self, *args, **kwargs):
                if not self.started:
                    raise RuntimeError("Android P never started recording")
                super().pull(*args, **kwargs)

        device = AndroidPDevice()
        size, _ = self.record(device, [], ["sleep 4"])
        self.assertEqual(size, len(b"trace"))
        self.assertIn(("shell", "pidof perfetto >/dev/null && echo running"), device.calls)

    def test_sleeps_wait_here_not_on_the_tv(self):
        device = FakeDevice()
        _, sleep = self.record(device, ["sleep 2"], ["sleep 1.5"])
        self.assertFalse(any(c[1].startswith("sleep") for c in device.calls if c[0] == "shell"))
        self.assertIn(mock.call(2.0), sleep.call_args_list)
        self.assertIn(mock.call(1.5), sleep.call_args_list)

    def test_waits_on_the_pid_perfetto_reports(self):
        device = FakeDevice("1234\n")
        self.record(device, [], ["sleep 1"])
        self.assertIn(("shell", "[ -d /proc/1234 ] && echo running"), device.calls)

    def test_an_empty_trace_is_an_error_not_a_file(self):
        device = FakeDevice()
        device.pull = lambda remote, local, **kw: Path(local).write_bytes(b"")
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(trace.time, "sleep"):
            with self.assertRaises(RuntimeError) as caught:
                trace.record_one(device, [], ["sleep 1"], "com.gathin.tvhome", os.path.join(tmp, "x.pftrace"))
            self.assertEqual(os.listdir(tmp), [], "no 0-byte trace is left behind")
        self.assertIn("empty trace", str(caught.exception))


    def test_a_tv_without_perfetto_says_so(self):
        with self.assertRaises(RuntimeError) as caught:
            self.record(FakeDevice("/system/bin/sh: perfetto: not found\n"), [], ["sleep 1"])
        self.assertIn("Android 9 and later", str(caught.exception))


class OtherAppTest(unittest.TestCase):
    def shell(self, answers):
        return lambda cmd: next((v for k, v in answers.items() if k in cmd), "")

    def test_settings_first(self):
        shell = self.shell({"android.settings.SETTINGS": "priority=0\ncom.android.tv.settings/.MainSettings\n",
                            "LEANBACK_LAUNCHER": "org.videolan.vlc/.StartActivity\n"})
        self.assertEqual(trace.other_app(shell, "com.gathin.tvhome"), "com.android.tv.settings/.MainSettings")

    def test_never_the_chooser_or_the_launcher(self):
        shell = self.shell({"android.settings.SETTINGS": "android/com.android.internal.app.ResolverActivity\n",
                            "LEANBACK_LAUNCHER": "com.gathin.tvhome/.MainActivity\norg.videolan.vlc/.StartActivity\n"})
        self.assertEqual(trace.other_app(shell, "com.gathin.tvhome"), "org.videolan.vlc/.StartActivity")


if __name__ == "__main__":
    unittest.main()

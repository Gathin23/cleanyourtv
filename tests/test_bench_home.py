"""Correlate HOME with the target's AM/WM resume event; no TV or network is used."""
import re
import shlex
import unittest
from unittest import mock

from tools import bench


COMPONENT = "com.gathin.tvhome/.MainActivity"
CLASS = "com.gathin.tvhome.MainActivity"
OTHER = "com.android.tv.settings/.MainSettings"


def home(stamp="1000.000"):
    return (f"  {stamp} 100 100 I Input: injectKeyEvent: KeyEvent {{ "
            "action=ACTION_DOWN, keyCode=KEYCODE_HOME, eventTime=50 }")


def resume(stamp="1000.125", activity=CLASS, tag="am_on_resume_called", extra=""):
    return f"  {stamp} 100 100 I {tag}: [0,{activity},RESUME_ACTIVITY{extra}]"


class LogTV:
    """Replay logs through the actual shell command's grep patterns.

    Returning every line regardless of the remote grep would hide a missing WM
    filter. Logs from events also survive the tool's main-buffer-only clear.
    """

    def __init__(self, *logs):
        self._other_app = OTHER
        self.logs = iter(logs)
        self.commands = []

    def shell(self, command):
        self.commands.append(command)
        if command.startswith("logcat -d "):
            logcat, separator, grep = command.partition(" | grep ")
            if not separator or logcat != "logcat -d -b main -b events -v epoch":
                raise AssertionError(f"unexpected log acquisition: {command}")
            args = shlex.split(grep)
            if len(args) % 2 or any(arg != "-e" for arg in args[::2]):
                raise AssertionError(f"unexpected grep arguments: {grep}")
            return "\n".join(line for line in next(self.logs)
                             if any(re.search(pattern, line) for pattern in args[1::2]))
        if command in (f"am start -W --activity-reorder-to-front -n {OTHER}", "logcat -c -b main", "input keyevent 3"):
            return ""
        raise AssertionError(f"unexpected shell command: {command}")


class ReturnToHomeTest(unittest.TestCase):
    def measure(self, *logs, component=COMPONENT):
        tv = LogTV(*logs)
        with mock.patch.object(bench.time, "sleep"):
            values = bench.return_to_home(tv, component, len(logs))
        return values, tv.commands

    def test_aosp_am_and_wm_events_survive_the_remote_filter(self):
        # Android 8/10: services/core/java/com/android/server/am/EventLogTags.logtags.
        # Android 14: core/java/android/app/EventLogTags.logtags, with the added time field.
        # https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/core/java/android/app/Activity.java#8745
        for version, tag, extra in (("8", "am_on_resume_called", ""),
                                    ("10", "am_on_resume_called", ""),
                                    ("14", "wm_on_resume_called", ",3")):
            for component in (COMPONENT, f"com.gathin.tvhome/{CLASS}"):
                with self.subTest(android=version, component=component):
                    values, commands = self.measure([home(), resume(tag=tag, extra=extra)],
                                                    component=component)
                    self.assertEqual(values, [125])
                    self.assertEqual(commands[:3], [f"am start -W --activity-reorder-to-front -n {OTHER}",
                                                   "logcat -c -b main", "input keyevent 3"])

    def test_an_old_resume_without_a_current_resume_is_not_a_sample(self):
        for lines in ([resume("999.000"), home()], [home(), resume("999.000")]):
            with self.subTest(lines=lines):
                values, _ = self.measure(lines)
                self.assertEqual(values, [])

    def test_old_resumes_do_not_hide_the_current_target_resume(self):
        values, _ = self.measure([resume("999.000"), home(), resume()])
        self.assertEqual(values, [125])

    def test_other_activity_names_cannot_replace_the_target_resume(self):
        for activity in ("com.other.MainActivity", CLASS + "Extra", CLASS + "$Other"):
            with self.subTest(activity=activity):
                values, _ = self.measure([home(), resume(), resume("1000.500", activity)])
                self.assertEqual(values, [125])

    def test_target_name_in_another_events_reason_does_not_count(self):
        line = f"  1000.500 100 100 I am_on_resume_called: [0,com.other.Activity,{CLASS}]"
        values, _ = self.measure([home(), resume(), line])
        self.assertEqual(values, [125])

    def test_the_last_eligible_target_resume_is_kept(self):
        for tag in ("am_on_resume_called", "wm_on_resume_called"):
            with self.subTest(tag=tag):
                values, _ = self.measure([home(), resume(tag=tag),
                                          resume("1000.250", "com.other.MainActivity", tag),
                                          resume("1000.375", tag=tag)])
                self.assertEqual(values, [375])

    def test_no_home_event_means_no_sample(self):
        values, _ = self.measure([resume()])
        self.assertEqual(values, [])

    def test_no_target_resume_means_no_sample(self):
        values, _ = self.measure([home(), resume(activity="com.other.MainActivity")])
        self.assertEqual(values, [])

    def test_malformed_resume_lines_are_ignored(self):
        for line in (resume("not-a-time"),
                     f"  1000.125 100 100 I am_on_resume_called: [{CLASS}]",
                     f"  1000.125 100 100 I am_on_resume_called: {CLASS}"):
            with self.subTest(line=line):
                values, _ = self.measure([home(), line])
                self.assertEqual(values, [])

    def test_a_previous_runs_resume_cannot_become_a_negative_sample(self):
        values, _ = self.measure([home(), resume()], [resume(), home("1005.000")])
        self.assertEqual(values, [125])

    def test_a_resume_at_the_key_timestamp_is_valid(self):
        values, _ = self.measure([home(), resume("1000.000")])
        self.assertEqual(values, [0])


class ColdStartHomeTest(unittest.TestCase):
    def test_each_cold_start_targets_the_home_task_and_keeps_wait_time(self):
        for component in (COMPONENT, "com.example.home/.Launcher"):
            with self.subTest(component=component):
                tv = mock.Mock()
                tv.package_of.return_value = component.split("/")[0]
                launch = ("am start -W -a android.intent.action.MAIN "
                          f"-c android.intent.category.HOME -n {component}")
                waits = iter(("WaitTime: 410", "WaitTime: 430"))
                tv.shell.side_effect = lambda command: next(waits) if command == launch else ""
                with mock.patch.object(bench, "other_app", return_value=OTHER), \
                        mock.patch.object(bench, "accessibility_services", return_value=[]), \
                        mock.patch.object(bench, "restore_accessibility") as restore, \
                        mock.patch.object(bench.time, "sleep"):
                    values = bench.cold_start(tv, component, 2)
                self.assertEqual(values, [410, 430])
                self.assertEqual(tv.shell.call_args_list, [
                    mock.call(f"am start -W --activity-reorder-to-front -n {OTHER}"),
                    mock.call(f"am force-stop {component.split('/')[0]}"),
                    mock.call(launch),
                ] * 2)
                restore.assert_called_once_with(tv, component.split("/")[0], [])


if __name__ == "__main__":
    unittest.main()

"""bench.py leaves Home for a real app: --app when given, else the TV's Settings, else a leanback app.

A plain LAUNCHER lookup answers with android's ResolverActivity on some sets (a TCL C755, a HOTACK L018
projector), so the cold start and the return to Home were measured against the app chooser dialog.
tools/trace.py already picks its other app this way; these checks hold bench.py to the same rule.

The TV is faked, so this needs no TV and no adb_shell.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import tempfile
import unittest
from unittest import mock

from tools import bench
from tests.test_bench_counts import FakeTV, run


class AnsweringTV:
    """A TV whose shell answers each lookup from a fixed table."""

    def __init__(self, answers):
        self._other_app = None
        self.answers = answers

    def home_component(self):
        return "com.gathin.tvhome/.MainActivity"

    def package_of(self, component):
        return component.split("/", 1)[0]

    def shell(self, command):
        return next((v for k, v in self.answers.items() if k in command), "")


class DefaultOtherAppTest(unittest.TestCase):
    def test_settings_first(self):
        tv = AnsweringTV({"android.settings.SETTINGS": "priority=0\ncom.android.tv.settings/.MainSettings\n",
                          "LEANBACK_LAUNCHER": "org.videolan.vlc/.StartActivity\n"})
        self.assertEqual(bench.other_app(tv), "com.android.tv.settings/.MainSettings")

    def test_never_the_chooser_or_the_launcher(self):
        tv = AnsweringTV({"android.settings.SETTINGS": "android/com.android.internal.app.ResolverActivity\n",
                          "category.LAUNCHER": "android/com.android.internal.app.ResolverActivity\n",
                          "LEANBACK_LAUNCHER": "com.gathin.tvhome/.MainActivity\norg.videolan.vlc/.StartActivity\n"})
        self.assertEqual(bench.other_app(tv), "org.videolan.vlc/.StartActivity")


def run_and_keep_the_tv(argv):
    """tests.test_bench_counts.run(), plus the fake TVs it built, to read what the run asked of them."""
    made = []
    init = FakeTV.__init__

    def keep(self, *args, **kwargs):
        init(self, *args, **kwargs)
        made.append(self)

    with mock.patch.object(FakeTV, "__init__", keep):
        status, out, err = run(argv)
    return status, err, made


class AppOptionTest(unittest.TestCase):
    def test_the_given_app_is_the_one_left_home_for(self):
        with tempfile.TemporaryDirectory() as tmp:
            status, err, made = run_and_keep_the_tv(
                ["--host", "192.0.2.7", "--out-dir", tmp, "--app", "org.videolan.vlc/.StartActivity",
                 "--keys", "1", "--series", "1", "--cold", "1", "--returns", "1", "--idle", "1"])
        self.assertEqual(status, 0, err)
        started = {m.group(1) for c in made[0].commands for m in [re.search(r"^am start.* -n (\S+)", c)] if m}
        self.assertEqual(started, {"org.videolan.vlc/.StartActivity", "com.gathin.tvhome/.MainActivity"})

    def test_a_component_without_a_slash_is_refused_before_the_tv(self):
        with tempfile.TemporaryDirectory() as tmp:
            status, err, made = run_and_keep_the_tv(["--host", "192.0.2.7", "--out-dir", tmp,
                                                     "--app", "org.videolan.vlc"])
            self.assertEqual((status, made, os.listdir(tmp)), (2, [], []), err)
        self.assertIn("--app needs package/activity, not 'org.videolan.vlc'", err)


if __name__ == "__main__":
    unittest.main()

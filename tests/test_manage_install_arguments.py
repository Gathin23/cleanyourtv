"""Scriptless add-ons refuse extra install arguments before building or contacting the TV."""
import contextlib
import io
import os
import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class InstallArgumentsTest(unittest.TestCase):
    def setUp(self):
        patcher = mock.patch.object(
            manage.subprocess, "run",
            return_value=subprocess.CompletedProcess([], 0, "Success\n", ""),
        )
        self.process = patcher.start()
        self.addCleanup(patcher.stop)
        patcher = mock.patch.object(manage, "tv_python", return_value="fake-python")
        self.python = patcher.start()
        self.addCleanup(patcher.stop)
        patcher = mock.patch.dict(os.environ, {"TV_HOST": "192.0.2.1"})
        patcher.start()
        self.addCleanup(patcher.stop)

    def call(self, operation, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            status = operation(*args)
        return status, out.getvalue(), err.getvalue()

    def test_scriptless_addon_refuses_every_extra_word_before_side_effects(self):
        for extra in (["--dry-run"], ["--unknown"], ["unexpected"], ["--"], ["--unknown", "value"]):
            with self.subTest(extra=extra):
                self.process.reset_mock()
                self.python.reset_mock()
                with mock.patch.object(manage, "tv", side_effect=AssertionError("TV was asked")) as tv:
                    status, out, err = self.call(manage.main, ["install", "jewish-calendar"] + extra)
                self.assertEqual(status, 2, err)
                self.assertEqual(out, "")
                self.assertIn("jewish-calendar", err)
                for word in extra:
                    self.assertIn(word, err)
                self.assertIn("install <add-on or theme>", err)
                self.process.assert_not_called()
                self.python.assert_not_called()
                tv.assert_not_called()

    def test_direct_scriptless_install_also_refuses_extra_arguments(self):
        item = manage.find("jewish-calendar")
        self.assertIsNone(item["script"])
        with mock.patch.object(manage, "run", side_effect=AssertionError("build was started")) as run, \
                mock.patch.object(manage, "tv", side_effect=AssertionError("TV was asked")) as tv:
            status, out, err = self.call(manage.install_addon, item, ["--dry-run"])
        self.assertEqual(status, 2, err)
        self.assertEqual(out, "")
        self.assertIn("--dry-run", err)
        run.assert_not_called()
        self.process.assert_not_called()
        self.python.assert_not_called()
        tv.assert_not_called()

    def test_custom_installers_keep_arguments_and_exit_status(self):
        for script in ("install.sh", "build-and-install.sh"):
            for extra in ([], ["--dry-run", "--name", "two words"]):
                with self.subTest(script=script, extra=extra):
                    self.process.reset_mock()
                    self.process.return_value = subprocess.CompletedProcess([], 7)
                    item = {"kind": "add-on", "id": "custom", "script": script}
                    with mock.patch.object(manage, "find", return_value=item):
                        status, out, err = self.call(manage.main, ["install", "custom"] + extra)
                    self.assertEqual(status, 7)
                    self.assertEqual(err, "")
                    self.process.assert_called_once_with(
                        ["bash", str(ROOT / "addons" / "custom" / script)] + extra, cwd=ROOT,
                    )
                    self.python.assert_not_called()

    def test_scriptless_install_without_arguments_keeps_build_push_and_install(self):
        folder = ROOT / "addons" / "jewish-calendar"
        gradlew = str(folder / "gradlew.bat") if os.name == "nt" else "./gradlew"
        apk = folder / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
        status, out, err = self.call(manage.main, ["install", "jewish-calendar"])
        self.assertEqual(status, 0)
        self.assertEqual(err, "")
        self.assertIn("Success", out)
        self.assertEqual(self.process.call_args_list, [
            mock.call([gradlew, "assembleRelease"], cwd=folder),
            mock.call(
                ["fake-python", "-I", str(ROOT / "tools" / "tv.py"), "--push",
                 str(apk), "/data/local/tmp/addon.apk"], cwd=ROOT,
            ),
            mock.call(
                ["fake-python", "-I", str(ROOT / "tools" / "tv.py"),
                 "pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk"],
                capture_output=True, text=True,
            ),
        ])

    def test_scriptless_install_stops_at_build_or_push_failure(self):
        for codes in ([7], [0, 9]):
            with self.subTest(codes=codes):
                self.process.reset_mock()
                self.process.side_effect = [subprocess.CompletedProcess([], code) for code in codes]
                with mock.patch.object(manage, "tv", side_effect=AssertionError("TV was asked")) as tv:
                    status, out, err = self.call(manage.main, ["install", "jewish-calendar"])
                self.assertEqual(status, codes[-1])
                self.assertEqual(self.process.call_count, len(codes))
                self.assertEqual(err, "")
                tv.assert_not_called()

    def test_scriptless_install_reports_package_install_failure(self):
        self.process.side_effect = [
            subprocess.CompletedProcess([], 0),
            subprocess.CompletedProcess([], 0),
            subprocess.CompletedProcess([], 0, "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]\n", ""),
        ]
        status, out, err = self.call(manage.main, ["install", "jewish-calendar"])
        self.assertEqual(status, 1)
        self.assertIn("INSTALL_FAILED_VERSION_DOWNGRADE", out)
        self.assertEqual(err, "")


if __name__ == "__main__":
    unittest.main()

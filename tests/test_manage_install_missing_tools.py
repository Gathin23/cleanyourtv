"""install names a missing bash or gradlew and what to do about it, instead of a FileNotFoundError traceback."""
import contextlib
import io
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class MissingToolTest(unittest.TestCase):
    def install(self, name, error, platform=sys.platform, item=None):
        out, err = io.StringIO(), io.StringIO()
        patches = [
            mock.patch.object(sys, "platform", platform),
            mock.patch.object(manage.subprocess, "run", side_effect=error),
            mock.patch.object(manage, "tv", side_effect=AssertionError("TV was asked")),
            mock.patch.object(manage, "tv_python", side_effect=AssertionError("push was started")),
        ]
        if item:
            patches.append(mock.patch.object(manage, "find", return_value=item))
        with contextlib.ExitStack() as stack:
            for p in patches:
                stack.enter_context(p)
            stack.enter_context(contextlib.redirect_stdout(out))
            stack.enter_context(contextlib.redirect_stderr(err))
            status = manage.main(["install", name])
        return status, err.getvalue()

    def test_missing_bash_is_named_with_what_to_install(self):
        item = {"kind": "add-on", "id": "custom", "script": "install.sh"}
        for platform, hint in (("win32", "Git for Windows"), ("linux", "package manager")):
            with self.subTest(platform=platform):
                status, err = self.install("custom", FileNotFoundError(2, "No such file or directory"),
                                           platform, item)
                self.assertEqual(status, 1, err)
                self.assertIn("cannot start bash", err)
                self.assertIn("No such file or directory", err)
                self.assertIn("install.sh", err)
                self.assertIn(hint, err)
                self.assertIn("Nothing was built, pushed or installed.", err)
                self.assertNotIn("Traceback", err)

    def test_missing_gradlew_is_named_with_where_it_belongs(self):
        folder = ROOT / "addons" / "jewish-calendar"
        gradlew = str(folder / "gradlew.bat") if os.name == "nt" else "./gradlew"
        for error in (FileNotFoundError(2, "No such file or directory"), PermissionError(13, "Permission denied")):
            with self.subTest(error=type(error).__name__):
                status, err = self.install("jewish-calendar", error)
                self.assertEqual(status, 1, err)
                self.assertIn(f"cannot start {gradlew}", err)
                self.assertIn(error.strerror, err)
                self.assertIn("addons/jewish-calendar", err)
                self.assertIn("Nothing was built, pushed or installed.", err)


if __name__ == "__main__":
    unittest.main()

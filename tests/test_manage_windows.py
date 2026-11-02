"""Theme operations use the existing PowerShell script on Windows, without needing Bash."""
import contextlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import manage  # noqa: E402


class ThemeDispatchTest(unittest.TestCase):
    def install(self, platform, executables, extra=(), result=0):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(sys, "platform", platform), \
                mock.patch("shutil.which", side_effect=executables.get), \
                mock.patch.object(manage.subprocess, "run",
                                  return_value=subprocess.CompletedProcess([], result)) as runner, \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = manage.main(["install", "aurora", *extra])
        return code, runner, out.getvalue(), err.getvalue()

    def test_windows_installs_with_powershell_without_a_profile_or_policy_bypass(self):
        code, runner, _, _ = self.install("win32", {"pwsh": r"C:\PowerShell\pwsh.exe"})
        self.assertEqual(code, 0)
        runner.assert_called_once_with(
            [r"C:\PowerShell\pwsh.exe", "-NoProfile", "-NonInteractive", "-File",
             str(manage.TOOLS / "theme.ps1"), "-Theme", str(ROOT / "themes" / "aurora")],
            cwd=ROOT)

    def test_windows_can_use_the_bundled_windows_powershell(self):
        code, runner, _, _ = self.install("win32", {"powershell": r"C:\Windows\powershell.exe"})
        self.assertEqual(code, 0)
        self.assertEqual(runner.call_args.args[0][0], r"C:\Windows\powershell.exe")

    def test_unix_keeps_the_existing_bash_command(self):
        code, runner, _, _ = self.install("linux", {})
        self.assertEqual(code, 0)
        runner.assert_called_once_with(
            ["bash", str(manage.TOOLS / "theme.sh"), str(ROOT / "themes" / "aurora")], cwd=ROOT)

    def test_a_failed_installer_keeps_its_exit_status(self):
        code, _, _, _ = self.install("win32", {"pwsh": "pwsh"}, result=17)
        self.assertEqual(code, 17)

    def test_extra_theme_arguments_are_refused_before_any_script_runs(self):
        for platform in ("win32", "linux"):
            with self.subTest(platform=platform):
                code, runner, _, err = self.install(platform, {}, extra=["--reset"])
                self.assertEqual(code, 2)
                self.assertIn("--reset", err)
                runner.assert_not_called()

    def test_an_executable_that_cannot_start_is_reported_without_a_traceback(self):
        err = io.StringIO()
        with mock.patch.object(sys, "platform", "win32"), \
                mock.patch("shutil.which", return_value="pwsh"), \
                mock.patch.object(manage.subprocess, "run", side_effect=FileNotFoundError("gone")), \
                contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(err):
            self.assertEqual(manage.main(["install", "aurora"]), 1)
        self.assertIn("cannot start", err.getvalue())
        self.assertIn("gone", err.getvalue())

    def test_missing_powershell_does_not_rewrite_a_personal_theme(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / "config.json"
            original = '{"columns": 4}\n'
            config.write_text(original, encoding="utf-8")
            with mock.patch.object(sys, "platform", "win32"), \
                    mock.patch("shutil.which", return_value=None), \
                    mock.patch.object(manage.subprocess, "run") as runner, \
                    self.assertRaisesRegex(SystemExit, "PowerShell"):
                manage.main(["set", "columns", "6", "--theme", tmp])
            self.assertEqual(config.read_text(encoding="utf-8"), original)
            runner.assert_not_called()

    def test_failed_set_keeps_the_local_edit_and_explains_the_failed_push(self):
        with tempfile.TemporaryDirectory(prefix="theme with spaces ") as tmp:
            folder = Path(tmp).resolve()
            config = folder / "config.json"
            config.write_text('{"dock_band": true}\n', encoding="utf-8")
            out, err = io.StringIO(), io.StringIO()
            with mock.patch.object(sys, "platform", "win32"), \
                    mock.patch("shutil.which", return_value="pwsh"), \
                    mock.patch.object(manage.subprocess, "run",
                                      return_value=subprocess.CompletedProcess([], 17)) as runner, \
                    contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                code = manage.main(["set", "columns", "6", "--theme", str(folder)])
            self.assertEqual(code, 17)
            self.assertEqual(json.loads(config.read_text(encoding="utf-8")),
                             {"dock_band": True, "columns": 6})
            runner.assert_called_once_with(
                ["pwsh", "-NoProfile", "-NonInteractive", "-File",
                 str(manage.TOOLS / "theme.ps1"), "-Theme", str(folder)], cwd=ROOT)
            self.assertIn(str(config), err.getvalue())
            self.assertIn("locally", err.getvalue())
            self.assertIn("failed", err.getvalue())
            self.assertIn("17", err.getvalue())


class WindowsGuidanceTest(unittest.TestCase):
    def test_help_and_show_print_windows_commands(self):
        for args in (["--help"], ["show", "aurora"], ["show", "columns"], ["show", "weather"]):
            with self.subTest(args=args):
                out = io.StringIO()
                with mock.patch.object(sys, "platform", "win32"), contextlib.redirect_stdout(out):
                    self.assertEqual(manage.main(args), 0)
                text = out.getvalue()
                self.assertIn(r".\tools\venv\Scripts\python.exe", text)
                self.assertIn("$env:TV_HOST", text)
                self.assertNotIn("TV_HOST=<tv ip>", text)
                self.assertNotIn("tools/venv/bin/python", text)

    def test_a_missing_venv_names_the_windows_setup_commands(self):
        err = io.StringIO()
        with tempfile.TemporaryDirectory() as tmp, \
                mock.patch.object(manage, "TOOLS", Path(tmp)), \
                mock.patch.object(sys, "platform", "win32"), \
                contextlib.redirect_stderr(err), \
                self.assertRaises(SystemExit) as stop:
            manage.tv_python()
        self.assertEqual(stop.exception.code, 2)
        self.assertIn(r"tools\venv\Scripts\python.exe", err.getvalue())
        self.assertNotIn("bin/pip", err.getvalue())


@unittest.skipUnless(os.name == "nt", "real Windows PowerShell subprocesses")
class WindowsSubprocessTest(unittest.TestCase):
    def test_each_available_powershell_receives_the_folder_and_returns_failures(self):
        shells = [path for name in ("pwsh", "powershell") if (path := shutil.which(name))]
        self.assertTrue(shells, "Windows theme operations need PowerShell on PATH")
        with tempfile.TemporaryDirectory(prefix="manage scripts ") as tmp:
            tools = Path(tmp).resolve()
            theme = tools / "personal theme ' & $name"
            theme.mkdir()
            receipt = tools / "received.json"
            (tools / "theme.ps1").write_text(
                "param([string]$Theme)\n"
                "$data = @{ theme = $Theme; extra = @($args) } | ConvertTo-Json -Compress\n"
                "[System.IO.File]::WriteAllText($env:MANAGE_TEST_RECEIPT, $data)\n"
                "exit ([int]$env:MANAGE_TEST_EXIT)\n", encoding="utf-8")
            for shell in shells:
                for status in (0, 17):
                    with self.subTest(shell=shell, status=status), \
                            mock.patch.object(manage, "TOOLS", tools), \
                            mock.patch("shutil.which", return_value=shell), \
                            mock.patch.dict(os.environ, {"MANAGE_TEST_RECEIPT": str(receipt),
                                                         "MANAGE_TEST_EXIT": str(status)}), \
                            contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                        receipt.unlink(missing_ok=True)
                        self.assertEqual(manage.main(
                            ["set", "columns", "6", "--theme", str(theme)]), status)
                        self.assertEqual(json.loads(receipt.read_text(encoding="utf-8")),
                                         {"theme": str(theme), "extra": []})
                        self.assertEqual(json.loads((theme / "config.json").read_text(encoding="utf-8")),
                                         {"columns": 6})


if __name__ == "__main__":
    unittest.main()

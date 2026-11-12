"""`manage.py status` reads the launcher the TV reports, not the id this repo builds by default.

Some firmware only lets a package name it allowlists be Home, so `launcher/app/build.gradle` takes
`-PlauncherApplicationId` and the device notes send people there (a China Xiaomi set wants `com.ts.launcher`, a
Coocaa one `com.coocaa.tvhome`). The activity class stays `com.gathin.tvhome.MainActivity` under any id, so the
package answering HOME with that activity is the launcher - and the directory whose `config.json` holds the theme
that was pushed. `status` read the default id and its directory, so on such a set it answered `launcher: not
installed` about a launcher that is installed and `theme: none (the built-in config.xml)` about a theme that is
there (#3703).
"""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("manage_status_launcher", ROOT / "tools" / "manage.py")
manage = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(manage)

FORK = "com.ts.launcher"
FORK_COMPONENT = f"{FORK}/com.gathin.tvhome.MainActivity"
DEFAULT_COMPONENT = f"{manage.LAUNCHER_PKG}/com.gathin.tvhome.MainActivity"
STOCK_COMPONENT = "com.google.android.tvlauncher/.tvlauncher.MainActivity"


def finished(returncode, stdout="", stderr=""):
    return subprocess.CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr=stderr)


class LauncherPackageTest(unittest.TestCase):
    def package(self, claims):
        with mock.patch.object(manage, "tv", return_value=claims) as tv:
            answer = manage.launcher_package()
        tv.assert_called_once_with(manage.CLAIM_QUERY, quiet=True)
        return answer

    def test_the_id_that_wears_the_launcher_activity_is_the_launcher(self):
        # A set answers HOME with its stock launcher first and this launcher's build second; only the component
        # whose class is the launcher's own names the package status has to read.
        self.assertEqual(self.package(f"{STOCK_COMPONENT}\n{FORK_COMPONENT}\n"), FORK)

    def test_the_default_build_is_still_the_answer(self):
        self.assertEqual(self.package(f"{STOCK_COMPONENT}\n{DEFAULT_COMPONENT}\n"), manage.LAUNCHER_PKG)

    def test_a_tv_that_answers_nothing_keeps_the_default_id(self):
        for claims in ("", "\n", "no activities found\n"):
            with self.subTest(claims=claims):
                self.assertEqual(self.package(claims), manage.LAUNCHER_PKG)

    def test_another_apps_main_activity_is_not_this_launcher(self):
        # `com.google.android.tvlauncher/.tvlauncher.MainActivity` ends in MainActivity too; the namespace is what
        # says whose it is.
        for claims in (f"{STOCK_COMPONENT}\n",
                       "com.android.tv.settings/com.android.tv.settings.FallbackHome\n",
                       f"{FORK}/com.gathin.tvhome.MainActivityExtra\n"):
            with self.subTest(claims=claims):
                self.assertEqual(self.package(claims), manage.LAUNCHER_PKG)

    def test_the_line_is_read_through_the_cr_a_windows_adb_sends(self):
        self.assertEqual(self.package(f"{STOCK_COMPONENT}\r\n{FORK_COMPONENT}\r\n"), FORK)

    def test_a_package_name_of_the_right_shape_is_required(self):
        # A component the shell printed with a path or a space in it would become a directory name and a `cat`
        # argument, and the run would report a theme it never read.
        for component in (f"../{FORK}/{manage.LAUNCHER_ACTIVITY}",
                          f"com .ts.launcher/{manage.LAUNCHER_ACTIVITY}",
                          f"/{manage.LAUNCHER_ACTIVITY}"):
            with self.subTest(component=component):
                self.assertEqual(self.package(component + "\n"), manage.LAUNCHER_PKG)


class QuietTvTest(unittest.TestCase):
    """The HOME query is one a firmware may not have: an Android 8 `cmd` answers nothing, and that is not a stop."""

    def ask(self, done, quiet):
        err = io.StringIO()
        with mock.patch.object(manage.subprocess, "run", return_value=done), \
                mock.patch.dict("os.environ", {"TV_HOST": "tv.local"}), \
                mock.patch.object(manage, "tv_python", return_value="/venv/bin/python"), \
                contextlib.redirect_stderr(err):
            return manage.tv(manage.CLAIM_QUERY, quiet=quiet), err.getvalue()

    def test_a_failed_quiet_query_answers_nothing_and_says_nothing(self):
        self.assertEqual(self.ask(finished(1, stderr="cmd: not found\n"), True), ("", ""))

    def test_a_failed_quiet_query_over_a_tv_it_never_reached_is_quiet_too(self):
        self.assertEqual(self.ask(finished(2, stderr="could not reach tv.local:5555\n"), True), ("", ""))

    def test_an_answer_that_arrived_is_returned(self):
        answer, said = self.ask(finished(0, stdout=FORK_COMPONENT + "\n"), True)
        self.assertEqual((answer, said), (FORK_COMPONENT + "\n", ""))

    def test_a_failed_query_that_was_asked_for_still_stops_the_run(self):
        with self.assertRaises(SystemExit) as caught:
            self.ask(finished(2, stderr="could not reach tv.local:5555\n"), False)
        self.assertEqual(caught.exception.code, 2)


class StatusReadsThatLauncherTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.pushed = json.dumps({"columns": 5, "greeting_name": "Room"})
        theme = self.root / "themes" / "aerial" / "config.json"
        theme.parent.mkdir(parents=True)
        theme.write_text(self.pushed, encoding="utf-8")

    def status(self, claims, installed, configs):
        calls = []

        def fake_tv(command, quiet=False):
            calls.append((command, quiet))
            if command.startswith("cmd package query-activities"):
                return claims
            return configs.get(command, "")

        out = io.StringIO()
        with mock.patch.object(manage, "ROOT", self.root), \
                mock.patch.object(manage, "installed_packages", return_value=set(installed)), \
                mock.patch.object(manage, "addons", return_value=[]), \
                mock.patch.object(manage, "themes", return_value=[{"id": "aerial"}]), \
                mock.patch.object(manage, "tv", fake_tv), contextlib.redirect_stdout(out):
            code = manage.cmd_status()
        return code, out.getvalue(), calls

    def config_command(self, package):
        return f"cat /sdcard/Android/data/{package}/files/config.json 2>/dev/null"

    def test_a_fork_build_is_reported_and_its_directory_read(self):
        fork_cat = self.config_command(FORK)
        code, out, calls = self.status(f"{STOCK_COMPONENT}\n{FORK_COMPONENT}\n", {FORK}, {fork_cat: self.pushed})
        self.assertEqual(code, 0)
        self.assertIn(f"launcher: installed ({FORK})", out)
        self.assertIn("theme: aerial", out)
        self.assertIn((fork_cat, False), calls)
        # The default directory is not asked about: it is the directory a build under another id never writes.
        self.assertNotIn((self.config_command(manage.LAUNCHER_PKG), False), calls)

    def test_the_default_build_reads_its_own_directory_and_names_no_id(self):
        default_cat = self.config_command(manage.LAUNCHER_PKG)
        code, out, calls = self.status(DEFAULT_COMPONENT + "\n", {manage.LAUNCHER_PKG},
                                       {default_cat: self.pushed})
        self.assertEqual(code, 0)
        self.assertIn("launcher: installed\n", out)
        self.assertIn("theme: aerial", out)
        self.assertIn((default_cat, False), calls)

    def test_a_fork_build_that_is_not_there_says_so_with_its_id(self):
        code, out, _ = self.status(f"{FORK_COMPONENT}\n", set(), {})
        self.assertEqual(code, 0)
        self.assertIn(f"launcher: not installed ({FORK})", out)
        self.assertIn("theme: none (the built-in config.xml)", out)

    def test_a_tv_that_cannot_answer_the_query_is_judged_on_the_default_id(self):
        code, out, calls = self.status("", {manage.LAUNCHER_PKG},
                                       {self.config_command(manage.LAUNCHER_PKG): self.pushed})
        self.assertEqual(code, 0)
        self.assertIn("launcher: installed\n", out)
        self.assertIn("theme: aerial", out)
        self.assertIn((manage.CLAIM_QUERY, True), calls)


class OneHomeQueryInTheTreeTest(unittest.TestCase):
    """A second spelling of the query would drift the day either side is edited."""

    def test_the_query_is_disable_sh_own(self):
        text = (ROOT / "tools" / "disable.sh").read_text(encoding="utf-8")
        self.assertIn(manage.CLAIM_QUERY, text)

    def test_the_activity_is_the_launchers_own_main_activity(self):
        manifest = (ROOT / "launcher" / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn('android:name=".MainActivity"', manifest)
        java = ROOT / "launcher" / "app" / "src" / "main" / "java" / "com" / "gathin" / "tvhome" / "MainActivity.java"
        self.assertTrue(java.is_file())
        self.assertEqual(manage.LAUNCHER_ACTIVITY, "com.gathin.tvhome.MainActivity")
        self.assertEqual(manage.LAUNCHER_ACTIVITY.rsplit(".", 1)[0], "com.gathin.tvhome")


if __name__ == "__main__":
    unittest.main()

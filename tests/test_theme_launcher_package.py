"""The theme tools name the launcher by the id the TV reports, not by the default build's (#3703).

`launcher/app/build.gradle` takes `-PlauncherApplicationId` because some firmware only lets a package name it
allowlists be Home (Xiaomi China wants `com.ts.launcher`, ColorOS TV `com.oneplus.tv.launcher2`), and
`docs/devices/xiaomi-mitv-mftr0-debloat-home.md` is the page that sends people there. On such a set every
path `tools/theme.sh` wrote was `/sdcard/Android/data/com.gathin.tvhome/files`, the provider it opened was
`content://com.gathin.tvhome.theme` and the package `tools/theme_restart.py` restarted was the default one, so
the theme landed in a directory no launcher reads and the run ended "Theme restart failed: TV refused the
command" - a push that had in fact worked being reported as a refusal.

The activity class does not move with the id: it is `<applicationId>/com.gathin.tvhome.MainActivity`. So the TV
is asked which package wears it (`cmd package query-activities --brief -a android.intent.action.MAIN -c
android.intent.category.HOME`, the query `tools/disable.sh` already reads HOME claimants with), the answer is
passed to the helper in `LAUNCHER_PACKAGE`, and `com.gathin.tvhome` stays the answer for a TV that says nothing
- the default build is what almost every reader has. `LAUNCHER_PACKAGE` also names the id by hand, for an
Android 8 firmware without `cmd package query-activities` or a launcher the set will not start yet.

The shell half runs the real `tools/theme.sh` against a stub `tools/tv.py`, so a call that names the wrong id
is visible in the log rather than in an assertion about a string. The helper half calls `theme_restart.restart`
on a fake device that answers like the one in tests/test_theme_restart.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from tools import theme_restart as restart

TOOLS = Path(__file__).resolve().parents[1] / "tools"
DEFAULT = "com.gathin.tvhome"
RENAMED = "com.ts.launcher"
STOCK = "com.mitv.tvhome/.MainActivityUserMode"
OURS = RENAMED + "/com.gathin.tvhome.MainActivity"

# Every call theme.sh makes reaches this file, and so does the id it exported, so a test can tell which package
# the run named without a TV. The claim query is the only call that answers.
FAKE_TV = '''\
import os
import sys
arguments = " ".join(sys.argv[1:])
with open(os.environ["FAKE_LOG"], "a") as log:
    if arguments:
        log.write("call " + arguments + "\\n")
    log.write("env " + os.environ.get("LAUNCHER_PACKAGE", "<unset>") + "\\n")
if "query-activities" in arguments:
    sys.stdout.write(os.environ.get("FAKE_CLAIMS", ""))
sys.exit(0)
'''


class ShellSideTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        for name in ("theme.sh", "theme_restart.py"):
            shutil.copy2(TOOLS / name, self.tools / name)
        (self.tools / "tv.py").write_text(FAKE_TV)
        venv = self.tools / "venv" / "bin"
        venv.mkdir(parents=True)
        try:
            (venv / "python").symlink_to(sys.executable)
        except OSError:
            if os.name != "nt":
                raise
        self.theme = self.base / "my-theme"
        self.theme.mkdir()
        (self.theme / "config.json").write_text('{"background_color": "#000000"}\n', encoding="utf-8")
        self.log = self.base / "tv-calls.log"

    def run_theme(self, *args, **env_overrides):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "LAUNCHER_PACKAGE")}
        env.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log)})
        env.update(env_overrides)
        for key, value in env_overrides.items():
            if value is None:
                env.pop(key, None)
        return subprocess.run(["bash", str(self.tools / "theme.sh"), *args], env=env,
                              capture_output=True, text=True, timeout=120)

    def calls(self):
        if not self.log.exists():
            return []
        return self.log.read_text().splitlines()

    def device_calls(self):
        return [line[5:] for line in self.calls() if line.startswith("call ")]

    def exported(self):
        """The launcher id the run handed theme_restart.py, as the stub saw it."""
        seen = [line[4:] for line in self.calls() if line.startswith("env ")]
        return seen[-1] if seen else None

    def assert_names(self, result, package):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        pushed = "\n".join(self.device_calls())
        self.assertIn("/sdcard/Android/data/%s/files" % package, pushed,
                      "the theme has to be written where this launcher reads it")
        self.assertIn("content://%s.theme" % package, pushed,
                      "the provider is com.<applicationId>.theme")
        self.assertEqual(package, self.exported(),
                         "theme_restart.py restarts its key helpers, so it has to be told the same id")

    def test_the_launcher_is_read_off_the_tv(self):
        # The defect: this pushed to com.gathin.tvhome on every set, and the answer was already in the query.
        result = self.run_theme(str(self.theme), FAKE_CLAIMS=STOCK + "\n" + OURS + "\n")
        self.assert_names(result, RENAMED)
        self.assertIn("query-activities", "\n".join(self.device_calls()),
                      "the id comes from the TV, not from a guess")

    def test_the_stock_launcher_alone_keeps_the_default(self):
        # A set where the launcher is not installed yet: the stock launcher answers the query and nothing wears
        # the launcher's MainActivity, so the default build's id is the answer.
        result = self.run_theme(str(self.theme), FAKE_CLAIMS=STOCK + "\n")
        self.assert_names(result, DEFAULT)

    def test_a_firmware_that_answers_nothing_keeps_the_default(self):
        # An Android 8 firmware without `cmd package query-activities`, or a `cmd` that prints nothing: the push
        # is not the place to discover that, and the default id is still what the script sends.
        result = self.run_theme(str(self.theme), FAKE_CLAIMS="")
        self.assert_names(result, DEFAULT)

    def test_launcher_package_names_the_id_without_asking(self):
        # The hand-named id for a set that cannot answer: it is used as typed, and no query is spent on it.
        result = self.run_theme(str(self.theme), LAUNCHER_PACKAGE="com.oneplus.tv.launcher2",
                                FAKE_CLAIMS=OURS + "\n")
        self.assert_names(result, "com.oneplus.tv.launcher2")
        self.assertNotIn("query-activities", "\n".join(self.device_calls()),
                         "an id given by hand is not the TV's to overrule")

    def test_a_launcher_package_that_is_not_an_id_sends_nothing(self):
        result = self.run_theme(str(self.theme), LAUNCHER_PACKAGE="com/ts.launcher")
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertEqual("", result.stdout)
        self.assertIn("LAUNCHER_PACKAGE takes the launcher's application id", result.stderr)
        self.assertIn("Nothing was sent to the TV.", result.stderr)
        self.assertEqual([], self.calls(), "a run that stopped at its command line asked the TV for something")

    def test_a_reset_names_the_same_id(self):
        result = self.run_theme("--reset", FAKE_CLAIMS=STOCK + "\n" + OURS + "\n")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("reset", result.stdout)
        self.assertIn("/sdcard/Android/data/%s/files" % RENAMED, "\n".join(self.device_calls()))
        self.assertEqual(RENAMED, self.exported())

    def test_a_theme_folder_the_script_cannot_read_still_sends_nothing(self):
        # The id is read just before the first command, not before the refusals: these runs answer 2 with an
        # empty log, as tests/test_theme_exit_status.py holds them to.
        (self.theme / "config.json").unlink()
        result = self.run_theme(str(self.theme), FAKE_CLAIMS=OURS + "\n")
        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("no config.json in", result.stderr)
        self.assertEqual([], self.calls())


class RestartSideTest(unittest.TestCase):
    """theme_restart.py restarts the launcher the callers named, in both of its start shapes."""

    def fake(self, home=STOCK):
        class Dev:
            def __init__(self):
                self.calls = []
                self.home = home

            def shell(self, command, read_timeout_s=None):
                self.calls.append(command.split("; rc=$?;")[0])
                cmd = command.split("; rc=$?;")[0]
                if cmd.startswith("am get-current-user"):
                    out = "0"
                elif cmd.startswith("settings"):
                    out = "null" if restart.SERVICES in cmd else "1"
                elif cmd.startswith("cmd package resolve-activity"):
                    out = self.home
                else:
                    out = ""
                return out + "\n__CYT_THEME_RC__0\n"

            def close(self):
                pass

        return Dev()

    def restart_with(self, package=None, home=STOCK):
        dev = self.fake(home)
        environment = {k: v for k, v in os.environ.items() if k != "LAUNCHER_PACKAGE"}
        if package is not None:
            environment["LAUNCHER_PACKAGE"] = package
        with patch.dict(os.environ, environment, clear=True):
            restart.restart(dev, lambda _seconds: None)
        return dev.calls

    def test_package_is_the_one_the_caller_read(self):
        with patch.dict(os.environ, {"LAUNCHER_PACKAGE": RENAMED}, clear=False):
            self.assertEqual(RENAMED, restart.package())
        with patch.dict(os.environ, {"LAUNCHER_PACKAGE": "com/ts"}, clear=False):
            self.assertEqual(DEFAULT, restart.package(), "a value that is not a package name is not restarted")
        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(DEFAULT, restart.package(), "a run by hand keeps the default build")

    def test_the_restart_names_that_id(self):
        calls = "\n".join(self.restart_with(package=RENAMED))
        self.assertIn("am force-stop --user 0 " + RENAMED, calls)
        self.assertIn("am start --user 0 -n " + RENAMED + "/.MainActivity", calls)
        self.assertNotIn(DEFAULT, calls, "no command may name the build this set does not have")

    def test_a_launcher_that_is_home_is_started_with_the_intent(self):
        calls = "\n".join(self.restart_with(package=RENAMED,
                                            home=RENAMED + "/com.gathin.tvhome.MainActivity"))
        self.assertIn("am start --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME", calls)

    def test_without_a_caller_the_default_is_restarted(self):
        calls = "\n".join(self.restart_with())
        self.assertIn("am force-stop --user 0 " + DEFAULT, calls)


if __name__ == "__main__":
    unittest.main()

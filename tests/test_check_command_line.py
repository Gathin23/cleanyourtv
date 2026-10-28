"""tools/check.py reads its command line before it reads a list or a TV.

README.md hands an owner a weekly job that runs `check.py <list>` and says "it prints what drifted and
exits 1 when anything did", so on that page exit 1 means "your TV drifted". A command line that cannot
run must therefore not come back as 1: `check.py --help` - the first thing a reader types at a script
they do not know - was taken as a list named `--help` and ended in a FileNotFoundError traceback with
exit 1, and a flag typed where the launcher package goes reached set-home-activity as a package name.

The same goes for what the command line points at. A list check.py cannot use, a launcher package the
TV does not have and a TV that answers no packages are exit 2 too. The launcher one was worse than exit
1: a typo there turned the Home check off, so Home on the wrong launcher came back as "nothing drifted",
exit 0, and a weekly job stayed quiet.

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

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_drift_check import FAKE_TV  # noqa: E402  (the simulated TV check.py already runs against)

ROOT = Path(__file__).resolve().parents[1]
STOCK = "com.google.android.tvlauncher"
CUSTOM = "com.gathin.tvhome"
# Google TV's stock launcher, which the list leaves out: build-and-install.sh disables it instead.
LAUNCHERX = "com.google.android.apps.tv.launcherx"


def collapsed(text):
    return " ".join(text.split())


class CheckCommandLineTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        for name in ("check.py", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, tools / name)
        (tools / "tv.py").write_text(FAKE_TV)

        self.home = self.base / "home"
        self.home.mkdir()
        self.state_path = self.base / "tv-state.json"
        self.list_path = self.base / "debloat.txt"
        self.list_path.write_text("# My TV\ncom.example.music\ncom.example.store\n" + STOCK + "\n")

    def tv(self, **overrides):
        state = {
            "installed": ["com.example.music", "com.example.store", STOCK, CUSTOM],
            "disabled": ["com.example.music", "com.example.store", STOCK],
            "home": f"{CUSTOM}/.MainActivity",
            "home_activities": [f"{CUSTOM}/.MainActivity", f"{STOCK}/.MainActivity"],
            "pinned": [],
            "commands": [],
            "unreachable": False,
            "wifi": "",
            "input_drivers": [],
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def run_check(self, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        return subprocess.run(
            [sys.executable, str(self.repo / "tools" / "check.py"), *args],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=120,
        )

    def sent(self):
        with open(self.state_path) as f:
            return json.load(f)["commands"]

    def assert_untouched(self, result):
        """Nothing reached the TV, and the run did not die on an exception."""
        self.assertEqual(self.sent(), [])
        self.assertNotIn("Traceback", result.stderr)

    def test_help_prints_the_usage_and_leaves_the_tv_alone(self):
        self.tv()
        result = self.run_check("--help")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("usage: check.py <debloat list> [launcher package]", result.stdout)
        self.assertIn("check.py --fix <debloat list> [launcher package]", result.stdout)
        self.assertIn("check.py --help", result.stdout)
        self.assertEqual(result.stderr, "")
        self.assert_untouched(result)

    def test_help_exits_zero_rather_than_the_drift_code(self):
        # The whole point: 1 is what a weekly job reports as "the TV drifted".
        self.tv()
        self.assertNotEqual(self.run_check("--help").returncode, 1)

    def test_short_help_is_the_same_note(self):
        self.tv()
        long, short = self.run_check("--help"), self.run_check("-h")
        self.assertEqual(short.returncode, 0, short.stderr)
        self.assertEqual(short.stdout, long.stdout)
        self.assert_untouched(short)

    def test_help_wins_over_the_fix_flag(self):
        self.tv()
        result = self.run_check("--fix", "--help")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("usage: check.py", result.stdout)
        self.assert_untouched(result)

    def test_help_after_the_list_still_prints_the_note(self):
        self.tv()
        result = self.run_check(str(self.list_path), "-h")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("usage: check.py", result.stdout)
        self.assert_untouched(result)

    def test_unknown_flag_is_refused_by_name(self):
        self.tv()
        result = self.run_check("--fx")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("no such flag: --fx", result.stderr)
        self.assertIn("usage: check.py", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assert_untouched(result)

    def test_unknown_flag_where_the_launcher_goes_never_reaches_the_tv(self):
        # Second position used to be taken as the launcher package: --fix would have run a
        # set-home-activity for a package called --fx.
        self.tv(disabled=["com.example.music", "com.example.store"])
        result = self.run_check("--fix", str(self.list_path), "--fx")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("no such flag: --fx", result.stderr)
        self.assert_untouched(result)

    def test_a_single_dash_word_is_refused_too(self):
        self.tv()
        result = self.run_check(str(self.list_path), "-x")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("no such flag: -x", result.stderr)
        self.assert_untouched(result)

    def test_a_list_that_is_not_there_is_named_not_traced(self):
        self.tv()
        missing = self.base / "typo.txt"
        result = self.run_check(str(missing))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("could not read", result.stderr)
        self.assertIn("typo.txt", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(self.sent(), [])

    def test_a_directory_is_refused_like_a_missing_file(self):
        self.tv()
        result = self.run_check(str(self.base))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("could not read", result.stderr)
        # POSIX refuses a directory outright ("Is a directory"); Windows reports the same refusal as
        # a permission error, since opening a directory is not a read that can start there.
        self.assertTrue("Is a directory" in result.stderr or "denied" in result.stderr.lower(),
                        result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(self.sent(), [])

    def test_a_list_with_every_line_commented_out_is_not_drift(self):
        # CONTRIBUTING has untested entries commented out, so a list can be all comments.
        self.list_path.write_text("# My TV\n# com.example.music   untested\n")
        self.tv()
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("no package names in this list", result.stderr)
        self.assertIn("Nothing was checked or changed", result.stderr)
        self.assert_untouched(result)

    def test_a_line_that_is_no_package_name_is_not_drift(self):
        # A curly quote from a word processor, in a report-only run as the weekly job makes it.
        self.list_path.write_text("# My TV\ncom.example.music\n“com.example.store”\n")
        self.tv()
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("debloat.txt:3: not a package name", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assert_untouched(result)

    def drifted_home(self, **overrides):
        """A firmware update gave Home back to launcherx, which the list does not name."""
        state = {"installed": ["com.example.music", "com.example.store", STOCK, CUSTOM, LAUNCHERX],
                 "home": f"{LAUNCHERX}/.home.HomeActivity",
                 "home_activities": [f"{LAUNCHERX}/.home.HomeActivity", f"{CUSTOM}/.MainActivity"]}
        self.tv(**dict(state, **overrides))

    def test_a_launcher_that_is_not_installed_is_refused_not_ignored(self):
        self.drifted_home()
        named = self.run_check(str(self.list_path), CUSTOM)
        self.assertEqual(named.returncode, 1, named.stdout + named.stderr)
        self.assertIn(f"Home is {LAUNCHERX}/.home.HomeActivity, not {CUSTOM}", named.stdout)
        # One letter short: it used to turn the Home check off and report "nothing drifted", exit 0.
        typo = self.run_check(str(self.list_path), "com.gathin.tvhom")
        self.assertEqual(typo.returncode, 2, typo.stdout + typo.stderr)
        self.assertIn("com.gathin.tvhom is not installed on this TV", typo.stderr)
        self.assertNotIn("nothing drifted", typo.stdout)
        self.assert_untouched(typo)

    def test_fix_with_a_launcher_that_is_not_installed_changes_nothing(self):
        self.drifted_home()
        result = self.run_check("--fix", str(self.list_path), "com.gathin.tvhom")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("is not installed on this TV", result.stderr)
        self.assert_untouched(result)

    def test_a_home_activity_names_the_package_to_pass(self):
        # "Home is <package>/<activity>" is how the report prints Home, so that is what gets copied.
        # Refused before the TV is reached: an unreachable TV would say so.
        self.drifted_home(unreachable=True)
        for args in ((str(self.list_path), f"{CUSTOM}/.MainActivity"),
                     ("--fix", str(self.list_path), f"{CUSTOM}/.MainActivity")):
            with self.subTest(args=args):
                result = self.run_check(*args)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertIn(f"is a Home activity, not a package: pass {CUSTOM}", result.stderr)
                self.assertIn("usage: check.py", result.stderr)
                self.assertNotIn("could not reach the TV", result.stderr)
                self.assert_untouched(result)

    def test_a_launcher_with_shell_syntax_never_reaches_the_tv(self):
        self.drifted_home()
        result = self.run_check("--fix", str(self.list_path), f"{CUSTOM};reboot")
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn(f"not a package name: '{CUSTOM};reboot'", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assert_untouched(result)

    def test_an_installed_stock_launcher_can_still_be_named(self):
        # The stock_home_redirect case README.md gives: the stock launcher stays Home on purpose.
        self.list_path.write_text("# My TV\ncom.example.music\ncom.example.store\n")
        self.drifted_home()
        result = self.run_check(str(self.list_path), LAUNCHERX)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"nothing drifted: 2 listed packages are still disabled and Home is {LAUNCHERX}/",
                      result.stdout)

    def test_a_tv_that_answers_no_packages_is_not_drift(self):
        self.tv(installed=[], disabled=[])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("the TV returned no packages", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assert_untouched(result)

    def test_no_argument_still_exits_2_with_the_usage(self):
        self.tv()
        result = self.run_check()
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("usage: check.py", result.stderr)
        self.assert_untouched(result)

    def test_the_fix_flag_still_reaches_the_tv(self):
        # The guard must not have eaten --fix: one package came back, --fix disables it again.
        self.tv(disabled=[STOCK])
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = self.sent()
        self.assertIn("pm disable-user --user 0 com.example.music", sent)
        self.assertIn("pm disable-user --user 0 com.example.store", sent)

    def test_the_usage_says_what_the_docstring_says(self):
        # One text, two places: the note a reader gets from --help is the one at the top of the file.
        source = (ROOT / "tools" / "check.py").read_text()
        docstring, usage = source.split('"""', 2)[1], source.split("USAGE = (", 1)[1]
        for fragment in ("check.py <debloat list> [launcher package]",
                         "check.py --fix <debloat list> [launcher package]"):
            self.assertIn(fragment, collapsed(docstring))
            self.assertIn(fragment, collapsed(usage))


if __name__ == "__main__":
    unittest.main()

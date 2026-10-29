"""With `stock_home_redirect`, the docs tell check.py which launcher is meant to be on Home.

In the redirect setup the stock launcher stays enabled and stays Home on purpose, and HomeKeyService
sends its home screen on to this launcher: the Chromecast with Google TV 4K (sabrina) runs that way
with launcherx and setupwraith kept for casting, and so does the Acer AR55AR2851UDPRO, where
`set-home-activity` does not stick. tools/check.py expects com.gathin.tvhome on Home whenever it is
installed, so on those TVs every run reported Home as drift ("Home is
com.google.android.apps.tv.launcherx/.home.HomeActivity, not com.gathin.tvhome", exit 1), and
`--fix` asked for a Home switch the TV does not keep. Naming the stock launcher as check.py's second
argument makes the same run come back clean. The README mentioned that argument only for forks.

The sabrina folder has two lists, and only debloat-keep-cast.txt matches the redirect setup: its
debloat.txt disables setupwraith and the screensaver pair (dreamx, backdrop), which this setup keeps,
and goes with the install script disabling launcherx. So the README names the lists by file instead
of `examples/<your tv>/debloat.txt`: filled in for the sabrina, that placeholder reported all three
as back on every run, and `--fix` disabled them.

These tests pin that the "After a firmware update" page (docs/firmware-updates.md, moved out of the
README, which links it), the sabrina list's "Home by
redirect" note and the keep-cast list's Check line carry the argument with the right list, and then
run the real check.py against the fake TV from tests/test_drift_check.py, set up the way the sabrina
answered on UTTC.250917.004, so the command the docs hand out is checked against the tool instead of
trusted.

What counts as a command: a line with no backticks on it, as in tests/test_reboot_docs.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tests"))
import recovery  # noqa: E402
from test_drift_check import FAKE_TV  # noqa: E402

README = ROOT / "docs" / "firmware-updates.md"  # the README's "After a firmware update" page
FOLDER = ROOT / "examples" / "google-chromecast-gtv-4k"
DEBLOAT = FOLDER / "debloat.txt"
KEEP_CAST = FOLDER / "debloat-keep-cast.txt"
ACER = ROOT / "examples" / "acer-ar55ar2851udpro"
ACER_LIST = ACER / "debloat.txt"
STOCK = "com.google.android.apps.tv.launcherx"
CUSTOM = "com.gathin.tvhome"
SETUPWRAITH = "com.google.android.tungsten.setupwraith"
# `tools/check.py <list> [launcher]`, with the list's path as the docs write it (`<your tv>` included).
CHECK = re.compile(r"tools/check\.py\s+(?P<list>examples/.+?\.txt)(?:\s+(?P<launcher>[A-Za-z_][\w.]*))?\s*$")


def firmware_section():
    """docs/firmware-updates.md, the README's "After a firmware update" page, whole."""
    text = README.read_text(encoding="utf-8")
    if not text.startswith("# After a firmware update\n"):
        return None
    return text


def check_commands(lines):
    """(list, launcher or None) for every check.py command in these lines, prose left out."""
    found = []
    for line in lines:
        if "`" in line or "tools/check.py" not in line:
            continue
        match = CHECK.search(line)
        if match:
            found.append((match.group("list"), match.group("launcher")))
    return found


def readme_commands_with_a_launcher():
    return [c for c in check_commands((firmware_section() or "").splitlines()) if c[1]]


def paragraph_starting(section, words):
    """The section's paragraph that starts with these words, wrapped lines joined."""
    for paragraph in section.split("\n\n"):
        if paragraph.startswith(words):
            return " ".join(paragraph.split())
    return ""


def relative(path):
    return path.relative_to(ROOT).as_posix()


def header(path):
    return [line for line in path.read_text(encoding="utf-8").splitlines() if line.startswith("#")]


def keep_cast_check_line():
    lines = [line for line in header(KEEP_CAST) if line.startswith("# Check:")]
    return lines[0] if len(lines) == 1 else None


def redirect_note():
    """The sabrina list's "Home by redirect" bullet, wrapped lines joined."""
    lines, inside = [], False
    for line in header(DEBLOAT):
        if line.startswith("# - Home by redirect:"):
            inside = True
        elif inside and not line.startswith("#   "):
            break
        if inside:
            lines.append(line.lstrip("#").strip())
    return " ".join(lines)


class DocsNameTheLauncherTest(unittest.TestCase):
    def test_the_readme_section_gives_the_redirect_its_argument(self):
        section = firmware_section()
        self.assertIsNotNone(section, "README has no 'After a firmware update' section")
        self.assertIn("`stock_home_redirect`", section)
        # By file, not `examples/<your tv>/debloat.txt`: the sabrina's debloat.txt is the list without
        # the redirect, and the Acer's stock launcher is not launcherx.
        acer_launcher = json.loads((ACER / "config.json").read_text(encoding="utf-8"))["stock_home_redirect"]
        self.assertEqual([(relative(KEEP_CAST), STOCK), (relative(ACER_LIST), acer_launcher)],
                         readme_commands_with_a_launcher(),
                         "the section should show check.py with the stock launcher as the second "
                         "argument for the sabrina's keep-cast list and the Acer's list, by name")

    def test_the_readme_says_the_sabrina_debloat_txt_takes_no_argument(self):
        # The folder's other list goes with the install script disabling launcherx, so launcherx on
        # Home is the drift check.py is there to report, and the argument would hide it.
        section = firmware_section() or ""
        self.assertIn(KEEP_CAST.name, section)
        paragraph = paragraph_starting(section, "Leave the argument out")
        self.assertIn("Chromecast", paragraph)
        self.assertIn(f"`{DEBLOAT.name}`", paragraph)

    def test_the_keep_cast_list_checks_itself_with_launcherx(self):
        line = keep_cast_check_line()
        self.assertIsNotNone(line, f"{KEEP_CAST.name} should have one '# Check:' line in its header")
        self.assertEqual([(relative(KEEP_CAST), STOCK)], check_commands([line.split(":", 1)[1]]))

    def test_the_launcher_it_names_is_not_one_the_list_disables(self):
        # check.py --fix switches Home to the package it is given before it disables what came back,
        # so a launcher the list also names would get Home and then lose it in the same run.
        self.assertNotIn(STOCK, recovery.read_list(KEEP_CAST))
        for name, launcher in readme_commands_with_a_launcher():
            with self.subTest(list=name):
                self.assertTrue((ROOT / name).is_file(), f"the README names {name}, which is not in the repo")
                self.assertNotIn(launcher, recovery.read_list(ROOT / name))

    def test_the_redirect_note_points_at_the_argument(self):
        note = redirect_note()
        self.assertIn("stock_home_redirect", note)
        for needed in ("tools/check.py", "second argument", KEEP_CAST.name):
            self.assertIn(needed, note, f"the 'Home by redirect' note in {DEBLOAT.name} should name {needed}")


class TheDocumentedCommandOnTheRedirectTvTest(unittest.TestCase):
    """The real check.py against a fake sabrina with the keep-cast list applied and the redirect on."""

    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        base = Path(tmp.name)
        self.repo = base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        for name in ("check.py", "recovery.py"):
            shutil.copy2(ROOT / "tools" / name, tools / name)
        (tools / "tv.py").write_text(FAKE_TV)
        self.home = base / "home"
        self.home.mkdir()
        self.state_path = base / "tv-state.json"
        self.listed = recovery.read_list(KEEP_CAST)
        self.kept = [name for name in recovery.read_list(DEBLOAT) if name not in self.listed]

    def tv(self, launcher_installed=True):
        # Home and the HOME claimants in the order the sabrina listed them (resolve-activity and
        # query-activities), with this launcher installed and HomeKeyService doing the redirect.
        # Everything debloat.txt names is installed, as on the sabrina; the keep-cast list is what
        # is disabled, and setupwraith and the screensaver pair stay on.
        claimants = [f"{STOCK}/.home.HomeActivity", f"{SETUPWRAITH}/.RecoveryActivity",
                     f"{CUSTOM}/.MainActivity", "com.android.tv.settings/.system.FallbackHome"]
        installed = self.listed + self.kept + [STOCK, CUSTOM, "com.android.tv.settings"]
        if not launcher_installed:
            claimants.remove(f"{CUSTOM}/.MainActivity")
            installed.remove(CUSTOM)
        self.state_path.write_text(json.dumps({
            "installed": installed, "disabled": list(self.listed),
            "home": f"{STOCK}/.home.HomeActivity", "home_activities": claimants,
            "pinned": [], "commands": [], "unreachable": False, "wifi": "", "input_drivers": [],
        }))

    def run_check(self, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", HOME=str(self.home),
                   FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.repo / "tools" / "check.py"), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True, timeout=120)

    def sent(self):
        return json.loads(self.state_path.read_text())["commands"]

    def documented_commands(self):
        """(list, launcher) as the README and the keep-cast Check line hand them to a sabrina owner.

        `<your tv>` is filled in with the sabrina's folder, the way its owner reads the command.
        """
        line = keep_cast_check_line() or ""
        folder = relative(FOLDER) + "/"
        readme = [(name.replace("<your tv>", FOLDER.name), launcher)
                  for name, launcher in readme_commands_with_a_launcher()]
        return {"README": [c for c in readme if c[0].startswith(folder)],
                KEEP_CAST.name: check_commands([line.split(":", 1)[-1]])}

    def test_without_the_argument_every_run_is_home_drift(self):
        self.tv()
        result = self.run_check(str(KEEP_CAST))
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn(f"Home is {STOCK}/.home.HomeActivity, not {CUSTOM}", result.stdout)
        self.assertEqual([], self.sent())

    def test_without_the_argument_fix_moves_home_off_the_stock_launcher(self):
        # What the README warns about: --fix asks for this launcher on Home, a switch the sabrina and
        # the Acer do not keep, so the run fails there and the next one reports the same drift.
        self.tv()
        self.run_check("--fix", str(KEEP_CAST))
        self.assertEqual([f"cmd package set-home-activity --user 0 {CUSTOM}/.MainActivity"], self.sent())

    def test_the_documented_command_comes_back_clean(self):
        # The list as written too, not only the launcher: the right argument on the folder's other
        # list still reports what the redirect setup keeps on.
        for source, commands in self.documented_commands().items():
            for installed in (True, False):
                with self.subTest(source=source, launcher_installed=installed):
                    self.assertEqual(1, len(commands), f"{source} hands the sabrina no check.py "
                                                       f"command with a launcher argument")
                    name, launcher = commands[0]
                    self.assertEqual(STOCK, launcher)
                    self.tv(launcher_installed=installed)
                    result = self.run_check(str(ROOT / name), launcher)
                    self.assertEqual(0, result.returncode, result.stdout + result.stderr)
                    self.assertIn("nothing drifted", result.stdout)
                    self.assertEqual([], self.sent())

    def test_debloat_txt_reports_what_the_redirect_keeps(self):
        # Why the README names debloat-keep-cast.txt: checked against the folder's debloat.txt, this
        # TV reports setupwraith and the screensaver pair as back on every run, and --fix would
        # disable them, which costs phone casting and the screensaver.
        self.tv()
        result = self.run_check(str(DEBLOAT), STOCK)
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertEqual(3, len(self.kept))
        self.assertIn(f"back on ({len(self.kept)}): {', '.join(self.kept)}", result.stdout)
        self.assertNotIn("Home is", result.stdout)
        self.assertEqual([], self.sent())


if __name__ == "__main__":
    unittest.main()

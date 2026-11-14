"""theme.sh pushes to the package the launcher is installed as, not to a fixed com.gathin.tvhome.

Firmware with a package-name HOME allowlist needs the launcher built under another applicationId -
`-PlauncherApplicationId=com.ts.launcher` on Xiaomi China sets, `com.oneplus.tv.launcher2` on ColorOS TV
(launcher/app/build.gradle, docs/devices/xiaomi-mitv-mftr0-debloat-home.md). theme.sh, theme.ps1 and
theme_restart.py each named com.gathin.tvhome, so on those sets the files went to a directory the launcher never
reads, the provider authority pointed at a package that is not installed, and the run ended "TV refused the
command" (#3703).

The activity class does not change with the applicationId - a component still reads
com.ts.launcher/com.gathin.tvhome.MainActivity - so the package is the part of the HOME resolution before the
slash. theme.sh reads it there (LAUNCHER_PKG=<package> skips the question, for a set where the launcher is
installed but not Home yet) and exports it for theme_restart.py.

These tests run the real tools/theme.sh against a stub tools/tv.py that logs every call and answers the HOME query
with FAKE_HOME, so the directory the push addresses is what the log holds. theme_restart.py is not copied: the push
reaches it, fails, and the run ends 1, which none of these assertions depend on.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(ROOT))

from tools import theme_restart  # noqa: E402

FAKE_TV = '''\
import os
import sys

with open(os.environ["FAKE_LOG"], "a") as log:
    log.write(" ".join(sys.argv[1:]) + "\\n")
# tv.py takes the whole shell line as one argument, so the command starts here with "cmd ", not with the word alone.
if len(sys.argv) > 1 and sys.argv[1].startswith("cmd package query-activities"):
    sys.stdout.write(os.environ.get("FAKE_HOME", ""))
sys.exit(0)
'''

DEFAULT_HOME = """\
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.gathin.tvhome/com.gathin.tvhome.MainActivity
"""
ALLOWLIST_HOME = """\
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.ts.launcher/com.gathin.tvhome.MainActivity
priority=-1000 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.xiaomi.mitv.settings/.entry.FallbackHome
"""
STOCK_HOME = """\
priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=true
com.mitv.tvhome/.MainActivityUserMode
"""


class ThemeLauncherPackageTest(unittest.TestCase):
    def setUp(self):
        self.base = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.base, ignore_errors=True)
        self.tools = self.base / "tools"
        self.tools.mkdir()
        shutil.copy2(TOOLS / "theme.sh", self.tools / "theme.sh")
        (self.tools / "tv.py").write_text(FAKE_TV)
        venv = self.tools / "venv" / "bin"
        venv.mkdir(parents=True)
        try:
            (venv / "python").symlink_to(sys.executable)
        except OSError:
            # Windows without the symlink privilege; theme.sh runs are skipped there.
            if os.name != "nt":
                raise
        self.theme = self.base / "my-theme"
        self.theme.mkdir()
        (self.theme / "config.json").write_text('{"background_color": "#000000"}\n', encoding="utf-8")
        self.log = self.base / "tv-calls.log"

    def run_theme(self, *args, **env_overrides):
        if os.name != "posix" or not shutil.which("bash"):
            raise unittest.SkipTest("theme.sh is a POSIX script; it needs bash")
        env = {k: v for k, v in os.environ.items() if k not in ("TV_HOST", "LAUNCHER_PKG")}
        env.update({"TV_HOST": "192.0.2.10", "FAKE_LOG": str(self.log)})
        for key, value in env_overrides.items():
            if value is None:
                env.pop(key, None)
            else:
                env[key] = value
        return subprocess.run(["bash", str(self.tools / "theme.sh"), *args], env=env,
                              capture_output=True, text=True, timeout=120)

    def tv_calls(self):
        if not self.log.exists():
            return []
        return [line for line in self.log.read_text().splitlines() if line.strip()]

    def sent(self):
        return "\n".join(self.tv_calls())

    def test_the_allowlist_identity_is_where_the_files_go(self):
        # The defect: the log held /sdcard/Android/data/com.gathin.tvhome/files on a set whose launcher is
        # com.ts.launcher, so the config.json was written where nothing reads it.
        self.run_theme(str(self.theme), FAKE_HOME=ALLOWLIST_HOME)
        self.assertIn("/sdcard/Android/data/com.ts.launcher/files/config.json", self.sent())
        self.assertNotIn("/sdcard/Android/data/com.gathin.tvhome/", self.sent())

    def test_the_provider_authority_follows_it_too(self):
        self.run_theme(str(self.theme), FAKE_HOME=ALLOWLIST_HOME)
        self.assertIn("content://com.ts.launcher.theme/config.json", self.sent())
        self.assertNotIn("content://com.gathin.tvhome.theme", self.sent())

    def test_a_reference_build_is_unchanged(self):
        # The default identity still reads what it always read: this is not a behaviour change for the sets that
        # already worked.
        self.run_theme(str(self.theme), FAKE_HOME=DEFAULT_HOME)
        self.assertIn("/sdcard/Android/data/com.gathin.tvhome/files/config.json", self.sent())

    def test_a_stock_launcher_on_home_keeps_the_reference_package(self):
        # The launcher installed but not Home yet: nothing in the resolution names its class, so the reference
        # package stays and the run reads like it did before.
        self.run_theme(str(self.theme), FAKE_HOME=STOCK_HOME)
        self.assertIn("/sdcard/Android/data/com.gathin.tvhome/files/config.json", self.sent())

    def test_launcher_pkg_answers_without_asking_the_tv(self):
        self.run_theme(str(self.theme), FAKE_HOME=ALLOWLIST_HOME, LAUNCHER_PKG="com.example.tvhome")
        self.assertIn("/sdcard/Android/data/com.example.tvhome/files/config.json", self.sent())
        self.assertNotIn("query-activities", self.sent(), "LAUNCHER_PKG was set, so the TV is not asked")

    def test_reset_removes_from_the_same_package(self):
        self.run_theme("--reset", FAKE_HOME=ALLOWLIST_HOME)
        self.assertIn("/sdcard/Android/data/com.ts.launcher/files", self.sent())
        self.assertNotIn("/sdcard/Android/data/com.gathin.tvhome/", self.sent())


class LauncherPackageFromHomeTest(unittest.TestCase):
    """The half theme_restart.py reads: LAUNCHER_PKG, else the class in the HOME resolution."""

    def tearDown(self):
        os.environ.pop("LAUNCHER_PKG", None)

    def test_the_allowlist_identity_is_read_off_the_component(self):
        os.environ.pop("LAUNCHER_PKG", None)
        self.assertEqual("com.ts.launcher", theme_restart.launcher_package(ALLOWLIST_HOME))

    def test_the_reference_build_is_read_off_the_component(self):
        os.environ.pop("LAUNCHER_PKG", None)
        self.assertEqual("com.gathin.tvhome", theme_restart.launcher_package(DEFAULT_HOME))

    def test_a_stock_home_keeps_the_reference_package(self):
        os.environ.pop("LAUNCHER_PKG", None)
        self.assertEqual("com.gathin.tvhome", theme_restart.launcher_package(STOCK_HOME))

    def test_an_empty_answer_keeps_the_reference_package(self):
        os.environ.pop("LAUNCHER_PKG", None)
        self.assertEqual("com.gathin.tvhome", theme_restart.launcher_package(""))

    def test_the_variable_wins_over_the_resolution(self):
        os.environ["LAUNCHER_PKG"] = "com.example.tvhome"
        self.assertEqual("com.example.tvhome", theme_restart.launcher_package(STOCK_HOME))


if __name__ == "__main__":
    unittest.main()

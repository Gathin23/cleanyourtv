"""tools/privacy.py names the Google TV telemetry packages that lists in examples/ already disable.

The Chromecast with Google TV HD and 4K and Google TV Streamer lists disable
com.google.android.tv.remotecontrol.logging, the 4K lists com.google.android.apps.tv.netoscope as
well, and many lists com.android.tv.feedbackconsent and com.google.android.partnersetup. The audit
knew only com.google.android.feedback, and none of the four matches its name hint, so on a
Chromecast with Google TV 4K (Google TV 14) the report left all four out, and --write left out
partnersetup, which was still enabled there.

com.android.tv.feedbackconsent runs in android.uid.system: AOSP's manifest says so, and that
Chromecast prints appId=1000 for it. Its dump therefore ends with the system UID's grants,
RECORD_AUDIO among them, although it asks only for READ_LOGS and DUMP. The dumps here follow that
TV's, in the shape test_privacy_android14_uid.py builds, so the report, and the list --write leaves
on a stock Google TV 14 where feedbackconsent is still on, must say it shares the system UID and
never that it declares RECORD_AUDIO. That needs the appId= fix tested there: without it, both read
"declares RECORD_AUDIO".

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is
simulated.

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
from test_privacy_android14_uid import dump, entry  # noqa: E402
from test_privacy_audit import FAKE_TV  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import privacy  # noqa: E402
import recovery  # noqa: E402

LOGGING = "com.google.android.tv.remotecontrol.logging"
NETOSCOPE = "com.google.android.apps.tv.netoscope"
CONSENT = "com.android.tv.feedbackconsent"
PARTNER = "com.google.android.partnersetup"
FEEDBACK = "com.google.android.feedback"
NEW = [LOGGING, NETOSCOPE, CONSENT, PARTNER]
# The four as that Chromecast prints them: their own appId and part of what they request. None of
# them asks for RECORD_AUDIO; feedbackconsent is in the system UID, which holds it.
DUMPS = {
    LOGGING: dump("appId", 10046, ["android.permission.RECEIVE_BOOT_COMPLETED",
                                   "android.permission.BLUETOOTH_CONNECT"], name=LOGGING),
    NETOSCOPE: dump("appId", 10042, ["android.permission.DUMP", "android.permission.INTERNET",
                                     "android.permission.ACCESS_WIFI_STATE"], name=NETOSCOPE),
    CONSENT: dump("appId", 1000, ["android.permission.READ_LOGS", "android.permission.DUMP"],
                  shared="android.uid.system", name=CONSENT),
    PARTNER: dump("appId", 10045, ["android.permission.RECEIVE_BOOT_COMPLETED",
                                   "android.permission.INTERNET"], name=PARTNER),
}


def disabled_by_folder():
    """examples/<folder> -> the packages its lists disable."""
    folders = {}
    for path in sorted((ROOT / "examples").glob("*/*.txt")):
        folders.setdefault(path.parent.name, set()).update(recovery.read_list(path))
    return folders


class AuditTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", tools / "privacy.py")
        (tools / "tv.py").write_text(FAKE_TV)
        self.script = tools / "privacy.py"
        self.state_path = self.base / "tv-state.json"
        # The state of the Chromecast with Google TV 4K after its list: partnersetup is still on.
        self.tv(disabled=[FEEDBACK, LOGGING, NETOSCOPE, CONSENT])

    def tv(self, disabled):
        self.state_path.write_text(json.dumps({
            "installed": [FEEDBACK] + NEW + ["com.example.plain"],
            "disabled": disabled,
            "voice": {},
            "dumps": DUMPS,
            "appops": {},
        }))

    def run_script(self, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.script), *args], cwd=self.repo, env=env,
                              capture_output=True, text=True, stdin=subprocess.DEVNULL, timeout=120)

    def test_the_report_lists_them_under_telemetry(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        telemetry = result.stdout.split("Telemetry and analytics\n", 1)[1].split("\n\n", 1)[0]
        for name in (LOGGING, NETOSCOPE, CONSENT):
            self.assertIn(f"disabled  {name}", telemetry)
        self.assertIn(f"enabled   {PARTNER}", telemetry)
        self.assertIn("RLZ partner-attribution pings", telemetry)
        self.assertNotIn("Matched by name only", result.stdout)

    def test_write_adds_the_one_still_enabled(self):
        target = self.base / "privacy.txt"
        result = self.run_script("--write", str(target))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(recovery.read_list(target), [PARTNER])

    def test_feedbackconsent_shares_the_system_uid(self):
        result = self.run_script()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("shares the system UID (uid 1000)", entry(result.stdout, CONSENT))
        # None of the four asks for the microphone, so no line may say one does.
        self.assertNotIn("declares RECORD_AUDIO", result.stdout)

    def test_a_stock_tv_s_list_does_not_claim_the_microphone(self):
        # Before any list all five are on, so --write names feedbackconsent too, with its first
        # evidence line, in a file that may be shared (docs/workflows/tv-list.md).
        self.tv(disabled=[])
        target = self.base / "privacy.txt"
        result = self.run_script("--write", str(target))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(sorted(recovery.read_list(target)), sorted([FEEDBACK] + NEW))
        written = target.read_text()
        line = next(line for line in written.splitlines() if line.startswith(CONSENT))
        self.assertIn("(shares the system UID (uid 1000)", line)
        self.assertNotIn("declares RECORD_AUDIO", written)


class CatalogueTest(unittest.TestCase):
    def test_they_are_telemetry_rows(self):
        for name in NEW:
            with self.subTest(package=name):
                self.assertEqual(privacy.CATALOGUE[name][0], "telemetry")
                self.assertFalse(privacy.NAME_HINT.search(name), "the catalogue is what finds it")

    def test_no_row_is_a_package_disable_sh_refuses(self):
        # --write puts every catalogue row the TV has into the list, and tools/disable.sh refuses a
        # list that names a core package (#884).
        self.assertEqual(recovery.core_packages(list(privacy.CATALOGUE)), [])

    def test_the_lists_a_note_names_disable_the_package(self):
        folders = disabled_by_folder()
        named = {
            LOGGING: ("Chromecast HD and 4K and Google TV Streamer lists",
                      ("google-chromecast-gtv-hd", "google-chromecast-gtv-4k",
                       "google-tv-streamer-4k-kirkwood")),
            NETOSCOPE: ("Chromecast with Google TV 4K lists", ("google-chromecast-gtv-4k",)),
        }
        for name, (words, expected) in named.items():
            self.assertIn(words, privacy.CATALOGUE[name][1])
            for folder in expected:
                with self.subTest(package=name, folder=folder):
                    self.assertIn(name, folders[folder])

    def test_the_shares_in_the_notes_match_examples(self):
        folders = disabled_by_folder()
        total = len(folders)
        both = sum(1 for names in folders.values() if CONSENT in names and FEEDBACK in names)
        partner = sum(1 for names in folders.values() if PARTNER in names)
        self.assertIn("about one in five lists", privacy.CATALOGUE[CONSENT][1])
        self.assertTrue(0.15 <= both / total <= 0.25, f"{both} of {total} lists")
        self.assertIn("about a third of the lists", privacy.CATALOGUE[PARTNER][1])
        self.assertTrue(0.28 <= partner / total <= 0.4, f"{partner} of {total} lists")


if __name__ == "__main__":
    unittest.main()

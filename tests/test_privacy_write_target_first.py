"""tools/privacy.py --write looks at the file it was given before it asks the TV anything.

The check that the file is new ran only after connect() and the whole audit, which is up to 25
`dumpsys package` calls, slow on old firmware, and as many `appops` calls. Then --write refused a
file that was there all along, and a name in a folder that does not exist ended in a
FileNotFoundError traceback. Both are now refused first, saying that nothing was asked of the TV.
The check when writing stays, for a file that appears during the audit.

With --include-low, the list also said nothing about the name matches the audit's probe cap left
out, though the report counts them, so it looked complete. The terminal and the list header now say
how many were left out.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated.
An unreachable fake TV is how a test sees that the TV was not asked: connecting to it fails.

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
from test_privacy_audit import FAKE_TV, OWN_DUMP  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import privacy  # noqa: E402
import recovery  # noqa: E402

# The fake TV, plus one thing a real run can meet: the file appears while the audit runs.
APPEARING_TV = FAKE_TV.replace("def main():", '''_connect = connect


def connect():
    device = _connect()
    if state.get("appears"):
        with open(state["appears"], "w") as f:
            f.write("# written during the audit\\n")
    return device


def main():''', 1)
KATNISS = "com.google.android.katniss"
MATCHES = [f"com.example.voice{i:02d}" for i in range(30)]


class PrivacyWriteTargetTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        tools = self.repo / "tools"
        tools.mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", tools / "privacy.py")
        (tools / "tv.py").write_text(APPEARING_TV)
        self.script = tools / "privacy.py"
        self.state_path = self.base / "tv-state.json"
        self.tv()

    def tv(self, installed=(KATNISS, "com.example.plain"), **overrides):
        state = {
            "installed": list(installed),
            "disabled": [],
            "voice": {"voice_interaction_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"},
            "dumps": {KATNISS: OWN_DUMP},
            "appops": {},
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def write(self, target, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.script), "--write", str(target), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True,
                              stdin=subprocess.DEVNULL, timeout=120)

    # The file, before the TV

    def test_an_existing_file_is_refused_before_the_tv_is_asked(self):
        target = self.repo / "privacy.txt"
        target.write_text("# hand edits\n")
        self.tv(unreachable=True)
        result = self.write("privacy.txt")
        self.assertEqual(result.returncode, 1)
        self.assertIn("privacy.txt already exists; move it aside or choose another name.",
                      result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(target.read_text(), "# hand edits\n")

    def test_a_missing_folder_is_refused_before_the_tv_is_asked(self):
        self.tv(unreachable=True)
        result = self.write("no-such-folder/privacy.txt")
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("there is no folder no-such-folder", result.stderr)
        self.assertIn("Nothing was asked of the TV.", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertFalse((self.repo / "no-such-folder").exists())

    def test_a_folder_that_is_a_file_is_refused_too(self):
        (self.repo / "lists").write_text("not a folder\n")
        self.tv(unreachable=True)
        result = self.write("lists/privacy.txt")
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("Traceback", result.stderr)
        self.assertIn("there is no folder lists", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual((self.repo / "lists").read_text(), "not a folder\n")

    def test_a_new_file_in_a_folder_that_exists_is_still_written(self):
        (self.repo / "lists").mkdir()
        result = self.write("lists/privacy.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(recovery.read_list(self.repo / "lists" / "privacy.txt"), [KATNISS])

    def test_a_file_that_appears_during_the_audit_is_not_replaced(self):
        target = self.repo / "privacy.txt"
        self.tv(appears=str(target))
        result = self.write("privacy.txt")
        self.assertEqual(result.returncode, 1)
        self.assertIn("privacy.txt already exists", result.stderr)
        # The TV was asked this time, so the refusal must not say it was not.
        self.assertNotIn("Nothing was asked of the TV", result.stderr)
        self.assertEqual(target.read_text(), "# written during the audit\n")

    # What the probe cap left out

    def test_include_low_says_how_many_name_matches_were_not_probed(self):
        self.tv(installed=[KATNISS] + MATCHES)
        target = self.repo / "low.txt"
        result = self.write("low.txt", "--include-low")
        self.assertEqual(result.returncode, 0, result.stderr)
        # katniss and 24 name matches fill the 25 probes; 6 of the 30 matches are left.
        self.assertEqual(len(recovery.read_list(target)), privacy.MAX_PROBES)
        self.assertIn("wrote 25 packages to low.txt", result.stdout)
        self.assertIn("6 more name matches were not probed (the audit stops at 25)", result.stdout)
        self.assertIn("`dumpsys package <name>`", result.stdout)
        text = target.read_text()
        self.assertIn("# 6 more name matches were not probed (the audit stops at 25)", text)
        header = text.split("\n\n", 1)[0]
        self.assertIn("dumpsys package <name>", header)
        self.assertNotIn("192.168.1.50", text)

    def test_the_count_matches_the_report(self):
        self.tv(installed=[KATNISS] + MATCHES)
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        report = subprocess.run([sys.executable, str(self.script)], cwd=self.repo, env=env,
                                capture_output=True, text=True, stdin=subprocess.DEVNULL,
                                timeout=120).stdout
        self.assertIn("6 more name matches were not probed", report)
        result = self.write("low.txt", "--include-low")
        self.assertIn("6 more name matches were not probed", result.stdout)

    def test_without_include_low_the_list_has_no_name_matches_to_count(self):
        self.tv(installed=[KATNISS] + MATCHES)
        target = self.repo / "privacy.txt"
        result = self.write("privacy.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(recovery.read_list(target), [KATNISS])
        self.assertNotIn("not probed", result.stdout)
        self.assertNotIn("not probed", target.read_text())

    def test_nothing_is_said_when_every_match_was_probed(self):
        self.tv(installed=[KATNISS] + MATCHES[:5])
        target = self.repo / "low.txt"
        result = self.write("low.txt", "--include-low")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(len(recovery.read_list(target)), 6)
        self.assertNotIn("not probed", result.stdout)
        self.assertNotIn("not probed", target.read_text())


if __name__ == "__main__":
    unittest.main()

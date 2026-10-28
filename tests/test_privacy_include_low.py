"""tools/privacy.py's --include-low is read by the list --write writes, and by nothing else.

`privacy.py --include-low` (no --write) was accepted and changed nothing: the flag is only read by
list_lines(), which write_list() calls, and the printed report names the name-only matches either way.
So an owner who asked for them - the flag's own words are "also the packages that only matched by
name" - got the same report as without it, exit 0, and no way to tell that the one thing they asked for
had not happened. The script's own usage line has always put the flag after `--write`.

It is refused now, with that usage and a line saying what to add, before connect(): the audit sends two
shell calls for every package it probes and `dumpsys package` is slow on old firmware, so a run that
cannot use the flag should not spend that.

`--write <file> --include-low` is unchanged in both orders, and `--write` without the flag still leaves
the name matches out - the behaviour the flag exists for.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated.

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
sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(0, str(ROOT / "tools"))
from test_privacy_audit import FAKE_TV, OWN_DUMP, SYSTEM_DUMP  # noqa: E402
import recovery  # noqa: E402

KATNISS = "com.google.android.katniss"
NAME_ONLY = "com.example.voicecontrol"     # matched by its name, not by the firmware or the catalogue
USAGE = "usage: privacy.py"
NEEDS_WRITE = "--include-low is read by the list --write writes"


class PrivacyIncludeLowOnlyTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.base = Path(tmp.name)
        self.repo = self.base / "repo"
        (self.repo / "tools").mkdir(parents=True)
        shutil.copy2(ROOT / "tools" / "privacy.py", self.repo / "tools" / "privacy.py")
        (self.repo / "tools" / "tv.py").write_text(FAKE_TV)
        self.state_path = self.base / "tv-state.json"
        self.tv()

    def tv(self, **overrides):
        state = {
            "installed": [KATNISS, NAME_ONLY, "com.example.plain"],
            "disabled": [],
            "voice": {"voice_interaction_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"},
            "dumps": {KATNISS: OWN_DUMP, NAME_ONLY: SYSTEM_DUMP},
            "appops": {},
            "unreachable": False,
        }
        state.update(overrides)
        self.state_path.write_text(json.dumps(state))

    def run_script(self, *args):
        env = dict(os.environ, TV_HOST="192.168.1.50", FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.repo / "tools" / "privacy.py"), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True,
                              stdin=subprocess.DEVNULL, timeout=120)

    def written(self, *args):
        """The list --write wrote, as the parser tools/disable.sh and restore.sh share reads it."""
        target = self.repo / "privacy.txt"
        result = self.run_script("--write", "privacy.txt", *args)
        self.assertEqual(result.returncode, 0, result.stderr)
        return recovery.read_list(target)

    # The flag on its own

    def test_the_flag_alone_is_the_usage_and_asks_nothing_of_the_tv(self):
        self.tv(unreachable=True)
        result = self.run_script("--include-low")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(USAGE, result.stderr)
        self.assertIn("--write <file> [--include-low]", result.stderr)
        self.assertIn(NEEDS_WRITE, result.stderr)
        self.assertIn("--write privacy.txt --include-low", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)
        self.assertEqual(result.stdout, "")

    def test_the_flag_alone_runs_no_audit(self):
        # Not only a fast exit: the report is what the audit produces, so no report means no audit.
        result = self.run_script("--include-low")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertNotIn("Voice assistants and wake word", result.stdout)
        self.assertEqual(sorted(p.name for p in self.repo.iterdir()), ["tools"])

    def test_the_flag_given_twice_is_the_same_answer(self):
        self.tv(unreachable=True)
        result = self.run_script("--include-low", "--include-low")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(NEEDS_WRITE, result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)

    def test_the_flag_with_a_write_that_has_no_name_keeps_the_usage(self):
        self.tv(unreachable=True)
        result = self.run_script("--include-low", "--write")
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn(USAGE, result.stderr)
        self.assertNotIn("cannot reach the TV", result.stderr)

    # What the flag is for, which must not change

    def test_with_write_it_still_adds_the_name_matches(self):
        packages = self.written("--include-low")
        self.assertIn(KATNISS, packages)
        self.assertIn(NAME_ONLY, packages)

    def test_the_flag_before_write_is_read_the_same_way(self):
        target = self.repo / "privacy.txt"
        result = self.run_script("--include-low", "--write", "privacy.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(NAME_ONLY, recovery.read_list(target))

    def test_without_the_flag_the_name_matches_stay_out(self):
        packages = self.written()
        self.assertIn(KATNISS, packages)
        self.assertNotIn(NAME_ONLY, packages)

    # Where the refusal sits

    def test_the_refusal_is_above_the_connection(self):
        source = (ROOT / "tools" / "privacy.py").read_text(encoding="utf-8")
        main = source.split("def main():", 1)[1]
        refusal = main.index("if include_low and not args:")
        self.assertLess(refusal, main.index("dev = connect()"))
        # One usage block, used by both branches that answer with it.
        self.assertEqual(len(re.findall(r"^USAGE = ", source, re.M)), 1)


if __name__ == "__main__":
    unittest.main()

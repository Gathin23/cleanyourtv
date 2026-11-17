"""The list tools/privacy.py --write leaves behind: it names packages, and nothing about the TV.

Two things went wrong in that file. When every candidate the audit found was already disabled, the
already-disabled filter ran after the check for an empty list, so --write wrote a file of comments and
empty headings, said "wrote 0 packages" and printed the commands to apply it, which tools/disable.sh
then refused. And the header carried the TV's address from TV_HOST, in a file that
docs/workflows/tv-list.md is how people share, while naming privacy.txt whatever file was written.

tools/tv.py is the fake from test_privacy_audit.py, so the real audit runs and only ADB is simulated.

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
from test_privacy_audit import FAKE_TV, INTERNET_DUMP, OWN_DUMP, SYSTEM_DUMP  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
HOST = "192.168.1.50"
IDENTIFIED = ["com.google.android.katniss", "tv.inscape.tvclient.inscapeacr",
              "org.droidtv.usagelogger"]


class PrivacyWrittenListTest(unittest.TestCase):
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
        self.tv()

    def tv(self, disabled=("org.droidtv.usagelogger",)):
        self.state_path.write_text(json.dumps({
            "installed": IDENTIFIED + ["com.example.voicecontrol", "com.example.plain"],
            "disabled": list(disabled),
            "voice": {"voice_interaction_service":
                      "com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService"},
            "dumps": {
                "com.google.android.katniss": OWN_DUMP,
                "tv.inscape.tvclient.inscapeacr": INTERNET_DUMP,
                "com.example.voicecontrol": SYSTEM_DUMP,
                "org.droidtv.usagelogger": "userId=10125\n",
            },
            "appops": {},
        }))

    def write(self, target, *args):
        env = dict(os.environ, TV_HOST=HOST, FAKE_TV_STATE=str(self.state_path))
        return subprocess.run([sys.executable, str(self.script), "--write", str(target), *args],
                              cwd=self.repo, env=env, capture_output=True, text=True,
                              stdin=subprocess.DEVNULL, timeout=120)

    # Nothing left to write

    def test_every_candidate_already_disabled_writes_no_list(self):
        self.tv(disabled=IDENTIFIED)
        target = self.base / "privacy.txt"
        result = self.write(target)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("already disabled", result.stderr)
        self.assertNotIn("wrote", result.stdout)
        self.assertNotIn("tools/disable.sh", result.stdout)
        self.assertFalse(target.exists())

    def test_include_low_counts_the_name_matches_before_refusing(self):
        self.tv(disabled=IDENTIFIED)
        target = self.base / "low.txt"
        result = self.write(target, "--include-low")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("wrote 1 packages", result.stdout)
        self.assertIn("com.example.voicecontrol", target.read_text())

        self.tv(disabled=IDENTIFIED + ["com.example.voicecontrol"])
        target = self.base / "low-all-off.txt"
        result = self.write(target, "--include-low")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("already disabled", result.stderr)
        self.assertFalse(target.exists())

    def test_a_section_with_nothing_left_in_it_has_no_heading(self):
        target = self.base / "privacy.txt"
        result = self.write(target)
        self.assertEqual(result.returncode, 0, result.stderr)
        text = target.read_text()
        self.assertIn("# Voice assistants and wake word", text)
        self.assertNotIn("org.droidtv.usagelogger", text)
        self.assertNotIn("# Telemetry and analytics", text)

    # The header

    def test_header_carries_the_placeholder_not_the_address(self):
        target = self.base / "privacy.txt"
        result = self.write(target)
        self.assertEqual(result.returncode, 0, result.stderr)
        text = target.read_text()
        self.assertNotIn(HOST, text)
        self.assertIn("#   TV_HOST=<tv ip> tools/disable.sh", text)
        self.assertIn("#   TV_HOST=<tv ip> tools/restore.sh", text)
        # The terminal is the owner's own screen: the commands printed there keep the real address.
        self.assertIn(f"TV_HOST={HOST} tools/disable.sh", result.stdout)

    def test_header_names_the_file_that_was_written(self):
        result = self.write("audit.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        text = (self.repo / "audit.txt").read_text()
        self.assertIn("#   TV_HOST=<tv ip> tools/disable.sh audit.txt\n", text)
        self.assertIn("#   TV_HOST=<tv ip> tools/restore.sh audit.txt\n", text)
        self.assertNotIn("privacy.txt", text)

    def test_a_relative_path_keeps_its_folder(self):
        # A list written into examples/<folder>/ is applied from the repository root, so the header
        # keeps the path as typed; only an absolute path is cut to the file name.
        (self.repo / "lists").mkdir()
        result = self.write("lists/audit.txt")
        self.assertEqual(result.returncode, 0, result.stderr)
        text = (self.repo / "lists" / "audit.txt").read_text()
        self.assertIn("#   TV_HOST=<tv ip> tools/disable.sh lists/audit.txt\n", text)
        self.assertIn("#   TV_HOST=<tv ip> tools/restore.sh lists/audit.txt\n", text)

    def test_an_absolute_path_is_cut_to_the_file_name(self):
        target = self.base / "lists" / "tcl-audit.txt"
        target.parent.mkdir()
        result = self.write(target)
        self.assertEqual(result.returncode, 0, result.stderr)
        text = target.read_text()
        self.assertIn("#   TV_HOST=<tv ip> tools/disable.sh tcl-audit.txt\n", text)
        self.assertNotIn(str(self.base), text)

    def test_the_list_still_parses_as_a_list(self):
        target = self.base / "privacy.txt"
        self.write(target)
        sys.path.insert(0, str(ROOT / "tools"))
        import recovery
        self.assertEqual(recovery.read_list(target),
                         ["com.google.android.katniss", "tv.inscape.tvclient.inscapeacr"])

    def check_suggested_commands(self, target, result):
        # These replace the real tools entirely: copied commands can only print their arguments.
        for name in ("disable.sh", "restore.sh"):
            script = self.repo / "tools" / name
            script.write_text('#!/bin/sh\n[ "$#" -eq 1 ] || exit 64\nprintf \'%s\' "$1"\n')
            script.chmod(0o755)
        text = (self.repo / target).read_text()
        header = [line[4:].replace("TV_HOST=<tv ip>", f"TV_HOST={HOST}")
                  for line in text.splitlines() if line.startswith("#   TV_HOST=")]
        terminal = [line.split(":", 1)[1].strip() for line in result.stdout.splitlines()
                    if line.startswith(("apply:", "undo:"))]
        self.assertEqual(len(header), 2)
        self.assertEqual(len(terminal), 2)
        target_name = os.fspath(target)
        expected_header = os.path.basename(target_name) if os.path.isabs(target_name) else target_name
        for group, commands, expected in (("header", header, expected_header),
                                           ("terminal", terminal, target_name)):
            for command in commands:
                with self.subTest(group=group, command=command):
                    run = subprocess.run(["/bin/sh", "-c", command], cwd=self.repo,
                                         capture_output=True, text=True, timeout=10)
                    self.assertEqual(run.returncode, 0, run.stderr)
                    self.assertEqual(run.stdout, expected)
        return text

    @unittest.skipUnless(os.name == "posix", "execute the printed POSIX shell commands")
    def test_relative_commands_pass_the_entire_list_path_as_one_argument(self):
        (self.repo / "lists").mkdir()
        for name in ("audit.txt", "family audit.txt", "family's audit.txt"):
            with self.subTest(name=name):
                target = "lists/" + name
                result = self.write(target)
                self.assertEqual(result.returncode, 0, result.stderr)
                text = self.check_suggested_commands(target, result)
                self.assertNotIn(HOST, text)

    @unittest.skipUnless(os.name == "posix", "execute the printed POSIX shell commands")
    def test_absolute_commands_keep_the_full_path_and_the_header_keeps_only_the_basename(self):
        target = self.base / "private folder" / "family's audit.txt"
        target.parent.mkdir()
        result = self.write(target)
        self.assertEqual(result.returncode, 0, result.stderr)
        text = self.check_suggested_commands(target, result)
        self.assertNotIn(str(target.parent), text)
        self.assertNotIn(HOST, text)


if __name__ == "__main__":
    unittest.main()

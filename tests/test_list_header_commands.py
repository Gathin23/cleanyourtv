"""A list header whose commands could not work as written, and the lists that hide with `pm hide`.

examples/ffalcon-r34a0-cn: the header says this firmware denies `pm disable-user` to the shell, yet its
Apply line ran tools/disable.sh, which sends nothing else. It now hides with the `pm hide` batch that
examples/redmi-mitv-antl0 uses for the same Android 5.1 refusal, and gives `pm unhide` as the undo
AGENTS.md asks for next to any `pm hide`. The test runs each header's command with tools/venv/bin/python
replaced by a stand-in that keeps what `tools/tv.py --batch` was given.

Which lists that rule is read for was a pair of folder names in this file, so the third list built the
same way - examples/pptv-p34a4p, whose header hides the same way for the same refusal - was read by
nothing, and a fourth could have shipped with no `pm unhide` and no test would have said so. `hiding()`
now finds them in the tree: a list whose header gives a `grep ... tools/tv.py --batch` pipeline that
sends `pm hide`. A header that only says `pm hide` is refused by its firmware (tivo-stream-4k,
google-tv-streamer-4k-kirkwood, xiaomi-mitv-ansp0, philips-65oled907, xiaomi-mitv-mteq0) gives no such
batch and is not one.

examples/anker-nebula-mars-3-air: the header and README said `tools/check.py --fix` re-disables
Assistant after a boot, but the list keeps com.google.android.katniss commented out and check.py reads
only active lines, so --fix never touched it. They now say to uncomment it and its LED service, then
run Apply again: disable.sh adds them to the recovery record first, and restore.sh leaves alone every
listed name the record does not hold, so that is what makes Undo bring them back. The owner's
choice stays: the shipped list still leaves Assistant on. That half reuses the drift check's harness and
fake tools/tv.py from tests/test_drift_check.py, and runs the real disable.sh and restore.sh against
it with run_script from tests/test_recovery_record.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
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
sys.path.insert(0, str(Path(__file__).resolve().parent))
import recovery  # noqa: E402
import test_drift_check as drift  # noqa: E402  (imported as a module so its tests do not run twice)
import test_recovery_record as record_test  # noqa: E402  (the same)

# A list whose firmware refuses `pm disable-user` for the shell hides instead, and its header says so with
# a batch. The folders are found in the tree (`hiding()`), not named here: naming them let a third list
# built the same way go unread.
EXAMPLES = ROOT / "examples"
HIDE = "pm hide"
UNDO = "Undo:"
ANKER = ROOT / "examples" / "anker-nebula-mars-3-air"
ASSISTANT = ("com.google.android.katniss", "com.google.android.tv.assistant")
LAUNCHERX = "com.google.android.apps.tv.launcherx"

# Stands in for tools/venv/bin/python running tools/tv.py: keeps the arguments, TV_HOST and the
# --batch input instead of sending anything to a TV.
FAKE_CLIENT = '#!/bin/sh\nprintf "%s\\n" "$TV_HOST $*" > "$ARGS"\ncat > "$CAPTURE"\n'


def header(path):
    """The comment lines above a list's first package line."""
    lines = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() and not line.lstrip().startswith("#"):
            break
        lines.append(line)
    return lines


def hide_command(lines):
    """The `grep ... | tools/tv.py --batch` pipeline a header gives, continuation lines joined."""
    body = [line.lstrip("#").strip() for line in lines]
    start = next((i for i, text in enumerate(body) if text.startswith("grep ")), None)
    if start is None:
        return None
    parts = []
    for text in body[start:]:
        parts.append(text.rstrip("\\").strip())
        if not text.endswith("\\"):
            break
    return " ".join(parts)


def hiding(root=None):
    """The list folders whose header hides: a `grep ... tools/tv.py --batch` pipeline that sends `pm hide`.

    The firmware on these sets refuses `pm disable-user` to the shell, so the header's Apply line is a
    batch of `pm hide` (Android 5.1: FFalcon r34a0, Redmi MiTV-ANTL0, PPTV p34a4p) instead of
    tools/disable.sh. A header that mentions `pm hide` as something its firmware refuses too
    (tivo-stream-4k, google-tv-streamer-4k-kirkwood, xiaomi-mitv-ansp0, philips-65oled907,
    xiaomi-mitv-mteq0) gives no such batch and is not one of these.
    """
    found = []
    for path in sorted((root or EXAMPLES).rglob("debloat.txt")):
        command = hide_command(header(path))
        if command and HIDE in command and "--batch" in command:
            found.append(path.parent.name)
    return sorted(found)


def undo_problem(path):
    """Why a list that hides gives no way back, or None.

    AGENTS.md's hard rule: `pm hide` only with the matching undo written in the list header. `pm enable`
    (tools/restore.sh) does not bring a hidden package back, so the undo has to be `pm unhide`, and the
    header may not hand the reader the disable or restore scripts for this list either.
    """
    lines = header(path)
    text = "\n".join(lines)
    if UNDO not in text:
        return ("the header hides with `pm hide` and gives no Undo line: tools/restore.sh's `pm enable` "
                "cannot undo a hide, so nothing in it brings the package back")
    undo = text[text.index(UNDO):].split("\n#\n", 1)[0]
    if "pm unhide" not in undo:
        return f"the Undo line gives no `pm unhide`, the one command that undoes a hide: {undo.strip()!r}"
    for line in lines:
        if re.search(r"tools[/\\](disable|restore)\.(sh|ps1)\s+\S*debloat", line):
            return (f"the header runs a script that sends `pm disable-user` or `pm enable`, which cannot "
                    f"undo a hide: {line.strip()!r}")
    return None


class HideHeaderTest(unittest.TestCase):
    def run_command(self, folder, command):
        with tempfile.TemporaryDirectory() as tmp:
            repo = Path(tmp) / "repo"
            listing = repo / "examples" / folder / "debloat.txt"
            listing.parent.mkdir(parents=True)
            listing.write_bytes((ROOT / "examples" / folder / "debloat.txt").read_bytes())
            client = repo / "tools" / "venv" / "bin" / "python"
            client.parent.mkdir(parents=True)
            client.write_text(FAKE_CLIENT)
            client.chmod(0o755)
            args, capture = Path(tmp) / "args.txt", Path(tmp) / "commands.txt"
            env = dict(os.environ, ARGS=str(args), CAPTURE=str(capture))
            env.pop("TV_HOST", None)
            result = subprocess.run(["bash", "-c", "set -o pipefail; " + command], cwd=repo, env=env,
                                    capture_output=True, text=True, stdin=subprocess.DEVNULL,
                                    timeout=60)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            return args.read_text().split(), capture.read_text().splitlines()

    def test_the_walk_finds_the_lists_that_hide(self):
        """A read guard: a walk that stopped finding a list would pass both tests below in silence."""
        found = hiding()
        self.assertLessEqual({"redmi-mitv-antl0", "ffalcon-r34a0-cn", "pptv-p34a4p"}, set(found),
                             f"the walk read {found}")

    def test_the_apply_command_hides_every_listed_package(self):
        for folder in hiding():
            with self.subTest(folder=folder):
                command = hide_command(header(ROOT / "examples" / folder / "debloat.txt"))
                self.assertIsNotNone(command, "the header gives no `pm hide` batch to apply the list")
                self.assertIn(f"examples/{folder}/debloat.txt", command)
                self.assertIn("TV_HOST=<tv ip>", command)
                args, sent = self.run_command(folder, command.replace("<tv ip>", "192.168.1.50"))
                self.assertEqual(args, ["192.168.1.50", "-I", "tools/tv.py", "--batch"])
                listed = recovery.read_list(ROOT / "examples" / folder / "debloat.txt")
                self.assertEqual(sent, [f"pm hide {name}" for name in listed])

    def test_the_undo_line_unhides_and_no_line_runs_the_disable_scripts(self):
        for folder in hiding():
            with self.subTest(folder=folder):
                self.assertIsNone(undo_problem(EXAMPLES / folder / "debloat.txt"))


class HideDiscoveryTest(unittest.TestCase):
    """The walk itself, over lists written here: the rules above are only as wide as it is."""

    def tree(self, lists):
        """A folder of lists as examples/<folder>/debloat.txt, written into a temporary directory."""
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        root = Path(tmp.name) / "examples"
        for folder, text in lists.items():
            path = root / folder / "debloat.txt"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        return root

    # The header shape the three lists on main use.
    APPLY = ("# Apply:   NOT tools/disable.sh — it uses pm disable-user, which this firmware DENIES.\n"
             "#   grep -vE '^\\s*(#|$)' examples/{folder}/debloat.txt | sed -E 's/\\s+#.*$//' \\\n"
             "#     | awk '{{print \"pm hide \" $1}}' | TV_HOST=<tv ip> tools/venv/bin/python -I "
             "tools/tv.py --batch\n")
    UNDO = "# Undo:    NOT tools/restore.sh — its pm enable cannot undo a hide.\n" \
           "#          Same as Apply, but `pm unhide`.\n"

    def test_a_list_that_hides_is_found(self):
        root = self.tree({"hide-tv": self.APPLY.format(folder="hide-tv") + self.UNDO + "\ncom.a.b\n"})
        self.assertEqual(hiding(root), ["hide-tv"])

    def test_a_list_that_only_mentions_hide_is_not_one(self):
        # The tivo-stream-4k shape: the firmware refuses pm hide too, and the list is suspended.
        text = ("# - Every pm disable-user and pm hide is refused (\"This command is illegal!\")\n"
                "# Apply:   NOT tools/disable.sh — refused on this firmware.\n"
                "# Undo:    nothing was disabled.\n\ncom.a.b\n")
        root = self.tree({"refusing-tv": text})
        self.assertEqual(hiding(root), [])

    def test_a_list_that_disables_with_disable_user_is_not_one(self):
        text = ("# Apply:   grep -vE '^\\s*(#|$)' examples/plain-tv/debloat.txt \\\n"
                "#     | TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --batch\n"
                "# Undo:    tools/restore.sh examples/plain-tv/debloat.txt com.a.launcher\n\ncom.a.b\n")
        root = self.tree({"plain-tv": text})
        self.assertEqual(hiding(root), [])

    def test_a_hide_list_a_folder_down_is_read_too(self):
        text = self.APPLY.format(folder="nested") + self.UNDO + "\ncom.a.b\n"
        root = self.tree({"nested/one-level-down": text})
        self.assertEqual(hiding(root), ["one-level-down"])

    def test_a_hide_list_with_no_undo_is_reported(self):
        root = self.tree({"hide-tv": self.APPLY.format(folder="hide-tv") + "\ncom.a.b\n"})
        problem = undo_problem(root / "hide-tv" / "debloat.txt")
        self.assertIsNotNone(problem)
        self.assertIn("no Undo line", problem)

    def test_an_undo_that_does_not_unhide_is_reported(self):
        text = (self.APPLY.format(folder="hide-tv")
                + "# Undo:    tools/restore.sh examples/hide-tv/debloat.txt com.a.launcher\n\ncom.a.b\n")
        root = self.tree({"hide-tv": text})
        problem = undo_problem(root / "hide-tv" / "debloat.txt")
        self.assertIsNotNone(problem)
        self.assertIn("pm unhide", problem)

    def test_an_undo_line_that_hands_over_the_scripts_is_reported(self):
        text = (self.APPLY.format(folder="hide-tv")
                + "# Undo:    pm unhide, or run tools/disable.sh examples/hide-tv/debloat.txt again\n"
                  "\ncom.a.b\n")
        root = self.tree({"hide-tv": text})
        problem = undo_problem(root / "hide-tv" / "debloat.txt")
        self.assertIsNotNone(problem)
        self.assertIn("cannot undo a hide", problem)

    def test_the_three_lists_on_main_pass_the_same_reader(self):
        for folder in ("redmi-mitv-antl0", "ffalcon-r34a0-cn", "pptv-p34a4p"):
            with self.subTest(folder=folder):
                self.assertIsNone(undo_problem(ROOT / "examples" / folder / "debloat.txt"))


class AnkerAssistantTest(unittest.TestCase):
    # The drift check's own harness: the same temporary repo, list, fake TV state and runner.
    write_list = drift.DriftCheckTest.write_list
    tv = drift.DriftCheckTest.tv
    run_check = drift.DriftCheckTest.run_check
    sent = drift.DriftCheckTest.sent
    # The recovery record test's runner for disable.sh and restore.sh, on the same repo and state.
    run_script = record_test.RecoveryRecordTest.run_script

    def setUp(self):
        drift.DriftCheckTest.setUp(self)
        self.text = (ANKER / "debloat.txt").read_text(encoding="utf-8")
        paragraph = []
        for line in header(ANKER / "debloat.txt"):
            if "Re-enabled by the firmware at every boot" in line or paragraph:
                if line.strip() == "#":
                    break
                paragraph.append(line.lstrip("#").strip())
        self.paragraph = " ".join(paragraph)

    def told_to_uncomment(self):
        self.assertIn("check.py --fix", self.paragraph)
        self.assertIn("uncomment", self.paragraph,
                      "check.py reads only active lines, so --fix needs the line uncommented first")
        told = self.paragraph.split("uncomment", 1)[1].split("check.py", 1)[0]
        return re.findall(r"com\.google\.android\.[a-z.]*[a-z]", told)

    def uncommented(self, names):
        text = self.text
        for name in names:
            # A package line that was commented out: the name alone, or followed by its own note.
            text, count = re.subn(rf"^# ({re.escape(name)})(?=\s+#|\s*$)", r"\1", text, flags=re.M)
            self.assertEqual(count, 1, f"no commented-out line for {name} in the list")
        return text

    def put_list(self, text):
        self.list_path.write_text(text, encoding="utf-8")
        listed = recovery.read_list(self.list_path)
        installed = listed + list(ASSISTANT) + [drift.CUSTOM]
        # Every listed package stays disabled; the firmware turned Assistant back on at boot.
        self.tv(installed=installed, disabled=[name for name in listed if name not in ASSISTANT],
                home=f"{drift.CUSTOM}/.MainActivity",
                home_activities=[f"{drift.CUSTOM}/.MainActivity", f"{LAUNCHERX}/.MainActivity"])

    def test_the_header_names_the_lines_to_uncomment(self):
        self.assertEqual(self.told_to_uncomment(), list(ASSISTANT))

    def test_check_fix_re_disables_assistant_once_its_lines_are_uncommented(self):
        self.put_list(self.uncommented(self.told_to_uncomment()))
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.sent(), [f"pm disable-user --user 0 {name}" for name in ASSISTANT])

    def test_undo_brings_assistant_back_after_the_header_steps(self):
        # restore.sh leaves alone every listed name the recovery record does not hold, and disable.sh
        # adds the names a list gained to the record before disabling them.
        names = self.told_to_uncomment()
        told = self.paragraph.split("uncomment", 1)[1].split("check.py", 1)[0]
        self.assertIn("run Apply again", told, "Apply records the uncommented lines for Undo")
        lines = header(ANKER / "debloat.txt")
        apply = next(line for line in lines if line.startswith("# Apply:"))
        undo = next(line for line in lines if line.startswith("# Undo:"))
        self.assertTrue(apply.endswith("tools/disable.sh examples/anker-nebula-mars-3-air/debloat.txt"))
        self.assertTrue(undo.endswith(f"tools/restore.sh examples/anker-nebula-mars-3-air/debloat.txt "
                                      f"{LAUNCHERX}"))

        for script in ("disable.sh", "restore.sh"):
            shutil.copy2(ROOT / "tools" / script, self.repo / "tools" / script)
        # The scripts call tools/venv/bin/python: run the real interpreter, which imports the fake
        # tv.py for the transport and the real recovery.py for the logic.
        client = self.repo / "tools" / "venv" / "bin" / "python"
        client.parent.mkdir(parents=True)
        client.write_text(f'#!/bin/sh\nexec "{sys.executable}" "$@"\n')
        client.chmod(0o755)

        # The first Apply, of the shipped list: the new launcher is Home and nothing listed is off yet.
        self.list_path.write_text(self.text, encoding="utf-8")
        listed = recovery.read_list(self.list_path)
        self.tv(installed=listed + list(ASSISTANT) + [drift.CUSTOM], disabled=[],
                home=f"{drift.CUSTOM}/.MainActivity",
                home_activities=[f"{drift.CUSTOM}/.MainActivity", f"{LAUNCHERX}/.MainActivity"])
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any(name in command for command in self.sent() for name in ASSISTANT))

        # Later the owner wants Assistant off: uncomment the two lines and run Apply again.
        self.list_path.write_text(self.uncommented(names), encoding="utf-8")
        result = self.run_script("disable.sh", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("added 2 names the list gained since then", result.stdout)
        applied = self.sent()
        for name in ASSISTANT:
            self.assertIn(f"pm disable-user --user 0 {name}", applied)

        # Undo, as the header's Undo line runs it.
        result = self.run_script("restore.sh", str(self.list_path), LAUNCHERX)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertNotIn("not in the record", result.stderr)
        undone = self.sent()[len(applied):]
        for name in ASSISTANT:
            self.assertIn(f"pm enable {name}", undone)

    def test_the_shipped_list_leaves_assistant_on(self):
        # The owner keeps it on so the mic button works: the fix is to the words, not the list.
        self.put_list(self.text)
        result = self.run_check("--fix", str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("nothing drifted", result.stdout)
        self.assertEqual(self.sent(), [])

    def test_the_readme_row_says_the_same(self):
        readme = (ANKER / "README.md").read_text(encoding="utf-8")
        row = next(line for line in readme.splitlines() if line.startswith("| **Back after every boot**"))
        self.assertIn("check.py --fix", row)
        self.assertIn("uncomment", row)
        for name in ASSISTANT:
            self.assertIn(name, row)
        # The run that records them for Undo comes before check.py --fix, as in the header.
        told = row.split("uncomment", 1)[1].split("check.py", 1)[0]
        self.assertIn("tools/disable.sh", told)


if __name__ == "__main__":
    unittest.main()

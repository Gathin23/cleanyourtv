"""check.py answers with three codes, and README.md's weekly job named only one of them.

README.md's "After a firmware update" hands an owner a cron line and used to say, of the run that line
makes, "it prints what drifted and exits 1 when anything did" - so on that page a non-zero exit was
drift and nothing else had a name. check.py answers `0` when the TV still matches the list, `1` when
something drifted, and `2` when the run could not check at all (the TV did not answer, nobody accepted
the "Allow USB debugging?" prompt, the list path is not there, the list disables Android's core
packages); its own source says why the two are kept apart - "exit 2, not the 1 a weekly job reads as
"drift". A job built from the page alone could therefore not tell "the TV drifted" from "the check
never ran". Two shapes that stop before any connection used to end at 1 for the same reason: with
`TV_HOST` unset the run stops in tools/tv.py with its own message, and on a computer without the ADB
client recovery.connect() stops with the line that builds the venv - both SystemExit, not Exception,
so the clause that answers 2 never saw them.

The section has since moved out of the README into docs/firmware-updates.md ("Exit codes, and a weekly
job"), which the README's "After a firmware update" links; the checks below read it there.

So: the codes are read out of tools/check.py and out of real runs, and the README section that gives
the job has to name each of them. Nothing here reads the page's wording: a number on a line that
talks about the exit the run makes is what counts, so the sentences can be rewritten freely.

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

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_drift_check import FAKE_TV  # noqa: E402  (the simulated TV check.py already runs against)

ROOT = Path(__file__).resolve().parents[1]
README = ROOT / "README.md"
GUIDE = ROOT / "docs" / "firmware-updates.md"
JOB = "## Exit codes, and a weekly job"
CHECK = ROOT / "tools" / "check.py"
STOCK = "com.google.android.tvlauncher"
CUSTOM = "com.gathin.tvhome"
HEADING = "## After a firmware update"


def literal_returns(source):
    """The codes a script returns as literals: `return 0`, `return 2`, `return 1 if ... else 0`."""
    codes = set()
    for line in source.split("\n"):
        stripped = line.split("#", 1)[0]
        for m in re.finditer(r"\breturn\s+(\d+)\b", stripped):
            codes.add(int(m.group(1)))
        for m in re.finditer(r"\belse\s+(\d+)\b", stripped):
            codes.add(int(m.group(1)))
    return codes


def readme_section(text, heading=HEADING):
    """The lines under a `## ` heading, up to the next one; None when the page has no such heading."""
    lines = text.split("\n")
    start = None
    for i, line in enumerate(lines):
        if line.strip() == heading:
            start = i + 1
            break
    if start is None:
        return None
    body = []
    for line in lines[start:]:
        if re.match(r"^##\s", line):
            break
        body.append(line)
    return body


def paragraphs(lines):
    """A paragraph's lines joined: the page wraps a sentence over several lines."""
    out, buf = [], ""
    for line in lines:
        if not line.strip():
            if buf:
                out.append(buf)
                buf = ""
            continue
        buf = (buf + " " + line.strip()).strip()
    if buf:
        out.append(buf)
    return out


def codes_named(lines):
    """Exit codes the reader of this block is told about: a number on a line about the exit made."""
    codes = set()
    for para in paragraphs(lines):
        if not re.search(r"\bexits?\b|\bexit code\b", para, re.I):
            continue
        for m in re.finditer(r"(?<![\w.])(\d)(?!\w|\.\d)", para):
            codes.add(int(m.group(1)))
    return codes


class CheckExitCodeTest(unittest.TestCase):
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

    def run_check(self, *args, host="192.168.1.50", python_args=()):
        env = dict(os.environ, HOME=str(self.home), FAKE_TV_STATE=str(self.state_path))
        env.pop("TV_HOST", None)
        if host is not None:
            env["TV_HOST"] = host
        return subprocess.run(
            [sys.executable, *python_args, str(self.repo / "tools" / "check.py"), *args],
            cwd=self.repo, env=env, capture_output=True, text=True, timeout=120,
        )

    def commands_sent(self):
        """What the run sent the (simulated) TV: empty means it was never asked."""
        return json.loads(self.state_path.read_text()).get("commands", [])

    def test_the_checker_answers_three_codes(self):
        codes = literal_returns(CHECK.read_text(encoding="utf-8"))
        self.assertEqual(codes, {0, 1, 2},
                         "check.py returns a code the README's job cannot tell apart: %s" % sorted(codes))
        self.assertIn("sys.exit(main())", CHECK.read_text(encoding="utf-8"))

    def test_the_tv_in_sync_is_0(self):
        self.tv()
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_drift_is_1(self):
        self.tv(disabled=[])
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)

    def test_a_tv_that_does_not_answer_is_2(self):
        self.tv(unreachable=True)
        result = self.run_check(str(self.list_path))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("could not reach the TV", result.stderr)

    def test_a_list_that_is_not_there_is_2(self):
        self.tv()
        result = self.run_check(str(self.base / "gone.txt"))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)

    def test_a_run_without_tv_host_is_2(self):
        # The real tv.py, not the fake: it stops at import time with its own message, before any
        # connection, so the TV was never asked and the run answers 2 - the code the page calls "a
        # check that never ran" - rather than 1, which the page tells a weekly job to mail about.
        # Which of the two messages comes back depends on the interpreter: the TV_HOST note here, or
        # recovery.py's note when adb_shell is not installed (CI runs the suite without the venv).
        source = (ROOT / "tools" / "recovery.py").read_text(encoding="utf-8")
        host_note = "Set TV_HOST to your TV's IP address"
        loader_note = "cannot load tools/tv.py"
        self.assertIn(host_note, (ROOT / "tools" / "tv.py").read_text(encoding="utf-8"))
        self.assertIn(loader_note, source)
        shutil.copy2(ROOT / "tools" / "tv.py", self.repo / "tools" / "tv.py")
        self.tv()
        result = self.run_check(str(self.list_path), host=None)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertTrue(host_note in result.stderr or loader_note in result.stderr, result.stderr)
        self.assertIn("Nothing was checked or changed.", result.stderr)
        self.assertNotIn("could not read the TV", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "", "the report of a run that never asked the TV")
        self.assertEqual([], self.commands_sent(), "the TV was asked by a run that could not read it")

    def test_a_run_without_the_adb_client_is_2(self):
        # -S drops site-packages, so adb_shell cannot be imported even under tools/venv's Python: the
        # same shape on a computer where the venv is gone, which is the other message recovery.py
        # stops with. The line that builds the venv is the whole answer the owner needs.
        shutil.copy2(ROOT / "tools" / "tv.py", self.repo / "tools" / "tv.py")
        self.tv()
        result = self.run_check(str(self.list_path), python_args=("-S",))
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertIn("cannot load tools/tv.py", result.stderr)
        self.assertIn("python3 -m venv tools/venv", result.stderr)
        self.assertIn("Nothing was checked or changed.", result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertEqual([], self.commands_sent())

    def test_the_readme_names_every_code_the_checker_can_return(self):
        self.assertIn("(docs/firmware-updates.md)", "\n".join(readme_section(README.read_text(encoding="utf-8"))))
        section = readme_section(GUIDE.read_text(encoding="utf-8"), JOB)
        self.assertIsNotNone(section, "%s is gone from docs/firmware-updates.md: the weekly job has no page" % JOB)
        self.assertGreaterEqual(len(paragraphs(section)), 4, "the section reads as nearly empty")
        named = codes_named(section)
        codes = literal_returns(CHECK.read_text(encoding="utf-8"))
        missing = sorted(codes - named)
        self.assertEqual(missing, [],
                         "check.py returns %s and the section that hands out the weekly job names %s"
                         % (sorted(codes), sorted(named)))

    def test_the_readme_still_carries_the_weekly_job(self):
        section = readme_section(GUIDE.read_text(encoding="utf-8"), JOB)
        self.assertIsNotNone(section)
        self.assertTrue(any(re.match(r"^\s*\d+\s+\d+\s+\*", line) for line in section),
                        "the cron line a home server runs is gone from the section")


class ReadmeReaderTest(unittest.TestCase):
    """The reader above, so the page test cannot pass by reading nothing."""

    def test_a_page_naming_only_drift_is_reported(self):
        page = ("## After a firmware update\n\n"
                "It prints what drifted and exits 1 when anything did.\n\n"
                "## Next\n")
        self.assertEqual(codes_named(readme_section(page)), {1})

    def test_a_page_naming_all_three_is_quiet(self):
        page = ("## After a firmware update\n\n"
                "It exits `0` in sync, `1` on drift, and `2` when it could not check.\n\n"
                "## Next\n")
        self.assertEqual(codes_named(readme_section(page)), {0, 1, 2})

    def test_a_number_without_the_word_exit_is_not_a_code(self):
        page = ("## After a firmware update\n\n"
                "The TV table holds 293 models and 83 brands.\n\n"
                "## Next\n")
        self.assertEqual(codes_named(readme_section(page)), set())

    def test_a_sentence_wrapped_over_lines_is_read_whole(self):
        page = ("## After a firmware update\n\n"
                "It exits `0` when the TV matches,\n`1` when it drifted, and `2` when the\n"
                "check could not run.\n\n"
                "## Next\n")
        self.assertEqual(codes_named(readme_section(page)), {0, 1, 2})

    def test_a_section_stops_at_the_next_heading(self):
        page = ("## After a firmware update\n\nIt exits 0.\n\n"
                "## When something goes wrong\n\nIt exits 2.\n")
        self.assertEqual(codes_named(readme_section(page)), {0})

    def test_a_page_without_the_section_reads_as_none(self):
        self.assertIsNone(readme_section("## Something else\n\nIt exits 1.\n"))

    def test_a_paragraph_without_a_blank_line_still_reads(self):
        page = ("## After a firmware update\nIt exits 1 when anything did.\n"
                "- a bullet that exits 2\n")
        self.assertEqual(codes_named(readme_section(page)), {1, 2})


if __name__ == "__main__":
    unittest.main()

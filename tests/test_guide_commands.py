"""The guides beside the scripts are held to the command rule the pages are.

tools/*.md are the pages a reader follows while using a tool: README.md's Tools table links
`tools/native-adb.md`, `tools/docker-build.md`, `tools/relay.md`, `tools/trace.md`,
`tools/flamechart.md` and `tools/screendiff.md` as each tool's [Guide], and docs/TROUBLESHOOTING.md and
docs/firmware-updates.md send a reader to native-adb.md's section by anchor. The sweep in
tests/test_documented_tool_commands.py judges the commands such a page prints - a bare `tools/x.py`
when git carries the file 100644 answers `Permission denied`, exit 126, so nothing runs - but its
PAGE_GLOBS are README*.md and the pages under docs/, themes/, examples/ and addons/. These six guides
were read by no check that runs their commands: a guide that opened a command with the bare path, the
shape #3686 corrected in eight tools' own docstrings, stayed green.

The rules, the git modes and the judgement are that module's, imported rather than copied, so a guide
is judged exactly like a page and a rule added there reaches these six too.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_documented_tool_commands import judged, refusals, tool_modes  # noqa: E402


def guides(root=ROOT):
    """The guides in tools/, the pages beside the scripts, in a stable order."""
    return sorted((Path(root) / "tools").glob("*.md"))


class GuideCommandsTest(unittest.TestCase):
    """Every tool command these guides print runs as typed, in a clone."""

    @classmethod
    def setUpClass(cls):
        cls.modes = tool_modes()
        if cls.modes is None:
            raise unittest.SkipTest("git is not installed, so the modes a clone gets cannot be read")

    def test_no_guide_gives_a_command_a_clone_cannot_run(self):
        problems, read = [], 0
        for path in guides():
            read += 1
            problems.extend(refusals(path.read_text(encoding="utf-8", errors="replace"), self.modes,
                                     page=str(path.relative_to(ROOT))))
        self.assertEqual([], problems, "\n".join(problems))
        # A walk that read nothing would pass; tools/ holds six guides today.
        self.assertGreaterEqual(read, 6, "the sweep read %d guides" % read)

    def test_the_sweep_reads_the_commands_the_guides_print(self):
        # The rule only has teeth if it sees the commands: name the ones it reads.
        seen = []
        for path in guides():
            for number, command, _, _ in judged(path.read_text(encoding="utf-8", errors="replace")):
                seen.append("%s:%d %s" % (path.relative_to(ROOT), number, command))
        self.assertGreaterEqual(len(seen), 6, "the sweep read %d tool commands:\n%s"
                                % (len(seen), "\n".join(seen)))


class GuideReaderTest(unittest.TestCase):
    """The rules have to see the shapes a guide really prints, and leave the others alone."""

    MODES = {"tools/check.py": "100644", "tools/adb-closed.py": "100755"}

    def test_a_bare_path_to_a_file_git_holds_100644_is_refused(self):
        problems = refusals("```bash\ntools/check.py examples/aipro2k/debloat.txt\n```", self.MODES,
                            page="tools/some-guide.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("tools/some-guide.md:2", problems[0])
        self.assertIn("Permission denied", problems[0])
        self.assertIn("exit 126", problems[0])

    def test_the_venv_python_and_an_executable_file_are_left_quiet(self):
        text = ("```bash\ntools/venv/bin/python -I tools/check.py examples/aipro2k/debloat.txt\n"
                "tools/adb-closed.py 192.168.1.50\n```")
        self.assertEqual([], refusals(text, self.MODES, page="tools/some-guide.md"))

    def test_a_name_tools_does_not_hold_is_refused(self):
        problems = refusals("```bash\ntools/no-such-tool.py --help\n```", self.MODES, page="tools/x.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("tools/no-such-tool.py, which tools/ does not hold", problems[0])


if __name__ == "__main__":
    unittest.main()

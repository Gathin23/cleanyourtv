"""Every reboot the docs run is the one that comes back: `tools/tv.py --reboot`.

`tools/tv.py reboot` is a shell command, and adbd stops before it sends the shell's last `CLSE`, so
the client waits for a reply that cannot arrive: the call ends in a timeout two minutes later. That
is #995 (a TCL 65C935, `G05_4K_GB`, Google TV 11, AR2101) where a chain that rebooted and then read
back the disabled list stalled there. #1005 added `tools/tv.py --reboot`, which goes through ADB's
own service and returns once the request is accepted - and four pages still handed a reader the
shell form. These tests keep every page on the mode that returns, and check the promise the pages
make ("returns once ADB accepts it") against tools/tv.py instead of trusting the prose.

A list's header is a page too: its `# Reboot:` line is what an owner pastes between batches, so the
comments in every `examples/<tv>/*.txt` are read as well. The quoted form counts:
`tools/tv.py 'reboot'` is the same shell command, written the way the repo writes every other one
(`tools/tv.py 'pm list packages -d'`).

What counts as a command: a line with no backticks on it. A page that explains the symptom has to
name the broken form, and it writes it in backticks (`tools/tv.py reboot`); a line a reader is meant
to paste does not.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_markdown_links import markdown_files  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
TV = ROOT / "tools" / "tv.py"
TROUBLE = ROOT / "docs" / "TROUBLESHOOTING.md"
README = ROOT / "README.md"

# `tv.py reboot` or `tv.py 'reboot'`, not `tv.py --reboot`: the dashes sit between the path and the word.
SHELL_REBOOT = re.compile(r"tv\.py\s+['\"]?reboot\b")

# Pages that reboot and then keep using the TV, so the next command needs it back.
CHAINS = ("docs/TROUBLESHOOTING.md", "docs/devices/durabrand-durat32isdb.md",
          "docs/devices/coop-cotv-0001.md", "docs/devices/sony-k85xr70-ae2.md")


def shell_reboots(text):
    """(line number, line) for every command in one page that reboots through the shell form."""
    return [(number, line.strip()) for number, line in enumerate(text.split("\n"), 1)
            if "`" not in line and SHELL_REBOOT.search(line)]


def mode_lines(text):
    """(line number, line) for every `tv.py --reboot` command in one page, prose left out."""
    return [(number, line.strip()) for number, line in enumerate(text.split("\n"), 1)
            if "`" not in line and "tv.py --reboot" in line]


def page(path):
    return path.read_text(encoding="utf-8")


def list_comments(text):
    """A list with each line cut to its comment, so the header is read and the line numbers stay."""
    return "\n".join(line[line.index("#"):] if "#" in line else "" for line in text.split("\n"))


def pages(root):
    """(path, text) for every page a reader runs commands from: each .md, and each list's comments."""
    root = Path(root)
    for path in markdown_files(root):
        yield path, page(path)
    for path in sorted((root / "examples").glob("*/*.txt")):
        yield path, list_comments(page(path))


class EveryPageUsesTheModeTest(unittest.TestCase):
    def test_no_page_runs_the_shell_form(self):
        found = []
        for path, text in pages(ROOT):
            for number, line in shell_reboots(text):
                found.append(f"{path.relative_to(ROOT).as_posix()}:{number}: {line}")
        self.assertEqual([], found, "use `tools/tv.py --reboot`; the shell form hangs (#995):\n"
                         + "\n".join(found))

    def test_the_reboot_chains_use_the_mode_and_say_what_it_returns(self):
        for name in CHAINS:
            text = page(ROOT / name)
            lines = mode_lines(text)
            with self.subTest(page=name):
                self.assertEqual(1, len(lines), f"{name} should reboot once through --reboot: {lines}")
                line = lines[0][1]
                self.assertRegex(line, r"#.*\bwait\b",
                                 f"{name}: the line after --reboot runs against a TV that is still "
                                 f"restarting, so the line has to say so")

    def test_the_symptom_bullet_names_the_cause_the_device_and_the_way_out(self):
        text = page(TROUBLE)
        marker = "- **`tools/tv.py reboot` never comes back.**"
        self.assertTrue(marker in text, "TROUBLESHOOTING has to name the hang a reader will hit")
        bullet = text.split(marker, 1)[1].split("\n- **", 1)[0]
        for token in ("adbd", "CLSE", "issues/995", "TCL 65C935", "G05_4K_GB",
                      "Google TV 11", "AR2101", "tools/tv.py --reboot", "tools/tv.py --probe"):
            with self.subTest(token=token):
                self.assertIn(token, bullet)

    def test_the_readme_tool_row_names_reboot(self):
        rows = [line for line in page(README).split("\n") if line.startswith("| [`tools/tv.py`]")]
        self.assertEqual(1, len(rows), "the README Tools table needs one tools/tv.py row")
        self.assertIn("reboot", rows[0], "the row is where a reader learns the mode exists")


class TheModeIsWhatThePagesPromiseTest(unittest.TestCase):
    """The prose says 'returns once ADB accepts it'; this reads that out of the tool."""

    def setUp(self):
        self.text = page(TV)

    def test_the_flag_is_a_known_argument(self):
        self.assertRegex(self.text, r'"--reboot":\s*1', "tv.py's ARITY table has to accept --reboot alone")

    def test_it_reboots_through_adb_and_closes(self):
        branch = self.text.split('if args == ["--reboot"]:', 1)
        self.assertEqual(2, len(branch), "tv.py needs the --reboot branch")
        body = branch[1].split("\n    status = 0", 1)[0]
        self.assertIn("dev.reboot()", body, "the mode sends ADB's own reboot service, not a shell command")
        self.assertIn("dev.close()", body)
        self.assertIn("wait for the TV to restart", body, "it returns before the TV is back")

    def test_the_docstring_lists_the_mode(self):
        docstring = self.text.split('"""', 2)[1]
        self.assertIn("tv.py --reboot", docstring)
        self.assertIn("return once the reboot request is accepted", docstring)


class TheCheckerItselfTest(unittest.TestCase):
    def test_a_bare_command_is_reported_with_its_line(self):
        text = "intro\n\ntools/tv.py reboot\nmore\n"
        self.assertEqual([(3, "tools/tv.py reboot")], shell_reboots(text))

    def test_the_mode_is_not_reported(self):
        self.assertEqual([], shell_reboots("tools/tv.py --reboot\n"))
        self.assertEqual([], shell_reboots("tools/venv/bin/python -I tools/tv.py --reboot\n"))

    def test_a_path_written_in_prose_is_not_a_command(self):
        self.assertEqual([], shell_reboots("`tools/tv.py reboot` never comes back.\n"))
        self.assertEqual([], shell_reboots("# - `tools/tv.py 'reboot'` never returns on this set (#995).\n"))
        self.assertEqual(1, len(shell_reboots("python -I tools/tv.py reboot\n")))

    def test_a_quoted_command_is_reported(self):
        for line in ("TV_HOST=<tv ip> tools/tv.py 'reboot'", 'tools/tv.py "reboot"',
                     r"# Reboot:  & tools\venv\Scripts\python.exe -I tools\tv.py 'reboot'"):
            with self.subTest(line=line):
                self.assertEqual([(1, line)], shell_reboots(line + "\n"))
        self.assertEqual([], shell_reboots("tools/tv.py 'pm list packages -d'\n"))

    def test_a_list_is_read_for_its_comments_with_its_line_numbers(self):
        text = ("# Apply:   tools/disable.sh <list>\n"
                "com.acme.ads   # ad SDK\n"
                "# Reboot:  tools/tv.py 'reboot'\n")
        comments = list_comments(text)
        self.assertEqual([(3, "# Reboot:  tools/tv.py 'reboot'")], shell_reboots(comments))
        self.assertNotIn("com.acme.ads", comments, "a package line is not a command")
        self.assertIn("# ad SDK", comments)

    def test_a_walk_finds_the_pages_it_is_given(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "docs" / "note.md").write_text("tools/tv.py reboot\n", encoding="utf-8")
            # A .txt that is not a TV list is not read; every list in an examples/ folder is.
            (root / "README.txt").write_text("tools/tv.py reboot\n", encoding="utf-8")
            tv = root / "examples" / "acme-tv"
            tv.mkdir(parents=True)
            (tv / "debloat.txt").write_text("# Reboot:  tools/tv.py 'reboot'\ncom.acme.ads\n", encoding="utf-8")
            (tv / "debloat-keep-cast.txt").write_text(
                "# - `tools/tv.py 'reboot'` never returns here: use --reboot\n"
                "# Reboot:  tools/tv.py --reboot\n", encoding="utf-8")
            found = [path.relative_to(root).as_posix()
                     for path, text in pages(root) if shell_reboots(text)]
            self.assertEqual(["docs/note.md", "examples/acme-tv/debloat.txt"], found)


if __name__ == "__main__":
    unittest.main()

"""Every script in tools/ answers `-h`/`--help` with its own note, on stdout, at exit 0.

A reader who wants to know what a tool does runs it with `--help`, and pipes it when the note is
longer than the screen: `tools/privacy.py --help | less`. Five of the tools read that word as a
command line they could not use instead - the two or three usage lines on stderr and exit 2 - so the
pipe showed nothing, and 2 is, in each of them, the status their own docstring gives to something
else: "nothing was sent to the TV", "a run that never asked the TV anything", "this run could not
tell". Repairing that one tool at a time is what the tree had been doing (tv.py in
tests/test_tv_help.py, check.py in tests/test_check_command_line.py, theme_restart.py in
tests/test_theme_restart.py, and blocky_wallpapers.py and scenes_wallpapers.py after they had
written into a folder called `--help`), so the next tool to grow a command line grew the same bug
and nothing went red.

This holds the whole folder to it, so a new script answers the way the rest do or fails here.

`tools/adb-closed.py` is left out on purpose: tests/test_adb_closed_address_words.py pins its own
behaviour, where `-h`/`--help` is read before the address is judged and so prints the note and
keeps exit 2 (the run cannot say anything about the port). That is the maintainer's decision, not a
shape to copy or to overrule from here.

The one module that is a library rather than a script - picture_file.py, imported by the tools and by
gen_tables.py, run by nothing - is left out too: it takes no command line, so there is no note to
answer. The three card scripts were in that list and are not libraries: readme_stats.py and
readme_card.py take `[--halloween] <font> <out>` and readme_featured.py takes `<font> <out>`
(docs/workflows/press.md prints the line, and gen_tables.py runs them as processes). Each read the
word as the font instead - "cannot open the font at --help", with the usage line on stderr - and
answered 1, the status each of them gives a run that wrote nothing, so they are read here with the
rest.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
# Imported by other tools, run by none of them: no command line, so no --help to answer.
LIBRARY = {"picture_file.py"}
# The three card scripts the sweep used to call libraries: each takes `<font> <out>` and is run as a
# process by gen_tables.py, so a help word is a reader asking for the usage line, not a font path.
CARD = {"readme_stats.py", "readme_card.py", "readme_featured.py"}
# Pinned at 2 by tests/test_adb_closed_address_words.py, which says why; see the module docstring.
EXCLUDED = {"adb-closed.py"}
# The four this rule was written for.
FIXED = {"b64push.py", "philips-api.py", "privacy.py", "recovery.py"}


def scripts(tools):
    """Every script in tools/ that takes a command line, in name order."""
    tools = Path(tools)
    return sorted(path for path in tools.glob("*.py")
                  if path.name not in LIBRARY and path.name not in EXCLUDED)


def run_tool(path, args):
    """The script run with `args` from a shell that has no TV address set: the note needs no TV, and a
    run that reached for one would be the bug this file is about."""
    env = {name: value for name, value in os.environ.items() if name not in ("TV_HOST", "TV_PORT")}
    return subprocess.run([sys.executable, "-I", str(path), *args], env=env, cwd=str(ROOT),
                          capture_output=True, text=True, timeout=120)


def help_run(path, word):
    """The script run with one help word."""
    return run_tool(path, [word])


def help_problem(path, word):
    """What is wrong with this script's answer to one help word, or "" when it answers as the rest
    of the folder does: exit 0, the note on stdout, nothing on stderr."""
    name = Path(path).name
    result = help_run(path, word)
    if result.returncode != 0:
        return (f"{name}: `{word}` exited {result.returncode}, and a reader asking what a script "
                f"does is not a failed run. Print the module docstring on stdout and return 0, as "
                f"tv.py, check.py and theme_restart.py do. "
                f"stderr said: {result.stderr.strip()[:200]}")
    if not result.stdout.strip():
        return (f"{name}: `{word}` printed nothing on stdout, so `{name} {word} | less` shows "
                f"nothing. Print the module docstring there.")
    if result.stderr.strip():
        return (f"{name}: `{word}` printed on stderr as well as the note: a reader piping stdout "
                f"sees the note, not this: {result.stderr.strip()[:200]}")
    return ""


class EveryToolsScriptTest(unittest.TestCase):
    def test_every_script_answers_help_with_its_own_note(self):
        problems = []
        for path in scripts(TOOLS):
            for word in ("-h", "--help"):
                with self.subTest(script=path.name, word=word):
                    problem = help_problem(path, word)
                    self.assertEqual("", problem, problem)
                    problems.append(problem)
        self.assertEqual([], [problem for problem in problems if problem])

    def test_the_sweep_reads_the_scripts_it_is_meant_to(self):
        """A glob that answered nothing, or that grew to skip the folder, would leave the test above
        green and empty. The folder holds 24 scripts with a command line: twenty-three are read here -
        the sixteen that already answered this way, the four this rule was written for and the three
        card scripts - and adb-closed.py is pinned in tests/test_adb_closed_address_words.py."""
        read = {path.name for path in scripts(TOOLS)}
        self.assertGreaterEqual(len(read), 23, sorted(read))
        self.assertTrue(FIXED <= read, FIXED - read)
        self.assertTrue(CARD <= read, CARD - read)
        self.assertIn("tv.py", read)
        # And the exclusions are exclusions, not an empty folder: the one library is in tools/ and is
        # not read here.
        shipped = {path.name for path in TOOLS.glob("*.py")}
        self.assertTrue(LIBRARY <= shipped, LIBRARY - shipped)
        self.assertEqual(set(), LIBRARY & read)
        self.assertEqual(set(), EXCLUDED & read)

    def test_a_card_script_that_reads_the_word_as_its_font_is_reported(self):
        """The shape the three card scripts had, on a stand-in tree: they take `<font> <out>`, so a
        help word handed to them arrived as the font's path and the run answered 1 - the status each
        of them gives a run that wrote nothing - with the usage lines on stderr. The reader has to
        name that, and to pass the shape they have now, where the word is read before the font is
        judged. Both are here because the sweep's own count cannot tell the two apart."""
        with tempfile.TemporaryDirectory() as tmp:
            tools = Path(tmp) / "tools"
            tools.mkdir()
            (tools / "foxtrot.py").write_text(
                '"usage: foxtrot.py <font.ttf> <out.png>\\n"\n'
                'import sys\n'
                'if len(sys.argv) != 3:\n'
                '    sys.stderr.write("usage: foxtrot.py <font.ttf> <out.png>\\n")\n'
                '    sys.exit(1)\n'
                'sys.exit(0)\n')
            (tools / "golf.py").write_text(
                'import sys\n'
                'if any(word in ("-h", "--help") for word in sys.argv[1:]):\n'
                '    print("usage: golf.py <font.ttf> <out.png>")\n'
                '    sys.exit(0)\n'
                'if sys.argv[1] == "missing.ttf":\n'
                '    sys.stderr.write("cannot open the font at missing.ttf\\n")\n'
                '    sys.exit(1)\n'
                'sys.exit(0)\n')
            reported = {path.name: help_problem(path, "--help") for path in scripts(tools)}
            self.assertIn("exited 1", reported["foxtrot.py"])
            self.assertIn("stdout", reported["foxtrot.py"])
            # Read before the font, so the same word answers the reader here.
            self.assertEqual("", reported["golf.py"])
            # And the font is still judged when the word really is a font path.
            self.assertIn("cannot open the font", run_tool(tools / "golf.py", ["missing.ttf"]).stderr)

    def test_a_script_that_refuses_help_is_reported(self):
        """The reader, on a stand-in tree: the shape four tools in this folder had (the usage lines
        on stderr, exit 2), the shape with the note on the wrong stream, a silent exit 0, and a
        library module that is not read whatever it does."""
        with tempfile.TemporaryDirectory() as tmp:
            tools = Path(tmp) / "tools"
            tools.mkdir()
            (tools / "alpha.py").write_text(
                '"""alpha: what it does."""\n'
                'import sys\n'
                'if sys.argv[1:] and sys.argv[1] in ("-h", "--help"):\n'
                '    sys.stderr.write("usage: alpha.py <thing>\\n")\n'
                '    sys.exit(2)\n'
                'sys.exit(0)\n')
            (tools / "bravo.py").write_text(
                '"""bravo: what it does."""\n'
                'import sys\n'
                'if sys.argv[1:] and sys.argv[1] in ("-h", "--help"):\n'
                '    print((__doc__ or "").strip())\n'
                '    sys.exit(0)\n'
                'sys.exit(0)\n')
            (tools / "charlie.py").write_text(
                '"""charlie: what it does."""\n'
                'import sys\n'
                'print((__doc__ or "").strip())\n'
                'sys.stderr.write("note: something to stderr\\n")\n'
                'sys.exit(0)\n')
            (tools / "delta.py").write_text("import sys\nsys.exit(0)\n")
            # A library module: not a script, so not read even though it would be reported.
            (tools / "picture_file.py").write_text(
                '"""a library."""\n'
                'import sys\n'
                'sys.stderr.write("usage\\n")\n'
                'sys.exit(2)\n')
            read = [path.name for path in scripts(tools)]
            self.assertEqual(["alpha.py", "bravo.py", "charlie.py", "delta.py"], read)

            reported = {path.name: help_problem(path, "--help") for path in scripts(tools)}
            self.assertIn("exited 2", reported["alpha.py"])
            self.assertIn("stdout", reported["alpha.py"])
            self.assertEqual("", reported["bravo.py"])
            self.assertIn("stderr", reported["charlie.py"])
            self.assertIn("nothing on stdout", reported["delta.py"])
            self.assertEqual("", reported["bravo.py"])
            self.assertNotIn("picture_file.py", reported)
            # -h is judged the same way, not looked for in the file.
            self.assertIn("exited 2", help_problem(tools / "alpha.py", "-h"))
            self.assertEqual("", help_problem(tools / "bravo.py", "-h"))

    def test_a_run_that_needs_no_tv_is_not_given_one(self):
        """The note is asked for before any address is read, so the sweep hands the script no
        address even when the reader's own shell has one exported: help_run() takes TV_HOST and
        TV_PORT out, and a tool that only answered once an address was set would fail here."""
        with mock.patch.dict(os.environ, {"TV_HOST": "192.0.2.1", "TV_PORT": "5555"}):
            self.assertEqual("", help_problem(TOOLS / "privacy.py", "--help"))
            self.assertEqual("", help_problem(TOOLS / "recovery.py", "-h"))
        result = help_run(TOOLS / "privacy.py", "--help")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("privacy.py --help", result.stdout)


if __name__ == "__main__":
    unittest.main()

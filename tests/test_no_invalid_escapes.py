r"""No Python file in the tree carries an invalid escape sequence, and a path in one reads as it was written.

Python keeps a backslash and the letter after it when it does not know the pair - `"\S"` is two
characters, as written - but it says so while compiling: `SyntaxWarning: invalid escape sequence '\S'`
since 3.12, a `DeprecationWarning` before that, and the sequences are deprecated in favour of becoming an
error. The valid ones are the quieter half, and they are worse: `tools\venv` in a plain string holds a
vertical tab where `\v` is, so `tools\venv\Scripts\python.exe` - the form
`tests/test_documented_tool_commands.py` wrote to show a Windows reader what to type - prints as "tools",
a line break, "env\Scripts\python.exe", and `git grep` cannot show the difference either.

The tree held exactly one such line when this file was written: that one, whose `\S` was the warning and
whose `\v` was the silent half. It writes `tools\\venv\\Scripts\\python.exe` now, and the walk below is
what would have said so.

The rule is the compiler's rather than a regular expression here, because the compiler is what reads the
file: `escapes_in()` compiles the text with warnings kept instead of filtered and reports every invalid
sequence at the line the compiler names - 3.12 as a `SyntaxWarning`, the versions before it as a
`DeprecationWarning`, so both count and the check reads the same on either. A valid escape spelling a
Windows path (`\t` in `tools\theme.ps1`, `\n` in `themes\new`) is not in here and cannot be: the
compiler accepts it, and telling a path from a newline is a guess about intent. That is the limit, and
`TheReaderTest` pins it so nobody reads this file as covering more.

Needs no TV and no interpreter beyond the one running the suite: the files are read and compiled.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import unittest
import warnings

ROOT = Path(__file__).resolve().parents[1]

# What the compiler says around the sequence it cannot read; the words have not changed since 3.6.
WARNING = "invalid escape sequence"
# The folders a contributor's checkout fills in: the venv has site-packages full of other people's escapes,
# and a build folder holds generated copies of the tree.
SKIP = {".git", "venv", "__pycache__", "build", "node_modules"}


def escapes_in(text, name="<source>"):
    """(line, message) for each invalid escape the compiler reports in TEXT, compiled as NAME."""
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        compile(text, name, "exec")
    return [(one.lineno, str(one.message)) for one in caught if WARNING in str(one.message)]


def python_files(root=ROOT):
    """Every Python file in the checkout, minus the folders SKIP names."""
    return sorted(one for one in root.rglob("*.py") if not SKIP & set(one.parts))


class TheTreeTest(unittest.TestCase):
    """The whole tree compiles without the compiler warning about a backslash it cannot read."""

    def test_no_python_file_carries_an_invalid_escape(self):
        files = python_files()
        # A walk that read nothing would pass; the tree holds over 300 Python files outside the venv.
        self.assertGreaterEqual(len(files), 300, "the walk read %d Python files" % len(files))
        problems = []
        for path in files:
            text = path.read_text(encoding="utf-8", errors="replace")
            for line, message in escapes_in(text, str(path)):
                problems.append("%s:%d %s" % (path.relative_to(ROOT), line, message))
        self.assertEqual([], problems, "Python reports an invalid escape sequence in these files, so a "
                                       "backslash in them does not read as it was written; write the "
                                       "literal raw (r\"...\") or double the backslash:\n"
                         + "\n".join(problems))

    def test_the_walk_reads_the_folders_the_tree_keeps_its_python_in(self):
        # The floor above says the walk saw files; this says which ones, since a SKIP that swallowed
        # tools/ or tests/ would still answer 300 out of the add-ons.
        folders = {one.relative_to(ROOT).parts[0] for one in python_files()}
        self.assertLessEqual({"tools", "tests", "addons"} - folders, set(),
                             "the walk read %s" % sorted(folders))

    def test_the_venv_is_not_walked(self):
        read = [one for one in python_files() if "venv" in one.parts or ".git" in one.parts]
        self.assertEqual([], [str(one) for one in read])


class TheReaderTest(unittest.TestCase):
    """The rule itself, on sources written here: what is reported, at which line, and what is not."""

    def test_an_unknown_escape_is_named(self):
        self.assertEqual([(1, "invalid escape sequence '\\S'")],
                         escapes_in('print("a\\Sb")'))

    def test_an_unknown_escape_in_a_docstring_is_named(self):
        # The shape the tree had: a docstring on line 1 quoting a Windows path.
        found = escapes_in('"""The venv is at tools\\venv\\Scripts\\python.exe."""\n')
        self.assertEqual(1, len(found), found)
        self.assertEqual(1, found[0][0])
        self.assertIn("\\S", found[0][1])

    def test_the_line_is_the_literals_line_not_the_files_first(self):
        found = escapes_in('one = 1\ntwo = 2\nthree = "x\\dy"\n')
        self.assertEqual([3], [line for line, _ in found], found)

    def test_a_raw_string_is_left_alone(self):
        # r"..." is how a Windows path is meant to be written: the backslashes are the text.
        self.assertEqual([], escapes_in('path = r"tools\\venv\\Scripts\\python.exe"'))

    def test_a_doubled_backslash_is_left_alone(self):
        # What the docstring writes now: "tools\\venv" in the source, "tools\venv" in the text.
        self.assertEqual([], escapes_in('path = "tools\\\\venv\\\\Scripts\\\\python.exe"'))

    def test_the_escapes_python_knows_are_left_alone(self):
        for source in ('x = "a\\nb"', 'x = "a\\tb"', 'x = "a\\\\b"', 'x = "\\x41"', 'x = "\\u00e9"',
                       'x = "\\N{BULLET}"', 'x = "\\0"', 'x = "a\\"b"', "x = 'a\\'b'", 'x = "\\r\\f\\a\\b\\v"'):
            self.assertEqual([], escapes_in(source), source)

    def test_a_valid_escape_spelling_a_windows_path_is_not_reported(self):
        # The limit, pinned: `\t` in tools\theme.ps1 is a tab to the compiler, and telling it from a
        # newline is a guess about intent. The walk cannot be read as covering this.
        self.assertEqual([], escapes_in('page = "tools\\theme.ps1"'))
        self.assertEqual([], escapes_in('page = "themes\\new"'))

    def test_only_the_sequence_python_names_is_reported(self):
        # `\v` is valid and `\S` is not, so the one line names the one it cannot read.
        found = escapes_in('x = "tools\\venv\\Scripts"\n')
        self.assertEqual(1, len(found), found)
        self.assertIn("\\S", found[0][1])
        self.assertNotIn("\\v", found[0][1])

    def test_a_file_that_compiles_is_not_reported(self):
        self.assertEqual([], escapes_in('"""A docstring with no backslash at all."""\nX = 1\n'))


if __name__ == "__main__":
    unittest.main()

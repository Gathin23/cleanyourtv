"""The command a tool's own usage block shows runs as typed, from a clone.

`tests/test_documented_tool_commands.py` holds the commands the pages print to the Python that runs them,
and `tests/test_printed_commands_run_as_typed.py` holds the next-step lines the tools print at run time.
The usage block at the top of each `tools/*.py` was the surface nothing read, and four of them showed a
command a clone cannot run:

    $ tools/screendiff.py shots/before.png shots/after.png
    bash: tools/screendiff.py: Permission denied          (exit 126)

`screendiff.py`, `flamechart.py`, `trace.py` and `trace_report.py` are 100644 in git, as every tool but
`tools/tv.py`, `tools/adb-closed.py` and the shell scripts is, so the bare path in their own header is a
command that stops before anything is read. The other four have the second half of the problem:
`blocky_wallpapers.py`, `readme_card.py`, `readme_stats.py` and `readme_featured.py` print
`tools/venv/bin/pip install -r tools/requirements-perf.txt` when Pillow is missing - the venv's pip, which
is where the recipe puts Pillow - and their usage line said `python3 tools/<x>.py`, the interpreter that
just failed to import it. Following the advice therefore bought the same message again, because the venv's
Pillow is not on that Python's path. Both halves now print the form the pages and the run-time hints use,
`tools/venv/bin/python -I tools/<x>.py`, so a reader who copies a usage line runs the tool the way its own
message says to.

Two forms are left alone on purpose. `tools/bench.py` and `tools/trace.py` re-exec themselves under the
venv's Python (`os.execv`), so a `python3` line on either runs - and trace.py's bare `tools/trace.py` was
fixed because a 100644 file cannot start at all. A re-exec rescues the packages, not the name of the
interpreter: `python` on its own is a command neither a stock Ubuntu 24.04 (its python3 is `/usr/bin/
python3`; `bash: python: command not found`, 127) nor macOS has, so bench.py's three usage lines -
`TV_HOST=... python tools/bench.py ...` - were the one command in the tree that no reader on those
systems could run, and the exemption above read them as fine. They now name the venv's Python like the
others, as docs/workflows/perf-ab.md already did, and a bare `python` in a usage block is refused here
whatever the file does with `os.execv`.
`tools/scenes_wallpapers.py` needs nothing from the venv (it draws SVG with the standard library), so
`python3` is the right interpreter there. `tools/wallpaper_prep.py` was let through on the same reading,
because its header told the reader to `python -m pip install` its own requirements - and that line does not
run either: on a stock Ubuntu 24.04 there is no `python` command at all (`bash: python: command not found`,
127), its python3 has no `pip` module (PEP 668 leaves the distro externally managed), and the Pillow the tool
needs is installed by the venv's pip. Its usage block and its two missing-package messages now print
`tools/venv/bin/python -I tools/wallpaper_prep.py` and `tools/venv/bin/pip install -r
tools/requirements-photos.txt`, and this check reads a tool's own `python -m pip install` as well as the
venv's, so a header cannot be excused by telling the reader to install somewhere the run cannot read.
A synopsis such as `b64push.py <local file> <remote path>` names the program rather than a path in the
checkout, and is not a command a reader copies.

Needs no TV and no interpreter: git's modes and the files' own text are the whole check.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import ast
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"

# A command as the usage blocks write it: the program, then its arguments. A leading ENV=value is part of
# the command (bench.py and trace.py show TV_HOST=... in front), so it is stepped over.
ASSIGNMENT = re.compile(r"[A-Z_][A-Z0-9_]*=\S*")
VIRTUAL_PYTHON = "tools/venv/bin/python"
VIRTUAL_PYTHON_REGEX = re.compile(r"(?:\./)?tools/venv/bin/python")
# The program a line starts with. `tools\x.py` is the Windows spelling the pages use in places.
SCRIPT = re.compile(r"(?:\./)?tools[/\\]([A-Za-z0-9_.-]+)\.py")
INTERPRETER = re.compile(r"(?:python(?:3(?:\.\d+)?)?|py)")
# What a tool says to run when the package is missing. The venv's pip is the one the recipe installs into,
# so a usage line that names another Python cannot import what this line installs. A tool that prints its
# own `python -m pip install -r tools/requirements...` is read the same way: that pip is not installed on a
# stock Ubuntu 24.04 (PEP 668) and would not be the one the venv's Python reads anyway.
VENV_PIP = re.compile(r"(?:tools/venv/bin/pip|python3?(?:\.\d+)? -m pip) install -r tools/requirements")
# `python` alone is not a command on a stock Ubuntu 24.04 (no /usr/bin/python - the interpreter is
# /usr/bin/python3) or on macOS: the shell answers `python: command not found`, exit 127, before the
# script is read. A file that re-execs under the venv rescues the packages, not the name, so a bare
# `python` usage line is refused whatever the file does with os.execv.
BARE_PYTHON = re.compile(r"python|py")
# bench.py and trace.py start again under that venv instead of asking the reader to: a `python3` line is
# then a command that runs.
RE_EXEC = re.compile(r"\bos\.execv\(")
# An install of the tools' requirements, as the tree prints it in a usage block or a message, and the venv's
# pip as the one spelling a hint is allowed to use.
INSTALL_HINT = re.compile(r"install -r tools/requirements[-\w.]*\.txt")
VENV_PIP_PREFIX = "tools/venv/bin/pip install"


def docstring(path):
    """The module docstring of a tools/ script; "" when it has none.

    Read unclean: `ast.get_docstring`'s clean=True runs the text through `inspect.cleandoc`, which strips
    the first line and then dedents everything after it by the smallest margin it finds - so a usage block
    whose every line is indented (the shape a tool written as one indented block gets) loses the four
    spaces that make it a command here, and the check goes quiet on it. The indentation is part of what a
    reader copies, so the file's own text is what is judged.
    """
    return ast.get_docstring(ast.parse(Path(path).read_text(encoding="utf-8")), clean=False) or ""


def tool_modes(root=ROOT):
    """git's mode for every file under tools/, as {"tools/check.py": "100644", ...}; None without git."""
    try:
        run = subprocess.run(["git", "ls-files", "-s", "--", "tools"], cwd=str(root),
                             capture_output=True, text=True)
    except (FileNotFoundError, OSError):
        return None
    if run.returncode != 0:
        return None
    modes = {}
    for line in run.stdout.splitlines():
        meta, path = line.split("\t", 1)
        modes[path] = meta.split()[0]
    return modes or None


def command_lines(text):
    """[(line, program, words)] for each line of TEXT that gives a command, assignments stepped over.

    Three shapes carry a command in a tool's docstring. The usage block is indented: `    tools/trace.py
    home --runs 5 ...`, and the flag reference under it starts with a dash, which is an argument rather
    than a command. The image scripts write theirs into a sentence - `Update STATS, then: python3
    tools/readme_stats.py Fredoka.ttf docs/stats.png` - so a line with a colon is read from after it, and
    the same sentence broken in two (`... first, then:` then `python3 tools/readme_card.py ...` on the
    next line) is read whole. Prose that names a tool ("tools/bench.py gives the headline numbers ...")
    is none of these, and stays out: every command this check cares about is a path a reader copies, not
    a mention.
    """
    found = []
    previous = ""
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if len(line) - len(line.lstrip()) >= 4:
            candidate = stripped
        elif ":" in line:
            candidate = line.rsplit(":", 1)[1].strip()
        elif previous.endswith(":"):
            candidate = stripped
        else:
            previous = stripped
            continue
        previous = stripped
        words = candidate.split()
        while words and ASSIGNMENT.fullmatch(words[0]):
            words.pop(0)
        if not words:
            continue
        if not (VIRTUAL_PYTHON_REGEX.fullmatch(words[0]) or SCRIPT.fullmatch(words[0])
                or INTERPRETER.fullmatch(words[0])):
            continue
        found.append((candidate, words[0], words))
    return found


def problems(name, text, mode, source):
    """Why the command lines of a tool's usage block would not run as typed; [] when they would."""
    found = []
    for line, program, words in command_lines(text):
        script = SCRIPT.fullmatch(program)
        if script:
            path = "tools/" + script.group(1) + ".py"
            if mode is not None and mode != "100755":
                found.append(f"{name}: the usage line starts {program} itself, and git has {path} as {mode}, "
                             f"so a clone answers `Permission denied` (exit 126). Write "
                             f"`{VIRTUAL_PYTHON} -I {path}`.")
            continue
        if not INTERPRETER.fullmatch(program):
            continue
        named = next((path for path in (SCRIPT.search(word) for word in words[1:]) if path), None)
        if not named:
            continue              # `python3 -m venv tools/venv`: a setup step, not this tool
        path = "tools/" + named.group(1) + ".py"
        if BARE_PYTHON.fullmatch(program):
            found.append(f"{name}: the usage line starts `{program} {path}`, and `{program}` is not a "
                         f"command a stock Ubuntu 24.04 or macOS has - the shell answers `command not "
                         f"found` (exit 127) before the script is read, and running the file again under "
                         f"tools/venv's Python cannot help with a name the shell never resolves. Write "
                         f"`{VIRTUAL_PYTHON} -I {path}`.")
            continue
        if VENV_PIP.search(source) and not RE_EXEC.search(source):
            found.append(f"{name}: the usage line starts `{program} {path}`, but the same file tells the "
                         f"reader to install its requirements with a `pip install -r tools/requirements-*.txt` "
                         f"for a Python that is not this one: the package lands where `{program}` cannot "
                         f"import it, so the run answers with the message again. Write "
                         f"`{VIRTUAL_PYTHON} -I {path}`.")
    return found


def offenders(root, modes, names=None):
    """{name: [problem]} for every tool under ROOT whose usage block gives a command that would not run."""
    root = Path(root)
    out = {}
    for path in sorted((root / "tools").glob("*.py")):
        if names is not None and path.name not in names:
            continue
        text = docstring(path)
        source = path.read_text(encoding="utf-8")
        mode = (modes or {}).get(f"tools/{path.name}")
        found = problems(path.name, text, mode, source)
        if found:
            out[path.name] = found
    return out


class ToolUsageTest(unittest.TestCase):
    def test_every_usage_command_runs_as_typed(self):
        modes = tool_modes()
        if modes is None:
            self.skipTest("git is not available, so the modes of the tools cannot be read")
        found = offenders(ROOT, modes)
        self.assertEqual({}, found, "\n".join(line for lines in found.values() for line in lines))

    def test_the_sweep_reads_the_tools_usage_blocks(self):
        modes = tool_modes() or {}

        def names_a_script(path):
            return any(SCRIPT.search(word) for _, _, words in command_lines(docstring(path))
                       for word in words[1:])

        read = [path.name for path in TOOLS.glob("*.py") if names_a_script(path)]
        self.assertGreaterEqual(len(read), 8, f"fewer usage blocks read than the tree has: {read}")
        for name in ("screendiff.py", "flamechart.py", "trace.py", "trace_report.py", "readme_card.py"):
            self.assertIn(name, read)

    def test_every_install_hint_names_the_venv_pip(self):
        """A tool that prints an install of tools/requirements names the venv's pip, not its own.

        The judging rule above reads a tool's pip hint; this is the other direction, so a header that says
        `python -m pip install -r tools/requirements-photos.txt` cannot be excused as "the tool tells the
        reader to install it" - on a stock Ubuntu 24.04 that pip does not exist and the Pillow lands
        somewhere the interpreter the tool prints cannot import from.
        """
        hints = []
        for path in sorted(TOOLS.glob("*.py")):
            for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if INSTALL_HINT.search(line):
                    hints.append((path.name, number, line.strip()))
        self.assertGreaterEqual(len(hints), 5, f"fewer install hints read than the tree prints: {hints}")
        wrong = [f"{name}:{number}: {line}" for name, number, line in hints
                 if VENV_PIP_PREFIX not in line]
        self.assertEqual([], wrong, "\n".join(wrong))


class ReaderTest(unittest.TestCase):
    """A stand-in tools/ folder, so each shape is judged without the real tree behind it."""

    def write(self, name, doc, body="print('hi')\n"):
        folder = Path(self.base) / "tools"
        folder.mkdir(exist_ok=True)
        path = folder / name
        path.write_text(f'"""{doc}"""\n{body}', encoding="utf-8")
        return path

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.base = self.temp.name

    def tearDown(self):
        self.temp.cleanup()

    def test_a_bare_script_git_keeps_100644_is_reported(self):
        self.write("screendiff.py",
                   "Compares two screenshots.\n\n    tools/screendiff.py a.png b.png --out pair.png\n")
        found = offenders(self.base, {"tools/screendiff.py": "100644"})
        self.assertIn("screendiff.py", found)
        self.assertIn("answers `Permission denied` (exit 126)", found["screendiff.py"][0])
        self.assertIn("tools/venv/bin/python -I tools/screendiff.py", found["screendiff.py"][0])

    def test_a_bare_script_git_keeps_100755_is_quiet(self):
        self.write("tv.py", "The ADB client.\n\n    tools/tv.py --probe\n")
        self.assertEqual({}, offenders(self.base, {"tools/tv.py": "100755"}))

    def test_a_python3_line_on_a_script_that_installs_into_the_venv_is_reported(self):
        self.write("readme_card.py",
                   "Stacks the card.\n\n    python3 tools/readme_card.py Font.ttf docs/card.jpg\n",
                   body='import sys\n\ndef load():\n    try:\n        import PIL\n    except ImportError:\n'
                        '        sys.exit("readme_card.py needs Pillow: tools/venv/bin/pip install -r '
                        'tools/requirements-perf.txt")\n')
        found = offenders(self.base, {"tools/readme_card.py": "100644"})
        self.assertIn("readme_card.py", found)
        self.assertIn("tools/venv/bin/python -I tools/readme_card.py", found["readme_card.py"][0])

    def test_a_python_line_on_a_script_that_prints_its_own_pip_is_reported(self):
        # The shape tools/wallpaper_prep.py had: the header told the reader to install with the same
        # interpreter that had just failed to import Pillow, which on a stock Ubuntu 24.04 does not
        # even exist as `python` and has no pip module under `python3`.
        self.write("wallpaper_prep.py",
                   "Turns photos into wallpapers.\n\n    python tools/wallpaper_prep.py ~/Pictures/trip out\n",
                   body='import sys\n\ndef load():\n    try:\n        import PIL\n    except ImportError:\n'
                        '        sys.exit("Pillow is missing: python -m pip install -r '
                        'tools/requirements-photos.txt")\n')
        found = offenders(self.base, {"tools/wallpaper_prep.py": "100644"})
        self.assertIn("wallpaper_prep.py", found)
        self.assertIn("tools/venv/bin/python -I tools/wallpaper_prep.py", found["wallpaper_prep.py"][0])

    def test_the_venv_form_is_quiet(self):
        self.write("readme_card.py",
                   "Stacks the card.\n\n    tools/venv/bin/python -I tools/readme_card.py Font.ttf docs/card.jpg\n",
                   body='import sys\n\ndef load():\n    try:\n        import PIL\n    except ImportError:\n'
                        '        sys.exit("needs Pillow: tools/venv/bin/pip install -r '
                        'tools/requirements-perf.txt")\n')
        self.assertEqual({}, offenders(self.base, {"tools/readme_card.py": "100644"}))

    def test_a_command_under_a_line_ending_in_a_colon_is_read(self):
        self.write("readme_card.py",
                   "Stacks the card. Run the two strips first, then:\n"
                   "python3 tools/readme_card.py Font.ttf docs/card.jpg\n",
                   body='import sys\n\ndef load():\n    try:\n        import PIL\n    except ImportError:\n'
                        '        sys.exit("readme_card.py needs Pillow: tools/venv/bin/pip install -r '
                        'tools/requirements-perf.txt")\n')
        found = offenders(self.base, {"tools/readme_card.py": "100644"})
        self.assertIn("readme_card.py", found)

    def test_a_python3_line_on_a_script_that_starts_again_under_the_venv_is_quiet(self):
        self.write("bench.py",
                   "Measures the launcher.\n\n    TV_HOST=192.168.1.50 python3 tools/bench.py --keys 36 --series 3\n",
                   body='import os\n\ndef again():\n    os.execv("/venv/bin/python", ["python"])\n'
                        'MSG = "needs adb_shell: tools/venv/bin/pip install -r tools/requirements.txt"\n')
        self.assertEqual({}, offenders(self.base, {"tools/bench.py": "100644"}))

    def test_a_python_line_is_reported_even_when_the_script_starts_again(self):
        # bench.py's shape at b9538048, and what a re-exec cannot fix: /usr/bin has python3 and no
        # python on the CI runner, so the line its header printed answered the shell, not the script.
        self.write("bench.py",
                   "Measures the launcher.\n\n    TV_HOST=192.168.1.50 python tools/bench.py --keys 36 --series 3\n",
                   body='import os\n\ndef again():\n    os.execv("/venv/bin/python", ["python"])\n'
                        'MSG = "needs adb_shell: tools/venv/bin/pip install -r tools/requirements.txt"\n')
        found = offenders(self.base, {"tools/bench.py": "100644"})
        self.assertIn("bench.py", found)
        self.assertIn("command not found", found["bench.py"][0])
        self.assertIn("tools/venv/bin/python -I tools/bench.py", found["bench.py"][0])

    def test_a_prose_line_that_names_a_tool_is_not_a_command(self):
        self.write("b64push.py", "The TV is reached through tools/tv.py, the client this project ships.\n"
                                 "    not a file stops the run with `not a file: <path>` (tools/trace_report.py).\n")
        self.assertEqual({}, offenders(self.base, {"tools/b64push.py": "100644"}))

    def test_a_setup_step_that_makes_the_venv_is_not_a_usage_line(self):
        self.write("trace.py", "Records traces.\n\n    python3 -m venv tools/venv && tools/venv/bin/pip install -r "
                               "tools/requirements.txt\n")
        self.assertEqual({}, offenders(self.base, {"tools/trace.py": "100644"}))


if __name__ == "__main__":
    unittest.main()

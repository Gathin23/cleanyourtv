"""Every `tools/<x>.py` command on a page a reader follows names the Python that runs it.

A clone cannot run the scripts bare: `git ls-files -s tools` has all of them as 100644 except
`tools/tv.py`, which is 100755 with the venv restart `tests/test_tv_runs_as_documented.py` exists for.
So a page that writes `tools/check.py --fix` hands the reader this:

    $ tools/check.py --fix list.txt
    bash: tools/check.py: Permission denied          (exit 126)

and the card scripts, made executable instead, would be started by the first python3 on PATH rather
than the venv docs/workflows/press.md has just installed Pillow into. A page that writes `"$PY" -I
tools/tv.py ...` without ever setting PY is worse: the shell runs `"" -I tools/tv.py ...`, which is
`bash: : command not found`, so nothing reaches the TV at all.

Ten commands on seven pages gave a 100644 script bare (docs/TROUBLESHOOTING.md, both pages under
docs/workflows/, themes/README.md twice, examples/anker-nebula-mars-3-air/README.md,
docs/devices/skyworth-7t861-a23.md twice and examples/tcl-65c8l-google-tv/README.md), and eleven on
four device notes ran on a `$PY` those pages never set (tcl-beyondtv4-uk-ar05, xiaomi-mitv-mftr0-hyperos3,
xiaomi-mitv-mteq0-baymax, xiaomi-mitv4-ansm0). All of them name `tools/venv/bin/python -I tools/<x>.py`
now, the form README.md's Tools table and docs/TROUBLESHOOTING.md's own
`tools/venv/bin/python -I tools/check.py <list>` already used; a page may still set `PY=` to the venv's
Python and use `"$PY"` (examples/skyworth-7s60-a5 and docs/devices/xiaomi-mitv-aetp0-dofus do).

The sweep reads the pages a reader follows - README*.md, docs/, themes/, examples/, addons/ - for a code
span or a code-block line that runs a tool with an argument, and judges what stands in front of the
script: a Python (its path or a variable the page sets to one), or the script's own 100755 mode. A span
that is only the path (`tools/gen_tables.py`, which a maintainer runs after merging) is a mention, not a
command. PROMPT.md and .github/maintainer/MAINTAINER.md run `tools/relay.py` and `tools/gen_tables.py`
bare too and are the maintainer's files (AGENTS.md, "Safety rules ... CI and workflows"), so they are not
in the sweep.

Needs no TV and no interpreter: the modes are git's and the pages are read.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import glob
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]

# The pages a reader follows: the READMEs, the notes and workflows under docs/, and every folder page.
PAGE_GLOBS = ("README*.md", "docs/**/*.md", "themes/**/*.md", "examples/**/*.md", "addons/**/*.md")

SPAN = re.compile(r"`([^`\n]+)`")
# The script a command runs, in the forms the pages use: tools/x.py, ./tools/x.py, tools\x.py.
SCRIPT = re.compile(r"(?:\./)?tools[/\\]([A-Za-z0-9_.-]+)\.py")
# A shell assignment of an interpreter, and a variable used as one: PY=tools/venv/bin/python, "$PY" -I ...
ASSIGNMENT = re.compile(r"\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(\S+)")
VARIABLE = re.compile(r"\$(?:\{([A-Za-z_][A-Za-z0-9_]*)\}|([A-Za-z_][A-Za-z0-9_]*))")


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


def code_commands(text):
    """(line number, candidate) for each code span and code-block line in PAGE."""
    found = []
    fenced = False
    for number, line in enumerate(text.splitlines(), 1):
        if line.strip().startswith("```"):
            fenced = not fenced
            continue
        if fenced:
            found.append((number, line.strip()))
        else:
            found.extend((number, span) for span in SPAN.findall(line))
    return found


def judged(text):
    """(line number, command, script, where the script starts) for each tool command the page gives."""
    found = []
    for number, candidate in code_commands(text):
        command = candidate.strip()
        match = SCRIPT.search(command)
        if not match:
            continue
        if not command[match.end():][:1].isspace():
            continue          # the path alone, or a path with a comment after it: a mention, not a command
        found.append((number, command, "tools/" + match.group(1) + ".py", match.start()))
    return found


def assignments(text):
    """The shell variables PAGE sets, as {"PY": "tools/venv/bin/python", ...}."""
    found = {}
    for line in text.splitlines():
        match = ASSIGNMENT.match(line)
        if match:
            found[match.group(1)] = match.group(2)
    return found


def refusals(text, modes, page=""):
    """Why the commands PAGE gives would not run as typed; [] when every one of them would."""
    variables = assignments(text)
    problems = []
    for number, command, script, start in judged(text):
        prefix = command[:start]
        where = f"{page}:{number}" if page else f"line {number}"
        if "python" in prefix.lower():
            continue          # `tools/venv/bin/python -I tools/x.py ...`
        named = [one or two for one, two in VARIABLE.findall(prefix)]
        if named:
            # `"$PY" -I tools/x.py ...`: fine when the page sets PY to an interpreter.
            unset = [name for name in named if name not in variables]
            if unset:
                problems.append(f"{where}: `{command}` runs on ${unset[0]}, which the page never sets: "
                                f"the shell answers `command not found`, so nothing reaches the TV. Set "
                                f"{unset[0]}=tools/venv/bin/python, or name the venv's Python in the command")
            elif not any("python" in variables[name] for name in named):
                problems.append(f"{where}: `{command}` runs on ${named[0]}, which the page sets to "
                                f"{variables[named[0]]}, not a Python")
            continue
        mode = modes.get(script)
        if mode == "100755":
            continue          # tv.py starts again under tools/venv itself
        if mode is None:
            problems.append(f"{where}: `{command}` names {script}, which tools/ does not hold")
        else:
            problems.append(f"{where}: `{command}` starts {script}, which git has as {mode}: "
                            f"the shell answers `Permission denied` (exit 126). Name the venv's Python, "
                            f"e.g. tools/venv/bin/python -I {script} ...")
    return problems


def pages(root=ROOT):
    found = []
    for pattern in PAGE_GLOBS:
        found.extend(glob.glob(str(root / pattern), recursive=True))
    return sorted(set(found))


class DocumentedCommandsTest(unittest.TestCase):
    """Every tool command on the pages a reader follows runs as typed, in a clone."""

    @classmethod
    def setUpClass(cls):
        cls.modes = tool_modes()
        if cls.modes is None:
            raise unittest.SkipTest("git is not installed, so the modes a clone gets cannot be read")

    def test_no_page_gives_a_command_a_clone_cannot_run(self):
        problems, read = [], 0
        for path in pages():
            read += 1
            text = Path(path).read_text(encoding="utf-8", errors="replace")
            problems.extend(refusals(text, self.modes, page=str(Path(path).relative_to(ROOT))))
        self.assertEqual([], problems, "\n".join(problems))
        # A walk that read nothing would pass; the tree has 200 pages under these globs.
        self.assertGreaterEqual(read, 200, "the sweep read %d pages" % read)

    def test_the_sweep_reads_the_tool_commands_it_was_written_for(self):
        # The rule only has teeth if it sees the commands: count the ones it reads, prefix included.
        seen = []
        for path in pages():
            for number, command, script, _ in judged(Path(path).read_text(encoding="utf-8", errors="replace")):
                seen.append("%s:%d %s" % (Path(path).relative_to(ROOT), number, command))
        self.assertGreaterEqual(len(seen), 40, "the sweep read %d tool commands:\n%s" % (len(seen), "\n".join(seen)))


class ReaderTest(unittest.TestCase):
    """The rule itself, on pages written here: a command is judged, a mention is not."""

    MODES = {"tools/check.py": "100644", "tools/readme_card.py": "100644", "tools/tv.py": "100755"}

    def test_a_bare_command_is_refused(self):
        problems = refusals("Run `tools/check.py --fix list.txt` on that list.", self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("page.md:1", problems[0])
        self.assertIn("tools/check.py", problems[0])
        self.assertIn("100644", problems[0])
        self.assertIn("Permission denied", problems[0])

    def test_the_venv_python_in_front_is_what_the_rule_wants(self):
        for text in ("Run `tools/venv/bin/python -I tools/check.py --fix list.txt` on that list.",
                     "   tools/venv/bin/python -I tools/check.py --fix list.txt\n",
                     "`TV_HOST=192.168.1.50 tools/venv/bin/python -I tools/check.py`"):
            self.assertEqual([], refusals(text, self.MODES, "page.md"), text)

    def test_an_environment_prefix_does_not_make_a_bare_command_run(self):
        problems = refusals("`TV_HOST=192.168.1.50 tools/check.py list.txt`", self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)

    def test_a_command_inside_a_code_block_is_judged_too(self):
        problems = refusals("   ```bash\n   TV_HOST=<tv ip> tools/readme_card.py font.ttf card.jpg\n   ```",
                            self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("page.md:2", problems[0])

    def test_an_executable_tool_may_be_given_bare(self):
        self.assertEqual([], refusals("`tools/tv.py --probe` prints them.", self.MODES, "page.md"))

    def test_a_mention_without_an_argument_is_not_a_command(self):
        for text in ("`tools/gen_tables.py`, which a maintainer runs after merging.",
                     "| [`tools/readme_card.py`](tools/readme_card.py) | Draws the card. |"):
            self.assertEqual([], refusals(text, self.MODES, "page.md"), text)

    def test_a_script_the_tree_does_not_hold_is_named(self):
        problems = refusals("`tools/no_such_tool.py --flag`", self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("which tools/ does not hold", problems[0])

    def test_a_variable_the_page_sets_to_a_python_is_accepted(self):
        text = ("```bash\nPY=tools/venv/bin/python\n"
                '"$PY" -I tools/check.py --fix list.txt\n```\n')
        self.assertEqual([], refusals(text, self.MODES, "page.md"))

    def test_a_variable_the_page_never_sets_is_named(self):
        problems = refusals('```bash\n"$PY" -I tools/check.py --fix list.txt\n```\n', self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("$PY, which the page never sets", problems[0])
        self.assertIn("command not found", problems[0])

    def test_a_variable_set_to_something_else_is_named(self):
        text = '```bash\nPY=/bin/true\n"$PY" -I tools/check.py --fix list.txt\n```\n'
        problems = refusals(text, self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("which the page sets to /bin/true, not a Python", problems[0])


if __name__ == "__main__":
    unittest.main()

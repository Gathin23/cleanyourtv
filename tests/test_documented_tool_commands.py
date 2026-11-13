"""Every command on a page a reader follows that runs a tool from `tools/` runs in a clone.

A clone cannot run the Python scripts bare: `git ls-files -s tools` has them as 100644 except
`tools/tv.py` and `tools/adb-closed.py`, which are 100755 - tv.py with the venv restart
`tests/test_tv_runs_as_documented.py` exists for, adb-closed.py because pages run it bare
(`tools/adb-closed.py <tv ip>`) and it needs only the standard library.
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

"A Python" counted the name `python` itself, and that name is not a command: a stock Ubuntu 24.04 has
/usr/bin/python3 and no /usr/bin/python, and macOS ships no `python` without the developer tools, so a
page that writes `python tools/tv.py 'pm enable ...'` hands the reader `command not found`, exit 127,
before the script is read - and a script that starts itself again under the venv rescues the packages, not
a name the shell never resolves. Three commands on two pages opened that way, and the sweep could not see
them because `python` was the interpreter it was looking for: examples/tcl-32l5a/README.md's two
powershell blocks, on a page whose Windows siblings print the venv's `tools\venv\Scripts\python.exe`, and
addons/sleep-timer/README.md's sh block. The bare name is refused now; `python3`, a command both systems
have, is left to the mode rule.

The four shell scripts are the other half, and they were unread: the pattern above ended at `.py`, though
`tools/disable.sh`, `restore.sh`, `theme.sh` and `docker-build.sh` are what 301 commands on these pages
run - the debloat, the undo and the theme push, all of them bare, as `TV_HOST=<tv ip> tools/disable.sh
<list>` (the four are 100755 in git, which is the only reason those commands work). A page may put `bash`
in front instead, and that reads the file whatever its mode; without one, the mode is the rule, as it is
for `.py`. Nothing read either half of that, so a script whose mode was lost - the way tools/tv.py lost
its own twice - would leave every page printing `Permission denied` and the sweep green. The modes are
still git's, and the walk is still the pages.

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
# The script a command runs, in the forms the pages use: tools/x.py, ./tools/x.py, tools\x.py - and the
# shell scripts beside them, which a page runs bare because git carries them executable.
SCRIPT = re.compile(r"(?:\./)?tools[/\\]([A-Za-z0-9_.-]+)\.(py|sh)")
# A shell named in front of a shell script: `bash tools/disable.sh <list>` reads the file whatever its mode.
# The path form is allowed (/bin/sh), and so are flags (bash -e); the name has to end the prefix.
SHELL = re.compile(r"(?:^|[\s;&|/])(?:bash|sh|dash|zsh|ksh)(?:\s+-[A-Za-z]+)*\s*$")
# A shell assignment of an interpreter, and a variable used as one: PY=tools/venv/bin/python, "$PY" -I ...
ASSIGNMENT = re.compile(r"\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(\S+)")
VARIABLE = re.compile(r"\$(?:\{([A-Za-z_][A-Za-z0-9_]*)\}|([A-Za-z_][A-Za-z0-9_]*))")
# The interpreter names a stock Ubuntu or macOS does not have: Ubuntu 24.04's is /usr/bin/python3, and macOS
# ships no `python` without the developer tools, so `python tools/x.py` is `command not found` (exit 127)
# before the script is read. A path, or a variable the page sets, is judged by the rule below; `python3` is
# a command on both, so it is not in here.
BARE_PYTHON = re.compile(r"(?:python|python2|py)")
VIRTUAL_PYTHON = "tools/venv/bin/python"


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
        found.append((number, command, "tools/" + match.group(1) + "." + match.group(2), match.start()))
    return found


def assignments(text):
    """The shell variables PAGE sets, as {"PY": "tools/venv/bin/python", ...}."""
    found = {}
    for line in text.splitlines():
        match = ASSIGNMENT.match(line)
        if match:
            found[match.group(1)] = match.group(2)
    return found


def bare_python(prefix):
    """The Python name in front of the script when neither Ubuntu nor macOS answers to it; None otherwise."""
    for word in prefix.split():
        name = word.strip("\"'")
        if BARE_PYTHON.fullmatch(name):
            return name
    return None


def refusals(text, modes, page=""):
    """Why the commands PAGE gives would not run as typed; [] when every one of them would."""
    variables = assignments(text)
    problems = []
    for number, command, script, start in judged(text):
        prefix = command[:start]
        where = f"{page}:{number}" if page else f"line {number}"
        shell_script = script.endswith(".sh")
        if shell_script:
            if SHELL.search(prefix):
                continue      # `bash tools/disable.sh <list>`: the shell reads the file whatever its mode
        else:
            bare = bare_python(prefix)
            if bare:
                windows = script.replace("/", "\\")
                problems.append(f"{where}: `{command}` starts `{bare}`, which a stock Ubuntu 24.04 and "
                                f"macOS do not have (their interpreter is python3): the shell answers "
                                f"`command not found` (exit 127) before the script is read. Write "
                                f"`{VIRTUAL_PYTHON} -I {script}` (on Windows `tools\\venv\\Scripts"
                                f"\\python.exe -I {windows}`)")
                continue
            if "python" in prefix.lower():
                continue      # `tools/venv/bin/python -I tools/x.py ...`
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
            continue          # git carries it executable, which is what a bare command needs
        if mode is None:
            problems.append(f"{where}: `{command}` names {script}, which tools/ does not hold")
        elif shell_script:
            problems.append(f"{where}: `{command}` starts {script}, which git has as {mode}: "
                            f"the shell answers `Permission denied` (exit 126). Name a shell in front, "
                            f"e.g. bash {script} ..., or keep the file executable in git "
                            f"(git update-index --chmod=+x {script})")
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

    def test_the_sweep_reads_the_shell_script_commands_too(self):
        # 301 commands on these pages run a tools/*.sh bare; a pattern that ended at `.py` read none of them.
        counts = {}
        for path in pages():
            for _, _, script, _ in judged(Path(path).read_text(encoding="utf-8", errors="replace")):
                counts[script] = counts.get(script, 0) + 1
        shell = {one: count for one, count in counts.items() if one.endswith(".sh")}
        self.assertGreaterEqual(sum(shell.values()), 200, "the sweep read %d shell-script commands: %s"
                                % (sum(shell.values()), shell))
        for name in ("tools/disable.sh", "tools/restore.sh", "tools/theme.sh"):
            self.assertIn(name, shell, "no page runs %s" % name)

    def test_every_shell_script_is_executable_in_git(self):
        # A page runs these bare, so the mode is the whole rule; the fix is git's, not the page's.
        modes = self.modes or {}
        scripts = sorted(one for one in modes if one.endswith(".sh"))
        self.assertGreaterEqual(len(scripts), 4, "tools/ holds %d shell scripts: %s" % (len(scripts), scripts))
        lost = ["%s is %s" % (one, modes[one]) for one in scripts if modes[one] != "100755"]
        self.assertEqual([], lost, "a page runs these bare, so a mode other than 100755 answers "
                                   "`Permission denied` (exit 126): " + ", ".join(lost))


class ReaderTest(unittest.TestCase):
    """The rule itself, on pages written here: a command is judged, a mention is not."""

    MODES = {"tools/check.py": "100644", "tools/readme_card.py": "100644", "tools/tv.py": "100755",
             "tools/disable.sh": "100755", "tools/theme.sh": "100644"}

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

    def test_a_page_that_starts_a_tool_with_python_is_refused(self):
        problems = refusals("`python tools/tv.py 'pm enable com.tcl.usercenter'`", self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("page.md:1", problems[0])
        self.assertIn("starts `python`", problems[0])
        self.assertIn("command not found", problems[0])
        self.assertIn("exit 127", problems[0])
        self.assertIn("tools/venv/bin/python -I tools/tv.py", problems[0])

    def test_the_bare_interpreter_is_refused_behind_an_environment_prefix_too(self):
        problems = refusals("```sh\nTV_HOST=<tv ip> python tools/tv.py --probe\n```", self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("starts `python`", problems[0])

    def test_the_names_a_reader_can_run_are_left_alone(self):
        # python3 is a command on both systems, and a path or a variable naming one is what the rule asks
        # for; only the name a shell cannot resolve is refused.
        for text in ("`python3 tools/check.py --fix list.txt`",
                     "`tools/venv/bin/python -I tools/tv.py --probe`",
                     "```powershell\ntools\\venv\\Scripts\\python.exe -I tools\\tv.py 'pm enable x'\n```"):
            self.assertEqual([], refusals(text, self.MODES, "page.md"), text)

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

    def test_a_shell_script_git_carries_executable_may_be_given_bare(self):
        # The form every list page uses: the env prefix, then the script, as git has it executable.
        for text in ("`TV_HOST=<tv ip> tools/disable.sh examples/acme/debloat.txt`",
                     "```bash\nTV_HOST=192.168.1.50 tools/disable.sh examples/acme/debloat.txt\n```"):
            self.assertEqual([], refusals(text, self.MODES, "page.md"), text)

    def test_a_shell_script_whose_mode_was_lost_is_refused(self):
        problems = refusals("`TV_HOST=<tv ip> tools/theme.sh examples/hy320` pushes it.",
                            self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("page.md:1", problems[0])
        self.assertIn("tools/theme.sh", problems[0])
        self.assertIn("100644", problems[0])
        self.assertIn("Permission denied", problems[0])
        self.assertIn("bash tools/theme.sh", problems[0])
        self.assertIn("git update-index --chmod=+x tools/theme.sh", problems[0])

    def test_a_shell_in_front_reads_the_script_whatever_its_mode(self):
        for text in ("`bash tools/theme.sh examples/hy320`", "`/bin/sh tools/theme.sh examples/hy320`",
                     "`TV_HOST=<tv ip> bash -e tools/theme.sh examples/hy320`"):
            self.assertEqual([], refusals(text, self.MODES, "page.md"), text)

    def test_a_shell_script_the_tree_does_not_hold_is_named(self):
        problems = refusals("`tools/no_such_thing.sh examples/acme/debloat.txt`", self.MODES, "page.md")
        self.assertEqual(1, len(problems), problems)
        self.assertIn("which tools/ does not hold", problems[0])

    def test_a_mention_of_a_shell_script_is_not_a_command(self):
        for text in ("`tools/theme.sh` takes the theme folder.",
                     "| [`tools/restore.sh`](tools/restore.sh) | Puts every package back. |"):
            self.assertEqual([], refusals(text, self.MODES, "page.md"), text)


if __name__ == "__main__":
    unittest.main()

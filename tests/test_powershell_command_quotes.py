"""A `powershell -Command "..."` in the docs does not carry a `$variable` inside double quotes.

The Windows lines in this repo are pasted into a PowerShell window, and that PowerShell expands a
double-quoted argument before the new `powershell` starts. examples/tcl-32l5a/README.md gave its restore
as `powershell -ExecutionPolicy Bypass -Command "$env:TV_HOST='<tv ip>'; tools\\restore.ps1 ..."`: the
outer shell replaced `$env:TV_HOST` with its own value, so the child was handed `='<tv ip>'` (not a
command) where the setting was, and then ran restore.ps1 without a host. It stopped with "pass -TvHost
<tv ip> or set $env:TV_HOST". In a window that already had TV_HOST set, it restored that other TV instead,
or did not parse at all when that value carried a port (IP:port). The JAVA_HOME line on the same page lost
both of its settings the same way.

The form that works is the one the rest of the repo uses,
`powershell -ExecutionPolicy Bypass -File tools\\restore.ps1 -TvHost <tv ip> <list>`, which runs the same
from PowerShell and cmd. A setting the script has no parameter for (JAVA_HOME) is made as its own statement
first: `$env:JAVA_HOME='...'` in PowerShell, `set JAVA_HOME=...` in cmd. A single-quoted `-Command '...'`
or a backtick-escaped `` `$ `` is not expanded and is not reported.

Only pages and lists are read (.md and .txt): a script may expand a variable on purpose.
Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# The same directories tests/test_ps1_invocations.py skips: build output, the venv, a person's own files.
SKIP_DIRS = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__", "shots", "backup", "traces"}

# `powershell` or `pwsh`, then within the same command `-Command` (or `-c`) and a double-quoted argument.
COMMAND_RE = re.compile(r"\b(?:powershell|pwsh)(?:\.exe)?\b[^;|&\n]*?\s-(?:command|c)\s+\"([^\"\n]*)\"",
                        re.IGNORECASE)
INVOCATION_RE = re.compile(r"\b(?:powershell|pwsh)(?:\.exe)?\s+-", re.IGNORECASE)
# What PowerShell expands in double quotes: $name, $env:NAME, ${name}, $(...). A backtick escapes it.
VARIABLE_RE = re.compile(r"(?<!`)\$(?:\{[^}]*\}|\(|[\w:]+)")


def problems(text):
    """`line: what` for each double-quoted -Command argument with something the caller expands."""
    found = []
    for number, line in enumerate(text.split("\n"), 1):
        for match in COMMAND_RE.finditer(line):
            expanded = VARIABLE_RE.findall(match.group(1))
            if expanded:
                found.append(
                    f"{number}: -Command \"...\" has {', '.join(dict.fromkeys(expanded))} in double quotes, "
                    f"which the PowerShell it is typed into expands first; use -File <script> -TvHost <tv ip>, "
                    f"or set the variable as its own statement before it")
    return found


def pages():
    for path in sorted(ROOT.rglob("*")):
        if path.suffix not in {".md", ".txt"} or not path.is_file():
            continue
        if SKIP_DIRS & set(path.relative_to(ROOT).parts):
            continue
        yield path


class RepositoryTest(unittest.TestCase):
    def test_no_page_puts_a_variable_in_a_double_quoted_command(self):
        findings, read = [], 0
        for path in pages():
            text = path.read_text(encoding="utf-8-sig", errors="replace")
            read += len(INVOCATION_RE.findall(text))
            findings += [f"{path.relative_to(ROOT).as_posix()}:{problem}" for problem in problems(text)]
        self.assertEqual([], findings, "\n".join(findings))
        # Cannot pass by reading nothing: the READMEs, add-ons, themes and lists have these lines.
        self.assertGreaterEqual(read, 10, "the walk found almost no powershell commands")


class ReaderTest(unittest.TestCase):
    """The check itself, on lines written here."""

    def test_the_old_restore_line_is_reported(self):
        found = problems("powershell -ExecutionPolicy Bypass -Command \"$env:TV_HOST='<tv ip>'; "
                         "tools\\restore.ps1 examples\\tcl-32l5a\\debloat.txt\"")
        self.assertEqual(1, len(found), found)
        self.assertIn("$env:TV_HOST", found[0])

    def test_every_variable_in_the_old_launcher_line_is_named(self):
        found = problems("powershell -ExecutionPolicy Bypass -Command \"$env:JAVA_HOME='<path to JDK 17>'; "
                         "$env:TV_HOST='<tv ip>'; & 'launcher\\build-and-install.ps1' -InputKeyService\"")
        self.assertEqual(1, len(found), found)
        self.assertIn("$env:JAVA_HOME, $env:TV_HOST", found[0])

    def test_short_flag_pwsh_and_subexpressions_are_reported(self):
        self.assertEqual(1, len(problems('pwsh -c "Write-Output $(Get-Date)"')))
        self.assertEqual(1, len(problems('powershell.exe -NoProfile -command "${x}"')))

    def test_the_file_form_is_quiet(self):
        self.assertEqual([], problems(
            "powershell -ExecutionPolicy Bypass -File tools\\restore.ps1 -TvHost <tv ip> examples\\a\\list.txt"))
        self.assertEqual([], problems("$env:JAVA_HOME='<path to JDK 17>'"))

    def test_single_quotes_and_backticks_are_not_expanded(self):
        self.assertEqual([], problems("powershell -Command '$env:TV_HOST=\"<tv ip>\"; tools\\restore.ps1 a.txt'"))
        self.assertEqual([], problems("powershell -Command \"`$env:TV_HOST='<tv ip>'; tools\\restore.ps1 a\""))
        self.assertEqual([], problems('powershell -Command "Get-Date"'))

    def test_another_shell_is_not_powershell(self):
        self.assertEqual([], problems('bash -c "echo $TV_HOST"'))
        self.assertEqual([], problems('In PowerShell, run it; then bash -c "echo $TV_HOST"'))


if __name__ == "__main__":
    unittest.main()

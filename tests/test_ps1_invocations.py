"""A PowerShell command in the docs runs as written: the script exists, the parameters do too.

Every Windows recipe in this repo is a `tools\\restore.ps1 <list> -Stock <package>` style line that a
reader copies onto their computer at the moment the TV looks wrong. Two ways such a line dies before
it reaches the TV, and both leave the TV as it was:

- the named parameter is not one the script declares (`restore.ps1` takes `-Stock`, its own usage line
  says so, and PowerShell refuses the whole script with "A parameter cannot be found"), so the undo
  never runs and the TV stays debloated;
- the script is not in the repository at all (a contributor's own helper named in an example), so
  `-File` fails with "cannot be found".

The third check is the one the argument order causes: the list is the only positional parameter, so a
stock-launcher package name typed as a second bare argument is bound to nothing and PowerShell stops
with "A positional parameter cannot be found that accepts argument". A package name is the one bare
argument a debloat-list invocation never has - the list itself is a path - so a package-shaped token
that is not a parameter's value is that mistake.

Parameters are read out of the scripts themselves (`param( ... )`), so a script that renames a
parameter fails the pages that still pass the old one instead of letting them go stale.
Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Directories the other repository-wide walkers skip: build output, the CI venv, and a person's own
# backups, screenshots and traces, which are gitignored.
SKIP_DIRS = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__", "shots", "backup", "traces"}

SCRIPT_RE = re.compile(r"([A-Za-z0-9_.\\/-]+\.ps1)\b")
# `-TvHost`, not the second half of `--fix`: a parameter is written with one dash and a letter.
PARAM_RE = re.compile(r"(?<![\w-])-([A-Za-z][A-Za-z0-9]*)")
POSITION_RE = re.compile(r"Position\s*=\s*(\d+)\s*\)")
PARAM_DECL_RE = re.compile(r"\[([A-Za-z_][\w.\[\]]*)\]\s*\$(\w+)")
# A package name as tools/disable.sh, disable.ps1 and recovery.py accept it: at least two dots, so
# `check.py` in prose is not one.
PACKAGE_RE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+){2,}")
# Where an invocation stops: the next command in the same line.
BREAK_RE = re.compile(r"(?:;|&&|\|\||\|)")


def param_block_of(text):
    """The text of a script's param( ... ) block, or "" when it has none."""
    match = re.search(r"\bparam\s*\(", text)
    if not match:
        return ""
    depth, i = 1, match.end()
    while i < len(text) and depth:
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
        i += 1
    return text[match.end():i - 1]


def parameters(block):
    """Declared parameter names, in order, from a param block."""
    return [name for _, name in PARAM_DECL_RE.findall(block)]


def positional_names(block):
    """Parameters given an explicit Position, as [(index, name)]."""
    found = []
    for match in POSITION_RE.finditer(block):
        name = PARAM_DECL_RE.search(block[match.end():])
        if name:
            found.append((int(match.group(1)), name.group(2)))
    return found


def invocations(text):
    """(script token, argument text) for every .ps1 named in a line of text.

    The argument text stops at the next shell separator, so a line that names two scripts in one
    command is read as the two invocations it would run - which is how a `-Stock` meant for
    restore.ps1 ends up in disable.ps1's argument list.
    """
    for line in text.split("\n"):
        for match in SCRIPT_RE.finditer(line):
            token = match.group(1)
            if token == ".ps1":
                continue
            # A URL path is somebody else's file, and a `<name>` placeholder names nothing.
            if match.start() and line[match.start() - 1] in "/:<>":
                continue
            if "<" in token or "*" in token:
                continue
            rest = line[match.end():]
            stop = BREAK_RE.search(rest)
            yield token, (rest[:stop.start()] if stop else rest)


def lines_problems(text, resolve, block_of):
    """What is wrong with the PowerShell commands in this text, as human-readable lines.

    `resolve(token)` returns the script source texts a documented path could mean (empty when the
    repository has no such file), and `block_of(text)` its param block.
    """
    found = []
    for token, rest in invocations(text):
        sources = resolve(token)
        if not sources:
            found.append(f"{token}: no such script in the repository")
            continue
        blocks = [block_of(source) for source in sources]
        declared = sorted({name for block in blocks for name in parameters(block)})
        if not declared:
            continue
        lower = [name.lower() for name in declared]
        for flag in PARAM_RE.findall(rest):
            low = flag.lower()
            matches = [name for name in lower if name.startswith(low)]
            if not matches:
                found.append(
                    f"{token} -{flag}: {token} declares {', '.join('-' + n for n in declared)}, "
                    f"so PowerShell refuses the script and nothing runs")
            elif len(matches) > 1:
                found.append(f"{token} -{flag}: matches {', '.join('-' + n for n in matches)}")
        # A stock-launcher package name is what a reader types after the list, but the list is the
        # only positional parameter: the package has to be the flag's value.
        skip_next = False
        for word in rest.split():
            if skip_next:
                skip_next = False
                continue
            if word.startswith("-"):
                skip_next = True
                continue
            if PACKAGE_RE.fullmatch(word.strip("\"'`")):
                found.append(
                    f"{token} {word}: a package name as a bare argument; the scripts take the stock "
                    f"launcher with {_stock_flag(declared)}")
    return found


def _stock_flag(declared):
    for name in declared:
        if name.lower().startswith("stock"):
            return "-" + name
    return "their own flag"


def problems(text):
    return lines_problems(text, resolve_in_repo, param_block_of)


def importable_files():
    for path in sorted(ROOT.rglob("*")):
        if path.suffix not in {".md", ".txt", ".sh", ".ps1"} or not path.is_file():
            continue
        if SKIP_DIRS & set(path.relative_to(ROOT).parts):
            continue
        yield path


def resolve_in_repo(token):
    """The script sources a documented path could name, from the repository root.

    A path with a directory in it is taken as written (backslashes are Windows). A bare file name is
    looked up by name, since `build-and-install.ps1` is how the pages refer to the launcher's, the
    add-ons' and the tools' own scripts.
    """
    cleaned = token.replace("\\", "/").lstrip("./")
    if "/" in cleaned:
        path = ROOT / cleaned
        return [path.read_text(encoding="utf-8-sig", errors="replace")] if path.is_file() else []
    found = [p for p in ROOT.rglob(cleaned) if p.is_file() and not SKIP_DIRS & set(p.parts)]
    return [p.read_text(encoding="utf-8-sig", errors="replace") for p in found]


class WindowsCommandTest(unittest.TestCase):
    def test_every_documented_powershell_command_runs(self):
        findings, read, scripts = [], 0, set()
        for path in importable_files():
            text = path.read_text(encoding="utf-8-sig", errors="replace")
            for token, _ in invocations(text):
                read += 1
                if resolve_in_repo(token):
                    scripts.add(token)
            for problem in problems(text):
                findings.append(f"{path.relative_to(ROOT)}: {problem}")
        self.assertEqual([], findings, "\n".join(findings))
        # Cannot pass by reading nothing: the repo documents plenty of these commands, against the
        # tools, the launcher and the add-ons.
        self.assertGreaterEqual(read, 30, "the walk found almost no PowerShell commands")
        self.assertGreaterEqual(len(scripts), 5, "the walk resolved almost no scripts")


class ProblemReaderTest(unittest.TestCase):
    """The checks themselves, on documents written here."""

    SCRIPTS = {
        "tools/restore.ps1": "param(\n    [Parameter(Mandatory = $true, Position = 0)][string]$List,\n"
                             "    [string]$Stock = 'com.google.android.tvlauncher',\n"
                             "    [string]$TvHost = $env:TV_HOST\n)\n",
        "tools/disable.ps1": "param(\n    [Parameter(Mandatory = $true, Position = 0)][string]$List,\n"
                             "    [string]$TvHost = $env:TV_HOST\n)\n",
    }

    def reader(self, text):
        def resolve(token):
            return [self.SCRIPTS[token.replace("\\", "/")]] if token.replace("\\", "/") in self.SCRIPTS else []
        return lines_problems(text, resolve, param_block_of)

    def test_undeclared_parameter_is_reported(self):
        found = self.reader(r"tools\restore.ps1 -TvHost 1.2.3.4 list.txt -StockLauncher com.foo.bar")
        self.assertEqual(1, len(found), found)
        self.assertIn("-StockLauncher", found[0])
        self.assertIn("-Stock", found[0])

    def test_declared_parameter_is_quiet(self):
        self.assertEqual([], self.reader(r"tools\restore.ps1 -TvHost 1.2.3.4 list.txt -Stock com.foo.bar"))
        self.assertEqual([], self.reader(r"tools\disable.ps1 -TvHost 1.2.3.4 list.txt"))

    def test_a_parameter_belongs_to_the_script_it_is_written_after(self):
        # The shape examples/tcl-65c735/debloat.txt had: both scripts on one line, one `-Stock`.
        found = self.reader(r"tools\disable.ps1 / tools\restore.ps1 -TvHost 1.2.3.4 list.txt -Stock com.foo.bar")
        self.assertTrue(any("-Stock" in line and "disable.ps1" in line for line in found), found)

    def test_package_name_as_a_second_bare_argument_is_reported(self):
        found = self.reader(r"tools\restore.ps1 list.txt com.google.android.apps.tv.launcherx")
        self.assertEqual(1, len(found), found)
        self.assertIn("-Stock", found[0])
        self.assertIn("com.google.android.apps.tv.launcherx", found[0])

    def test_a_list_path_is_not_a_package_name(self):
        self.assertEqual([], self.reader(r"tools\restore.ps1 examples\a-b\debloat.txt"))

    def test_a_script_the_repository_does_not_ship_is_reported(self):
        found = self.reader(r"powershell -ExecutionPolicy Bypass -File run_build.ps1")
        self.assertEqual(1, len(found), found)
        self.assertIn("no such script", found[0])

    def test_a_url_path_is_somebody_elses_file(self):
        self.assertEqual([], self.reader("see https://github.com/x/y/blob/main/build.ps1 for that"))

    def test_a_placeholder_names_nothing(self):
        self.assertEqual([], self.reader(r"addons\<name>\build-and-install.ps1 -TvHost 1.2.3.4"))

    def test_the_invocation_stops_at_the_next_command(self):
        # `$env:TV_HOST='1.2.3.4'; tools\restore.ps1 list.txt` - the setting is not an argument.
        self.assertEqual([], self.reader("$env:TV_HOST='1.2.3.4'; tools\\restore.ps1 list.txt"))
        found = self.reader(r"tools\restore.ps1 list.txt; tools\disable.ps1 -Stock com.foo.bar")
        self.assertTrue(any("disable.ps1" in line for line in found), found)

    def test_two_dots_make_a_package_name_one_does_not(self):
        self.assertEqual([], self.reader(r"tools\disable.ps1 whatever check.py says"))

    def test_parameters_come_out_of_the_param_block(self):
        block = param_block_of(self.SCRIPTS["tools/restore.ps1"])
        self.assertEqual(["List", "Stock", "TvHost"], parameters(block))
        self.assertEqual([(0, "List")], positional_names(block))


if __name__ == "__main__":
    unittest.main()

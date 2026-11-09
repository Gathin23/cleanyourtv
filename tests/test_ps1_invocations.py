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

The fourth is the TV the command is for. Every one of these scripts reads `$TvHost` from `$env:TV_HOST`
and stops at its own guard before it sends anything - `pass -TvHost <tv ip> or set $env:TV_HOST`, the
line tests/test_powershell_command_quotes.py quotes from the TCL 32L5A page - so a command that hands
one a list with no TV named sends nothing to the TV at all. examples/acer-ar3-r3's README had one,
`tools\\disable.ps1 examples\\acer-ar3-r3\\debloat.txt`, and the Apply lines of examples/acer-ar3-r3 and
examples/tdsystems-ch17f40s-redwood wrote the TV the way a POSIX shell writes it,
`TV_HOST=<tv ip> tools\\disable.ps1 <list>`: PowerShell reads a bare `NAME=value` word as the name of
the command to run, so those lines died too. The setting is `$env:TV_HOST = '<tv ip>'` as its own
statement (or the script's own -TvHost), which is the shape the other pages and headers already use.

A page that names a script in passing - "`tools\\disable.ps1` then disabled the wizard" - hands it no
path and is not read as a command.

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
# The TV a command acts on. Every one of these scripts declares `[string]$TvHost = $env:TV_HOST` and
# stops at its own guard when it is empty, so a command that sends anything has to name one.
TV_HOST_PARAM = re.compile(r"\$\{?TvHost\}?\s*=", re.IGNORECASE)
TV_HOST_FALLBACK = re.compile(r"\$env:TV_HOST\b", re.IGNORECASE)
# The setting the shell the command is typed into accepts. PowerShell has no `NAME=value` prefix: it reads
# that word as the command to run, so only `$env:TV_HOST = '...'` (its own statement, this line or an
# earlier one) or the script's -TvHost count. A bare `TV_HOST=` is the shell's shape, read by the rule below.
TV_HOST_SETTING = re.compile(r"\$env:TV_HOST\s*=(?!=)", re.IGNORECASE)
# The script's own parameter for it: `-TvHost 192.168.1.50`, `-TvHost <tv ip>`.
TV_HOST_FLAG = re.compile(r"(?<![\w-])-TvHost\b", re.IGNORECASE)
# The POSIX shape in front of the script it is meant for: `TV_HOST=<tv ip> tools\disable.ps1 <list>`.
POSIX_PREFIX = re.compile(
    r"(?:^|[\s;&|])([A-Za-z_][A-Za-z0-9_]*)=(<[^>]*>|\S*)\s+([A-Za-z0-9_.\\/-]+\.ps1)\b")
# The argument a command hands a script: a saved list (`examples\x\debloat.txt`, `list.txt`) or a theme
# folder (`themes\aurora`, `examples\x`). A page that names a script in passing hands it no path.
TARGET_RE = re.compile(r"(?:^|[\s`'\"(])([^\s`'\"()]*\.txt\b|(?:themes|examples)[\\/][^\s`'\"()]*)")


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


def needs_tv(block):
    """True when the script reads its TV from `$env:TV_HOST` and has no other value to fall back on."""
    return bool(TV_HOST_PARAM.search(block) and TV_HOST_FALLBACK.search(block))


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


def hands_a_target(rest):
    """True when the script's own argument list starts with a list or a theme folder.

    A command writes its arguments from its first word (`examples\\x\\debloat.txt`, or `-TvHost <tv ip>
    examples\\x\\debloat.txt`); a page that names a script in passing writes a sentence there instead
    ("`tools\\disable.ps1` on Windows, or comment out the lines of `debloat.txt`"), so looking at the
    first few words is what tells the two apart.
    """
    return bool(TARGET_RE.search(" ".join(rest.split()[:3])))


def host_problems(text, resolve, block_of):
    """A Windows command that does not tell the script which TV it acts on, as human-readable lines.

    Two shapes, both of which stop one of these scripts at its own guard - `pass -TvHost <tv ip> or set
    $env:TV_HOST` - before it sends anything:

    - the command hands the script a list or a theme folder and names no TV at all;
    - it names one the POSIX way, `TV_HOST=<tv ip> tools\\disable.ps1 <list>`, which PowerShell reads as a
      command named `TV_HOST=<tv` rather than a setting.

    `$env:TV_HOST = '...'` as its own statement, this line or an earlier one, is the setting PowerShell
    does take, so a page that makes it before the command is quiet.
    """
    found = []
    named = False
    for line in text.split("\n"):
        if TV_HOST_SETTING.search(line):
            named = True
        prefixed = set()
        for name, value, token in POSIX_PREFIX.findall(line):
            if name.upper() != "TV_HOST":
                continue
            blocks = [block_of(source) for source in resolve(token)]
            if not any(needs_tv(block) for block in blocks):
                continue
            prefixed.add(token)
            found.append(
                f"{token}: `{name}={value}` in front of it is a command name to PowerShell, not a setting, "
                f"so nothing runs: put `$env:TV_HOST = '<tv ip>'` before it, or pass -TvHost <tv ip>")
        for token, rest in invocations(line):
            if named or token in prefixed or TV_HOST_FLAG.search(rest) or not hands_a_target(rest):
                continue
            if not any(needs_tv(block_of(source)) for source in resolve(token)):
                continue
            found.append(
                f"{token}: hands it a list and names no TV, so the script stops with \"pass -TvHost <tv ip> "
                f"or set $env:TV_HOST\" before anything is sent: add -TvHost <tv ip>")
    return found


def problems(text):
    return (lines_problems(text, resolve_in_repo, param_block_of)
            + host_problems(text, resolve_in_repo, param_block_of))


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
        "tools/docker-build.ps1": "param(\n    [string]$Project = 'launcher'\n)\n",
        "tools/theme.ps1": "param(\n    [Parameter(Position = 0)][string]$Theme,\n    [switch]$Reset,\n"
                           "    [string]$TvHost = $env:TV_HOST\n)\n",
    }

    def reader(self, text):
        def resolve(token):
            return [self.SCRIPTS[token.replace("\\", "/")]] if token.replace("\\", "/") in self.SCRIPTS else []
        return lines_problems(text, resolve, param_block_of)

    def host_reader(self, text):
        def resolve(token):
            return [self.SCRIPTS[token.replace("\\", "/")]] if token.replace("\\", "/") in self.SCRIPTS else []
        return host_problems(text, resolve, param_block_of)

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

    # The TV the command acts on: a script reads $TvHost from $env:TV_HOST and stops without one.

    def test_a_list_command_that_names_no_tv_is_reported(self):
        found = self.host_reader(r"tools\disable.ps1 examples\a-b\debloat.txt")
        self.assertEqual(1, len(found), found)
        self.assertIn("names no TV", found[0])
        self.assertIn("-TvHost", found[0])

    def test_a_list_command_that_passes_the_tv_is_quiet(self):
        self.assertEqual([], self.host_reader(r"tools\disable.ps1 -TvHost <tv ip> examples\a-b\debloat.txt"))
        self.assertEqual([], self.host_reader(r"tools\restore.ps1 -TvHost 192.168.1.50 list.txt"))

    def test_a_tv_set_as_its_own_statement_is_quiet(self):
        # The shape examples/skyworth-swtv-24ae-4k-nf and tcl-65c735 use, on the line and above it.
        self.assertEqual([], self.host_reader('$env:TV_HOST="<tv ip>"; tools\\disable.ps1 examples\\a-b\\debloat.txt'))
        self.assertEqual([], self.host_reader("$env:TV_HOST = '192.168.1.50'\n"
                                              "tools\\disable.ps1 examples\\a-b\\debloat.txt"))

    def test_a_posix_prefix_before_a_powershell_script_is_reported(self):
        # The shape the two list headers had: PowerShell reads `TV_HOST=<tv` as the command to run.
        found = self.host_reader(r"TV_HOST=<tv ip> tools/disable.ps1 examples/a-b/debloat.txt")
        self.assertEqual(1, len(found), found)
        self.assertIn("command name", found[0])
        self.assertIn("$env:TV_HOST", found[0])

    def test_a_posix_prefix_before_the_shell_twin_is_quiet(self):
        self.assertEqual([], self.host_reader(r"TV_HOST=<tv ip> tools/disable.sh examples/a-b/debloat.txt"))

    def test_a_theme_folder_with_no_tv_is_reported(self):
        found = self.host_reader(r"tools\theme.ps1 themes\aurora")
        self.assertEqual(1, len(found), found)
        self.assertIn("names no TV", found[0])

    def test_a_prose_mention_is_not_a_command(self):
        # examples/xiaomi-mitv-mssp3-android9's sentence: a script named, then a list named later on.
        text = ("The set was applied with `tools\\disable.ps1` on Windows, or comment out the lines of "
                "`debloat.txt` that are not here.")
        self.assertEqual([], self.host_reader(text))

    def test_a_script_with_no_tv_parameter_is_quiet(self):
        self.assertEqual([], self.host_reader(r"tools\docker-build.ps1 -Project launcher"))
        self.assertFalse(needs_tv(param_block_of(self.SCRIPTS["tools/docker-build.ps1"])))


if __name__ == "__main__":
    unittest.main()

"""A shell command in the docs names the TV it acts on.

`tools/disable.sh`, `tools/restore.sh` and `tools/theme.sh` read the TV from `TV_HOST` and stop at
their own guard before they send anything when it is empty:

    TV_HOST=<tv ip> tools/disable.sh <list>

so a command that hands one of them a debloat list, a theme folder or `--reset` and names no TV sends
nothing at all. 399 list headers and the pages around them already write the prefix - that is the
shape the scripts' own usage lines print - and the eight commands below were the exceptions:

- examples/dcolor-gd2/debloat.txt, its Apply and Restore lines;
- examples/hisense-u7-wutaishan/README.md, examples/sankey-smart-tv-hi3751v350/README.md and
  examples/rocktek-gb1/README.md, each ending "... Push it with `tools/theme.sh examples/<folder>`"
  (the rocktek cell even prints the PowerShell twin with -TvHost beside it);
- examples/wanbo-x5-pro/README.md, whose Undo hands `restore.sh` the list and a stock launcher;
- docs/devices/hisense-32a5700fa.md, whose Undo is `tools/theme.sh --reset`;
- docs/workflows/tv-list.md, whose theme-subfolder example hands `theme.sh` the subfolder.

A page that sets the TV once - `export TV_HOST=192.168.1.50`, the shape the front pages and many
list pages use - is quiet: every command after it reads the same value. A page that only names a
script in passing hands it no path and is not read as a command ("`tools/disable.sh` in four
batches", "`tools/theme.sh` and then ...").

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# The walkers' usual skip list: build output, the CI venv, and a person's own backups, screenshots
# and traces, which are gitignored.
SKIP_DIRS = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__", "shots", "backup", "traces"}

# The scripts that read TV_HOST. tools/docker-build.sh builds an image and sends nothing to a TV.
SCRIPT_RE = re.compile(r"(?:^|[\s`'\"(])(tools/(?:disable|restore|theme)\.sh)\b")
# The argument that makes a command a command: a list (`examples/x/debloat.txt`, `list.txt`), a theme
# folder (`themes/aurora`, `examples/x`, `examples/x/theme`) or the reset flag. It has to be one of
# the first two words, so a sentence that names the script and a file further along is not read.
TARGET_RE = re.compile(r"(?:[^\s`'\"()]*\.txt|(?:themes|examples)[\\/][^\s`'\"()]*|--reset)")
# The TV, set once for the page: `TV_HOST=` or `export TV_HOST=`, in a fence, a header or prose.
SETTING_RE = re.compile(r"(?:^|[\s`'\"(])(?:export\s+)?TV_HOST=")
# The TV, named for this one command: `TV_HOST=<tv ip> tools/theme.sh ...`.
PREFIX_RE = re.compile(r"TV_HOST=\S+\s+$")
# Markdown leaves punctuation on a word: "`tools/theme.sh examples/x`." is one command, not a
# sentence, and the folder is `examples/x`.
TRIM = "`'\"(),.;:[]"


def trim(word):
    return word.strip(TRIM)


def files():
    """Every page and list in the tree, the same sweep the other documentation walks read."""
    for path in sorted(ROOT.rglob("*")):
        if path.suffix not in {".md", ".txt"} or not path.is_file():
            continue
        if SKIP_DIRS & set(path.relative_to(ROOT).parts):
            continue
        yield path


def names_the_tv(text):
    """True when the page sets TV_HOST once, so every command after it reads the same value."""
    return bool(SETTING_RE.search(text))


def commands(text):
    """(script, line number, line, argument text) for every command a page prints.

    The argument text is what follows the script on its line; only its first two words decide
    whether this is a command at all.
    """
    for number, line in enumerate(text.split("\n"), 1):
        for match in SCRIPT_RE.finditer(line):
            token = match.group(1)
            rest = line[match.end():]
            if not any(TARGET_RE.fullmatch(trim(word)) for word in rest.split()[:2]):
                continue
            yield token, number, line, rest


def problems(text):
    """The commands that hand one of these scripts a list and name no TV, as human-readable lines."""
    if names_the_tv(text):
        return []
    found = []
    for token, number, line, _ in commands(text):
        if PREFIX_RE.search(line[:line.index(token)]):
            continue
        found.append(
            f"line {number}: `{token}` is handed a list and names no TV, so the script stops with "
            f"\"Set TV_HOST to your TV's IP address\" before anything is sent: add `TV_HOST=<tv ip> ` "
            f"in front of it, or set `export TV_HOST=<tv ip>` above the commands")
    return found


class ShellCommandTest(unittest.TestCase):
    def test_every_shell_command_names_the_tv(self):
        findings, read, scripts = [], 0, set()
        for path in files():
            text = path.read_text(encoding="utf-8-sig", errors="replace")
            for token, _, _, _ in commands(text):
                read += 1
                scripts.add(token)
            for problem in problems(text):
                findings.append(f"{path.relative_to(ROOT)}: {problem}")
        self.assertEqual([], findings, "\n".join(findings))
        # Cannot pass by reading nothing: the three scripts are how a list is applied and a theme
        # pushed, so the pages carry hundreds of these commands.
        self.assertGreaterEqual(read, 300, "the walk found almost no shell commands")
        self.assertEqual({"tools/disable.sh", "tools/restore.sh", "tools/theme.sh"}, scripts)


class ProblemReaderTest(unittest.TestCase):
    """The check itself, on pages written here."""

    def test_a_command_with_no_tv_is_reported(self):
        found = problems("Undo:\n\n`tools/restore.sh examples/a-b/debloat.txt` re-enables the list.\n")
        self.assertEqual(1, len(found), found)
        self.assertIn("tools/restore.sh", found[0])
        self.assertIn("TV_HOST", found[0])

    def test_a_prefixed_command_is_quiet(self):
        self.assertEqual([], problems("TV_HOST=<tv ip> tools/disable.sh examples/a-b/debloat.txt\n"))
        self.assertEqual([], problems("TV_HOST=192.168.1.50 tools/theme.sh --reset\n"))

    def test_a_page_that_sets_the_tv_once_is_quiet(self):
        text = ("export TV_HOST=192.168.1.50\n\n"
                "tools/disable.sh examples/a-b/debloat.txt\n"
                "tools/theme.sh examples/a-b\n")
        self.assertEqual([], problems(text))

    def test_a_prose_mention_is_not_a_command(self):
        text = ("All 14 were applied with `tools/disable.sh` in four batches, with a screenshot after each, "
                "and the list is `examples/a-b/debloat.txt`.\n")
        self.assertEqual([], problems(text))

    def test_the_reset_flag_is_a_command(self):
        found = problems("`tools/theme.sh --reset`, or Open on start -> Off, undoes it.\n")
        self.assertEqual(1, len(found), found)

    def test_a_script_with_no_argument_is_left_alone(self):
        self.assertEqual([], problems("It was pushed with `tools/theme.sh`, and then picked in Settings.\n"))


if __name__ == "__main__":
    unittest.main()

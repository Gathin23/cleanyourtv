"""Every undo step in the repo names a file that is there: the list, and a folder's own script.

restore.sh takes the same list that disable.sh ran against (#239): recovery.py reads it, checks every
name against the record taken before the change and prints the batch. So the name in a page's undo
step is what an owner types when the TV looks wrong. A name that is not there is refused before a
single command reaches the TV, which is the worst moment to find out.

A list is read the way the pages write it: with Windows backslashes, after the `-TvHost <tv ip>` that
restore.ps1's usage line puts first, and in quotes. The Apply line above an undo is read the same way,
for tools/disable.sh and disable.ps1, so a list's own Apply and Undo lines both have to name that
list: debloat.txt, or a second list beside it such as
examples/google-chromecast-gtv-4k/debloat-keep-cast.txt.

The same goes for a folder's own install and restore script: a page that sends an owner to
`examples/<folder>/restore.sh` names a file the folder has to have, and the TCL 55S470G header named
one that was not there. No folder ships one any more - `examples/epson-ls800` was the last, and it went
the way `examples/benq-gv32` and `examples/tcl-43c645` went before it (#1717): the undo is
`tools/restore.sh` with the list, `tools/theme.sh --reset` for the theme, and
`tests/test_list_folders_ship_lists_only.py` keeps a new folder from arriving with a script of its own.
A page or a list header that still names one is a typo now, so the sweep reads none.

And a command that passes the stock launcher but no list names nothing at all: restore.sh takes the
list as its first argument and the stock launcher as its second, so the CVTE T972 page's
`tools/restore.sh ... com.cvte.tv.launcher.highend` was refused before a single package came back,
which the reference pattern above never read because there was no `.txt` in the line.

`STOCK_LAUNCHER=<package>` is how `launcher/build-and-install.sh` takes the stock launcher, from the
environment; restore.sh never reads it. The Skyworth SWTV-22AE-FHD page said to pass
`STOCK_LAUNCHER=` "when restoring", which leaves restore.sh on its default launcher, so a line that
pairs a restore with that setting has to name the installer it belongs to.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import re
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Same walk as tests/test_markdown_links.py: the tools venv CI builds and a launcher build are not docs.
PRUNE = {".git", "venv", "node_modules", "build", ".gradle", "__pycache__"}
SUFFIXES = (".md", ".txt", ".sh", ".ps1")
# The list a command hands to the script: `tools/restore.sh examples/x/debloat.txt com.stock.launcher`,
# `tools/restore.sh "examples/x/debloat.txt"`, and the PowerShell twin with a Windows path, also after
# -TvHost: `tools\restore.ps1 -TvHost <tv ip> examples\x\debloat.txt`.
LIST_ARGUMENT = r"(?:\s+-TvHost\s+(?:<[^<>]*>|\S+))?\s+[\"']?([A-Za-z0-9_./\\<>-]*\.txt)"
REFERENCE = re.compile(r"restore\.(?:sh|ps1)" + LIST_ARGUMENT)
# The Apply line the undo replays: `tools/disable.sh examples/x/debloat.txt`, and disable.ps1.
APPLY = re.compile(r"disable\.(?:sh|ps1)" + LIST_ARGUMENT)
# README.md writes the reader's own list as examples/<your tv>/debloat.txt, left out below as a
# placeholder. Three translations (it, ja, vi) write it as a made-up folder name, in quotes.
PLACEHOLDERS = {"examples/il-tuo-modello/debloat.txt", "examples/your-model/debloat.txt",
                "examples/model-cua-ban/debloat.txt"}
# A helper script inside a list folder, e.g. `examples/epson-ls800/restore.sh`. The folders that shipped
# the install and restore scripts their submission came with (#265) have all been trimmed to the shared
# tools, so a page or a list header that still names one names a file the folder no longer has.
FOLDER_SCRIPT = re.compile(r"(examples/[A-Za-z0-9._-]+/(?:restore|disable|install|build-and-install)\."
                           r"(?:sh|ps1))")
# `tools/restore.sh <list> <stock launcher>`: the list is restore.sh's first argument, the stock
# launcher its second (see its usage line and LIST=${1:?} / STOCK=${2:-...}). Both arguments are
# matched with no `.txt` required in the pattern, so a command that leaves the list out is read too.
PAIR = re.compile(r"tools[/\\]restore\.sh\s+(\S+)\s+(\S+)")
PACKAGE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+")
# A token as a page wraps it: `code`, "quoted", and the punctuation that ends the sentence.
WRAPPER = "`'\",;."
# "restore.sh", "Restore", "when restoring"; not RESTORE_KEY_SERVICES, an installer setting.
RESTORING = re.compile(r"(?i)\brestor(?:e|ing)\b")
STOCK_SETTING = re.compile(r"STOCK_LAUNCHER\b")
INSTALLER = re.compile(r"build-and-install")


def unwrapped(token):
    """The argument a reader would type, without the backticks, quotes and full stop around it."""
    return token.strip(WRAPPER).rstrip(".")


# Every list a reader can run: each folder's debloat.txt and a second list beside it. A page outside
# examples/ that needs one names that file too.
LISTS = "examples/*/debloat*.txt"


def references(text, pattern=REFERENCE):
    """The list names a line of text hands to restore.sh (or, with APPLY, to disable.sh), with forward
    slashes, placeholders like <your tv> left out."""
    names = [name.replace("\\", "/") for name in pattern.findall(text)]
    return [name for name in names if "<" not in name and ">" not in name and name not in PLACEHOLDERS]


def walk(root):
    """(relative path, text) for every page, list and script under root, in a stable order."""
    for directory, subdirectories, files in os.walk(root):
        subdirectories[:] = sorted(d for d in subdirectories if d not in PRUNE)
        for name in sorted(files):
            if not name.endswith(SUFFIXES):
                continue
            path = Path(directory) / name
            yield path.relative_to(root), path.read_text(encoding="utf-8", errors="replace")


def read_references(root, pattern=REFERENCE):
    """(file, name) for every restore.sh reference under root (disable.sh with APPLY), as a reader
    would copy it."""
    found = []
    for path, text in walk(root):
        for target in references(text, pattern):
            found.append((path, target))
    return found


def own_commands(root, pattern=REFERENCE):
    """(list, name) for every restore.sh reference (disable.sh with APPLY) in a list in examples/."""
    return [(path.as_posix(), name) for path, name in read_references(root, pattern)
            if path.match(LISTS)]


def commands_for_another_list(root, pattern=REFERENCE):
    """(list, name) for each of those that names a file other than the list it is in."""
    return [(own, name) for own, name in own_commands(root, pattern) if name != own]


def script_references(text):
    """The helper scripts inside a list folder a line names, placeholders left out."""
    return [name for name in FOLDER_SCRIPT.findall(text) if "<" not in name and ">" not in name]


def read_script_references(root):
    """(file, name) for every `examples/<folder>/<script>` reference under root."""
    found = []
    for path, text in walk(root):
        for target in script_references(text):
            found.append((path, target))
    return found


def restore_commands(root):
    """(file, line number, first argument, second argument) for every two-argument tools/restore.sh.

    Only the lines that hand it something package-shaped second are read: those are commands passing
    the stock launcher, and restore.sh's stock launcher is its second argument.
    """
    found = []
    for path, text in walk(root):
        for number, line in enumerate(text.splitlines(), 1):
            for first, second in PAIR.findall(line):
                if PACKAGE.fullmatch(unwrapped(second)):
                    found.append((path, number, first, second))
    return found


def commands_without_a_list(root):
    """(file, line, first argument, second argument) for the ones whose first argument is no list."""
    return [(path, number, first, second) for path, number, first, second in restore_commands(root)
            if not unwrapped(first).endswith(".txt") and "<" not in first]


def restore_with_stock_setting(root):
    """(file, line number, names the installer, sets it) for every line about a restore that names
    STOCK_LAUNCHER; "sets it" is the `STOCK_LAUNCHER=` form an owner would type."""
    found = []
    for path, text in walk(root):
        for number, line in enumerate(text.splitlines(), 1):
            if RESTORING.search(line) and STOCK_SETTING.search(line):
                found.append((path, number, bool(INSTALLER.search(line)), "STOCK_LAUNCHER=" in line))
    return found


def stock_setting_for_restore(root):
    """(file, line number) for the lines that set STOCK_LAUNCHER= for a restore and name no installer."""
    return [(path, number) for path, number, installer, sets in restore_with_stock_setting(root)
            if sets and not installer]


class RestoreReferencesTest(unittest.TestCase):
    def test_every_reference_names_a_list_that_is_there(self):
        missing = [(str(path), name) for path, name in read_references(ROOT)
                   if not (ROOT / name).is_file()]
        self.assertEqual([], missing,
                         "these lines restore with a list that is not in the repo, so recovery.py "
                         "refuses them and nothing is re-enabled")

    def test_each_list_undo_replays_that_same_list(self):
        # restore.sh replays the list disable.sh ran against, so a list's own undo names that file:
        # 237 of the 238 references in examples/ already do.
        self.assertEqual([], commands_for_another_list(ROOT),
                         "a list's undo points somewhere else, so it replays another set of packages "
                         "or nothing at all")

    def test_every_apply_names_a_list_that_is_there(self):
        missing = [(str(path), name) for path, name in read_references(ROOT, APPLY)
                   if not (ROOT / name).is_file()]
        self.assertEqual([], missing,
                         "these lines apply a list that is not in the repo, so disable.sh stops on it "
                         "before anything reaches the TV")

    def test_each_list_apply_runs_that_same_list(self):
        # The Apply counterpart of the undo rule above: the owner copies the Apply line of the list
        # they are reading, and the undo below it replays that list.
        self.assertGreaterEqual(len(own_commands(ROOT, APPLY)), 250,
                                "the sweep should read the Apply line of nearly every list; it found "
                                "fewer, so it cannot fail on one")
        self.assertEqual([], commands_for_another_list(ROOT, APPLY),
                         "a list's Apply line runs another file, so the TV gets another set of packages "
                         "than the list shows")

    def test_no_page_sends_an_owner_to_a_script_inside_a_list_folder(self):
        # examples/epson-ls800 shipped the restore script it came with and its page named it; that was the
        # last one, and it goes the way tcl-43c645 (#1717) and benq-gv32 went - the undo is
        # tools/restore.sh, tools/theme.sh --reset and the one settings command the folder's README gives.
        # The TCL 55S470G header named a folder script its folder did not have, so an owner's first undo
        # command was "No such file"; with no folder shipping one, every such name is a typo.
        missing = [(str(path), name) for path, name in read_script_references(ROOT)
                   if not (ROOT / name).is_file()]
        self.assertEqual([], missing,
                         "these lines hand the owner a script inside a list folder that is not in the "
                         "repo, so the command fails before anything reaches the TV")
        self.assertEqual([], [name for _, name in read_script_references(ROOT)],
                         "a list folder ships no script of its own (docs/workflows/tv-list.md step 6, "
                         "AGENTS.md's hard rules, MAINTAINER.md's 'dropped on merge', and "
                         "tests/test_list_folders_ship_lists_only.py), so no page or list header names one")

    def test_a_restore_that_names_the_launcher_names_the_list(self):
        # The CVTE T972 page sent an owner to `tools/restore.sh ... com.cvte.tv.launcher.highend`: the
        # stock launcher in the list's place, so restore.sh exits on its usage line with "usage:
        # restore.sh <debloat list> [stock launcher package]" and nothing is re-enabled - at the moment
        # the TV looks wrong. 168 of the 169 two-argument commands under this repo pass the list first.
        found = restore_commands(ROOT)
        self.assertGreaterEqual(len(found), 100,
                                "the repo-wide sweep should read every restore command that passes the "
                                "stock launcher; it found fewer, so it cannot fail on one")
        wrong = [(str(path), number, first, second)
                 for path, number, first, second in commands_without_a_list(ROOT)]
        self.assertEqual([], wrong,
                         "these lines hand restore.sh a stock launcher and no list, so it stops on its "
                         "usage line before recovery.py runs and nothing comes back")

    def test_restore_takes_the_list_first_and_the_launcher_second(self):
        # The sweep above is only right while that is the order; if restore.sh swaps or drops an
        # argument, this fails instead of the page being blamed for a script change.
        text = (ROOT / "tools" / "restore.sh").read_text(encoding="utf-8")
        self.assertIn('LIST="${1:', text,
                      "restore.sh no longer takes the list as its first argument, so the rule above "
                      "(and the undo lines it checks) no longer match the script")
        self.assertIn('STOCK="${2:', text,
                      "restore.sh no longer takes the stock launcher as its second argument")

    def test_a_restore_is_not_told_to_take_stock_launcher_from_the_environment(self):
        # The Skyworth SWTV-22AE-FHD page said "When restoring or building/installing, pass
        # `STOCK_LAUNCHER=...`". restore.sh reads only its second argument, so an owner who did that
        # got com.google.android.tvlauncher back instead of launcherx.
        self.assertGreaterEqual(len(restore_with_stock_setting(ROOT)), 4,
                                "the sweep should read the lines that mention restore.sh and "
                                "STOCK_LAUNCHER together; it found fewer, so it cannot fail on one")
        wrong = [(str(path), number) for path, number in stock_setting_for_restore(ROOT)]
        self.assertEqual([], wrong,
                         "these lines set STOCK_LAUNCHER= for a restore without naming "
                         "launcher/build-and-install.sh; restore.sh takes the launcher as its second "
                         "argument and never reads that setting")
        for script in ("restore.sh", "restore.ps1"):
            text = (ROOT / "tools" / script).read_text(encoding="utf-8")
            self.assertNotIn("STOCK_LAUNCHER", text,
                             f"{script} reads STOCK_LAUNCHER now, so the rule above is out of date")

    # ---- the reader itself, so the two tests above cannot pass by reading nothing ---------------

    def write(self, root, path, text):
        target = root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
        return target

    def test_a_missing_list_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "docs/page.md",
                       "Undo: TV_HOST=<tv ip> tools/restore.sh examples/gone/debloat.txt\n")
            found = read_references(root)
            self.assertEqual([(Path("docs/page.md"), "examples/gone/debloat.txt")], found)
            self.assertFalse((root / found[0][1]).is_file())

    def test_a_list_that_is_there_resolves(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/kept/debloat.txt", "com.example.one\n")
            self.write(root, "docs/page.md",
                       "Undo: tools/restore.sh examples/kept/debloat.txt com.example.stock\n")
            found = read_references(root)
            self.assertEqual([(Path("docs/page.md"), "examples/kept/debloat.txt")], found)
            self.assertTrue((root / found[0][1]).is_file())

    def test_the_powershell_twin_is_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "docs/page.md", "Undo: tools\\restore.ps1 examples/x/debloat.txt\n")
            self.assertEqual([(Path("docs/page.md"), "examples/x/debloat.txt")], read_references(root))

    def test_a_windows_path_is_read_with_forward_slashes(self):
        # How the Windows undo lines in examples/ write it, e.g. hisense-65d62qh and sony-kd-55x8000c.
        for line in ("tools\\restore.ps1 examples\\gone\\debloat.txt",
                     "tools\\restore.ps1 -TvHost <tv ip> examples\\gone\\debloat.txt",
                     "tools\\restore.ps1 -TvHost 192.168.1.50 examples\\gone\\debloat.txt -Stock com.x.y",
                     "$env:TV_HOST=\"<tv ip>\"; tools\\restore.ps1 examples\\gone\\debloat.txt"):
            with self.subTest(line=line):
                self.assertEqual(["examples/gone/debloat.txt"], references(line))

    def test_a_list_in_quotes_is_read(self):
        self.assertEqual(["examples/gone/debloat.txt"],
                         references("tools/restore.sh \"examples/gone/debloat.txt\" com.x.y"))
        self.assertEqual(["examples/gone/debloat.txt"],
                         references("tools/restore.sh 'examples/gone/debloat.txt'"))

    def test_an_apply_line_is_read_apart_from_the_undo(self):
        for line in ("# Apply: TV_HOST=<tv ip> tools/disable.sh examples/gone/debloat.txt",
                     "# Apply:   tools\\disable.ps1 -TvHost <tv ip> examples\\gone\\debloat.txt"):
            with self.subTest(line=line):
                self.assertEqual(["examples/gone/debloat.txt"], references(line, APPLY))
                self.assertEqual([], references(line))
        self.assertEqual([], references("tools/restore.sh examples/gone/debloat.txt", APPLY))

    def test_a_translated_placeholder_is_not_a_reference(self):
        # README.it.md, README.ja.md and README.vi.md: README.md's examples/<your tv>/debloat.txt.
        self.assertEqual([], references("TV_HOST=192.168.1.50 tools/restore.sh "
                                        "\"examples/il-tuo-modello/debloat.txt\" com.x.y"))

    def test_a_placeholder_is_not_a_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "README.md",
                       "TV_HOST=192.168.1.50 tools/restore.sh examples/<your tv>/debloat.txt\n"
                       "Pass the list you ran disable with to `tools/restore.sh`.\n")
            self.assertEqual([], read_references(root))

    def test_a_build_tree_is_not_read(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "tools/venv/lib/page.md",
                       "Undo: tools/restore.sh examples/gone/debloat.txt\n")
            self.assertEqual([], read_references(root))

    def test_a_missing_folder_script_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/kept/debloat.txt",
                       "# Undo: TV_HOST=<tv ip> examples/kept/restore.sh\n")
            self.assertEqual([(Path("examples/kept/debloat.txt"), "examples/kept/restore.sh")],
                             read_script_references(root))
            self.assertFalse((root / "examples/kept/restore.sh").is_file())

    def test_a_folder_script_that_is_there_resolves(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/kept/restore.sh", "#!/usr/bin/env bash\n")
            self.write(root, "examples/kept/README.md",
                       "TV_HOST=<tv ip> examples/kept/restore.sh   # undo\n")
            found = read_script_references(root)
            self.assertEqual([(Path("examples/kept/README.md"), "examples/kept/restore.sh")], found)
            self.assertTrue((root / found[0][1]).is_file())

    def test_a_placeholder_folder_script_is_not_a_reference(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "README.md",
                       "TV_HOST=<tv ip> examples/<your tv>/restore.sh\n"
                       "A folder with a `restore.sh` of its own is not the rule here.\n")
            self.assertEqual([], read_script_references(root))

    def test_a_launcher_without_a_list_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/cvte/debloat.txt", "com.example.one\n")
            self.write(root, "examples/cvte/README.md",
                       "- Undo with `tools/restore.sh ... com.cvte.tv.launcher.highend`.\n")
            self.assertEqual([(Path("examples/cvte/README.md"), 1, "...",
                               "com.cvte.tv.launcher.highend`.")],
                             commands_without_a_list(root))

    def test_a_launcher_with_its_list_is_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/cvte/debloat.txt", "com.example.one\n")
            self.write(root, "examples/cvte/README.md",
                       "- Undo with `TV_HOST=<tv ip> tools/restore.sh examples/cvte/debloat.txt "
                       "com.cvte.tv.launcher.highend`.\n")
            self.assertEqual([(Path("examples/cvte/README.md"), 1, "examples/cvte/debloat.txt",
                               "com.cvte.tv.launcher.highend`.")],
                             restore_commands(root))
            self.assertEqual([], commands_without_a_list(root))

    def test_a_quoted_list_is_read_as_the_list(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "docs/page.md",
                       "Undo: tools/restore.sh \"examples/kept/debloat.txt\" com.example.stock\n")
            self.assertEqual([], commands_without_a_list(root))

    def test_a_command_that_passes_only_the_list_is_not_a_pair(self):
        # One argument is the list alone: restore.sh then uses its default stock launcher, which is
        # what 168 lists rely on, so it is not a command that forgot the list.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "docs/page.md", "Undo: tools/restore.sh examples/kept/debloat.txt\n")
            self.assertEqual([], restore_commands(root))

    def test_prose_about_restore_sh_is_not_a_command(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "docs/page.md",
                       "restore.sh would turn them ON, and `tools/restore.sh` takes the list first.\n")
            self.assertEqual([], restore_commands(root))

    def test_stock_launcher_set_for_a_restore_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/x/README.md",
                       "- When restoring or building/installing, pass "
                       "`STOCK_LAUNCHER=com.google.android.apps.tv.launcherx`.\n")
            self.assertEqual([(Path("examples/x/README.md"), 1)], stock_setting_for_restore(root))

    def test_stock_launcher_set_for_the_installer_is_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "examples/x/debloat.txt",
                       "# Stock launcher: com.example.home (pass it to restore.sh, and to the installer as\n"
                       "#   STOCK_LAUNCHER=com.example.home launcher/build-and-install.sh)\n"
                       "# Stock launcher: com.example.home (pass it as STOCK_LAUNCHER / to restore.sh).\n"
                       "TV_HOST=<tv ip> RESTORE_KEY_SERVICES=1 STOCK_LAUNCHER=com.example.home make\n"
                       "- When restoring, pass it to `tools/restore.sh` as the second argument; when "
                       "building/installing, pass `STOCK_LAUNCHER=com.example.home` to "
                       "`launcher/build-and-install.sh`.\n")
            self.assertEqual([], stock_setting_for_restore(root))
            self.assertEqual([3, 5], [number for _, number, _, _ in restore_with_stock_setting(root)])

    def test_the_powershell_twin_is_a_named_parameter_command(self):
        # restore.ps1 takes -List and -Stock and has no package-shaped bare argument, so none of this
        # is read as a two-argument bash command.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.write(root, "docs/page.md",
                       "Undo: tools\\restore.ps1 -TvHost 192.168.1.50 -Stock com.example.stock "
                       "-List examples\\kept\\debloat.txt\n")
            self.assertEqual([], restore_commands(root))


if __name__ == "__main__":
    unittest.main()

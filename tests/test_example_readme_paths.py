"""A list folder's README names files this repository ships, and every one of them is there.

`docs/workflows/tv-list.md` step 6 welcomes a `README.md` in a list folder and says two of its claims
are read back against the list beside it (the package count and the undo). A path is the third, and
nothing read it: `tests/test_markdown_links.py` walks the links in every page, and
`tests/test_agents_md.py` walks the paths named in `AGENTS.md` and in `docs/workflows/*.md`, so a path
written in backticks inside `examples/<folder>/README.md` - the page an owner with this exact set reads -
was read by neither. `examples/xiaomi-mitv-moor0/README.md` passed the key helper to
`launcher/build-and-install`, without the `.sh` the command at the top of the same page writes, and the
file that keeps Home and the remote buttons working on that set was a name `bash` answers
"No such file or directory" for.

A path counts when it starts at the repository root, with one of the folders this repo ships: that is
what makes it a claim about this tree rather than a file the owner has (`wallpaper.jpg` beside their
config.json), a Windows twin written with backslashes, a placeholder (`examples/<your tv>/debloat.txt`,
`$TV_HOST`), or an Android component (`com.gathin.tvhome/.MainActivity`). Backticks inside a fenced block
are read too, since a path written that way is a command either way; the fence's own lines are not walked
as commands, because one there can name a folder the reader makes (`themes/mine`), which is not a claim
about this tree. Folders `.gitignore` names are skipped the way `tests/test_agents_md.py` skips them, so
the venv a clone builds (`tools/venv/bin/python`) is not read as a missing file.

An argument is the second claim such a command makes, and nothing read it. `tools/theme.sh` (and its
`theme.ps1` twin) takes a theme folder - a directory with a `config.json` in it - and nothing else, while
`examples/hy320/README.md` handed it `examples/hy320/config.json`, a file. Run as written, the command
answers `no config.json in .../examples/hy320/config.json. Nothing was sent to the TV.` and exits 2, so the
owner of that set never got the theme the page offered; and because the path it names is there, the link
walk above read that page as fine. `theme_arguments()` reads the argument of every `theme.sh` command in a
folder README, and `theme_problems()` holds one that names a path in this tree to the shape the script
reads: a file, or a folder with no `config.json` in it, is reported. A folder the page tells the reader to
make (`themes/mine`) is not in the tree yet and is left alone, the same way a ghost path is the link walk's
claim and not this one's.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from test_agents_md import ignored_prefixes  # noqa: E402  (the .gitignore folders both walks skip)

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

# The folders a path has to start with to be about this checkout. `examples/README.md` and the docs
# pages are read elsewhere; this is the folder README half.
ANCHORED = ("tools/", "docs/", "examples/", "themes/", "addons/", "tests/", "launcher/", ".github/")
CODE = re.compile(r"(`+)(.+?)\1")
FENCE = re.compile(r"`{3,}")             # the ``` that open and close a command block
PLACEHOLDER = re.compile(r"[<>*{}$%]")   # <your tv>, examples/<brand>-<model>/, $TV_HOST, a trailing %2F

# A theme command and the argument it hands the script. tools/theme.sh and theme.ps1 take one argument, a
# theme folder; the argument ends at a quote, a backtick, a bracket or the end of the line's word.
THEME_COMMAND = re.compile(r"\btools/theme\.(?:sh|ps1)\s+([^\s`'\")]+)")
THEME_ROOTS = ("examples/", "themes/")   # the folders a theme command in a list folder README can name here


def named_paths(body):
    """Every repository path a README writes in backticks, in page order.

    Fence markers are dropped first, so a backticked path inside a command block is read like any other;
    the fence's own lines are not walked as commands, because a command there can name a folder the reader
    makes (`cp -r themes/aurora themes/mine`), which is not a claim about this tree.
    """
    found = []
    for _, span in CODE.findall(FENCE.sub("", body)):
        for word in span.split():
            word = word.strip("'\"(),;:.`")
            if not word or "\\" in word or PLACEHOLDER.search(word):
                continue
            if word.startswith(("~", "/", "-", "http")) or not word.startswith(ANCHORED):
                continue
            found.append(word)
    return found


def folder_readmes(root=ROOT):
    """Every list folder README: examples/<folder>/README.md, at any depth a folder is listed at."""
    examples = root / "examples"
    return sorted(examples.glob("*/README.md")) + sorted(examples.glob("*/*/README.md"))


def missing(root=ROOT):
    """(readme, path) for each repository path a list folder's README names and this tree lacks."""
    ignored = ignored_prefixes()
    problems = []
    for readme in folder_readmes(root):
        for path in named_paths(readme.read_text(encoding="utf-8")):
            if any(path == prefix or path.startswith(prefix + "/") for prefix in ignored):
                continue
            if not (root / path).exists():
                problems.append((readme.relative_to(root).as_posix(), path))
    return problems


def theme_arguments(body):
    """Every argument a list folder README hands `tools/theme.sh`, in page order, that names a path here.

    The fence markers come off first, because a command block is where the reader copies the command from;
    the argument itself is read from the line, not from backticks, since a page can write it bare inside a
    fence. An option (`--reset`), a Windows path and a placeholder (`themes/<name>`, `$THEME`) are not
    arguments about this tree, and neither is a folder under a root this tree does not ship.
    """
    found = []
    for match in THEME_COMMAND.finditer(FENCE.sub("", body)):
        word = match.group(1).strip("'\"(),;:.`")
        if not word or "\\" in word or PLACEHOLDER.search(word):
            continue
        if word.startswith(("-", "~", "/", "http")) or not word.startswith(THEME_ROOTS):
            continue
        found.append(word)
    return found


def theme_problems(root=ROOT):
    """(readme, argument, why) for each theme command whose argument is not the folder theme.sh reads.

    tools/theme.sh opens `<argument>/config.json` before it sends anything and answers 2 when that file is
    not there, so an argument that names a file - or a folder this tree ships without a config.json - is a
    command that can never push the theme the page is describing. A path this tree does not have yet is not
    judged here: a page is allowed to tell the reader to make the folder first, and a ghost path is the
    link walk's claim.
    """
    problems = []
    for readme in folder_readmes(root):
        for word in theme_arguments(readme.read_text(encoding="utf-8")):
            target = root / word
            if not target.exists():
                continue
            if target.is_file():
                problems.append((readme.relative_to(root).as_posix(), word,
                                 "theme.sh reads a theme folder, and this is a file: it answers "
                                 "'no config.json in ...' and sends nothing"))
            elif not (target / "config.json").is_file():
                problems.append((readme.relative_to(root).as_posix(), word,
                                 "the folder holds no config.json, so theme.sh refuses it before it sends anything"))
    return problems


class TheFolderReadmesTest(unittest.TestCase):
    def test_every_path_a_list_readme_names_exists(self):
        problems = missing()
        self.assertEqual([], problems, "a list folder's README names a path this tree does not have: "
                         + "; ".join(f"{readme} names {path}" for readme, path in problems))

    def test_it_reads_the_folders(self):
        # A walk that finds nothing would pass the test above for any tree.
        readmes = folder_readmes()
        self.assertGreaterEqual(len(readmes), 90, "the walk over examples/*/README.md read too few folders")
        named = [path for readme in readmes for path in named_paths(readme.read_text(encoding="utf-8"))]
        self.assertGreaterEqual(len(named), 250, "the walk read too few paths to be checking the folders")
        self.assertIn("tools/disable.sh", named, "the walk no longer reads the command a list README gives")


class TheThemeCommandsTest(unittest.TestCase):
    def test_every_theme_command_hands_theme_sh_a_folder_it_can_read(self):
        problems = theme_problems()
        self.assertEqual([], problems, "a list folder's README hands tools/theme.sh an argument it cannot read: "
                         + "; ".join(f"{readme} hands {word}, where {why}" for readme, word, why in problems))

    def test_it_reads_the_theme_commands(self):
        # A walk that reads nothing would pass the test above for any tree.
        commands = [word for readme in folder_readmes()
                    for word in theme_arguments(readme.read_text(encoding="utf-8"))]
        self.assertGreaterEqual(len(commands), 40, "the walk read too few theme commands to be checking the folders")
        self.assertIn("themes/aurora", commands, "the walk no longer reads the theme the front pages push")


class TheReaderTest(unittest.TestCase):
    def test_a_path_in_backticks_inside_a_fenced_block_is_read(self):
        # A command block writes its paths in backticks for the reader to copy, so a fence is not a wall.
        body = "```bash\nTV_HOST=<tv ip> `tools/disable.sh` `examples/demo-1/debloat.txt`\n```\n"
        self.assertEqual(["tools/disable.sh", "examples/demo-1/debloat.txt"], named_paths(body))

    def test_a_bare_word_in_a_fence_is_not_a_path(self):
        # A fenced line can name a folder the reader makes, so only what the page puts in backticks is read.
        self.assertEqual([], named_paths("```bash\ncp -r themes/aurora themes/mine\n```\n"))

    def test_a_windows_twin_is_not_a_repository_path(self):
        self.assertEqual([], named_paths("... -File `launcher\\build-and-install.ps1` -TvHost <tv ip>"))

    def test_a_placeholder_is_not_a_path(self):
        for body in ("`examples/<your tv>/debloat.txt`", "`$TV_IP`", "`examples/<brand>-<model>/`"):
            with self.subTest(body=body):
                self.assertEqual([], named_paths(body))

    def test_an_android_component_is_not_a_path(self):
        self.assertEqual([], named_paths("`com.gathin.tvhome/.MainActivity` and `setupwraith/.RecoveryActivity`"))

    def test_a_path_this_repo_does_not_anchor_is_left_alone(self):
        # The owner's own files, a URL and a plain word: none of them is a claim about this checkout.
        self.assertEqual([], named_paths("`wallpaper.jpg` in this folder, `theme/config.json`, `https://x/y`"))

    def test_trailing_punctuation_comes_off(self):
        self.assertEqual(["tools/theme.sh"], named_paths("Re-run `tools/theme.sh`."))
        self.assertEqual(["launcher/build-and-install.sh"], named_paths("(see `launcher/build-and-install.sh`),"))

    def test_a_ghost_path_in_a_stand_in_tree_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "examples" / "demo-1").mkdir(parents=True)
            (root / "tools").mkdir()
            (root / "tools" / "theme.sh").write_text("", encoding="utf-8")
            (root / "examples" / "demo-1" / "README.md").write_text(
                "Run `tools/theme.sh examples/demo-1` then `launcher/build-and-install`.\n", encoding="utf-8")
            self.assertEqual([("examples/demo-1/README.md", "launcher/build-and-install")], missing(root))

    def test_a_file_argument_is_reported(self):
        # hy320's page handed theme.sh examples/hy320/config.json: a file where the script reads a folder.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "demo-1"
            folder.mkdir(parents=True)
            (folder / "config.json").write_text("{}", encoding="utf-8")
            (folder / "README.md").write_text(
                "```sh\nTV_HOST=<tv ip> tools/theme.sh examples/demo-1/config.json\n```\n", encoding="utf-8")
            problems = theme_problems(root)
            self.assertEqual("examples/demo-1/README.md", problems[0][0])
            self.assertEqual("examples/demo-1/config.json", problems[0][1])
            self.assertIn("this is a file", problems[0][2])

    def test_a_folder_without_a_config_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "demo-1"
            folder.mkdir(parents=True)
            (folder / "README.md").write_text("Run `tools/theme.sh examples/demo-1`.\n", encoding="utf-8")
            problems = theme_problems(root)
            self.assertEqual([("examples/demo-1/README.md", "examples/demo-1")],
                             [(readme, word) for readme, word, _ in problems])

    def test_a_folder_with_a_config_is_fine(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name in ("examples/demo-1", "examples/demo-2/theme", "themes/aurora"):
                (root / name).mkdir(parents=True)
                (root / name / "config.json").write_text("{}", encoding="utf-8")
            (root / "examples" / "demo-2" / "README.md").write_text(
                "TV_HOST=<tv ip> tools/theme.sh examples/demo-2/theme\n"
                "TV_HOST=<tv ip> tools/theme.sh themes/aurora\n", encoding="utf-8")
            self.assertEqual([], theme_problems(root))

    def test_an_option_a_placeholder_and_a_windows_path_are_not_read(self):
        body = ("TV_HOST=<tv ip> tools/theme.sh --reset\n"
                "TV_HOST=<tv ip> tools/theme.sh themes/<name>\n"
                "TV_HOST=<tv ip> tools/theme.sh ~/my-theme\n"
                "TV_HOST=<tv ip> tools/theme.sh $THEME\n"
                '-File tools\\theme.ps1 examples\\demo-1\n')
        self.assertEqual([], theme_arguments(body))

    def test_a_folder_the_reader_makes_is_left_alone(self):
        # freebox-player-pop's page says `cp -r themes/aurora themes/mine` and then pushes themes/mine.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "demo-1"
            folder.mkdir(parents=True)
            (folder / "README.md").write_text(
                "```bash\ncp -r themes/aurora themes/mine\n"
                "TV_HOST=<tv ip> tools/theme.sh themes/mine\n```\n", encoding="utf-8")
            self.assertEqual(["themes/mine"], theme_arguments((folder / "README.md").read_text(encoding="utf-8")))
            self.assertEqual([], theme_problems(root))

    def test_a_folder_gitignore_names_is_skipped(self):
        # tools/venv is what a clone builds, and a list README that runs a command through it is right to:
        # reading that path as a missing file would red the suite for every reader who has not built it yet.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "examples" / "demo-1").mkdir(parents=True)
            (root / "examples" / "demo-1" / "README.md").write_text(
                "`tools/venv/bin/python -I tools/tv.py --probe`\n", encoding="utf-8")
            self.assertEqual([("examples/demo-1/README.md", "tools/tv.py")],
                             [p for p in missing(root) if not p[1].startswith("tools/venv")])
            self.assertIn("tools/venv", ignored_prefixes(), ".gitignore no longer names the venv this skips")


if __name__ == "__main__":
    unittest.main()

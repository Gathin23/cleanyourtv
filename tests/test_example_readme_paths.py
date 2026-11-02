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

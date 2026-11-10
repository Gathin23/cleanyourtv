"""The top of `examples/` holds one folder per tested TV model, and the index that lists them.

`docs/workflows/tv-list.md` step 2 is how a list arrives: `examples/<brand>-<model>/debloat.txt`, with the
folder's `tv.json` beside it. So everything under `examples/` is a folder, the index is
`examples/README.md`, and a model with notes but no tested list goes in `docs/devices/` (the section
`examples/README.md` keeps for them). `AGENTS.md`'s Repo map reads the folder the same way: "One folder per
tested TV model: `debloat.txt`, `tv.json` (its row in the TV tables) ... Indexed in `examples/README.md`".

A file dropped at the top of `examples/` is a stray, which `.github/maintainer/MAINTAINER.md` says is dropped
on merge ("Files nothing links to (screenshots, stray root files, unrelated build changes) are dropped on
merge"). Round AV's fold-in (3f175120) deleted one: `examples/Clean Your TV_.txt`, 165 lines of the Clean
Your TV app's own TV report - the phone it was read on, the set's app list - which arrived beside the folders
with the list PR that carried it (`examples/toshiba-shandao-atv11`, #3382). It is not a list, no page links
to it, and it is not something a reader of the folder needs: the report a list is built from belongs in the
PR or in the folder's own header, not in the tree.

Nothing read the top of `examples/`, though. Every sweep of the folder starts by filtering `is_dir()` - the
list-format checks, the count and header readers, `tools/match.py`'s tally, `tools/gen_tables.py` - so the
report sat in a green tree: 3097 tests OK (skipped=34) and `tools/gen_tables.py --check` exit 0 with it
restored. The fold-in was the only thing that could catch it, and it did, by hand.

So the top is walked here: a folder, or `examples/README.md`, and nothing else. A stand-in tree in the tests
holds the walk itself, so a walk that stops reading fails loudly instead of passing by finding nothing.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"
INDEX = "README.md"


def loose_files(examples):
    """Every entry at the top of `examples/` that is not a folder: the index is the only one allowed.

    A folder is quiet here; what it ships is read by the checks that know each kind of file (a list's
    format, its header's commands, its `tv.json`, its README's count). This rule is about the one level
    that had no reader.
    """
    if not Path(examples).is_dir():
        return []
    return sorted(path.name for path in Path(examples).iterdir()
                  if not path.is_dir() and path.name != INDEX)


def folders(examples):
    """The folders at the top of `examples/`, so a walk that read nothing can be told apart."""
    if not Path(examples).is_dir():
        return []
    return sorted(path.name for path in Path(examples).iterdir() if path.is_dir())


class ExamplesTopLevelTest(unittest.TestCase):
    def test_the_top_of_examples_holds_folders_and_its_index(self):
        found = loose_files(EXAMPLES)
        self.assertEqual([], found,
                         "examples/ holds one folder per tested TV model, plus its README.md index: "
                         "docs/workflows/tv-list.md step 2 adds examples/<brand>-<model>/debloat.txt, and "
                         "a model with notes but no tested list goes in docs/devices/. A report or a "
                         "screenshot at the top is a stray file, which MAINTAINER.md drops on merge. Found: "
                         + ", ".join(found))

    def test_the_walk_reads_the_tree(self):
        # Without this, a walk that stopped finding anything would pass the test above by finding nothing.
        self.assertIn(INDEX, [path.name for path in EXAMPLES.iterdir() if path.is_file()],
                      "examples/README.md is the index the rule above allows")
        self.assertGreaterEqual(len(folders(EXAMPLES)), 350,
                                "expected every list folder under examples/")


class TheWalkTest(unittest.TestCase):
    """The same reader over trees written here."""

    def tree(self, tmp, files):
        examples = Path(tmp) / "examples"
        examples.mkdir(parents=True, exist_ok=True)
        for name, text in files.items():
            path = examples / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        return examples

    def test_a_report_beside_the_folders_is_reported(self):
        # The shape 3f175120 deleted: the app's own report, dropped at the top of the folder.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"README.md": "# Lists\n",
                                       "x-tv/debloat.txt": "# TV\ncom.example.one\n",
                                       "Clean Your TV_.txt": "Clean Your TV: TV report\nPhone: a phone\n"})
            self.assertEqual(["Clean Your TV_.txt"], loose_files(examples))

    def test_the_index_and_a_folder_are_quiet(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"README.md": "# Lists\n",
                                       "x-tv/debloat.txt": "# TV\ncom.example.one\n",
                                       "x-tv/tv.json": "{}\n",
                                       "x-tv/README.md": "# TV\n"})
            self.assertEqual([], loose_files(examples))

    def test_a_file_inside_a_folder_is_not_this_rule(self):
        # A folder ships a screenshot and notes; the checks that read a folder judge those.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"x-tv/home.jpg": "",
                                       "x-tv/notes.txt": "a note beside the list\n"})
            self.assertEqual([], loose_files(examples))

    def test_a_hidden_file_is_reported(self):
        # macOS leaves these in a folder a contributor commits from; one at the top is still a stray.
        with tempfile.TemporaryDirectory() as tmp:
            examples = self.tree(tmp, {"README.md": "# Lists\n", ".DS_Store": ""})
            self.assertEqual([".DS_Store"], loose_files(examples))

    def test_a_missing_folder_is_not_an_error(self):
        # The readers take a root so they can be pointed at a tree; an absent one reads as no stray files.
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], loose_files(Path(tmp) / "examples"))


if __name__ == "__main__":
    unittest.main()

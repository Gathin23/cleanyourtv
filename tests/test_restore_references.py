"""Every undo step in the repo names a file that is there: the list, and a folder's own script.

restore.sh takes the same list that disable.sh ran against (#239): recovery.py reads it, checks every
name against the record taken before the change and prints the batch. So the name in a page's undo
step is what an owner types when the TV looks wrong. A name that is not there is refused before a
single command reaches the TV, which is the worst moment to find out.

The same goes for the install and restore scripts three list folders still ship from before the rule
that a list folder holds none (AGENTS.md, "Hard rules"): a page that sends an owner to
`examples/<folder>/restore.sh` names a file the folder has to have, and the TCL 55S470G header
named one that was not there.

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
# `tools/restore.sh examples/x/debloat.txt com.stock.launcher`, and the PowerShell twin.
REFERENCE = re.compile(r"restore\.(?:sh|ps1)\s+([A-Za-z0-9_./<>-]*\.txt)")
# A helper script inside a list folder, e.g. `examples/epson-ls800/restore.sh`. Three folders still
# ship the install and restore scripts their submission came with (#265); a page or a list header
# that sends an owner to one names a file the folder has to have.
FOLDER_SCRIPT = re.compile(r"(examples/[A-Za-z0-9._-]+/(?:restore|disable|install|build-and-install)\."
                           r"(?:sh|ps1))")

# Every list a reader can run. A page outside examples/ that needs one names that file too.
LIST_FOLDERS = ROOT / "examples"


def references(text):
    """The list names a line of text hands to restore.sh, placeholders like <your tv> left out."""
    return [name for name in REFERENCE.findall(text) if "<" not in name and ">" not in name]


def walk(root):
    """(relative path, text) for every page, list and script under root, in a stable order."""
    for directory, subdirectories, files in os.walk(root):
        subdirectories[:] = sorted(d for d in subdirectories if d not in PRUNE)
        for name in sorted(files):
            if not name.endswith(SUFFIXES):
                continue
            path = Path(directory) / name
            yield path.relative_to(root), path.read_text(encoding="utf-8", errors="replace")


def read_references(root):
    """(file, name) for every restore.sh reference under root, as a reader would copy it."""
    found = []
    for path, text in walk(root):
        for target in references(text):
            found.append((path, target))
    return found


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
        wrong = []
        for path, name in read_references(ROOT):
            own = path.as_posix()
            if own.startswith(LIST_FOLDERS.relative_to(ROOT).as_posix() + "/") and path.name == "debloat.txt" \
                    and name != own:
                wrong.append((own, name))
        self.assertEqual([], wrong, "a list's undo points somewhere else, so it replays another set "
                                    "of packages or nothing at all")

    def test_a_folder_script_a_page_names_is_there(self):
        # examples/epson-ls800, anker-nebula-d2426 and tcl-43c645 ship the install and restore scripts
        # they came with, and their pages name them; the TCL 55S470G header named one its folder does
        # not have, so an owner's first undo command was "No such file".
        missing = [(str(path), name) for path, name in read_script_references(ROOT)
                   if not (ROOT / name).is_file()]
        self.assertEqual([], missing,
                         "these lines hand the owner a script inside a list folder that is not in the "
                         "repo, so the command fails before anything reaches the TV")
        self.assertGreaterEqual(len(read_script_references(ROOT)), 4,
                                "the folders that do ship such a script should be read; the scanner "
                                "found fewer")

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


if __name__ == "__main__":
    unittest.main()

"""A repository-wide walk does not read the folders .gitignore keeps out of a checkout.

AGENTS.md tells the person doing the work where their own files go: their backups (`backup/`),
screenshots (`shots/`), notes (`work/`) and the ADB key (`tools/adbkey/`) "are listed in .gitignore and
must never be committed". `tools/bench.py` writes `bench-results/` where it is run and `tools/trace.py`
writes `traces/`, both gitignored for the identifiers they carry.

Seven test files walk the whole checkout and judge whatever they read as the project's own
documentation: the PowerShell commands (`test_ps1_invocations.py`, `test_powershell_command_quotes.py`),
the shell commands (`test_sh_commands_name_the_tv.py`), the flags a page hands `tools/tv.py`
(`test_tv_py_flags_in_docs.py`), markdown links (`test_markdown_links.py`), picture files
(`test_image_files.py`) and the names AGENTS.md may point at (`test_agents_md.py`). Each carried its own
copy of the skip list, and the copies were three names short of .gitignore - `work`, `bench-results` and
`adbkey` - so the layout AGENTS.md asks for was the one that failed the suite: a `work/notes.txt` that
reads `tools\\disable.ps1 com.vendor.package` was reported as a page typing a package name as a bare
argument, a `work/notes.md` with a relative link failed the link walk, and one naming a made-up `tv.py`
flag failed the flag walk. A clone has no `work/` folder, so CI never saw any of it.

The folders are read out of .gitignore here rather than copied in, so a folder it learns about fails this
check until every walk skips it too, and the message names the file to edit.

`work` is one of those folders, and CI checks this repository out under `/home/runner/work/cleanyourtv`.
A walk that matched the skip list against the absolute path therefore pruned the whole checkout there and
read nothing, which every check starting from what the walk found then passed. The walks match
`relative_to(root)` (or prune by directory name) for that reason, and `WalkBelowASkippedParentTest` holds
them to it: the same small checkout read from an ordinary folder and from below a folder named `work`.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
import tempfile
import types
import unittest

from tests import test_agents_md, test_image_files, test_markdown_links, test_ps1_invocations
from tests import test_powershell_command_quotes, test_sh_commands_name_the_tv, test_tv_py_flags_in_docs


ROOT = Path(__file__).resolve().parents[1]
GITIGNORE = ROOT / ".gitignore"

# The whole-checkout walks, and where each keeps the set it prunes with: two call it SKIP, the rest
# SKIP_DIRS, and each has to be a set here so a walk cannot pass by holding no list at all.
WALKS = {
    "tests/test_agents_md.py": (test_agents_md, "SKIP_DIRS"),
    "tests/test_image_files.py": (test_image_files, "SKIP"),
    "tests/test_markdown_links.py": (test_markdown_links, "SKIP"),
    "tests/test_ps1_invocations.py": (test_ps1_invocations, "SKIP_DIRS"),
    "tests/test_powershell_command_quotes.py": (test_powershell_command_quotes, "SKIP_DIRS"),
    "tests/test_sh_commands_name_the_tv.py": (test_sh_commands_name_the_tv, "SKIP_DIRS"),
    "tests/test_tv_py_flags_in_docs.py": (test_tv_py_flags_in_docs, "SKIP"),
}


def ignored_folders(body):
    """The folders .gitignore keeps out of a checkout, as the name a walk sees in a path's parts.

    A line is a folder when it ends in `/` (`work/`, `tools/adbkey/`) or when its last part carries no
    extension (`tools/venv`). A glob (`*.apk`, `launcher/hs_err_pid*.log`) and a file
    (`launcher/local.properties`, `.DS_Store`) are not folders to prune: no walk meets them as one.
    """
    found = []
    for line in body.split("\n"):
        entry = line.strip()
        if not entry or entry.startswith("#") or "*" in entry:
            continue
        last = entry.rstrip("/").rsplit("/", 1)[-1]
        if entry.endswith("/") or "." not in last:
            found.append(last)
    return found


def missing(skip, folders):
    """The folders a walk's skip set does not carry."""
    return [folder for folder in folders if folder not in skip]


FOLDERS = ignored_folders(GITIGNORE.read_text(encoding="utf-8"))

# The walks, called with the root they read, and the file each is expected to find in a small checkout.
# CI checks this repository out under `/home/runner/work/cleanyourtv/cleanyourtv`, so a walk that pruned
# with the absolute path would read nothing there - and a walk that finds nothing passes every check that
# starts from what it found. These five were named in the review of this file; `test_ps1_invocations.py`
# and `test_tv_py_flags_in_docs.py` read `relative_to(root)` too, and the first holds its own case for it.
WALKERS = {
    "tests/test_agents_md.py": (test_agents_md.file_names, "disable.ps1"),
    "tests/test_image_files.py": (test_image_files.image_files, "screenshot.jpg"),
    "tests/test_markdown_links.py": (test_markdown_links.markdown_files, "README.md"),
    "tests/test_powershell_command_quotes.py": (test_powershell_command_quotes.pages, "README.md"),
    "tests/test_sh_commands_name_the_tv.py": (test_sh_commands_name_the_tv.files, "README.md"),
}


def fixture_checkout(root):
    """A small checkout at `root`: one file each walk reads, and two of the person's own under work/."""
    bodies = {"README.md": b"# a page\n", "tools/disable.ps1": b"param([string]$List)\n",
              "themes/demo/screenshot.jpg": b"\xff\xd8\xff a picture",
              "work/notes.md": b"# the owner's own note\n",
              "work/photos/demo.jpg": b"\xff\xd8\xff the owner's own photo"}
    for name, body in bodies.items():
        path = Path(root) / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(body)
    return Path(root)


def walked(walk, root):
    """The names a walk reads below `root`, whether it yields paths or names."""
    return {Path(entry).name for entry in walk(root)}


def walk_problems(walks=None, folders=None):
    """One line per walk that does not skip a folder .gitignore keeps out of a checkout."""
    walks = WALKS if walks is None else walks
    folders = FOLDERS if folders is None else folders
    found = []
    for name, (module, attribute) in walks.items():
        skip = getattr(module, attribute, None)
        if not isinstance(skip, (set, frozenset)):
            found.append(f"{name} has no {attribute} set, so its walk reads the whole checkout")
            continue
        absent = missing(skip, folders)
        if absent:
            found.append(f"{name} does not skip {', '.join(absent)}: .gitignore keeps "
                         f"{'them' if len(absent) > 1 else 'it'} out of a checkout, so a person's own "
                         f"file there is read as the project's")
    return found


class WalkScopeTest(unittest.TestCase):
    def test_every_walk_skips_the_folders_the_checkout_keeps_out(self):
        problems = walk_problems()
        self.assertEqual([], problems, "\n".join(problems))

    def test_the_walks_carry_one_list(self):
        """The folders are written out once and copied, so the copies stay the same."""
        walks = {name: getattr(module, attribute, None) for name, (module, attribute) in WALKS.items()}
        lists = {frozenset(skip) for skip in walks.values() if skip}
        self.assertEqual(
            1, len(lists),
            "the walks prune different folders:\n" + "\n".join(
                f"{name}: {sorted(skip or ())}" for name, skip in walks.items()))

    def test_the_reader_finds_the_folders_the_file_names(self):
        # Cannot pass by reading nothing: the checkout keeps the person's own files in these.
        self.assertGreaterEqual(len(FOLDERS), 5, f"the .gitignore reader found {FOLDERS}")
        for folder in ("backup", "shots", "work", "traces", "bench-results", "venv", "adbkey"):
            self.assertIn(folder, FOLDERS, f"the reader missed {folder}/ in .gitignore: {FOLDERS}")

    def test_a_glob_and_a_file_are_not_folders(self):
        body = "\n".join(["# a comment", "", "*.apk", "*.aab", ".DS_Store", "launcher/local.properties",
                          "launcher/hs_err_pid*.log", "work/", "backup/", "tools/venv", ""])
        self.assertEqual(["work", "backup", "venv"], ignored_folders(body))

    def test_a_walk_that_misses_one_is_named(self):
        absent = missing({"backup", "shots"}, ["backup", "work", "bench-results"])
        self.assertEqual(["work", "bench-results"], absent)
        self.assertEqual([], missing({"backup", "work", "bench-results"}, ["backup", "work", "bench-results"]))

    def test_a_walk_with_no_list_at_all_is_named(self):
        walk = {"tests/test_written_here.py": (types.SimpleNamespace(SKIP=frozenset({"venv"})), "SKIP_DIRS")}
        problems = walk_problems(walk, {"venv"})
        self.assertEqual(1, len(problems), problems)
        self.assertIn("has no SKIP_DIRS set", problems[0])

    def test_a_short_list_is_named_with_its_folders(self):
        walk = {"tests/test_written_here.py": (types.SimpleNamespace(SKIP={"venv"}), "SKIP")}
        problems = walk_problems(walk, ["venv", "work", "bench-results"])
        self.assertEqual(1, len(problems), problems)
        self.assertIn("work, bench-results", problems[0])
        self.assertIn("tests/test_written_here.py", problems[0])


class WalkBelowASkippedParentTest(unittest.TestCase):
    """The same checkout read from an ordinary folder and from below a folder named `work`.

    CI checks this repository out under `/home/runner/work/cleanyourtv/cleanyourtv`, and `work` is one of
    the folders .gitignore keeps out of a checkout. A walk that matched its skip list against absolute
    parts pruned the whole checkout there and read nothing - and a check that starts from what the walk
    found passes on the empty set, so those seven reports would each have said "nothing to judge". So
    both directions are held: below such a parent the walk reads what it reads in an ordinary folder, and
    inside the checkout the person's own files are still the ones skipped.
    """

    def checkouts(self, scratch):
        """The same fixture checkout in an ordinary folder and below one named `work`."""
        return (fixture_checkout(Path(scratch) / "cleanyourtv"),
                fixture_checkout(Path(scratch) / "work" / "cleanyourtv"))

    def test_a_checkout_below_a_folder_named_work_reads_the_same_files(self):
        with tempfile.TemporaryDirectory() as scratch:
            plain, below = self.checkouts(scratch)
            for name, (walk, expected) in WALKERS.items():
                with self.subTest(walk=name):
                    here = walked(walk, plain)
                    self.assertEqual(
                        sorted(here), sorted(walked(walk, below)),
                        f"{name} reads {sorted(walked(walk, below))} below a folder named work and "
                        f"{sorted(here)} in an ordinary folder")
                    # A floor of its own: a walk that read nothing would match itself in both places.
                    self.assertIn(expected, here, f"{name} did not find {expected} in an ordinary folder")
                    self.assertIn(expected, walked(walk, below),
                                  f"{name} did not find {expected} below a folder named work")

    def test_the_persons_own_files_are_skipped_in_both_places(self):
        with tempfile.TemporaryDirectory() as scratch:
            for where in self.checkouts(scratch):
                for name, (walk, _) in WALKERS.items():
                    with self.subTest(walk=name, where=where.name):
                        names = walked(walk, where)
                        self.assertNotIn("notes.md", names,
                                         f"{name} read work/notes.md as the project's own in {where}")
                        # A picture under work/ is the case test_image_files.py has to prune by folder.
                        self.assertNotIn("demo.jpg", names,
                                         f"{name} read work/photos/demo.jpg as the project's own in {where}")

    def test_the_shape_this_case_was_written_for_is_caught(self):
        """A walk pruning absolute parts reads nothing below `work`, which is what this case pins."""
        def absolute_parts_walk(root):
            names = set()
            for path in Path(root).rglob("*"):
                if not {"work"}.intersection(path.parts):
                    names.add(path.name)
            return names

        with tempfile.TemporaryDirectory() as scratch:
            plain, below = self.checkouts(scratch)
            self.assertIn("README.md", walked(absolute_parts_walk, plain))
            self.assertEqual(set(), walked(absolute_parts_walk, below),
                             f"the fixture does not sit below a folder named work: {below}")
            self.assertNotEqual(
                sorted(walked(absolute_parts_walk, plain)), sorted(walked(absolute_parts_walk, below)),
                "the comparison above cannot tell the two checkouts apart, so it would pass any walk")


if __name__ == "__main__":
    unittest.main()

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

Run from the repository root: python3 -m unittest discover -s tests -v
"""
from pathlib import Path
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


if __name__ == "__main__":
    unittest.main()

"""A list's Undo line and its folder README's restore command hand restore.sh the same stock launcher.

`tools/restore.sh <list> [stock launcher]` enables the package it is given, or
`com.google.android.tvlauncher` when it is given none (see tests/test_stock_launcher_undo.py). An owner
copies whichever undo they find first: the list header, or the README in the same folder. When the two
pass different packages, one of them leaves the stock home disabled. The Skyworth 7S60_A5 README
passes com.tianci.movieplatform, the Coocaa home it tells the owner to disable, while the list's own
Undo line passed nothing.

Only restore commands for the folder's own list are compared, in the bash and PowerShell forms.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import re
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

DEFAULT_STOCK = "com.google.android.tvlauncher"
PACKAGE = r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+"
# `restore.sh examples/x/debloat.txt [package]`, `restore.ps1 examples\x\debloat.txt [-Stock package]`,
# with the list and the package as a page may quote them.
COMMAND = re.compile(
    r"restore\.(?:sh|ps1)\s+[\"'`]?([A-Za-z0-9_./\\-]+\.txt)[\"'`]?"
    r"(?:\s+(?:-Stock\s+)?[\"'`]?(" + PACKAGE + r"))?")


def passed(text, own):
    """The stock launchers the restore commands for list `own` hand restore.sh, the default included."""
    found = set()
    for line in text.splitlines():
        for name, stock in COMMAND.findall(line):
            if name.replace("\\", "/") == own:
                found.add(stock or DEFAULT_STOCK)
    return found


def disagreements(root):
    """How many folders were compared, and one sentence for each whose list and README disagree."""
    compared = 0
    wrong = []
    for readme in sorted((root / "examples").glob("*/README.md")):
        own = f"examples/{readme.parent.name}/debloat.txt"
        if not (root / own).is_file():
            continue
        in_list = passed((root / own).read_text(encoding="utf-8"), own)
        in_readme = passed(readme.read_text(encoding="utf-8"), own)
        if not (in_list and in_readme):
            continue
        compared += 1
        if in_list != in_readme:
            wrong.append(f"{own}: its Undo passes {', '.join(sorted(in_list))}, "
                         f"its README.md passes {', '.join(sorted(in_readme))}")
    return compared, wrong


class FolderUndoAgreesTest(unittest.TestCase):
    def test_list_and_readme_pass_the_same_stock_launcher(self):
        compared, wrong = disagreements(ROOT)
        self.assertGreaterEqual(compared, 20, "the sweep compared too few folders to be meaningful")
        self.assertEqual([], wrong, "\n".join(wrong))

    def test_no_package_means_the_default(self):
        own = "examples/x/debloat.txt"
        self.assertEqual({DEFAULT_STOCK}, passed("# Undo:    tools/restore.sh examples/x/debloat.txt\n", own))
        self.assertEqual({"com.tianci.movieplatform"},
                         passed("tools/restore.sh examples/x/debloat.txt com.tianci.movieplatform\n", own))

    def test_the_powershell_form_is_read(self):
        own = "examples/x/debloat.txt"
        line = "tools\\restore.ps1 examples\\x\\debloat.txt -Stock com.google.android.apps.tv.launcherx\n"
        self.assertEqual({"com.google.android.apps.tv.launcherx"}, passed(line, own))

    def test_a_folder_that_disagrees_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "x"
            folder.mkdir(parents=True)
            (folder / "debloat.txt").write_text(
                "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n", encoding="utf-8")
            (folder / "README.md").write_text(
                "tools/restore.sh examples/x/debloat.txt com.tianci.movieplatform\n", encoding="utf-8")
            compared, wrong = disagreements(root)
            self.assertEqual(1, compared)
            self.assertEqual(["examples/x/debloat.txt: its Undo passes com.google.android.tvlauncher, "
                              "its README.md passes com.tianci.movieplatform"], wrong)
            (folder / "debloat.txt").write_text(
                "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt com.tianci.movieplatform\n",
                encoding="utf-8")
            self.assertEqual((1, []), disagreements(root))

    def test_another_lists_restore_is_not_compared(self):
        self.assertEqual(set(), passed("tools/restore.sh examples/y/debloat.txt com.example.home\n",
                                       "examples/x/debloat.txt"))


if __name__ == "__main__":
    unittest.main()

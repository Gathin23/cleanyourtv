"""A list's Undo line and its folder README's restore command hand restore.sh the same stock launcher.

`tools/restore.sh <list> [stock launcher]` enables the package it is given, or
`com.google.android.tvlauncher` when it is given none (see tests/test_stock_launcher_undo.py). An owner
copies whichever undo they find first: the list header, or the README in the same folder. When the two
pass different packages, one of them leaves the stock home disabled. The Skyworth 7S60_A5 README
passes com.tianci.movieplatform, the Coocaa home it tells the owner to disable, while the list's own
Undo line passed nothing.

Only restore commands for the folder's own list are compared, in the bash and PowerShell forms.

A folder can hold a second list beside debloat.txt (debloat-keep-cast.txt, debloat-65u7esg.txt,
debloat-32-inch.txt, debloat-extra.txt, debloat-display-only.txt x2, debloat-75t8c-eu.txt), and
tools/disable.sh and restore.sh take one like any other, so the README's way back from that list is
the same rule on the same terms. This walk read debloat.txt alone, so a README that sent an owner to
restore.sh with a second list and a different stock launcher than that list's own Undo line was quiet -
the failure mode below, in the file the owner applied second. No folder's README hands a restore
command for its second list today, so nothing fails now: the check is there for the first one that
does, which is why docs/workflows/tv-list.md states the rule for "the list" and not for debloat.txt.

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


def lists(root):
    """Every list the folders under examples/ ship: each debloat.txt and a second list beside it."""
    return sorted((root / "examples").glob("*/debloat*.txt"))


def disagreements(root):
    """How many list and README pairs were compared, and one sentence for each that disagrees.

    A folder's second list is compared the same way: the README's restore command for that file against
    that file's own Undo line. A folder with no README, or a file neither place hands to restore.sh, has
    nothing to compare and is left out, as before.
    """
    compared = 0
    wrong = []
    for own in lists(root):
        readme = own.parent / "README.md"
        if not readme.is_file():
            continue
        rel = own.relative_to(root).as_posix()
        in_list = passed(own.read_text(encoding="utf-8"), rel)
        in_readme = passed(readme.read_text(encoding="utf-8"), rel)
        if not (in_list and in_readme):
            continue
        compared += 1
        if in_list != in_readme:
            wrong.append(f"{rel}: its Undo passes {', '.join(sorted(in_list))}, "
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

    def test_the_sweep_reads_every_list_a_folder_ships(self):
        # Eight folders ship a second list, which restore.sh takes like the first: a walk back to
        # */debloat.txt alone stops holding the README's way back from them.
        read = lists(ROOT)
        seconds = [p for p in read if p.name != "debloat.txt"]
        self.assertGreaterEqual(len(read), 470, "the sweep read too few lists to be meaningful")
        self.assertGreaterEqual(len(seconds), 8, "the sweep read too few second lists to be meaningful")
        names = {p.name for p in seconds}
        self.assertIn("debloat-75t8c-eu.txt", names)
        self.assertIn("debloat-keep-cast.txt", names)
        self.assertIn(ROOT / "examples" / "google-chromecast-gtv-4k" / "debloat-keep-cast.txt", read)

    def test_a_second_list_that_disagrees_with_its_readme_is_reported(self):
        # This walk read debloat.txt alone, so the same difference in a second list passed.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "x"
            folder.mkdir(parents=True)
            (folder / "debloat.txt").write_text("com.example.one\n", encoding="utf-8")
            second = folder / "debloat-display-only.txt"
            second.write_text(
                "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat-display-only.txt com.google.android.apps.tv.launcherx\n"
                "com.example.two\n", encoding="utf-8")
            readme = folder / "README.md"
            readme.write_text(
                "tools/restore.sh examples/x/debloat-display-only.txt\n", encoding="utf-8")
            compared, wrong = disagreements(root)
            self.assertEqual(1, compared)
            self.assertEqual(["examples/x/debloat-display-only.txt: its Undo passes "
                              "com.google.android.apps.tv.launcherx, its README.md passes "
                              "com.google.android.tvlauncher"], wrong)
            readme.write_text(
                "tools/restore.sh examples/x/debloat-display-only.txt com.google.android.apps.tv.launcherx\n",
                encoding="utf-8")
            self.assertEqual((1, []), disagreements(root))

    def test_a_second_list_no_readme_undoes_is_left_out(self):
        # Four folders that ship a second list have no README at all, and the other four never hand
        # restore.sh one of them: nothing to compare is not a disagreement.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "examples" / "x"
            folder.mkdir(parents=True)
            (folder / "debloat.txt").write_text(
                "# Undo:    tools/restore.sh examples/x/debloat.txt\n", encoding="utf-8")
            (folder / "debloat-extra.txt").write_text(
                "# Undo:    tools/restore.sh examples/x/debloat-extra.txt com.example.home\n",
                encoding="utf-8")
            (folder / "README.md").write_text(
                "tools/restore.sh examples/x/debloat.txt\n", encoding="utf-8")
            self.assertEqual((1, []), disagreements(root))

    def test_another_lists_restore_is_not_compared(self):
        self.assertEqual(set(), passed("tools/restore.sh examples/y/debloat.txt com.example.home\n",
                                       "examples/x/debloat.txt"))


if __name__ == "__main__":
    unittest.main()

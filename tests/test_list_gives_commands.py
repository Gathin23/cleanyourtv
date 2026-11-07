"""A list header names the command it was applied with, or the page where that command is written.

`docs/workflows/tv-list.md` step 2 asks for the Apply and Undo lines beside the model, the firmware
and the stock launcher, because the file an owner downloads is the only place they look: the tools
want `TV_HOST` and the list path, and `tools/restore.sh` wants the same list back. Every folder in
`examples/` that ships a list names one of `tools/disable.sh`, `tools/disable.ps1`, `tools/restore.sh`,
`tools/restore.ps1` or `tools/check.py` in the comment block above its first package line, or points
at a README that does (four of them say "the commands in README.md here" because the firmware refuses
`pm disable-user` and the batch is their own).

The walk reads every list a folder ships (`*/debloat*.txt`), not only its `debloat.txt`. Eight folders
keep a second list beside it - `debloat-keep-cast.txt` on the two Google TV boxes that keep casting,
`debloat-display-only.txt` on the two Sony sets used as a screen, and `debloat-65u7esg.txt`,
`debloat-32-inch.txt`, `debloat-extra.txt`, `debloat-75t8c-eu.txt` - and each is a file an owner opens
and applies with the same two commands (the header of the OPPO one says to apply it on top of the
folder's own list). The other sweeps over lists in `tests/` already read them (`test_examples.py`,
`test_list_header_counts.py`, `test_stock_launcher_undo.py`); this one globbed `debloat.txt`, so eight
headers were held to no rule at all. They name a command today, and the walk now says so.

`examples/mecool-km2-plus` named none: seven header lines of model and build, one package, and no
command at all, so an owner who opened it had to work out which script sends a list and that undo is
a second one. Nothing read this - the checks over a list look at its package lines, its counts, its
folder and its references, never at whether the file says how to apply it.

The rule is about the header, not the file: a comment halfway down that happens to name a script is
usually a quirk about that package ("disable.sh still refused setupwraith because of the old network"),
not the way to apply the list.
"""
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
EXAMPLES = ROOT / "examples"

# The tools a header can name: the two ways to apply a list, the two ways to undo it, and check.py,
# which a list that expects the TV to undo an update itself sends the owner to.
COMMANDS = ("disable.sh", "disable.ps1", "restore.sh", "restore.ps1", "check.py")
POINTER = "README.md"


def header(text):
    """The comment lines above a list's first package line, blank lines included.

    A list is comments and package names, so the header starts at the first line and ends at the
    first one that is neither blank nor a comment - or at the end of a file that is all comments.
    """
    lines = []
    for line in text.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("#"):
            break
        lines.append(line)
    return "\n".join(lines)


def problem(text):
    """Why this list's header gives a reader no command, or None when it names one."""
    head = header(text)
    if any(command in head for command in COMMANDS):
        return None
    if POINTER in head:
        return None
    return ("its header names none of " + ", ".join(COMMANDS) + " and no README, so a reader who "
            "opens the list has no command to apply it or undo it")


def lists(root=None):
    """(folder name, path) for every list in examples/, in tree order.

    `debloat*.txt`, the discovery the other list sweeps use: a folder that ships a second list
    (`debloat-extra.txt`, `debloat-display-only.txt`) ships a second file an owner opens and applies.
    """
    tree = root or EXAMPLES
    return [(path.parent.name, path) for path in sorted(tree.glob("*/debloat*.txt")) if path.is_file()]


class TreeTest(unittest.TestCase):
    def test_every_list_header_names_a_command_or_a_readme(self):
        found = lists()
        self.assertGreaterEqual(len(found), 390, "the reader found no lists to check")
        bad = []
        for folder, path in found:
            why = problem(path.read_text(encoding="utf-8"))
            if why:
                bad.append(f"examples/{folder}/{path.name}: {why}")
        self.assertEqual([], bad, "a list header that gives an owner no command")

    def test_the_walk_reaches_the_second_list_a_folder_ships(self):
        names = {path.name for _, path in lists()}
        self.assertIn("debloat-extra.txt", names,
                      "a folder's second list is a file an owner applies, so it is read too")
        self.assertGreater(len(names), 1)


class TheWalk(unittest.TestCase):
    """lists() over a tree written here, so the discovery is pinned rather than the repo's names."""

    def walk(self, files):
        with tempfile.TemporaryDirectory() as tmp:
            for name, text in files.items():
                path = Path(tmp, name)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            return sorted(path.name for _, path in lists(Path(tmp)))

    def test_a_second_list_that_names_no_command_is_reported(self):
        command = "# Apply: tools/disable.sh examples/tv/debloat.txt\ncom.example.bloat\n"
        quiet = "# Some TV, second unit\n# Build: X\ncom.example.other\n"
        with tempfile.TemporaryDirectory() as tmp:
            for name, text in {"tv/debloat.txt": command, "tv/debloat-extra.txt": quiet}.items():
                path = Path(tmp, name)
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
            found = lists(Path(tmp))
            self.assertEqual(["debloat-extra.txt", "debloat.txt"],
                             sorted(path.name for _, path in found))
            bad = [f"examples/{folder}/{path.name}"
                   for folder, path in found
                   if problem(path.read_text(encoding="utf-8"))]
            self.assertEqual(["examples/tv/debloat-extra.txt"], bad,
                             "the second list is the one that gives a reader no command")

    def test_a_second_list_that_names_a_command_passes(self):
        same = ("# Some TV, second unit\n"
                "# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/tv/debloat-extra.txt\n"
                "com.example.other\n")
        self.assertIsNone(problem(same))
        self.assertEqual(["debloat-extra.txt"],
                         self.walk({"tv/debloat-extra.txt": same}),
                         "a folder with no debloat.txt still has its list read")

    def test_a_list_two_folders_down_and_a_stray_txt_are_not_lists(self):
        text = "# no command here\ncom.example.bloat\n"
        self.assertEqual([], self.walk({"tv/2026/debloat.txt": text,
                                        "tv/debloat.txt.bak": text,
                                        "tv/debloat-old.md": text}))


class TheReader(unittest.TestCase):
    """problem() over headers written here, so the rule is pinned rather than the tree's wording."""

    def test_apply_and_undo_lines_are_read(self):
        text = ("# MECOOL KM2 Plus\n"
                "# Apply:   TV_HOST=<tv ip> tools/disable.sh examples/x/debloat.txt\n"
                "# Undo:    TV_HOST=<tv ip> tools/restore.sh examples/x/debloat.txt\n"
                "# Everything is DISABLED, not uninstalled.\n"
                "\n"
                "# Stock recommendations\n"
                "com.google.android.tvrecommendations\n")
        self.assertIsNone(problem(text))

    def test_the_windows_twin_counts(self):
        text = ("# Box, build X\n"
                "# Apply:   tools\\disable.ps1 -TvHost <box ip> examples\\x\\debloat.txt\n"
                "com.example.bloat\n")
        self.assertIsNone(problem(text), "a Windows list names disable.ps1 rather than disable.sh")

    def test_a_pointer_at_the_folder_readme_counts(self):
        text = ("# Nokia Streaming Box 8000\n"
                "# Apply and undo: the commands in README.md here, NOT tools/disable.sh (this firmware\n"
                "# refuses it).\n"
                "com.example.bloat\n")
        self.assertIsNone(problem(text))

    def test_a_header_with_no_command_is_reported(self):
        text = ("# MECOOL KM2 Plus\n"
                "# Brand: MECOOL\n"
                "# Build: UKG3.250826.001.C1.3.3_20260611\n"
                "\n"
                "# Stock recommendations and ads\n"
                "com.google.android.tvrecommendations\n")
        why = problem(text)
        self.assertTrue(why and "no command to apply it or undo it" in why, why)

    def test_a_comment_below_the_first_package_is_not_the_header(self):
        text = ("# Some TV\n"
                "com.example.bloat\n"
                "# disable.sh still refused setupwraith on this firmware\n"
                "com.example.other\n")
        self.assertIsNotNone(problem(text), "the rule is about the header, not the whole file")

    def test_a_blank_line_inside_the_header_does_not_end_it(self):
        text = ("# Some TV\n"
                "\n"
                "# Undo: tools/restore.sh examples/x/debloat.txt\n"
                "\n"
                "com.example.bloat\n")
        self.assertIsNone(problem(text))

    def test_a_list_that_is_all_comments_is_read_to_the_end(self):
        self.assertIsNone(problem("# Some TV\n# Apply: tools/disable.sh examples/x/debloat.txt\n"))
        self.assertIsNotNone(problem("# Some TV\n# Model: X\n"))

    def test_another_tool_is_not_an_apply_or_undo_command(self):
        text = ("# Some TV\n"
                "# the wallpapers came from tools/scenes_wallpapers.py\n"
                "com.example.bloat\n")
        self.assertIsNotNone(problem(text), "only the five commands that send or undo a list count")


if __name__ == "__main__":
    unittest.main()

"""A list header names the command it was applied with, or the page where that command is written.

`docs/workflows/tv-list.md` step 2 asks for the Apply and Undo lines beside the model, the firmware
and the stock launcher, because the file an owner downloads is the only place they look: the tools
want `TV_HOST` and the list path, and `tools/restore.sh` wants the same list back. 397 of the 398
folders in `examples/` name one of `tools/disable.sh`, `tools/disable.ps1`, `tools/restore.sh`,
`tools/restore.ps1` or `tools/check.py` in the comment block above their first package line, or point
at a README that does (four of them say "the commands in README.md here" because the firmware refuses
`pm disable-user` and the batch is their own).

`examples/mecool-km2-plus` named none: seven header lines of model and build, one package, and no
command at all, so an owner who opened it had to work out which script sends a list and that undo is
a second one. Nothing read this - the checks over a list look at its package lines, its counts, its
folder and its references, never at whether the file says how to apply it.

The rule is about the header, not the file: a comment halfway down that happens to name a script is
usually a quirk about that package ("disable.sh still refused setupwraith because of the old network"),
not the way to apply the list.
"""
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


def lists():
    """(folder name, path) for every debloat.txt in examples/, in tree order."""
    return [(path.parent.name, path) for path in sorted(EXAMPLES.glob("*/debloat.txt"))]


class TreeTest(unittest.TestCase):
    def test_every_list_header_names_a_command_or_a_readme(self):
        found = lists()
        self.assertGreaterEqual(len(found), 390, "the reader found no lists to check")
        bad = []
        for folder, path in found:
            why = problem(path.read_text(encoding="utf-8"))
            if why:
                bad.append(f"examples/{folder}/debloat.txt: {why}")
        self.assertEqual([], bad, "a list header that gives an owner no command")


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

"""The sweeps over examples/ read every list there, not only each folder's debloat.txt.

A folder can hold a second list beside debloat.txt:
examples/google-chromecast-gtv-4k/debloat-keep-cast.txt is the same cleanup minus the three packages
that cost something on that device (setupwraith for phone casting, dreamx and backdrop for the
screensaver), with its own Apply, Undo and Check lines, and tools/disable.sh and restore.sh
take it like any other list. The list format check (tests/test_examples.py), the stock-launcher undo
check (tests/test_stock_launcher_undo.py) and the rule that a list's undo names that list
(tests/test_restore_references.py) read only debloat.txt, so a second list with a package listed twice
or an undo that names another file passed all three. tests/test_core_packages.py and
tests/test_apply_override_caveat.py already read both.

tools/match.py's per-package tally was the fourth: it printed how many examples/*/debloat.txt disable a
package, so a package only a second list carries - examples/oppo-k9-55-oppocnm632/debloat-extra.txt's
com.changba.sd, com.google.android.videos on four of the eight second lists - read as 0, "no tested list
has tried this", in the one tool an owner runs before disabling something.

The mic walk (tests/test_remote_mic_bridge_lists.py) was the fifth, on both halves of the same rule: the
`grep -l '^com.google.android.tv.remote.service'` that docs/TROUBLESHOOTING.md hands a reader who wants
to know which lists turn the remote's bridge off, and the walk that models it. Both read
`examples/*/debloat.txt`, so a second list that carries the line - examples/tcl-65c7k-google-tv's
debloat-75t8c-eu.txt, which already carries a katniss line with the mirror of the same warning - would
answer "not in the set" to the reader and to the check.

Each test below runs the function a sweep calls (format_problems, sweep, commands_for_another_list,
match.tally, the mic walk's lists and grep) on a temp tree whose second list breaks that sweep's rule, so
a sweep that goes back to reading debloat.txt alone fails here.

The kept-stock-launcher rule (tests/test_kept_stock_launcher_install.py) was the last one: it read only
each folder's debloat.txt, so a second list could pass a stock launcher as STOCK_LAUNCHER= while its own
note says to keep that package enabled - the failure mode the rule exists for on the two Xiaomi CN lists
- and nothing saw it. Its walk reads both kinds of file now.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
# Imported as modules so their tests do not run twice.
import test_examples as list_format  # noqa: E402
import test_kept_stock_launcher_install as kept_install  # noqa: E402
import test_remote_mic_bridge_lists as mic_lists  # noqa: E402
import test_restore_references as references  # noqa: E402
import test_stock_launcher_undo as stock_undo  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import match  # noqa: E402

KEEP_CAST = ROOT / "examples" / "google-chromecast-gtv-4k" / "debloat-keep-cast.txt"
LAUNCHERX = "com.google.android.apps.tv.launcherx"


def write(root, path, text):
    target = root / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")
    return target


class SecondListsTest(unittest.TestCase):
    def test_the_keep_cast_list_is_read_by_every_sweep(self):
        self.assertTrue(KEEP_CAST.is_file(), "this test needs the Chromecast folder's second list")
        own = KEEP_CAST.relative_to(ROOT).as_posix()
        self.assertIn(KEEP_CAST, list_format.list_files(ROOT / "examples"))
        self.assertIn(KEEP_CAST, stock_undo.list_files(ROOT))
        self.assertIn((own, own), references.own_commands(ROOT))
        self.assertIn((own, own), references.own_commands(ROOT, references.APPLY))

    def test_a_second_list_with_a_package_listed_twice_is_reported(self):
        # test_list_format read only debloat.txt, so this passed.
        with tempfile.TemporaryDirectory() as tmp:
            examples = Path(tmp) / "examples"
            write(examples, "x/debloat.txt", "# TV\ncom.example.one\n")
            write(examples, "x/debloat-keep-two.txt", "# TV\ncom.example.one\ncom.example.one\n")
            self.assertEqual(["x/debloat-keep-two.txt: listed twice: ['com.example.one']"],
                             list_format.format_problems(examples))

    def test_a_second_list_whose_undo_misses_its_stock_launcher_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "examples/x/debloat.txt",
                  f"# Undo: tools/restore.sh examples/x/debloat.txt {LAUNCHERX}\n{LAUNCHERX}\n")
            write(root, "examples/x/debloat-keep-two.txt",
                  f"# Stock launcher: {LAUNCHERX}\n"
                  "# Undo: tools/restore.sh examples/x/debloat-keep-two.txt\n"
                  f"{LAUNCHERX}\n")
            lists, _, _, _, failures = stock_undo.sweep(root)
            self.assertEqual(2, lists)
            self.assertEqual(1, len(failures), failures)
            self.assertTrue(failures[0].startswith("examples/x/debloat-keep-two.txt: "), failures[0])
            self.assertIn("does not pass it", failures[0])

    def test_a_second_list_whose_commands_name_another_file_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "examples/x/debloat.txt",
                  "# Apply: tools/disable.sh examples/x/debloat.txt\n"
                  "# Undo:  tools/restore.sh examples/x/debloat.txt\n"
                  "com.example.one\n")
            write(root, "examples/x/debloat-keep-two.txt",
                  "# Apply: tools/disable.sh examples/x/debloat.txt\n"
                  "# Undo:  tools/restore.sh examples/x/debloat.txt\n"
                  "com.example.two\n")
            for pattern in (references.REFERENCE, references.APPLY):
                with self.subTest(pattern=pattern.pattern):
                    self.assertEqual([("examples/x/debloat-keep-two.txt", "examples/x/debloat.txt")],
                                     references.commands_for_another_list(root, pattern))

    def test_the_tally_reads_a_second_list(self):
        # tools/match.py counted only debloat.txt, so a package on the second list alone printed as 0 -
        # the answer an owner reads as "no tested list disables this".
        with tempfile.TemporaryDirectory() as tmp:
            examples = Path(tmp) / "examples"
            write(examples, "x/debloat.txt", "com.example.one\n")
            write(examples, "x/debloat-extra.txt", "com.example.two  # karaoke\n")
            counts, read = match.tally(["com.example.one", "com.example.two"], examples)
            self.assertEqual(2, read, "the second list was not counted")
            self.assertEqual((1, 0, ""), counts["com.example.one"])
            self.assertEqual((1, 0, "karaoke"), counts["com.example.two"])

    def test_the_kept_stock_launcher_sweep_reads_a_second_list(self):
        with tempfile.TemporaryDirectory() as tmp:
            examples = Path(tmp) / "examples"
            write(examples, "x/debloat.txt", "# Stock launcher: com.mitv.tvhome\ncom.example.one\n")
            write(examples, "x/debloat-keep-two.txt",
                  "# Stock launcher: com.mitv.tvhome - KEEP IT ENABLED, the cast receiver needs its Home.\n"
                  "# Install with: STOCK_LAUNCHER=com.mitv.tvhome launcher/build-and-install.sh\n"
                  "com.example.one\n")
            lists, second, _, _, failures = kept_install.sweep(examples)
            self.assertEqual((2, 1), (lists, second))
            self.assertEqual(1, len(failures), failures)
            self.assertIn("debloat-keep-two.txt", failures[0])
            self.assertIn("passes STOCK_LAUNCHER=com.mitv.tvhome", failures[0])

    def test_the_mic_walk_and_its_grep_read_a_second_list(self):
        # docs/TROUBLESHOOTING.md's bullet and the walk that models it both read examples/*/debloat.txt,
        # so a second list carrying the line answered "not in the set".
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "examples/x/debloat.txt", "com.example.one\n")
            second = write(root, "examples/x/debloat-display-only.txt",
                           f"# TV\n{mic_lists.PACKAGE}\n")
            self.assertIn(second, mic_lists.lists(root))
            self.assertEqual({"x"}, mic_lists.grep_finds(
                "^" + mic_lists.PACKAGE.replace(".", r"\."), mic_lists.LISTS_GLOB, root))


if __name__ == "__main__":
    unittest.main()

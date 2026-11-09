"""tools/gen_tables.py publishes its pages as one set: a write that cannot finish changes none of them.

Path.write_text() opens the file before it writes, so a page nobody could finish - a full disk, a
read-only checkout, a file another process holds open - was left cut short under the name the repository
ships it at. On README.md that is the front page cut to 16,384 bytes by a run stopped at a 16 KB file
limit, a traceback and exit 1, the code --check keeps for "the tables are out of date"; and the pages
written before the one that stopped kept the new counts while the ones after it kept the old ones, so the
tree was one no single run had written.

These tests run the script under a real file-size limit (RLIMIT_FSIZE, the same stop `ulimit -f` gives a
shell) and through a stand-in for a full disk, and hold it to the three things the change promises: the
page that stopped the run is named, every page keeps the text it had, and a run that can write still
reports the pages it updated. Two more pin what a replaced page keeps - the mode it had and the symlink a
reader may have put there.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import errno
import os
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
sys.path.insert(0, str(ROOT / "tools"))
import gen_tables  # noqa: E402  (needs tools/ on the path first)
from test_gen_tables import add, run, tree, tv  # noqa: E402

PAGES = ["README.md", "examples/README.md", "tools/readme_stats.py"]
LIMIT = 16 * 1024          # what `ulimit -f 16` gives a shell: 16 KB, in bytes once it reaches a process


def run_limited(root, *args, limit=LIMIT):
    """The script as a process whose every write past `limit` bytes fails, as a full disk's would."""
    import resource

    def cap():
        resource.setrlimit(resource.RLIMIT_FSIZE, (limit, limit))

    return subprocess.run([sys.executable, str(ROOT / "tools" / "gen_tables.py"), "--root", str(root), *args],
                          capture_output=True, text=True, preexec_fn=cap)


def padded(tmp):
    """A small checkout whose README is over the limit, and a third list, so a run has pages to rewrite."""
    root = tree(tmp, [("tcl-a", tv("TCL A", "TCL")), ("tcl-b", tv("TCL B", "TCL"))])
    readme = root / "README.md"
    readme.write_text(readme.read_text(encoding="utf-8") + "Hand-written text stays. " * 700, encoding="utf-8")
    add(root, "tcl-c", tv("TCL C", "TCL"))
    return root


def contents(root):
    return {page: (root / page).read_bytes() for page in PAGES}


@unittest.skipUnless(sys.platform != "win32", "RLIMIT_FSIZE is a POSIX limit")
class TheWriteThatCannotFinishTest(unittest.TestCase):
    def test_a_run_that_runs_out_of_room_leaves_every_page_as_it_was(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = padded(tmp)
            before = contents(root)
            done = run_limited(root)
            self.assertEqual(done.returncode, 2, done.stdout + done.stderr)
            for page, data in before.items():
                self.assertEqual((root / page).read_bytes(), data,
                                 f"{page} was changed by a run that could not finish")

    def test_the_page_that_stopped_it_is_named_and_no_staging_folder_is_left(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = padded(tmp)
            done = run_limited(root)
            self.assertIn("cannot write README.md", done.stderr)
            self.assertIn("File too large", done.stderr)
            self.assertIn("Nothing was written", done.stderr)
            self.assertNotIn("Traceback", done.stderr)
            leftovers = [p.name for p in root.iterdir() if p.name.startswith(".")]
            self.assertEqual(leftovers, [], f"the run left staging folders behind: {leftovers}")

    def test_an_adopt_run_that_cannot_write_a_tv_json_says_so_and_what_it_left(self):
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as patches:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL"))])
            add(root, "tcl-c", None)       # a folder with a list and no tv.json yet
            patch = Path(patches) / "old-style.diff"
            patch.write_text("| `tcl-c/` | TCL C | Android TV 11 | ads off |\n", encoding="utf-8")
            done = run_limited(root, "--adopt", str(patch), limit=40)
            self.assertEqual(done.returncode, 2, done.stdout + done.stderr)
            self.assertIn("cannot write examples/tcl-c/tv.json", done.stderr)
            self.assertIn("No tv.json was written.", done.stderr)
            self.assertNotIn("Traceback", done.stderr)
            self.assertFalse((root / "examples" / "tcl-c" / "tv.json").exists())


class TheSetIsWrittenTogetherTest(unittest.TestCase):
    def test_a_page_that_cannot_be_staged_stops_the_run_before_any_other_is_replaced(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL"))])
            before = contents(root)
            real = Path.write_text

            def flaky(self, text, *args, **kwargs):
                if self.name == "readme_stats.py":     # the last of the three, so two were written
                    raise OSError(errno.ENOSPC, "No space left on device")
                return real(self, text, *args, **kwargs)

            with mock.patch.object(Path, "write_text", flaky):
                with self.assertRaises(gen_tables.Problem) as caught:
                    gen_tables.publish(root, [(page, "the new text\n") for page in PAGES])
            message = str(caught.exception)
            self.assertIn("cannot write tools/readme_stats.py", message)
            self.assertIn("No space left on device", message)
            for page, data in before.items():
                self.assertEqual((root / page).read_bytes(), data,
                                 f"{page} was replaced by a run that stopped on another page")
            self.assertEqual([p.name for p in root.iterdir() if p.name.startswith(".")], [])

    def test_a_run_that_can_write_reports_every_page_it_updated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL")), ("tcl-b", tv("TCL B", "TCL"))])
            add(root, "tcl-c", tv("TCL C", "TCL"))
            code, out = run(root)
            self.assertEqual(code, 0, out)
            self.assertIn("Updated: README.md, examples/README.md, tools/readme_stats.py", out)


class TheReplacedPageTest(unittest.TestCase):
    def test_an_existing_page_keeps_the_mode_it_had(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            page = root / "README.md"
            page.write_text("old\n", encoding="utf-8")
            os.chmod(page, 0o640)
            gen_tables.publish(root, [("README.md", "new\n")])
            self.assertEqual(page.read_text(encoding="utf-8"), "new\n")
            self.assertEqual(stat.S_IMODE(page.stat().st_mode), 0o640)

    def test_a_symlinked_page_keeps_its_link(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            real = root / "real-readme.md"
            real.write_text("old\n", encoding="utf-8")
            link = root / "README.md"
            link.symlink_to(real.name)     # a checkout whose page is a link to a copy elsewhere
            gen_tables.publish(root, [("README.md", "new\n")])
            self.assertTrue(link.is_symlink(), "the run replaced the link with a regular file")
            self.assertEqual(real.read_text(encoding="utf-8"), "new\n")


if __name__ == "__main__":
    unittest.main()

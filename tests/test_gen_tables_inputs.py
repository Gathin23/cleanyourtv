"""tools/gen_tables.py refuses a path it cannot read instead of dying in a traceback, the way it refuses a font.

Exit 1 is what main's "README tables" job reads as "the tables are out of date", so the run that never read a
byte must not come back as 1: `--root` on a folder that is not a checkout, and `--adopt` on a patch that is not
there, both ended in a FileNotFoundError traceback - exit 1 - before, which is the same answer a stale README.md
gives. The script's own docstring names 2 for "what it was given cannot be read", and `--font` on a path that is
not there already exits 2 through ap.error, so this is one rule applied to its other two paths.

Every test runs the real script in a subprocess, since the exit status and the line on stderr are what a
maintainer sees; the trees come from tests/test_gen_tables.py, so a valid checkout is built the same way there.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
sys.path.insert(0, str(ROOT / "tools"))
from test_gen_tables import add, tree, tv  # noqa: E402  (the small checkout the other gen_tables tests use)

SCRIPT = ROOT / "tools" / "gen_tables.py"


def run(*args, cwd=None):
    """(exit status, stdout, stderr) of the real script."""
    done = subprocess.run([sys.executable, str(SCRIPT), *args], cwd=cwd, capture_output=True, text=True)
    return done.returncode, done.stdout, done.stderr


def valid(tmp):
    """The small checkout tests/test_gen_tables.py builds: examples/ with two folders and the three files."""
    return tree(tmp, [("tcl-a", tv("TCL A", "TCL")), ("sony-b", tv("Sony B", "Sony"))])


class RootTest(unittest.TestCase):
    def test_a_root_that_is_not_a_folder_exits_2(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "nope"
            code, out, err = run("--root", str(root), "--check")
            self.assertEqual(code, 2, err)
            self.assertIn("not a folder", err)
            self.assertIn(str(root), err)
            self.assertNotIn("Traceback", err)
            self.assertEqual(out, "")

    def test_a_root_that_is_a_file_exits_2(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "README.md"
            path.write_text("# not a checkout\n", encoding="utf-8")
            code, out, err = run("--root", str(path), "--check")
            self.assertEqual(code, 2, err)
            self.assertIn("not a folder", err)
            self.assertEqual(out, "")

    def test_the_root_it_could_not_read_is_quoted(self):
        # Quoted, so the line shows the path it was handed even when that token is empty or has spaces in it.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "no such checkout"
            code, _, err = run("--root", str(root), "--check")
            self.assertEqual(code, 2, err)
            self.assertIn(f"--root '{root}'", err)

    def test_a_folder_that_is_not_a_checkout_names_what_is_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "empty"
            root.mkdir()
            code, out, err = run("--root", str(root), "--check")
            self.assertEqual(code, 2, err)
            for name in ("examples/", "README.md", "examples/README.md", "tools/readme_stats.py"):
                with self.subTest(missing=name):
                    self.assertIn(name, err)
            self.assertEqual(out, "")

    def test_one_missing_file_is_named_and_the_others_are_not(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            (root / "tools" / "readme_stats.py").unlink()
            code, out, err = run("--root", str(root), "--check")
            self.assertEqual(code, 2, err)
            self.assertIn("no tools/readme_stats.py in it", err)
            # The other four paths must not be named as missing: pin the phrase, not a bare "examples/",
            # since the line also carries the root, whose path a runner could one day spell with it.
            self.assertNotIn("no examples/", err)

    def test_a_refused_root_is_never_the_out_of_date_exit(self):
        # The one thing a weekly job and the README-tables job both read as a real finding.
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "repo").mkdir()
            code, _, err = run("--root", str(Path(tmp) / "repo"), "--check")
            self.assertNotEqual(code, 1, err)
            self.assertEqual(code, 2)

    def test_a_refused_root_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            # A tree whose tables are stale: a run that got as far as render() would rewrite README.md.
            add(root, "tcl-c", tv("TCL C", "TCL"))
            before = (root / "README.md").read_bytes()
            (root / "examples" / "README.md").unlink()
            code, _, err = run("--root", str(root))
            self.assertEqual(code, 2, err)
            self.assertEqual((root / "README.md").read_bytes(), before)


class AdoptTest(unittest.TestCase):
    def test_a_patch_that_is_not_there_exits_2(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            patch = Path(tmp) / "pr.diff"
            code, out, err = run("--root", str(root), "--adopt", str(patch))
            self.assertEqual(code, 2, err)
            self.assertIn(str(patch), err)
            self.assertIn("No such file or directory", err)
            self.assertIn("Nothing was written", err)
            self.assertNotIn("Traceback", err)
            self.assertEqual(out, "")

    def test_a_patch_that_is_a_folder_exits_2(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            code, out, err = run("--root", str(root), "--adopt", tmp)
            self.assertEqual(code, 2, err)
            self.assertIn("Is a directory", err)
            self.assertIn("Nothing was written", err)
            self.assertEqual(out, "")

    def test_an_empty_patch_path_is_still_named_in_the_line(self):
        # `--adopt ''` (or a stray pair of quotes) leaves the token empty: quoted, the line still shows what
        # it tried to read instead of "cannot read : No such file or directory".
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            code, out, err = run("--root", str(root), "--adopt", "")
            self.assertEqual(code, 2, err)
            self.assertIn("cannot read ''", err)
            self.assertIn("Nothing was written", err)
            self.assertEqual(out, "")

    def test_the_root_is_judged_before_the_patch_is_applied(self):
        # A readable patch and a root with nothing of the repository in it: no tv.json may appear.
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            add(root, "tcl-d", None)  # a folder with a list and no tv.json: what --adopt exists to write
            patch = Path(tmp) / "pr.diff"
            patch.write_text("| `tcl-d/` | TCL D | Android TV 11 | ads off |\n", encoding="utf-8")
            (root / "README.md").unlink()
            code, out, err = run("--root", str(root), "--adopt", str(patch))
            self.assertEqual(code, 2, err)
            self.assertIn("README.md", err)
            self.assertFalse((root / "examples" / "tcl-d" / "tv.json").exists(), "the patch was applied anyway")
            self.assertEqual(out, "")


class CheckAdoptTest(unittest.TestCase):
    def assert_conflict(self, code, out, err):
        self.assertEqual(code, 2, err)
        self.assertEqual(out, "")
        self.assertIn("usage:", err)
        error = err.splitlines()[-1]
        self.assertIn("--check", error)
        self.assertIn("--adopt", error)

    def test_existing_metadata_is_not_changed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            metadata = root / "examples" / "tcl-a" / "tv.json"
            before = metadata.read_bytes()
            patch = root / "pr.diff"
            patch.write_text("-| `tcl-a/` | TCL A | Android TV 11 | ads off |\n"
                             "+| `tcl-a/` | TCL A | Android TV 11 | updated notes |\n", encoding="utf-8")
            result = run("--root", str(root), "--check", "--adopt", str(patch), cwd=root)
            self.assertEqual(metadata.read_bytes(), before)
            self.assert_conflict(*result)

    def test_missing_metadata_is_not_created(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            add(root, "tcl-d", None)
            metadata = root / "examples" / "tcl-d" / "tv.json"
            patch = root / "pr.diff"
            patch.write_text("| `tcl-d/` | TCL D | Android TV 11 | ads off |\n", encoding="utf-8")
            result = run("--root", str(root), "--adopt", str(patch), "--check", cwd=root)
            self.assertFalse(metadata.exists())
            self.assert_conflict(*result)

    def test_conflict_is_reported_before_inputs_are_checked(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "missing-checkout"
            patch = Path(tmp) / "missing.diff"
            self.assert_conflict(*run("--root", str(root), "--check", "--adopt", str(patch), cwd=tmp))
            self.assertFalse(root.exists())

    def test_an_empty_adopt_argument_still_conflicts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            self.assert_conflict(*run("--root", str(root), "--check", "--adopt", "", cwd=root))


class UnchangedTest(unittest.TestCase):
    def test_a_checkout_exits_0_and_a_stale_one_still_1(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            code, out, err = run("--root", str(root))
            self.assertEqual(code, 0, err)
            metadata = root / "examples" / "tcl-a" / "tv.json"
            before = metadata.read_bytes()
            code, out, err = run("--root", str(root), "--check")
            self.assertEqual(code, 0, err)
            self.assertEqual(metadata.read_bytes(), before)
            add(root, "tcl-c", tv("TCL C", "TCL"))
            code, out, err = run("--root", str(root), "--check")
            self.assertEqual(code, 1, err)
            self.assertIn("Out of date", out)
            self.assertEqual(metadata.read_bytes(), before)

    def test_a_readable_patch_is_still_applied(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = valid(tmp)
            add(root, "tcl-d", None)
            patch = Path(tmp) / "pr.diff"
            patch.write_text("| `tcl-d/` | TCL D | Android TV 11 | ads off |\n", encoding="utf-8")
            code, out, err = run("--root", str(root), "--adopt", str(patch))
            self.assertEqual(code, 0, err)
            self.assertIn("examples/tcl-d/tv.json", out)
            self.assertTrue((root / "examples" / "tcl-d" / "tv.json").is_file())
            self.assertEqual(json.loads((root / "examples" / "tcl-d" / "tv.json").read_text(encoding="utf-8")),
                             tv("TCL D", "TCL"))


if __name__ == "__main__":
    unittest.main()

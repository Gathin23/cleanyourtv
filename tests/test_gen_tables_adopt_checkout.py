"""`--adopt` reads every tv.json in the checkout before it writes one, in the words `--check` uses.

`gen_tables.py --adopt PATCH` is the path MAINTAINER.md sends a maintainer down for an old-style list PR:
it writes `examples/<folder>/tv.json` from the table rows the patch added, then rewrites the tables. The
files already in the checkout were read with a bare `json.loads`, so a `tv.json` with a trailing comma was
a `JSONDecodeError` traceback and one holding a list was an `AttributeError` - both exit 1, the code
`--check` keeps for "the tables are out of date", which is the answer a maintainer and CI act on. A
`tv.json` that the table builder itself refuses (`{}`, a folder named `tv.json`) was not seen at all until
the render step after the patch had been applied, so the run wrote a folder's `tv.json`, printed a line
saying it had, and then exited 2 on a line that says nothing is written then.

`load()` now takes `allow_missing` - the one shape `--adopt` is for, a folder with no `tv.json` yet - and
`adopt()` reads the checkout through it, so every file that is there is judged by the rules the tables are
built with, before the first write. A patch is applied whole or not at all.

Every test runs the real script in a subprocess, since the exit status and the lines on stderr are what a
maintainer sees; the trees come from tests/test_gen_tables.py, so a valid checkout is built the same way
there.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
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
ROW = "| `tcl-d/` | TCL D | Android TV 11 | ads off |\n"


def run(*args):
    """(exit status, stdout, stderr) of the real script."""
    done = subprocess.run([sys.executable, str(SCRIPT), *args], capture_output=True, text=True)
    return done.returncode, done.stdout, done.stderr


def checkout(tmp):
    """Two folders with a tv.json, and tcl-d with none - what --adopt exists to write."""
    root = tree(tmp, [("tcl-a", tv("TCL A", "TCL")), ("sony-b", tv("Sony B", "Sony"))])
    add(root, "tcl-d", None)
    patch = Path(tmp) / "pr.diff"
    patch.write_text(ROW, encoding="utf-8")
    return root, patch


def metadata(root):
    return root / "examples" / "tcl-a" / "tv.json"


class NothingWrittenTest(unittest.TestCase):
    """A tv.json the tables cannot be built from stops the run before the patch is applied."""

    def refused(self, break_it):
        with tempfile.TemporaryDirectory() as tmp:
            root, patch = checkout(tmp)
            break_it(root)
            before = (root / "README.md").read_bytes()
            code, out, err = run("--root", str(root), "--adopt", str(patch))
            self.assertEqual(code, 2, err)
            self.assertEqual(out, "", "a refused run printed a folder it did not write")
            self.assertNotIn("Traceback", err)
            self.assertFalse((root / "examples" / "tcl-d" / "tv.json").exists(), "the patch was applied anyway")
            self.assertEqual((root / "README.md").read_bytes(), before, "the tables were rewritten")
            return err

    def test_not_valid_json(self):
        # A trailing comma, the commonest hand edit, used to be a JSONDecodeError traceback with exit 1.
        def break_it(root):
            metadata(root).write_text('{"tv": "TCL A",}', encoding="utf-8")
        err = self.refused(break_it)
        self.assertIn("examples/tcl-a/tv.json: not valid JSON", err)
        # json names a trailing comma differently by Python version (3.13: "Illegal trailing comma").
        self.assertRegex(err, "Expecting property name|Illegal trailing comma")

    def test_json_that_is_not_an_object(self):
        # It parsed and then met d.get(): AttributeError, exit 1.
        def break_it(root):
            metadata(root).write_text("[1, 2]", encoding="utf-8")
        err = self.refused(break_it)
        self.assertIn("examples/tcl-a/tv.json: must be a JSON object", err)

    def test_an_object_without_the_cells_the_tables_need(self):
        # This one used to be written-then-refused: the patch's tcl-d/tv.json appeared and the run printed
        # "examples/tcl-d/tv.json: new, ..." before the render step named the empty file and exited 2.
        def break_it(root):
            metadata(root).write_text('{"os": "Android TV 11"}', encoding="utf-8")
        err = self.refused(break_it)
        for key in ("tv", "brand", "notes"):
            with self.subTest(key=key):
                self.assertIn(f'examples/tcl-a/tv.json: "{key}" must be a non-empty string', err)

    def test_a_folder_named_tv_json(self):
        # load() has always refused this; --adopt used to miss it (is_file() is false) and then the render
        # step found it, after the patch had been applied.
        def break_it(root):
            path = metadata(root)
            path.unlink()
            path.mkdir()
        err = self.refused(break_it)
        self.assertIn("examples/tcl-a/tv.json is not a file", err)


class ReadsTheSameRulesTest(unittest.TestCase):
    def test_adopt_names_it_in_the_line_check_prints(self):
        # One reader, so a maintainer who ran --check first reads the same sentence from --adopt.
        with tempfile.TemporaryDirectory() as tmp:
            root = tree(tmp, [("tcl-a", tv("TCL A", "TCL")), ("sony-b", tv("Sony B", "Sony"))])
            patch = Path(tmp) / "pr.diff"
            patch.write_text(ROW, encoding="utf-8")  # tcl-d is not in this checkout, so the row is skipped
            metadata(root).write_text('{"tv": "TCL A",}', encoding="utf-8")
            check_code, check_out, check_err = run("--root", str(root), "--check")
            code, adopt_out, adopt_err = run("--root", str(root), "--adopt", str(patch))
            self.assertEqual(check_code, 2, check_err)
            self.assertEqual(code, 2, adopt_err)
            self.assertEqual(check_out, "")
            self.assertEqual(adopt_out, "")
            self.assertEqual(adopt_err, check_err)

    def test_a_checkout_with_no_bad_file_still_applies_the_patch(self):
        # The guard must not start refusing the run it exists for.
        with tempfile.TemporaryDirectory() as tmp:
            root, patch = checkout(tmp)
            code, out, err = run("--root", str(root), "--adopt", str(patch))
            self.assertEqual(code, 0, err)
            self.assertIn("examples/tcl-d/tv.json: new", out)
            self.assertTrue((root / "examples" / "tcl-d" / "tv.json").is_file())
            code, out, err = run("--root", str(root), "--check")
            self.assertEqual(code, 0, err)


class TheReaderTest(unittest.TestCase):
    def test_a_folder_without_a_tv_json_is_the_one_shape_adopt_allows(self):
        source = (ROOT / "tools" / "gen_tables.py").read_text(encoding="utf-8")
        self.assertIn("def load(root, allow_missing=False):", source)
        self.assertIn("if allow_missing and not path.exists():", source)

    def test_adopt_reads_the_checkout_before_it_writes_anything(self):
        source = (ROOT / "tools" / "gen_tables.py").read_text(encoding="utf-8")
        adopt = source[source.index("def adopt("):]
        reader = adopt.index("have = dict(load(root, allow_missing=True))")
        self.assertNotIn("json.loads(path", adopt[:reader], "an existing tv.json is read without load()'s rules")
        first_write = adopt.index("write_json(")
        self.assertLess(reader, first_write, "the checkout is read after the first write")


if __name__ == "__main__":
    unittest.main()

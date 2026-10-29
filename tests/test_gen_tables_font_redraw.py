"""tools/gen_tables.py --font: a picture it could not draw is reported, not raised.

--font is the one argument of gen_tables.py that starts two other programs, tools/readme_stats.py and
tools/readme_card.py, and each of them answers a font that is there but is not Fredoka's variable TTF (the
Google Fonts zip renamed, one of the static single-weight files beside it) or a venv without Pillow -
requirements.txt has none, docs/workflows/press.md installs requirements-perf.txt - with its own line and
exit 1. subprocess.run(check=True) then turned that into a CalledProcessError traceback naming the font path
as a command, after README.md, examples/README.md and tools/readme_stats.py had already been rewritten: the
run read like "nothing was drawn" while two files had changed, and neither picture was there. A maintainer
following the script's own hint ("redraw docs/stats.png and docs/card.jpg (--font Fredoka.ttf ...)") in the
venv AGENTS.md tells them to build (requirements.txt) got that.

The redraw now reads each script's exit status: the table run finishes and says which picture is missing, the
script that stopped, and the command to draw it with. Exit 0 still means the tables and both pictures were
written (or nothing had moved); exit 1 means a picture is not redrawn, said on stderr.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tests"))
sys.path.insert(0, str(ROOT / "tools"))
import test_gen_tables as helper  # noqa: E402  (its tree() writes the three files the script rewrites)
from test_card_font_argument import HAVE_PIL, LAUNCHER_FONT, shadow_dir  # noqa: E402

SCRIPT = ROOT / "tools" / "gen_tables.py"
# A card script stub: the STATS line render() rewrites (so a count moved and the redraw is reached), then the
# picture it was asked for. Exit code and bytes say which half of the redraw answered.
STUB = """STATS = [("2", "TV MODELS"), ("2", "BRANDS"), ("2", "LANGUAGES"), ("1 KB", "LAUNCHER")]
import sys
from pathlib import Path
Path(sys.argv[2]).write_bytes(b"{name}")
sys.exit({code})
"""


def checkout(tmp, lists=(("tcl-a", None),)):
    """A small repository with the real card scripts, the docs/ a checkout has, and one list added."""
    root = Path(tmp) / "repo"
    root.mkdir()
    helper.tree(root, [(name, data if data is not None else helper.tv("TCL A", "TCL"))
                       for name, data in lists])
    for name in ("readme_stats.py", "readme_card.py"):
        shutil.copy(ROOT / "tools" / name, root / "tools" / name)
    (root / "docs").mkdir()
    return root


def stub_checkout(tmp):
    """The same repository with two card scripts that only write their picture, so both routes can be run."""
    root = checkout(tmp)
    (root / "tools" / "readme_stats.py").write_text(STUB.format(name="stats", code=0), encoding="utf-8")
    (root / "tools" / "readme_card.py").write_text(STUB.format(name="card", code=0), encoding="utf-8")
    return root


def run(root, font, cwd=None, env=None):
    return subprocess.run([sys.executable, str(SCRIPT), "--root", str(root), "--font", str(font)],
                          cwd=str(cwd or root.parent), env=env, capture_output=True, text=True)


class RedrawFailureTest(unittest.TestCase):
    def test_a_font_that_is_not_fredoka_names_the_picture_and_how_to_draw_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            caller = Path(tmp) / "caller"
            caller.mkdir()
            font = caller / "Fredoka.ttf"
            # The Google Fonts download is a zip; a file that is there and is not the variable TTF is the
            # case the card scripts answer with their own line and exit 1.
            font.write_bytes(b"PK\x03\x04 not a font\n")
            done = run(root, "Fredoka.ttf", cwd=caller)
            readme = (root / "README.md").read_text(encoding="utf-8")
            pictures = [root / "docs" / "stats.png", root / "docs" / "card.jpg"]
        self.assertEqual(done.returncode, 1, f"a redraw that could not happen exited {done.returncode}")
        self.assertIn("docs/stats.png was not redrawn", done.stderr, done.stderr)
        self.assertIn("tools/readme_stats.py exited 1", done.stderr, done.stderr)
        self.assertIn("docs/stats.png", done.stderr, "the remedy does not name the file to write")
        self.assertNotIn("Traceback", done.stderr, "the failure still ends in a CalledProcessError")
        # The tables are written: saying "nothing was drawn" would be wrong, and so would hiding it.
        self.assertIn("TCL A", readme, "the tables were not written before the redraw")
        self.assertNotIn("Updated:", done.stdout, f"the run claims it updated {done.stdout!r}")
        for picture in pictures:
            self.assertFalse(picture.exists(), f"{picture.name} was written with a font that is not Fredoka")

    def test_a_second_picture_that_fails_is_named_too(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            (root / "tools" / "readme_card.py").write_text(STUB.format(name="card", code=3), encoding="utf-8")
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            stats = (root / "docs" / "stats.png").exists()
        self.assertEqual(done.returncode, 1, f"a card picture that was not drawn exited {done.returncode}")
        self.assertIn("docs/card.jpg was not redrawn", done.stderr, done.stderr)
        self.assertIn("tools/readme_card.py exited 3", done.stderr, done.stderr)
        self.assertNotIn("docs/stats.png was not redrawn", done.stderr, "the picture that was drawn is blamed")
        self.assertTrue(stats, "the first picture was not drawn before the second was tried")
        self.assertNotIn("Traceback", done.stderr, "the failure still ends in a traceback")

    @unittest.skipUnless(HAVE_PIL, "the card scripts need Pillow (tools/requirements-perf.txt)")
    def test_a_venv_without_pillow_says_so_and_does_not_traceback(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: Pillow is checked first")
            (Path(tmp) / "no-pillow").mkdir()
            env = dict(os.environ, PYTHONPATH=str(shadow_dir(Path(tmp) / "no-pillow")))
            done = run(root, font, env=env)
        self.assertEqual(done.returncode, 1, f"a redraw without Pillow exited {done.returncode}")
        self.assertIn("needs Pillow", done.stderr, "the script that stopped does not say what it needs")
        self.assertIn("docs/stats.png was not redrawn", done.stderr, done.stderr)
        self.assertNotIn("Traceback", done.stderr, "a missing Pillow still ends in a traceback")


class RedrawSuccessTest(unittest.TestCase):
    def test_both_pictures_are_drawn_and_listed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = stub_checkout(tmp)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"any font path that exists: the stubs never read it")
            done = run(root, font)
            stats = (root / "docs" / "stats.png").read_bytes() if (root / "docs" / "stats.png").exists() else b""
            card = (root / "docs" / "card.jpg").read_bytes() if (root / "docs" / "card.jpg").exists() else b""
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual((stats, card), (b"stats", b"card"), "the redraw did not write both pictures")
        self.assertIn("Updated:", done.stdout, done.stdout)
        self.assertIn("docs/stats.png", done.stdout, "the run does not list the picture it drew")
        self.assertIn("docs/card.jpg", done.stdout, "the run does not list the picture it drew")
        self.assertNotIn("was not redrawn", done.stderr, done.stderr)

    def test_a_count_that_did_not_move_draws_nothing(self):
        # --font draws when a count moved; a second run has nothing to draw, so a font it could not use is
        # never read and must not be reported as a failure.
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            first = subprocess.run([sys.executable, str(SCRIPT), "--root", str(root)],
                                   cwd=tmp, capture_output=True, text=True)
            font = Path(tmp) / "Fredoka.ttf"
            font.write_bytes(b"not a font at all")
            second = run(root, font, cwd=tmp)
            pictures = [root / "docs" / "stats.png", root / "docs" / "card.jpg"]
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(second.returncode, 0, second.stderr)
        self.assertIn("Up to date", second.stdout, second.stdout)
        self.assertNotIn("was not redrawn", second.stderr, second.stderr)
        for picture in pictures:
            self.assertFalse(picture.exists(), f"{picture.name} was drawn with no count to draw it for")

    @unittest.skipUnless(HAVE_PIL, "the card scripts need Pillow (tools/requirements-perf.txt)")
    def test_the_launchers_own_variable_font_reaches_the_scripts(self):
        # The guard must not refuse the font a checkout can hand it: launcher/ carries a variable TTF, and
        # readme_stats.py draws the picture with it. (readme_card.py also needs the screenshots docs/ has,
        # so it is the stubs above that prove its half.)
        with tempfile.TemporaryDirectory() as tmp:
            root = checkout(tmp)
            done = run(root, LAUNCHER_FONT)
            stats = root / "docs" / "stats.png"
            written = stats.stat().st_size if stats.exists() else 0
        self.assertTrue(LAUNCHER_FONT.is_file(), "the launcher's font is gone")
        self.assertNotIn("cannot use the font", done.stderr, done.stderr)
        self.assertTrue(written > 1000, f"docs/stats.png is {written} B with a variable font")


class OrderTest(unittest.TestCase):
    def test_the_redraw_reads_exit_status_instead_of_raising(self):
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("stopped = redraw(root, font)", source, "main() does not read what the redraw returned")
        for line in source.splitlines():
            if "subprocess.run(" in line and not line.lstrip().startswith("#"):
                self.assertNotIn("check=True", line, f"a child's exit 1 is raised again: {line.strip()}")

    def test_the_docstring_names_the_exit_status_a_missing_picture_answers_with(self):
        doc = SCRIPT.read_text(encoding="utf-8").split('"""')[1]
        self.assertIn("1 out of date", doc, "the exit-status line is gone")
        self.assertIn("pictures", doc, "the docstring does not say what exit 1 also means")
        self.assertIn("the tables are written", doc, "the docstring does not say what a failed redraw left")


if __name__ == "__main__":
    unittest.main()

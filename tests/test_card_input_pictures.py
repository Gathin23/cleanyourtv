"""The pictures the README card scripts read are not arguments, and Pillow answered them last.

`docs/workflows/press.md` step 2 sends a contributor to `tools/readme_featured.py ... docs/featured.png`
and `tools/readme_card.py ... docs/card.jpg`, and the card's own docstring says to run
`tools/readme_stats.py` first. The font and the written picture were judged before Pillow was imported
(#1929, #2069), but the pictures the scripts *read* were not: they are opened by name relative to the
directory the reader stands in, so

    readme_card.py run from tools/        -> FileNotFoundError: 'docs/stock-home.jpg'
    card.jpg run before readme_stats.py   -> FileNotFoundError: 'docs/stats.png'
    a logo renamed without its picture    -> FileNotFoundError: 'docs/press/xda.png'
    a truncated stats.png                 -> UnidentifiedImageError: cannot identify image file

were each a traceback out of the middle of the draw with exit 1 - the code a run that wrote nothing
returns - naming neither the sibling script that draws `docs/stats.png` / `docs/featured.png` nor
whether anything reached the disk, while the font and the output got a sentence instead.

Both scripts judge their pictures now, with the path named, the sibling that draws it where there is
one, and 'Nothing was written.', before the Pillow import (so CI, whose tools/venv has no Pillow, runs
these tests); each open still catches a file that is there and is not a picture, in the same words. The
two pictures the repository ships, the four the card stacks and the logos in LOGOS are read out of the
sources so a rename fails here rather than in a maintainer's run.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import ast
import importlib.util
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
DOCS = ROOT / "docs"
CARD = TOOLS / "readme_card.py"
FEATURED = TOOLS / "readme_featured.py"

# The launcher's own font: one vertical weight axis, so it is the variable TTF these scripts ask for.
LAUNCHER_FONT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "font" / "outfit.ttf"

HAVE_PIL = importlib.util.find_spec("PIL") is not None


def source(script):
    return script.read_text(encoding="utf-8")


def read_paths(script):
    """The picture paths a script checks before Pillow: INPUTS' names, or LOGOS' docs/press/<name>.png."""
    tree = ast.parse(source(script))
    for node in tree.body:
        if not isinstance(node, ast.Assign) or not isinstance(node.value, (ast.List, ast.Tuple)):
            continue
        name = getattr(node.targets[0], "id", "")
        items = [elt.value for elt in node.value.elts
                 if isinstance(elt, ast.Constant) and isinstance(elt.value, str)]
        if name == "INPUTS":
            return items
        if name == "LOGOS":
            return [f"docs/press/{n}.png" for n in items]
    raise AssertionError(f"{script.name} has no INPUTS or LOGOS list")


def run_without_pillow(argv, cwd, shadow):
    """A run whose PIL import cannot succeed, the way CI's tools/venv runs: the refusal must not need it."""
    pkg = Path(shadow) / "PIL"
    pkg.mkdir(parents=True, exist_ok=True)
    (pkg / "__init__.py").write_text('raise ImportError("No module named \'PIL\'")\n', encoding="utf-8")
    env = dict(os.environ)
    env["PYTHONPATH"] = str(shadow)
    return subprocess.run([sys.executable, *argv], cwd=cwd, env=env, capture_output=True, text=True)


def run_plain(argv, cwd):
    """The same command with the Pillow this machine has, so a real picture is drawn."""
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    return subprocess.run([sys.executable, *argv], cwd=cwd, env=env, capture_output=True, text=True)


class CardPicturesTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.cwd = Path(self._tmp.name) / "checkout"   # a stand-in for a clone seen from somewhere else
        (self.cwd / "docs").mkdir(parents=True)
        self.out = Path(self._tmp.name) / "out.jpg"
        self.shadow = Path(self._tmp.name) / "shadow"

    def touch(self, *names):
        for name in names:
            path = self.cwd / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"not a picture yet\n")

    def assert_refused(self, done, *wanted):
        self.assertEqual(done.returncode, 1, done.stderr)
        self.assertNotIn("Traceback", done.stderr)
        self.assertIn("Nothing was written", done.stderr)
        for text in wanted:
            self.assertIn(text, done.stderr)

    def card(self, out=None):
        return run_without_pillow([str(CARD), str(LAUNCHER_FONT), str(out or self.out)], self.cwd, self.shadow)

    def featured(self, out=None):
        out = out or Path(self._tmp.name) / "out.png"
        return run_without_pillow([str(FEATURED), str(LAUNCHER_FONT), str(out)], self.cwd, self.shadow)

    def test_a_missing_screenshot_is_named(self):
        """The two the repository ships, read before either sibling has drawn anything."""
        done = self.card()
        self.assert_refused(done, "docs/stock-home.jpg", "repository root")

    def test_a_missing_stats_picture_names_the_script_that_draws_it(self):
        """The card stacks docs/stats.png, which tools/readme_stats.py draws - not a file the card ships."""
        self.touch("docs/stock-home.jpg", "docs/new-home.jpg", "docs/featured.png")
        done = self.card()
        self.assert_refused(done, "docs/stats.png", "tools/readme_stats.py")

    def test_a_missing_featured_picture_names_its_script(self):
        self.touch("docs/stock-home.jpg", "docs/new-home.jpg", "docs/stats.png")
        done = self.card()
        self.assert_refused(done, "docs/featured.png", "tools/readme_featured.py")

    def test_a_picture_that_is_a_folder_is_named_as_one(self):
        self.touch("docs/stock-home.jpg", "docs/new-home.jpg", "docs/featured.png")
        (self.cwd / "docs" / "stats.png").mkdir()
        done = self.card()
        self.assert_refused(done, "docs/stats.png", "is a folder")

    def test_a_folder_where_the_logos_go_is_named(self):
        (self.cwd / "docs" / "press").mkdir()
        done = self.featured()
        self.assert_refused(done, "docs/press/techcrunch.png", "LOGOS")

    def test_a_missing_logo_is_named_with_the_repository_root(self):
        done = self.featured()
        self.assert_refused(done, "docs/press/techcrunch.png", "repository root")

    def test_the_refusal_comes_before_the_pillow_message(self):
        """No Pillow, as in CI's tools/venv: the picture is judged first, so that is the answer given."""
        self.touch("docs/stock-home.jpg", "docs/new-home.jpg", "docs/featured.png")
        done = self.card()
        self.assertNotIn("needs Pillow", done.stderr)
        done = self.featured()
        self.assertNotIn("needs Pillow", done.stderr)

    @unittest.skipUnless(HAVE_PIL, "reads a picture with Pillow, which tools/venv has not")
    def test_a_picture_that_is_not_a_picture_is_named(self):
        for name in ("docs/stock-home.jpg", "docs/new-home.jpg", "docs/featured.png"):
            shutil.copy(DOCS / Path(name).name, self.cwd / name)
        (self.cwd / "docs" / "stats.png").write_bytes(b"not a picture")
        done = run_plain([str(CARD), str(LAUNCHER_FONT), str(self.out)], self.cwd)
        self.assert_refused(done, "docs/stats.png")

    @unittest.skipUnless(HAVE_PIL, "reads a picture with Pillow, which tools/venv has not")
    def test_a_logo_that_is_not_a_picture_is_named(self):
        press = self.cwd / "docs" / "press"
        press.mkdir()
        for name in read_paths(FEATURED):
            (self.cwd / name).write_bytes(b"not a picture")
        done = run_plain([str(FEATURED), str(LAUNCHER_FONT), str(Path(self._tmp.name) / "o.png")], self.cwd)
        self.assert_refused(done, "docs/press/techcrunch.png")

    @unittest.skipUnless(HAVE_PIL, "draws the card with Pillow, which tools/venv has not")
    def test_the_card_still_writes_with_its_pictures_there(self):
        for name in ("docs/stock-home.jpg", "docs/new-home.jpg", "docs/stats.png", "docs/featured.png"):
            shutil.copy(DOCS / Path(name).name, self.cwd / name)
        done = run_plain([str(CARD), str(LAUNCHER_FONT), str(self.out)], self.cwd)
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertGreater(self.out.stat().st_size, 50_000, "the card came out empty")

    @unittest.skipUnless(HAVE_PIL, "draws the panel with Pillow, which tools/venv has not")
    def test_the_panel_still_writes_with_its_logos_there(self):
        press = self.cwd / "docs" / "press"
        press.mkdir()
        for name in read_paths(FEATURED):
            shutil.copy(DOCS / "press" / Path(name).name, self.cwd / name)
        out = Path(self._tmp.name) / "featured.png"
        done = run_plain([str(FEATURED), str(LAUNCHER_FONT), str(out)], self.cwd)
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertGreater(out.stat().st_size, 20_000, "the panel came out empty")

    def test_the_judgement_sits_above_the_pillow_import(self):
        for script, call in ((CARD, "inputs_problem()"), (FEATURED, "logo_problem()")):
            with self.subTest(script=script.name):
                text = source(script)
                self.assertIn(call, text, f"{script.name} does not judge its pictures")
                self.assertLess(text.index(call), text.index("from PIL import"),
                                f"{script.name} judges its pictures after Pillow is imported")

    def test_every_picture_named_is_in_this_checkout(self):
        """A renamed file fails here, where the scripts would have named it in a maintainer's run."""
        for script in (CARD, FEATURED):
            for path in read_paths(script):
                with self.subTest(script=script.name, picture=path):
                    self.assertTrue((ROOT / path).is_file(), f"{script.name} reads {path}, which is not here")
        self.assertGreaterEqual(len(read_paths(FEATURED)), 10, "the LOGOS read found almost nothing")


if __name__ == "__main__":
    unittest.main()

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
import hashlib
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


def read_paths(script, halloween=False):
    """The picture paths a script checks before Pillow: INPUTS' names, or LOGOS' docs/press/<name>.png."""
    tree = ast.parse(source(script))
    paths = []
    for node in tree.body:
        if not isinstance(node, ast.Assign) or not isinstance(node.value, (ast.List, ast.Tuple)):
            continue
        name = getattr(node.targets[0], "id", "")
        if name == "LOGOS":
            return [f"docs/press/{n}.png" for n in ast.literal_eval(node.value)]
        if name == "INPUTS" or (halloween and name == "HALLOWEEN_INPUTS"):
            paths.extend(item[0] if isinstance(item, tuple) else item
                         for item in ast.literal_eval(node.value))
    if paths:
        return paths
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


class SourceCollisionTest(unittest.TestCase):
    """Run even the destructive red phase only on copied scripts and copied source assets."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.directory = Path(self.tmp.name)
        self.template = self.directory / "template"
        (self.template / "tools").mkdir(parents=True)
        for script in (CARD, FEATURED):
            shutil.copy2(script, self.template / "tools" / script.name)
            # picture_file.py is imported from beside the script: the staging write it publishes with.
            shutil.copy2(TOOLS / "picture_file.py", self.template / "tools" / "picture_file.py")
        shutil.copy2(LAUNCHER_FONT, self.template / "font.ttf")
        for name in set(read_paths(CARD, halloween=True) + read_paths(FEATURED)):
            target = self.template / name
            self.assertTrue(target.resolve().is_relative_to(self.template.resolve()))
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / name, target)
        self.shadow = self.directory / "shadow"
        self.serial = 0

    def checkout(self):
        self.serial += 1
        checkout = self.directory / str(self.serial)
        shutil.copytree(self.template, checkout)
        return checkout

    def run_script(self, checkout, script, output, halloween=False, blocked=False):
        copied_script = checkout / "tools" / script.name
        self.assertTrue(copied_script.resolve().is_relative_to(checkout.resolve()))
        destination = Path(output)
        if not destination.is_absolute():
            destination = checkout / destination
        self.assertTrue(destination.resolve().is_relative_to(checkout.resolve()))
        argv = ["-B", str(copied_script), *(["--halloween"] if halloween else []), "font.ttf", str(output)]
        if blocked:
            return run_without_pillow(argv, checkout, self.shadow)
        # Keep any existing local package path; no dependency is installed for these optional draws.
        return subprocess.run([sys.executable, *argv], cwd=checkout, env=dict(os.environ),
                              capture_output=True, text=True, timeout=30)

    def hashes(self, checkout):
        return {p.relative_to(checkout).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in checkout.rglob("*") if p.is_file()}

    def assert_collision(self, script, image, halloween=False, spelling="direct"):
        checkout = self.checkout()
        target = checkout / image
        output = image
        if spelling == "absolute":
            output = str(target)
        elif spelling == "relative":
            output = os.path.relpath(target, checkout)
        elif spelling == "dot":
            output = "./" + image
        elif spelling == "dotdot":
            (target.parent / "nested").mkdir()
            output = str(target.parent / "nested" / ".." / target.name)
        elif spelling in ("symlink", "hardlink"):
            alias = checkout / ("alias" + target.suffix)
            try:
                if spelling == "symlink":
                    alias.symlink_to(target)
                else:
                    os.link(target, alias)
            except (OSError, NotImplementedError) as error:
                self.skipTest(f"this filesystem cannot create a {spelling}: {error}")
            output = str(alias)
        elif spelling == "case":
            output = image.upper()
            if not (checkout / output).is_file():
                self.skipTest("this directory is case-sensitive")
        original = self.hashes(checkout)
        names = set(checkout.rglob("*"))
        for blocked in ((True, False) if HAVE_PIL else (True,)):
            with self.subTest(blocked_pillow=blocked):
                done = self.run_script(checkout, script, output, halloween, blocked)
                try:
                    self.assertEqual(done.returncode, 1, done.stderr)
                    self.assertIn(str(output), done.stderr)
                    self.assertIn(image, done.stderr)
                    self.assertIn("same file", done.stderr)
                    self.assertIn("Nothing was written.", done.stderr)
                    self.assertNotIn("needs Pillow", done.stderr)
                    self.assertNotIn("Traceback", done.stderr)
                    self.assertEqual(done.stdout, "")
                finally:
                    after = self.hashes(checkout)
                    self.assertEqual(after[image], original[image], image)
                    self.assertEqual(after, original)
                    self.assertEqual(set(checkout.rglob("*")), names, "an unrelated output was created")

    def test_card_source_collisions(self):
        self.assertGreaterEqual(len(read_paths(CARD)), 4)
        halloween_paths = read_paths(CARD, halloween=True)
        for name in ("docs/new-home-halloween.jpg", "docs/stats-halloween.png"):
            self.assertIn(name, halloween_paths)
        for halloween in (False, True):
            for image in read_paths(CARD, halloween=halloween):
                with self.subTest(halloween=halloween, image=image):
                    self.assert_collision(CARD, image, halloween)

    def test_logo_source_collisions(self):
        logos = read_paths(FEATURED)
        self.assertIn("docs/press/techcrunch.png", logos)
        self.assertIn("docs/press/techpp.png", logos)
        for image in logos:
            with self.subTest(image=image):
                self.assert_collision(FEATURED, image)

    def alias_collisions(self, spelling):
        for script, image, halloween in ((CARD, "docs/new-home.jpg", False),
                                         (CARD, "docs/new-home-halloween.jpg", True),
                                         (FEATURED, "docs/press/techcrunch.png", False)):
            with self.subTest(script=script.name, halloween=halloween, spelling=spelling):
                self.assert_collision(script, image, halloween, spelling)

    def test_spelling_collisions(self):
        for spelling in ("absolute", "relative", "dot", "dotdot"):
            self.alias_collisions(spelling)

    def test_symlink_collisions(self):
        self.alias_collisions("symlink")

    def test_hardlink_collisions(self):
        self.alias_collisions("hardlink")

    @unittest.skipUnless(os.name == "nt", "Windows case-insensitive path spelling")
    def test_windows_case_collisions(self):
        self.alias_collisions("case")

    def test_missing_distinct_outputs_reach_the_pillow_check(self):
        for script, halloween in ((CARD, False), (CARD, True), (FEATURED, False)):
            with self.subTest(script=script.name, halloween=halloween):
                checkout = self.checkout()
                original = self.hashes(checkout)
                done = self.run_script(checkout, script, "new.png", halloween, blocked=True)
                self.assertEqual(done.returncode, 1)
                self.assertIn("needs Pillow", done.stderr)
                self.assertNotIn("same file", done.stderr)
                self.assertNotIn("Traceback", done.stderr)
                self.assertEqual(done.stdout, "")
                self.assertEqual(self.hashes(checkout), original)

    @unittest.skipUnless(HAVE_PIL, "drawing the card needs existing Pillow")
    def test_rendering_controls_create_and_overwrite_distinct_outputs(self):
        from PIL import Image
        for script, halloween in ((CARD, False), (CARD, True), (FEATURED, False)):
            for existing in (False, True):
                with self.subTest(script=script.name, halloween=halloween, existing=existing):
                    checkout = self.checkout()
                    name = "featured.png" if script == FEATURED else "card.jpg"
                    output = checkout / name
                    if existing:
                        shutil.copy2(DOCS / name, output)
                    original = self.hashes(checkout)
                    done = self.run_script(checkout, script, str(output), halloween)
                    self.assertEqual(done.returncode, 0, done.stderr)
                    self.assertEqual(done.stdout, "")
                    self.assertEqual(done.stderr, "")
                    self.assertGreater(output.stat().st_size, 1000)
                    with Image.open(output) as picture:
                        self.assertEqual(picture.width, 1600)
                        self.assertEqual(picture.format, "PNG" if script == FEATURED else "JPEG")
                    self.assertEqual({p: h for p, h in self.hashes(checkout).items() if p != name},
                                     {p: h for p, h in original.items() if p != name})


if __name__ == "__main__":
    unittest.main()

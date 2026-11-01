"""The picture the README card scripts write is read as whatever was typed, and Pillow answered it last.

`docs/workflows/press.md` step 2 is where a contributor redraws `docs/featured.png` and `docs/card.jpg`,
and it prints `tools/readme_featured.py Fredoka.ttf docs/featured.png` and
`tools/readme_card.py Fredoka.ttf docs/card.jpg`; `tools/readme_stats.py`'s own docstring prints
`docs/stats.png`. The second argument is typed from wherever the reader stands, and each script handed it
to `img.save()` as the last thing in the draw:

    docs/            -> ValueError: unknown file extension:        (a folder, read as a suffixless name)
    docs/card        -> ValueError: unknown file extension:        (no suffix)
    shots/after.png  -> FileNotFoundError: [Errno 2] ... 'shots/after.png'   (no such folder)
    a file in the way -> NotADirectoryError

each a 12-line traceback with exit 1 - the code a run that wrote nothing returns - after the whole picture
had been drawn, naming neither the argument that was wrong nor whether anything reached the disk.

The four shapes are judged now, in `out_problem()`, before Pillow is imported (the order the font guard
takes, and what lets CI, which has no Pillow, run these tests), with the path named, the file the script
writes as the example, and 'Nothing was written.'; the save at the end belongs to its own
`except (OSError, ValueError)`, so a folder that went read-only, a full disk or an extension Pillow does
not know (`.txt`) is named too instead of arriving as a traceback. A real picture still writes.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import importlib.util
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"

# The three scripts that draw the card, with the picture the page or the docstring tells the reader to write.
CARD_SCRIPTS = {
    "readme_stats.py": "docs/stats.png",
    "readme_featured.py": "docs/featured.png",
    "readme_card.py": "docs/card.jpg",
}

# The launcher's own font: one vertical weight axis, so it is the variable TTF these scripts ask for.
LAUNCHER_FONT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "font" / "outfit.ttf"

HAVE_PIL = importlib.util.find_spec("PIL") is not None


def run(argv, tmp):
    """A run with a PIL package that cannot be imported: the venv CI uses has no Pillow.

    The picture path is judged before that import, so its answer must arrive without it - and a test that
    only passed on a machine with Pillow would say nothing about the scripts as CI runs them.
    """
    pkg = Path(tmp) / "PIL"
    pkg.mkdir(exist_ok=True)
    (pkg / "__init__.py").write_text('raise ImportError("No module named \'PIL\'")\n', encoding="utf-8")
    env = dict(os.environ)
    env["PYTHONPATH"] = str(tmp)
    return subprocess.run([sys.executable, *argv], cwd=ROOT, env=env, capture_output=True, text=True)


def run_plain(argv):
    """The same command with the Pillow this machine has, so a real picture is drawn."""
    env = dict(os.environ)
    env.pop("PYTHONPATH", None)
    return subprocess.run([sys.executable, *argv], cwd=ROOT, env=env, capture_output=True, text=True)


class PicturePathTest(unittest.TestCase):
    def assert_refused(self, out, why):
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name, out=why):
                    done = run([str(TOOLS / name), str(LAUNCHER_FONT), str(out)], tmp)
                    self.assertEqual(done.returncode, 1,
                                     f"{name} did not refuse {why} the way the other tools refuse an argument")
                    self.assertIn(str(out), done.stderr, f"{name} does not name the picture path it was given")
                    self.assertIn("Nothing was written", done.stderr,
                                  f"{name} does not say whether a picture was written")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on a Pillow traceback")
                    self.assertNotIn("unknown file extension", done.stderr,
                                     f"{name} leaves {why} to Pillow, so the answer needs Pillow installed")
                    self.assertNotIn("No such file or directory", done.stderr,
                                     f"{name} leaves {why} to Pillow, so the answer needs Pillow installed")
                    self.assertEqual(done.stdout, "", f"{name} printed a picture path it did not write")

    def test_a_folder_is_not_a_picture_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assert_refused(Path(tmp), "a folder")

    def test_a_folder_that_is_not_there_is_named(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assert_refused(Path(tmp) / "shots" / "after.png", "a folder that is not there")

    def test_a_file_in_the_middle_of_the_path_is_named(self):
        with tempfile.TemporaryDirectory() as tmp:
            blocker = Path(tmp) / "after.png"
            blocker.write_bytes(b"not a folder")
            self.assert_refused(blocker / "x.png", "a file in the middle of the path")

    def test_a_name_with_no_extension_says_what_the_format_needs(self):
        with tempfile.TemporaryDirectory() as tmp:
            for name in CARD_SCRIPTS:
                with self.subTest(script=name):
                    out = Path(tmp) / "picture"
                    done = run([str(TOOLS / name), str(LAUNCHER_FONT), str(out)], tmp)
                    self.assertEqual(done.returncode, 1, f"{name} took a suffixless name")
                    self.assertIn("no extension", done.stderr, f"{name} does not say what is missing")
                    self.assertIn(str(out), done.stderr, f"{name} does not name the argument")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on a Pillow traceback")
                    self.assertFalse(out.exists(), f"{name} wrote {out}")

    def test_the_folder_message_names_the_picture_the_script_writes(self):
        # The example is the file the page, or the docstring, tells the reader to write: gen_tables.py
        # redraws the same two paths, so a reader who follows the message lands on the tracked file.
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    done = run([str(TOOLS / name), str(LAUNCHER_FONT), tmp], tmp)
                    self.assertIn(picture, done.stderr, f"{name} does not name {picture} as the file to write")
                    self.assertTrue((ROOT / picture).parent.is_dir(), f"{picture} has no folder in a checkout")

    def test_the_picture_is_judged_before_the_pillow_import(self):
        # What makes the checks above work in CI, where Pillow is absent: the guard sits above the import.
        for name in CARD_SCRIPTS:
            with self.subTest(script=name):
                text = (TOOLS / name).read_text(encoding="utf-8")
                guard = text.find("out_problem(OUT)")
                pillow = text.find("from PIL import")
                self.assertGreater(guard, 0, f"{name} does not judge the picture it is given")
                self.assertGreater(pillow, 0, f"{name} no longer imports Pillow, so this check would prove nothing")
                self.assertLess(guard, pillow, f"{name} judges the picture after importing Pillow")

    def test_the_write_at_the_end_is_guarded(self):
        # A folder that went read-only, a full disk or an unknown extension is found at the write, after
        # the drawing: that one belongs to its own handler rather than to a traceback.
        for name in CARD_SCRIPTS:
            with self.subTest(script=name):
                text = (TOOLS / name).read_text(encoding="utf-8")
                handler = text.find("except (OSError, ValueError) as error:")
                self.assertGreater(handler, 0, f"{name} leaves the save to a bare traceback")
                last_save = text.rfind(".save(OUT")
                self.assertGreater(last_save, 0, f"{name} no longer saves to OUT, so this check would prove nothing")
                self.assertLess(handler, last_save, f"{name} does not guard the write that ends it")


@unittest.skipUnless(HAVE_PIL, "drawing the card needs Pillow (tools/requirements-perf.txt)")
class RealPictureTest(unittest.TestCase):
    def test_a_variable_font_still_writes_the_picture(self):
        # The guard must not refuse a path that is fine: the page's own command still writes its picture.
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    if name == "readme_card.py":
                        for part in ("docs/stats.png", "docs/featured.png"):
                            if not (ROOT / part).is_file():
                                self.skipTest(f"{part} is not in this checkout, so the card cannot be stacked")
                    out = Path(tmp) / Path(picture).name
                    done = run_plain([str(TOOLS / name), str(LAUNCHER_FONT), str(out)])
                    self.assertEqual(done.returncode, 0, f"{name} refused a picture path it should write: {done.stderr}")
                    self.assertTrue(out.is_file(), f"{name} said it was fine and wrote nothing")
                    self.assertGreater(out.stat().st_size, 1000, f"{name} wrote an empty picture")

    def test_a_write_that_fails_is_named(self):
        # The one failure only the write can find: /dev/full answers ENOSPC once the picture is drawn.
        if not Path("/dev/full").exists():
            self.skipTest("no /dev/full on this machine")
        with tempfile.TemporaryDirectory() as tmp:
            for name, picture in CARD_SCRIPTS.items():
                with self.subTest(script=name):
                    if name == "readme_card.py":
                        for part in ("docs/stats.png", "docs/featured.png"):
                            if not (ROOT / part).is_file():
                                self.skipTest(f"{part} is not in this checkout, so the card cannot be stacked")
                    out = Path(tmp) / Path(picture).name
                    out.symlink_to("/dev/full")
                    done = run_plain([str(TOOLS / name), str(LAUNCHER_FONT), str(out)])
                    self.assertEqual(done.returncode, 1, f"{name} took a write that failed as success")
                    self.assertIn("cannot write", done.stderr, f"{name} does not name the file it could not write")
                    self.assertIn("Nothing was written", done.stderr, f"{name} does not say the picture is missing")
                    self.assertNotIn("Traceback", done.stderr, f"{name} still dies on the write")


if __name__ == "__main__":
    unittest.main()

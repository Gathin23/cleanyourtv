"""tools/scenes_wallpapers.py takes one argument, the folder to write the 14 scenes into, and read it as a
folder whatever it was: `scenes_wallpapers.py --help`, the first thing a reader types at an unfamiliar
script, made a directory named `--help` in the directory they were standing in, filled it with 14 SVGs and
exited 0 - a stray folder nothing links to, easy to commit by accident. `--out /tmp/scenes`, the shape the
other tools in tools/ take, would have made a folder called `--out`. And a path that is a file ended in
os.makedirs' `FileExistsError: [Errno 17] File exists: .../README.md` traceback instead of naming it.

The script now answers -h/--help with its own note and writes nothing, refuses any other leading dash and
any run that is not exactly one argument, and names a path that is a file. Three more of the same:
an empty word (a variable that came out empty), a folder it cannot create (a path through a file) and a
folder sitting where one scene's file goes each ended in a traceback with exit 1, the code a run that
wrote nothing returns; they now name the path and say what did land. So a test here has to be run,
not read: every check below executes the script in a folder of its own and looks at what it left there.

The 14 names are not copied in: the scenes' own record is themes/scenes/wallpapers/captions.txt, one
`<file><TAB><name>` line per picture (themes/README.md), so a fifteenth scene, or a renamed one, fails here
until the folder and the captions agree.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "scenes_wallpapers.py"
CAPTIONS = ROOT / "themes" / "scenes" / "wallpapers" / "captions.txt"
USAGE_WORDS = ("scenes_wallpapers.py", "<folder>")


def caption_names():
    """The picture names themes/scenes ships a caption for, without the suffix: 01-dusk-peaks, ..."""
    names = []
    for line in CAPTIONS.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        names.append(line.split("\t")[0].strip().rsplit(".", 1)[0])
    return names


def run(*args, cwd):
    """The script, from `cwd`, with the arguments given: its exit code and both streams, as text."""
    proc = subprocess.run([sys.executable, str(SCRIPT), *args], cwd=str(cwd),
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    return proc.returncode, proc.stdout.decode(), proc.stderr.decode()


class ScenesWallpapersArgumentsTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.cwd = Path(self._tmp.name)

    def assertNothingWritten(self):
        self.assertEqual(sorted(p.name for p in self.cwd.iterdir()), [],
                         f"the script wrote into the directory it was run from: {self.cwd}")

    def test_help_prints_the_note_and_writes_nothing(self):
        code, out, err = run("--help", cwd=self.cwd)
        self.assertEqual(code, 0, f"--help exited {code}: {err}")
        for word in USAGE_WORDS:
            self.assertIn(word, out, "--help does not print the script's own usage line")
        self.assertIn("themes/scenes/wallpapers", out, "--help no longer says where the JPEGs live")
        self.assertNothingWritten()

    def test_help_does_not_make_a_folder_named_after_the_option(self):
        # The defect itself: the option became the folder, and 14 SVGs went into a directory called --help.
        run("--help", cwd=self.cwd)
        self.assertFalse((self.cwd / "--help").exists(),
                         "a run with --help made a directory named '--help'")

    def test_short_help_is_the_same_note(self):
        code, out, _ = run("-h", cwd=self.cwd)
        self.assertEqual(code, 0)
        self.assertEqual(out, run("--help", cwd=self.cwd)[1], "-h and --help print different notes")
        self.assertNothingWritten()

    def test_an_option_it_does_not_take_is_refused_by_name(self):
        # --out and --dry-run are what a reader tries on a script with no --help; neither is a folder.
        for option in ("--out", "--dry-run"):
            with self.subTest(option=option):
                code, out, err = run(option, cwd=self.cwd)
                self.assertEqual(code, 2, f"{option} was not refused (exit {code}, stdout {out!r})")
                self.assertIn(option, err, f"{option} was refused without naming it")
                self.assertIn("scenes_wallpapers.py", err, "the refusal does not print the usage line")
                self.assertNotIn("Traceback", err)
                self.assertFalse((self.cwd / option).exists(), f"made a folder called {option}")
        self.assertNothingWritten()

    def test_help_beside_a_folder_is_a_mistake_not_a_run(self):
        code, _, err = run("-h", "scenes", cwd=self.cwd)
        self.assertEqual(code, 2)
        self.assertIn("<folder>", err)
        self.assertNothingWritten()

    def test_no_argument_prints_the_usage_and_names_nothing(self):
        code, out, err = run(cwd=self.cwd)
        self.assertEqual(code, 2, "a run with no folder should say so, not write scenes somewhere")
        self.assertIn("<folder>", err)
        self.assertNotIn("scenes written", out)
        self.assertNothingWritten()

    def test_two_folders_are_refused(self):
        code, out, err = run("scenes", "more", cwd=self.cwd)
        self.assertEqual(code, 2)
        self.assertIn("<folder>", err)
        self.assertNotIn("scenes written", out)
        self.assertNothingWritten()

    def test_a_file_is_named_instead_of_a_traceback(self):
        picture = self.cwd / "README.md"
        picture.write_text("not a folder\n", encoding="utf-8")
        code, _, err = run(str(picture), cwd=self.cwd)
        self.assertEqual(code, 2, f"a file as the folder exited {code}")
        self.assertIn(str(picture), err, "the refusal does not name the path")
        self.assertNotIn("Traceback", err, "a file as the folder still ends in a traceback")
        self.assertEqual(picture.read_text(encoding="utf-8"), "not a folder\n", "the file was changed")

    def test_an_empty_argument_is_refused(self):
        # `scenes_wallpapers.py ""` is what a variable that came out empty looks like: os.makedirs('')
        # is a FileNotFoundError whose line names no path, so nothing in it says what to fix.
        code, out, err = run("", cwd=self.cwd)
        self.assertEqual(code, 2, f"an empty folder exited {code} (stdout {out!r})")
        self.assertIn("empty", err, "the refusal does not say the argument was empty")
        self.assertIn("<folder>", err, "the refusal does not print the usage line")
        self.assertNotIn("Traceback", err, "an empty folder still ends in a traceback")
        self.assertNotIn("scenes written", out)
        self.assertNothingWritten()

    def test_a_folder_it_cannot_create_is_named(self):
        # A path through a file: makedirs answered NotADirectoryError, a traceback with exit 1.
        picture = self.cwd / "README.md"
        picture.write_text("not a folder\n", encoding="utf-8")
        target = picture / "scenes"
        code, out, err = run(str(target), cwd=self.cwd)
        self.assertEqual(code, 2, f"a folder under a file exited {code} (stdout {out!r})")
        self.assertIn(str(target), err, "the refusal does not name the path it could not create")
        self.assertIn("Nothing was written", err, "the refusal does not say nothing was written")
        self.assertNotIn("Traceback", err, "a folder it cannot create still ends in a traceback")
        self.assertEqual(picture.read_text(encoding="utf-8"), "not a folder\n", "the file was changed")

    def test_a_folder_where_a_scene_goes_is_named_with_what_was_written(self):
        # A stale directory called after one scene's file: open(..., 'w') answered IsADirectoryError,
        # a traceback that also looked like a run that wrote nothing.
        names = caption_names()
        self.assertGreaterEqual(len(names), 3, "read no picture names out of captions.txt")
        out_dir = self.cwd / "scenes"
        (out_dir / f"{names[1]}.svg").mkdir(parents=True)
        code, out, err = run(str(out_dir), cwd=self.cwd)
        self.assertEqual(code, 2, f"a folder in the way exited {code} (stdout {out!r})")
        self.assertIn(str(out_dir / f"{names[1]}.svg"), err, "the refusal does not name the file")
        self.assertIn(f"1 of {len(names)}", err, "the refusal does not say how much was written")
        self.assertNotIn("Traceback", err, "a folder in the way still ends in a traceback")
        self.assertNotIn("scenes written", out)
        self.assertTrue((out_dir / f"{names[0]}.svg").is_file(),
                        "the scene written before the failure is not on disk")

    def test_the_scenes_land_in_the_folder_as_promised(self):
        out_dir = self.cwd / "scenes"
        code, out, err = run(str(out_dir), cwd=self.cwd)
        self.assertEqual(code, 0, f"a plain run exited {code}: {err}")
        self.assertIn(str(out_dir), out, "a plain run does not say where it wrote")
        written = sorted(p.name for p in out_dir.iterdir())
        expected = [f"{name}.svg" for name in caption_names()]
        self.assertGreaterEqual(len(expected), 10, "read no picture names out of captions.txt")
        self.assertEqual(written, sorted(expected),
                         "the SVGs written and the captions themes/scenes ships do not name the same set")
        for name in written:
            head = (out_dir / name).read_text(encoding="utf-8")[:200]
            self.assertIn("<svg", head, f"{name} is not an SVG")
            self.assertIn('width="1280"', head, f"{name} is not 1280 px wide")


if __name__ == "__main__":
    unittest.main()

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
import contextlib
import errno
import io
import hashlib
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from tools import scenes_wallpapers


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


    def test_render_failure_preserves_an_existing_or_missing_scene(self):
        name = caption_names()[0] + ".svg"
        original = b'<svg xmlns="http://www.w3.org/2000/svg"><title>Saved scene</title></svg>'
        for existing in (False, True):
            with self.subTest(existing=existing):
                folder = self.cwd / str(existing)
                folder.mkdir()
                destination = folder / name
                if existing:
                    destination.write_bytes(original)
                scene = mock.Mock(side_effect=OSError(errno.ENOSPC, "No space left on device"))
                out, err = io.StringIO(), io.StringIO()
                with mock.patch.object(scenes_wallpapers, "SCENES", [(name[:-4], scene)]), \
                        contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
                    with self.assertRaises(SystemExit) as caught:
                        scenes_wallpapers.write(str(folder))
                self.assertEqual(caught.exception.code, 2)
                self.assertEqual(out.getvalue(), "")
                self.assertIn("No space left", err.getvalue())
                if existing:
                    self.assertEqual(destination.read_bytes(), original)
                else:
                    self.assertFalse(destination.exists())
                self.assertEqual(sorted(p.name for p in folder.iterdir()), [name] if existing else [])



class FailingSVG:
    def __init__(self, stream, fault):
        self.stream, self.fault = stream, fault

    def __enter__(self):
        self.stream.__enter__()
        return self

    def __exit__(self, *args):
        result = self.stream.__exit__(*args)
        if self.fault == "close" and args[0] is None:
            raise OSError(errno.ENOSPC, "No space left on device")
        return result

    def write(self, text):
        if self.fault in ("empty", "partial"):
            self.stream.write(text[:23] if self.fault == "partial" else "")
            self.stream.flush()
            raise OSError(errno.ENOSPC, "No space left on device")
        return self.stream.write(text)


def failing_open(name, fault):
    original = open

    def opened(path, mode="r", *args, **kwargs):
        stream = original(path, mode, *args, **kwargs)
        return FailingSVG(stream, fault) if "w" in mode and Path(path).name == name else stream

    return opened


class ScenesWallpapersWritesTest(unittest.TestCase):
    ORIGINAL = b'<svg xmlns="http://www.w3.org/2000/svg"><title>Saved complete scene</title></svg>'

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.names = [name + ".svg" for name, _ in scenes_wallpapers.SCENES]

    def outputs(self, folder, existing):
        folder.mkdir()
        if existing:
            for name in self.names:
                (folder / name).write_bytes(self.ORIGINAL)
        (folder / "keep.txt").write_bytes(b"unrelated")

    def run_writer(self, folder):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                scenes_wallpapers.write(str(folder))
                code = 0
            except SystemExit as failure:
                code = failure.code
        return code, out.getvalue(), err.getvalue()

    def test_cli_write_failures_preserve_the_failed_scene_and_later_files(self):
        wrapper = """import pathlib, runpy, sys
from unittest import mock
script, folder, name, fault = sys.argv[1:]
sys.path.insert(0, str(pathlib.Path(script).resolve().parents[1] / 'tests'))
from test_scenes_wallpapers_args import failing_open
sys.argv = [script, folder]
with mock.patch('builtins.open', side_effect=failing_open(name, fault)):
    runpy.run_path(script, run_name='__main__')
"""
        for index in (0, 1):
            for existing in (False, True):
                for fault in ("empty", "partial", "close"):
                    with self.subTest(index=index, existing=existing, fault=fault):
                        folder = self.root / f"{index}-{existing}-{fault}"
                        self.outputs(folder, existing)
                        result = subprocess.run([sys.executable, "-B", "-c", wrapper, str(SCRIPT),
                                                 str(folder), self.names[index], fault],
                                                capture_output=True, timeout=60)
                        self.assertEqual(result.returncode, 2, result.stderr)
                        self.assertEqual(result.stdout, b"")
                        self.assertNotIn(b"Traceback", result.stderr)
                        self.assertIn(b"No space left", result.stderr)
                        self.assertIn(f"{index} of {len(self.names)}".encode(), result.stderr)
                        for j, name in enumerate(self.names):
                            target = folder / name
                            if j < index:
                                self.assertEqual(target.read_text(), scenes_wallpapers.SCENES[j][1]())
                            elif existing:
                                self.assertEqual(hashlib.sha256(target.read_bytes()).digest(),
                                                 hashlib.sha256(self.ORIGINAL).digest())
                            else:
                                self.assertFalse(target.exists())
                        self.assertEqual((folder / "keep.txt").read_bytes(), b"unrelated")
                        self.assertEqual(sorted(p.name for p in folder.iterdir()),
                                         sorted(["keep.txt", *(self.names if existing else self.names[:index])]))

    def test_failed_publication_or_permission_copy_preserves_output(self):
        for operation, existing in (("replace", False), ("replace", True), ("copymode", True)):
            with self.subTest(operation=operation, existing=existing):
                folder = self.root / f"{operation}-{existing}"
                self.outputs(folder, existing)
                owner = os if operation == "replace" else shutil
                with mock.patch.object(owner, operation, side_effect=PermissionError("publication denied")):
                    code, out, err = self.run_writer(folder)
                self.assertEqual(code, 2, err)
                self.assertEqual(out, "")
                self.assertIn("publication denied", err)
                if existing:
                    self.assertEqual((folder / self.names[0]).read_bytes(), self.ORIGINAL)
                self.assertEqual(sorted(p.name for p in folder.iterdir()),
                                 sorted(["keep.txt", *(self.names if existing else [])]))

    def test_cleanup_error_after_publication_warns_and_keeps_success(self):
        folder = self.root / "scenes"
        self.outputs(folder, True)
        original_exit = tempfile.TemporaryDirectory.__exit__

        def failed_cleanup(directory, *args):
            original_exit(directory, *args)
            raise OSError("staging cleanup failed")

        with mock.patch.object(tempfile.TemporaryDirectory, "__exit__", failed_cleanup):
            code, out, err = self.run_writer(folder)
        self.assertEqual(code, 0, err)
        self.assertIn("scenes written", out)
        self.assertIn("warning", err)
        self.assertIn("cleanup failed", err)
        self.assertNotIn("cannot write", err)
        self.assertEqual(sorted(p.name for p in folder.iterdir()), sorted(["keep.txt", *self.names]))
        for name, scene in scenes_wallpapers.SCENES:
            self.assertEqual((folder / (name + ".svg")).read_text(), scene())

    def test_unexpected_render_error_propagates_without_touching_output(self):
        for existing in (False, True):
            with self.subTest(existing=existing):
                folder = self.root / str(existing)
                self.outputs(folder, existing)
                scenes = [(self.names[0][:-4], mock.Mock(side_effect=RuntimeError("render failed")))]
                with mock.patch.object(scenes_wallpapers, "SCENES", scenes):
                    with self.assertRaisesRegex(RuntimeError, "render failed"):
                        self.run_writer(folder)
                if existing:
                    self.assertEqual((folder / self.names[0]).read_bytes(), self.ORIGINAL)
                else:
                    self.assertFalse((folder / self.names[0]).exists())

    def test_symlink_is_retained_and_its_target_is_updated(self):
        folder = self.root / "scenes"
        self.outputs(folder, False)
        target = self.root / self.names[0]
        for existing in (False, True):
            with self.subTest(existing=existing):
                link = folder / self.names[0]
                link.unlink(missing_ok=True)
                target.unlink(missing_ok=True)
                if existing:
                    target.write_bytes(self.ORIGINAL)
                try:
                    link.symlink_to(target)
                except OSError as error:
                    self.skipTest(f"symlinks unavailable: {error}")
                original_link = os.readlink(link)
                code, _, err = self.run_writer(folder)
                self.assertEqual(code, 0, err)
                self.assertTrue(link.is_symlink())
                self.assertEqual(os.readlink(link), original_link)
                self.assertTrue(os.path.samefile(link, target))
                self.assertEqual(target.read_text(), scenes_wallpapers.SCENES[0][1]())

    def test_hardlinked_destination_is_refused_without_breaking_the_link(self):
        folder = self.root / "scenes"
        self.outputs(folder, False)
        other = self.root / "saved.svg"
        other.write_bytes(self.ORIGINAL)
        destination = folder / self.names[0]
        try:
            os.link(other, destination)
        except OSError as error:
            self.skipTest(f"hardlinks unavailable: {error}")
        code, out, err = self.run_writer(folder)
        self.assertEqual(code, 2, err)
        self.assertEqual(out, "")
        self.assertIn("hard", err)
        self.assertTrue(os.path.samefile(other, destination))
        self.assertEqual(other.read_bytes(), self.ORIGINAL)
        self.assertEqual(destination.read_bytes(), self.ORIGINAL)
        self.assertEqual(sorted(p.name for p in folder.iterdir()), [self.names[0], "keep.txt"])

    @unittest.skipIf(os.name == "nt", "POSIX permission bits")
    def test_success_preserves_existing_modes_including_write_only_files(self):
        for mode in (0o640, 0o200):
            with self.subTest(mode=oct(mode)):
                folder = self.root / str(mode)
                self.outputs(folder, True)
                destination = folder / self.names[0]
                destination.chmod(mode)
                try:
                    code, _, err = self.run_writer(folder)
                    self.assertEqual(code, 0, err)
                    self.assertEqual(stat.S_IMODE(destination.stat().st_mode), mode)
                finally:
                    destination.chmod(0o600)
                self.assertEqual(destination.read_text(), scenes_wallpapers.SCENES[0][1]())

    def test_read_only_existing_scene_is_not_replaced(self):
        folder = self.root / "scenes"
        self.outputs(folder, True)
        destination = folder / self.names[0]
        destination.chmod(0o444)
        try:
            if os.access(destination, os.W_OK):
                self.skipTest("this account can write read-only files")
            code, out, err = self.run_writer(folder)
            self.assertEqual(code, 2, err)
            self.assertEqual(out, "")
            self.assertEqual(destination.read_bytes(), self.ORIGINAL)
        finally:
            destination.chmod(0o600)


if __name__ == "__main__":
    unittest.main()

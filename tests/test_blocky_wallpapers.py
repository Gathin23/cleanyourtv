"""themes/blocky and the script that draws its wallpapers, tools/blocky_wallpapers.py.

The theme's record of its pictures is wallpapers/captions.txt (`<file><TAB><name>`, themes/README.md); the folder,
the captions, the script's scene list and config.json's wallpaper_moods must all name the same 11 files, or a
scene goes without its name, or never shows because no time of day lists it.

The script takes exactly one folder, like tools/scenes_wallpapers.py: -h/--help prints its note and writes
nothing, and a leading dash, an empty word or a path that is a file is refused with exit 2 before anything is
written. Those checks run without Pillow. With Pillow (tools/requirements-perf.txt) the script is run into a
folder of its own, and every picture must be a 1920x1080 PNG under themes/README.md's 300 KB whose top six block
rows, where the clock and the pills sit, are plain sky in every outdoor scene.

A picture is encoded into a staging folder and renamed into place, as tools/scenes_wallpapers.py does, so a
save that fails - a full disk, an encoder that stops halfway, a read-only file - cannot leave a truncated PNG
under the name the theme ships. `BlockyWrites` holds that half, with a stand-in image so it runs without
Pillow, and one real-encoder run of the command line.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import errno
import io
import json
import os
import shutil
import stat
import subprocess
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

from tools import blocky_wallpapers

ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "blocky_wallpapers.py"
THEME = ROOT / "themes" / "blocky"
INDOOR = {"10-cave.png", "11-lava-cavern.png"}   # rock from edge to edge: no sky to keep plain

try:
    from PIL import Image
except ImportError:
    Image = None


def run(*args, cwd):
    return subprocess.run([sys.executable, "-I", str(SCRIPT), *args], cwd=cwd, capture_output=True, text=True,
                          timeout=300)


def caption_files():
    lines = (THEME / "wallpapers" / "captions.txt").read_text(encoding="utf-8").splitlines()
    return [line.split("\t")[0].strip() for line in lines if line.strip()]


class BlockyTheme(unittest.TestCase):
    def test_folder_captions_and_script_name_the_same_pictures(self):
        pictures = sorted(p.name for p in (THEME / "wallpapers").glob("*.png"))
        self.assertEqual(len(pictures), 11)
        self.assertEqual(caption_files(), pictures)
        source = SCRIPT.read_text(encoding="utf-8")
        for name in pictures:
            self.assertIn(f'"{name[:-4]}"', source, f"{name} is not drawn by {SCRIPT.name}")

    def test_every_picture_has_a_time_of_day_and_every_mood_names_a_picture(self):
        moods = json.loads((THEME / "config.json").read_text(encoding="utf-8"))["wallpaper_moods"]
        listed = {name for group in moods.values() for name in group}
        self.assertEqual(listed, set(caption_files()))


class BlockyScript(unittest.TestCase):
    def test_help_writes_nothing(self):
        with tempfile.TemporaryDirectory() as d:
            for flag in ("-h", "--help"):
                result = run(flag, cwd=d)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("blocky_wallpapers.py <folder>", result.stdout)
            self.assertEqual(list(Path(d).iterdir()), [])

    def test_refuses_what_is_not_one_folder(self):
        with tempfile.TemporaryDirectory() as d:
            Path(d, "file.txt").write_text("x")
            for args in ([], ["--out", "x"], ["-x"], [""], ["file.txt"], ["a", "b"]):
                result = run(*args, cwd=d)
                self.assertEqual(result.returncode, 2, (args, result.stdout, result.stderr))
            self.assertEqual(sorted(p.name for p in Path(d).iterdir()), ["file.txt"])

    @unittest.skipIf(Image is None, "Pillow is not installed (tools/requirements-perf.txt)")
    def test_draws_eleven_screen_sized_pictures_with_a_plain_header(self):
        with tempfile.TemporaryDirectory() as d:
            result = run("out", cwd=d)
            self.assertEqual(result.returncode, 0, result.stderr)
            pictures = sorted(Path(d, "out").glob("*.png"))
            self.assertEqual([p.name for p in pictures], caption_files())
            for path in pictures:
                self.assertLess(path.stat().st_size, 300 * 1024, path.name)
                with Image.open(path) as im:
                    self.assertEqual(im.size, (1920, 1080), path.name)
                    if path.name in INDOOR:
                        continue
                    rgb = im.convert("RGB")
                    for row in range(6):
                        band = rgb.crop((0, row * 40, 1920, row * 40 + 40))
                        self.assertEqual(len(band.getcolors(1 << 16) or [None, None]), 1,
                                         f"{path.name}: header row {row} is not plain sky")


class StubPicture:
    """The Pillow image a scene is drawn on, as far as publication is concerned: `save()` is the encoder.

    A real encoder opens the path it is given and writes into it, so a failure partway leaves the bytes it had
    already written there. The stand-in does the same, which is what lets these tests run without Pillow.
    """

    PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 64

    def __init__(self, fault=None):
        self.fault = fault

    def save(self, path, **kwargs):
        if self.fault == "before":
            raise OSError(errno.ENOSPC, "No space left on device")
        with open(path, "wb") as f:
            f.write(self.PNG[: 8 if self.fault == "partial" else len(self.PNG)])
            if self.fault == "partial":
                raise OSError(errno.ENOSPC, "No space left on device")


def stub_canvas(picture):
    class Canvas:
        def __init__(self, seed):
            self.img = picture

    return Canvas


class BlockyWrites(unittest.TestCase):
    """What a failed save leaves behind: nothing at the destination, and no truncated PNG on its name."""

    ORIGINAL = (THEME / "wallpapers" / "01-meadow-day.png").read_bytes()   # the picture the theme ships

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.names = [name + ".png" for name, _ in blocky_wallpapers.SCENES]

    def outputs(self, folder, existing):
        folder.mkdir()
        if existing:
            for name in self.names:
                (folder / name).write_bytes(self.ORIGINAL)
        (folder / "keep.txt").write_bytes(b"unrelated")

    def run_writer(self, folder, picture=None):
        scenes = [(name, lambda cv: None) for name, _ in blocky_wallpapers.SCENES]
        out, err = io.StringIO(), io.StringIO()
        # CI installs tools/requirements.txt only, so Pillow is not there to import; the encoder is stood in for.
        with mock.patch.object(blocky_wallpapers, "SCENES", scenes), \
                mock.patch.object(blocky_wallpapers, "Canvas", stub_canvas(picture or StubPicture())), \
                mock.patch.dict(sys.modules, {"PIL": types.ModuleType("PIL")}), \
                contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                blocky_wallpapers.write(str(folder))
                code = 0
            except SystemExit as failure:
                code = failure.code
        return code, out.getvalue(), err.getvalue()

    def test_an_encoder_that_fails_preserves_the_picture_it_would_replace(self):
        for fault in ("before", "partial"):
            for existing in (False, True):
                with self.subTest(fault=fault, existing=existing):
                    folder = self.root / f"{fault}-{existing}"
                    self.outputs(folder, existing)
                    code, out, err = self.run_writer(folder, StubPicture(fault))
                    self.assertEqual(code, 2, err)
                    self.assertEqual(out, "")
                    self.assertIn("No space left", err)
                    self.assertIn(f"0 of {len(self.names)}", err)
                    self.assertNotIn("Traceback", err)
                    self.assertEqual(sorted(p.name for p in folder.iterdir()),
                                     sorted(["keep.txt", *(self.names if existing else [])]))
                    if existing:
                        self.assertEqual((folder / self.names[0]).read_bytes(), self.ORIGINAL)

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
        for name in self.names:
            self.assertEqual((folder / name).read_bytes(), StubPicture.PNG)

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
                self.assertEqual(target.read_bytes(), StubPicture.PNG)

    def test_hardlinked_destination_is_refused_without_breaking_the_link(self):
        folder = self.root / "scenes"
        self.outputs(folder, False)
        other = self.root / "saved.png"
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
        self.assertEqual(sorted(p.name for p in folder.iterdir()), sorted([self.names[0], "keep.txt"]))

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
                self.assertEqual(destination.read_bytes(), StubPicture.PNG)

    def test_read_only_existing_picture_is_not_replaced(self):
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

    @unittest.skipIf(Image is None, "Pillow is not installed (tools/requirements-perf.txt)")
    def test_the_command_line_failing_midway_keeps_every_picture_on_disk(self):
        """The run that produced the defect: the real encoder stops partway through one picture."""
        wrapper = """import builtins, errno, pathlib, runpy, sys
script, folder, name = sys.argv[1:]
real_open = builtins.open


class Half:
    def __init__(self, stream):
        self.stream = stream

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.stream.close()
        return False

    def write(self, data):
        self.stream.write(bytes(data)[:4096])
        self.stream.flush()
        raise OSError(errno.ENOSPC, "No space left on device")

    def __getattr__(self, attribute):
        return getattr(self.stream, attribute)


def failing_open(path, mode="r", *args, **kwargs):
    stream = real_open(path, mode, *args, **kwargs)
    if "w" in mode and pathlib.Path(path).name == name:
        return Half(stream)
    return stream


builtins.open = failing_open
sys.argv = [script, folder]
runpy.run_path(script, run_name="__main__")
"""
        folder = self.root / "shots"
        self.outputs(folder, True)
        result = subprocess.run([sys.executable, "-B", "-c", wrapper, str(SCRIPT), str(folder), self.names[1]],
                                capture_output=True, timeout=300)
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertEqual(result.stdout, b"")
        self.assertNotIn(b"Traceback", result.stderr)
        self.assertIn(b"No space left", result.stderr)
        self.assertIn(f"1 of {len(self.names)}".encode(), result.stderr)
        first = (folder / self.names[0]).read_bytes()
        self.assertTrue(first.startswith(b"\x89PNG\r\n\x1a\n"), "the first picture was not published")
        self.assertGreater(len(first), 1000)
        for name in self.names[1:]:
            self.assertEqual((folder / name).read_bytes(), self.ORIGINAL, name)
        self.assertEqual((folder / "keep.txt").read_bytes(), b"unrelated")
        self.assertEqual(sorted(p.name for p in folder.iterdir()), sorted(["keep.txt", *self.names]))


if __name__ == "__main__":
    unittest.main()

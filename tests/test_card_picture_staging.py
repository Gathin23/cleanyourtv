"""tools/picture_file.py publishes a picture by rename, so a failed write cannot cut the old one short.

`tools/readme_stats.py`, `tools/readme_featured.py` and `tools/readme_card.py` drew the README card and
ended with `img.save(OUT, ...)`. Pillow opens the destination before it encodes (`ImageFile.save()` opens
it `w+b`, then calls the encoder), so the three shapes a failed redraw takes - a full disk, an encoder
that raises, a file the run may not rewrite - were each answered by whatever the encoder had already
written, under the name the repository ships the picture at: docs/stats.png, docs/featured.png or
docs/card.jpg cut short. The scripts still ended with "Nothing was written.".

docs/stats.png is also an input: readme_card.py loads it to stack the card, so one truncated strip stops
the card being rebuilt until the picture is restored with git.

The tests here hold `picture_file.save()` to the four things that make it safe to call on a file that is
already there - the old picture survives a failed encode, a symlink keeps its link, a second hard link or
a device is refused before anything is encoded, and an existing file's mode is carried onto the new one -
and to the two shapes a fresh file takes (the mode `open()` left, and a staging folder that cannot be
removed after the rename being a warning rather than "nothing was written"). They need no Pillow: the
"encoder" is a stand-in whose `save()` writes what a real one writes before it fails.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import errno
import importlib.util
import io
import os
from contextlib import redirect_stderr
from pathlib import Path
import stat
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "tools"
sys.path.insert(0, str(TOOLS))
import picture_file  # noqa: E402

# The three scripts that draw the card, with the picture each one is told to write.
CARD_SCRIPTS = {
    "readme_stats.py": "docs/stats.png",
    "readme_featured.py": "docs/featured.png",
    "readme_card.py": "docs/card.jpg",
}
LAUNCHER_FONT = ROOT / "launcher" / "app" / "src" / "main" / "res" / "font" / "outfit.ttf"
HAVE_PIL = importlib.util.find_spec("PIL") is not None

OLD = b"\x89PNG\r\n\x1a\nOLD PICTURE, ALREADY IN THE REPOSITORY"


class Encoder:
    """A stand-in for a Pillow image: `save()` writes `data`, then fails if `error` is given."""

    def __init__(self, data=b"\x89PNG\r\n\x1a\nNEW PICTURE", error=None):
        self.data, self.error = data, error
        self.seen = []

    def save(self, path, **options):
        self.seen.append((path, options))
        with open(path, "wb") as f:
            f.write(self.data)
            f.flush()
            if self.error is not None:
                raise self.error


class StagedWriteTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self._tmp.name)
        self.out = self.dir / "stats.png"

    def tearDown(self):
        self._tmp.cleanup()

    def leftovers(self):
        return sorted(p.name for p in self.dir.iterdir() if p != self.out)

    def assert_staged_write(self):
        encoder = Encoder()
        picture_file.save(encoder, str(self.out), optimize=True)
        self.assertEqual(self.out.read_bytes(), encoder.data)
        self.assertEqual(len(encoder.seen), 1)
        staged, options = encoder.seen[0]
        self.assertEqual(options, {"optimize": True})
        # The encoder is handed a file beside the destination with the same name, so Pillow picks the
        # format from the same suffix it would have seen.
        self.assertEqual(Path(staged).name, "stats.png")
        # Directory aliases (including macOS /var) need not have the same spelling.
        self.assertTrue(Path(staged).parent.parent.samefile(self.dir))
        self.assertEqual(self.leftovers(), [], "the staging folder outlived a successful write")

    def test_the_picture_lands_and_the_options_reach_the_encoder(self):
        self.assert_staged_write()

    def test_a_destination_directory_symlink_keeps_the_same_staging_checks(self):
        with tempfile.TemporaryDirectory() as tmp:
            alias = Path(tmp) / "pictures"
            alias.symlink_to(self.dir, target_is_directory=True)
            self.dir = alias
            self.out = alias / "stats.png"
            self.assert_staged_write()

    def test_a_failed_encode_leaves_the_picture_that_was_there(self):
        self.out.write_bytes(OLD)
        with self.assertRaises(OSError) as caught:
            picture_file.save(Encoder(error=OSError(errno.ENOSPC, "No space left on device")), str(self.out))
        self.assertEqual(caught.exception.errno, errno.ENOSPC)
        self.assertEqual(self.out.read_bytes(), OLD, "a failed encode cut the old picture short")
        self.assertEqual(self.leftovers(), [], "a failed encode left its staging folder behind")

    def test_an_existing_file_keeps_its_mode(self):
        self.out.write_bytes(OLD)
        os.chmod(self.out, 0o640)
        picture_file.save(Encoder(), str(self.out))
        self.assertEqual(stat.S_IMODE(os.stat(self.out).st_mode), 0o640)

    def test_a_fresh_file_keeps_the_mode_open_left(self):
        umask = os.umask(0o022)
        os.umask(umask)
        picture_file.save(Encoder(), str(self.out))
        self.assertEqual(stat.S_IMODE(os.stat(self.out).st_mode), 0o666 & ~umask,
                         "a fresh picture should keep the mode open() left, not a staging file's")

    def test_a_symlink_keeps_its_link_and_its_target_is_replaced(self):
        target = self.dir / "elsewhere.png"
        target.write_bytes(OLD)
        self.out.symlink_to(target)
        picture_file.save(Encoder(), str(self.out))
        self.assertTrue(self.out.is_symlink(), "the link itself was replaced by a plain file")
        self.assertEqual(target.read_bytes(), b"\x89PNG\r\n\x1a\nNEW PICTURE")

    def test_a_second_hard_link_is_refused_before_anything_is_encoded(self):
        self.out.write_bytes(OLD)
        other = self.dir / "same-picture.png"
        os.link(self.out, other)
        encoder = Encoder()
        with self.assertRaises(OSError) as caught:
            picture_file.save(encoder, str(self.out))
        self.assertIn("hard link", str(caught.exception))
        self.assertEqual(encoder.seen, [], "the encoder ran for a destination that was refused")
        self.assertEqual(self.out.read_bytes(), OLD)
        self.assertEqual(other.read_bytes(), OLD)

    def test_a_destination_that_is_not_a_regular_file_is_refused(self):
        fifo = self.dir / "a-pipe.png"
        os.mkfifo(fifo)
        with self.assertRaises(OSError) as caught:
            picture_file.save(Encoder(), str(fifo))
        self.assertIn("not a regular file", str(caught.exception))
        self.assertTrue(stat.S_ISFIFO(os.stat(fifo).st_mode))
        folder = self.dir / "a-folder.png"
        folder.mkdir()
        with self.assertRaises(OSError) as caught:
            picture_file.save(Encoder(), str(folder))
        self.assertIn("not a regular file", str(caught.exception))

    @unittest.skipIf(os.geteuid() == 0, "root may write a file whose mode says otherwise")
    def test_a_read_only_file_is_refused_without_being_truncated(self):
        self.out.write_bytes(OLD)
        os.chmod(self.out, 0o444)
        encoder = Encoder()
        with self.assertRaises(OSError) as caught:
            picture_file.save(encoder, str(self.out))
        self.assertIn("cannot be written", str(caught.exception))
        self.assertEqual(encoder.seen, [])
        self.assertEqual(self.out.read_bytes(), OLD)

    def test_a_staging_folder_that_cannot_be_removed_after_the_rename_is_a_warning(self):
        # os.replace() has already published the file: a cleanup failure after it must not read as a run
        # that wrote nothing, which is what the three scripts print for an OSError.
        class Stuck(tempfile.TemporaryDirectory):
            def __exit__(self, *exc):
                super().__exit__(*exc)
                raise OSError(errno.EACCES, "Permission denied")

        err = io.StringIO()
        with mock.patch.object(picture_file.tempfile, "TemporaryDirectory", Stuck), redirect_stderr(err):
            picture_file.save(Encoder(), str(self.out))
        self.assertEqual(self.out.read_bytes(), b"\x89PNG\r\n\x1a\nNEW PICTURE")
        self.assertIn("warning:", err.getvalue())
        self.assertIn("could not be removed", err.getvalue())


class EncoderInTheScriptsTest(unittest.TestCase):
    """Each card script hands its picture to picture_file.save() rather than to img.save()."""

    def test_the_scripts_publish_through_the_helper(self):
        for name in CARD_SCRIPTS:
            with self.subTest(script=name):
                text = (TOOLS / name).read_text(encoding="utf-8")
                self.assertIn("import picture_file", text, f"{name} no longer imports the staged write")
                self.assertIn("picture_file.save(", text, f"{name} writes its picture some other way")
                self.assertNotIn(".save(OUT", text, f"{name} still opens the destination before encoding it")


@unittest.skipUnless(HAVE_PIL, "drawing the card needs Pillow (tools/requirements-perf.txt)")
class RealRedrawTest(unittest.TestCase):
    def test_a_redraw_keeps_the_mode_and_replaces_an_old_picture(self):
        for name, picture in CARD_SCRIPTS.items():
            with self.subTest(script=name):
                if name == "readme_card.py":
                    for part in ("docs/stats.png", "docs/featured.png"):
                        if not (ROOT / part).is_file():
                            self.skipTest(f"{part} is not in this checkout, so the card cannot be stacked")
                with tempfile.TemporaryDirectory() as tmp:
                    out = Path(tmp) / Path(picture).name
                    out.write_bytes(OLD)
                    os.chmod(out, 0o640)
                    done = subprocess.run([sys.executable, str(TOOLS / name), str(LAUNCHER_FONT), str(out)],
                                          cwd=ROOT, capture_output=True, text=True)
                    self.assertEqual(done.returncode, 0, f"{name} failed on a path it should write: {done.stderr}")
                    self.assertNotEqual(out.read_bytes()[:16], OLD[:16], f"{name} left the old picture in place")
                    self.assertGreater(out.stat().st_size, 1000, f"{name} wrote an empty picture")
                    self.assertEqual(stat.S_IMODE(os.stat(out).st_mode), 0o640,
                                     f"{name} dropped the mode the picture had")

    def test_a_hard_linked_destination_is_refused_and_left_alone(self):
        for name, picture in CARD_SCRIPTS.items():
            with self.subTest(script=name):
                with tempfile.TemporaryDirectory() as tmp:
                    out = Path(tmp) / Path(picture).name
                    out.write_bytes(OLD)
                    other = Path(tmp) / "shipped.png"
                    os.link(out, other)
                    done = subprocess.run([sys.executable, str(TOOLS / name), str(LAUNCHER_FONT), str(out)],
                                          cwd=ROOT, capture_output=True, text=True)
                    self.assertEqual(done.returncode, 1, f"{name} took a refused write as a success")
                    self.assertIn("cannot write", done.stderr)
                    self.assertIn("Nothing was written", done.stderr)
                    self.assertNotIn("Traceback", done.stderr)
                    self.assertEqual(out.read_bytes(), OLD)
                    self.assertEqual(other.read_bytes(), OLD)


if __name__ == "__main__":
    unittest.main()

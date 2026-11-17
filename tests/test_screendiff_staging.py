"""A failed screenshot report encode must leave an earlier report intact."""
from contextlib import redirect_stderr, redirect_stdout
import errno
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("screendiff_staging", ROOT / "tools" / "screendiff.py")
screendiff = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(screendiff)
HAVE_PIL = importlib.util.find_spec("PIL") is not None


@unittest.skipUnless(HAVE_PIL, "Pillow is optional")
class ScreenshotOutputTest(unittest.TestCase):
    def setUp(self):
        from PIL import Image
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.folder = Path(tmp.name)
        self.before, self.after = (self.folder / name for name in ("before.png", "after.png"))
        with Image.new("RGB", (16, 8), "black") as image:
            image.save(self.before)
            image.putpixel((3, 2), (255, 255, 255))
            image.save(self.after)
        self.originals = [p.read_bytes() for p in (self.before, self.after)]

    def test_failed_encodes_preserve_old_reports_and_leave_no_partial_new_report(self):
        from PIL import Image
        target = self.folder / "report.png"
        for option in ("--out", "--diff"):
            for old in (None, b"previous complete report"):
                for error in (OSError(errno.ENOSPC, "disk full"), ValueError("encoder failed")):
                    with self.subTest(option=option, old=old, error=type(error).__name__):
                        target.unlink(missing_ok=True)
                        if old is not None:
                            target.write_bytes(old)
                        expected = set(self.folder.iterdir())

                        def fail(image, path, **kwargs):
                            Path(path).write_bytes(b"partial PNG")
                            raise error

                        out, err = io.StringIO(), io.StringIO()
                        with mock.patch.object(Image.Image, "save", fail), \
                                redirect_stdout(out), redirect_stderr(err):
                            code = screendiff.main([str(self.before), str(self.after), option, str(target)])
                        self.assertEqual(code, 2)
                        self.assertIn(str(target), err.getvalue())
                        self.assertNotIn("Traceback", err.getvalue())
                        self.assertNotIn("different:", out.getvalue())
                        if old is None:
                            self.assertFalse(target.exists(), "a failed encode left a partial report")
                        else:
                            self.assertEqual(target.read_bytes(), old)
                        self.assertEqual(set(self.folder.iterdir()), expected, "staging files were left behind")
                        self.assertEqual([p.read_bytes() for p in (self.before, self.after)], self.originals)

    def test_isolated_cli_writes_decodable_pair_and_diff_and_keeps_symlinks(self):
        from PIL import Image
        pair, diff = self.folder / "pair.png", self.folder / "diff.png"
        pair_target, diff_target = self.folder / "pair.bin", self.folder / "diff.bin"
        try:
            pair.symlink_to(pair_target)
            diff.symlink_to(diff_target)
        except (OSError, NotImplementedError) as error:
            self.skipTest(f"this filesystem cannot create a symlink: {error}")
        run = subprocess.run([sys.executable, "-I", str(ROOT / "tools" / "screendiff.py"),
                              str(self.before), str(self.after), "--out", str(pair), "--diff", str(diff)],
                             capture_output=True, text=True, timeout=20)
        self.assertEqual(run.returncode, 1, run.stderr)
        self.assertNotIn("Traceback", run.stderr)
        self.assertTrue(pair.is_symlink())
        self.assertTrue(diff.is_symlink())
        with Image.open(pair_target) as image:
            image.load()
            self.assertEqual((image.format, image.size), ("PNG", (46, 64)))
        with Image.open(diff_target) as image:
            image.load()
            self.assertEqual((image.format, image.size), ("PNG", (16, 8)))
            self.assertEqual(image.getpixel((3, 2)), 255)
            self.assertEqual(image.getpixel((0, 0)), 0)
        self.assertEqual([p.read_bytes() for p in (self.before, self.after)], self.originals)
        self.assertFalse(list(self.folder.glob(".picture-*")))

    @unittest.skipUnless(hasattr(os, "mkfifo"), "POSIX special file")
    def test_a_special_destination_is_refused_before_the_encoder_runs(self):
        from PIL import Image
        target = self.folder / "pipe.png"
        os.mkfifo(target)
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(Image.Image, "save") as encode, \
                redirect_stdout(out), redirect_stderr(err):
            code = screendiff.main([str(self.before), str(self.after), "--out", str(target)])
        self.assertEqual(code, 2)
        self.assertIn(str(target), err.getvalue())
        self.assertIn("not a regular file", err.getvalue())
        encode.assert_not_called()
        self.assertTrue(target.is_fifo())

"""tools/wallpaper_prep.py: size, darkening, numbering, the size limit and the refusals."""
import importlib.util
import contextlib
import io
import os
import tempfile
import unittest
from unittest import mock
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("wallpaper_prep", ROOT / "tools" / "wallpaper_prep.py")
prep = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(prep)

try:
    from PIL import Image
except ImportError:  # the tool needs Pillow; the rest of the repo does not
    Image = None


def make_photo(path, size=(400, 300), colour=(220, 200, 180)):
    Image.new("RGB", size, colour).save(path)


class ParseSizeTest(unittest.TestCase):
    def test_accepts_width_x_height(self):
        self.assertEqual(prep.parse_size("1280x720"), (1280, 720))
        self.assertEqual(prep.parse_size("1920X1080"), (1920, 1080))

    def test_refuses_nonsense(self):
        for bad in ("1280", "axb", "10x5", "99999x720", ""):
            with self.subTest(bad=bad):
                with self.assertRaises(Exception):
                    prep.parse_size(bad)


class SourceFilesTest(unittest.TestCase):
    def test_only_pictures_in_name_order(self):
        with tempfile.TemporaryDirectory() as d:
            for name in ("b.JPG", "a.png", "notes.txt", "c.webp", "d.heic"):
                Path(d, name).write_bytes(b"x")
            os.mkdir(Path(d, "sub.jpg"))
            names = [os.path.basename(p) for p in prep.source_files(d)]
            self.assertEqual(names, ["a.png", "b.JPG", "c.webp", "d.heic"])


class OverlapTest(unittest.TestCase):
    """The preflight that refuses a numbered output which is, or links to, an original."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.src = Path(self.tmp.name, "photos")
        self.out = Path(self.tmp.name, "out")
        self.src.mkdir()
        self.out.mkdir()
        for name in ("a.jpg", "b.jpg"):
            (self.src / name).write_bytes(name.encode())
        self.files = prep.source_files(str(self.src))

    def link(self, make, target, link):
        try:
            make(target, link)
        except (OSError, NotImplementedError):
            self.skipTest("this kind of link is not available here")

    def test_separate_and_missing_outputs_are_fine(self):
        self.assertEqual([], prep.overlapping_outputs(self.files, str(self.out)))
        (self.out / "01.jpg").write_bytes(b"an old result")
        self.assertEqual([], prep.overlapping_outputs(self.files, str(self.out)))

    def test_file_symlink_to_an_original_is_found(self):
        self.link(os.symlink, self.src / "a.jpg", self.out / "01.jpg")
        self.assertEqual(["01.jpg"], prep.overlapping_outputs(self.files, str(self.out)))

    def test_hardlink_to_an_original_is_found(self):
        self.link(os.link, self.src / "a.jpg", self.out / "01.jpg")
        self.assertEqual(["01.jpg"], prep.overlapping_outputs(self.files, str(self.out)))

    def test_link_to_a_later_photo_is_found(self):
        self.link(os.symlink, self.src / "b.jpg", self.out / "01.jpg")
        self.assertEqual(["01.jpg"], prep.overlapping_outputs(self.files, str(self.out)))

    def test_symlink_to_a_file_outside_the_source_is_fine(self):
        other = Path(self.tmp.name, "elsewhere.jpg")
        other.write_bytes(b"not an original")
        self.link(os.symlink, other, self.out / "01.jpg")
        self.assertEqual([], prep.overlapping_outputs(self.files, str(self.out)))


@unittest.skipIf(Image is None, "Pillow not installed")
class PrepareTest(unittest.TestCase):
    def test_cleanup_failure_after_replace_still_counts_the_picture(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "photos"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")
            cleanup = tempfile.TemporaryDirectory.cleanup

            def cleanup_then_fail(stage):
                cleanup(stage)
                raise PermissionError("injected cleanup failure")

            err, output = io.StringIO(), io.StringIO()
            with mock.patch.object(tempfile.TemporaryDirectory, "cleanup", cleanup_then_fail), \
                    contextlib.redirect_stderr(err), contextlib.redirect_stdout(output):
                self.assertEqual(prep.main([str(src), str(out), "--size", "32x18"]), 0)
            with Image.open(out / "01.jpg") as image:
                self.assertEqual(image.size, (32, 18))
            self.assertIn("warning:", err.getvalue())
            self.assertIn("1 of 1 pictures written", output.getvalue())

    def test_failed_encoder_keeps_previous_output_and_cleans_stage(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "in.png"), Path(d, "out.jpg")
            make_photo(src)
            out.write_bytes(b"previous wallpaper")

            def fail_save(image, path, *args, **kwargs):
                Path(path).write_bytes(b"partial JPEG")
                raise OSError(28, "No space left on device")

            with mock.patch.object(Image.Image, "save", fail_save), self.assertRaises(OSError):
                prep.prepare(str(src), str(out), (32, 18), 1, 0, 84, 300)
            self.assertEqual(out.read_bytes(), b"previous wallpaper")
            self.assertEqual(sorted(p.name for p in Path(d).iterdir()), ["in.png", "out.jpg"])

    def test_failed_replace_keeps_previous_output(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "in.png"), Path(d, "out.jpg")
            make_photo(src)
            out.write_bytes(b"previous wallpaper")
            with mock.patch.object(prep.os, "replace", side_effect=PermissionError("busy")), \
                    self.assertRaises(PermissionError):
                prep.prepare(str(src), str(out), (32, 18), 1, 0, 84, 300)
            self.assertEqual(out.read_bytes(), b"previous wallpaper")
            self.assertEqual(sorted(p.name for p in Path(d).iterdir()), ["in.png", "out.jpg"])

    def test_success_preserves_symlink_and_target_permissions(self):
        with tempfile.TemporaryDirectory() as d:
            src, target, out = Path(d, "in.png"), Path(d, "target.jpg"), Path(d, "out.jpg")
            make_photo(src)
            target.write_bytes(b"old")
            target.chmod(0o640)
            try:
                out.symlink_to(target)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are not available here")
            prep.prepare(str(src), str(out), (32, 18), 1, 0, 84, 300)
            self.assertTrue(out.is_symlink())
            with Image.open(target) as image:
                self.assertEqual(image.size, (32, 18))
            if os.name == "posix":
                self.assertEqual(target.stat().st_mode & 0o777, 0o640)

    def test_failed_new_output_leaves_no_partial_picture(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "in.png"), Path(d, "out.jpg")
            make_photo(src)
            with mock.patch.object(Image.Image, "save", side_effect=OSError("encoder failed")), \
                    self.assertRaises(OSError):
                prep.prepare(str(src), str(out), (32, 18), 1, 0, 84, 300)
            self.assertFalse(out.exists())
            self.assertEqual(list(Path(d).iterdir()), [src])

    def test_output_is_1280x720_jpeg_and_darker(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "in.png"), Path(d, "out.jpg")
            make_photo(src)
            kb, quality = prep.prepare(str(src), str(out), (1280, 720), 0.25, 0.6, 84, 300)
            with Image.open(out) as result:
                self.assertEqual(result.size, (1280, 720))
                self.assertEqual(result.format, "JPEG")
                top = result.getpixel((640, 5))
                bottom = result.getpixel((640, 715))
            self.assertLess(sum(top), sum((220, 200, 180)) * 0.35, "top should be much darker than the source")
            self.assertLess(sum(bottom), sum(top), "bottom should be darker than top")
            self.assertLessEqual(kb, 300)

    def test_quality_drops_until_the_limit_fits(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "noise.png"), Path(d, "out.jpg")
            Image.effect_noise((1280, 720), 80).convert("RGB").save(src)
            full_kb, full_quality = prep.prepare(str(src), str(out), (1280, 720), 1.0, 0.0, 90, 10_000)
            self.assertEqual(full_quality, 90, "a limit it already meets keeps the starting quality")
            limit = int(full_kb * 0.8)
            kb, quality = prep.prepare(str(src), str(out), (1280, 720), 1.0, 0.0, 90, limit)
            self.assertLess(quality, 90)
            self.assertLessEqual(kb, limit)

    def test_an_impossible_limit_stops_at_the_quality_floor(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "noise.png"), Path(d, "out.jpg")
            Image.effect_noise((1280, 720), 80).convert("RGB").save(src)
            kb, quality = prep.prepare(str(src), str(out), (1280, 720), 1.0, 0.0, 90, 5)
            self.assertLessEqual(quality, 50)
            self.assertGreater(kb, 5, "it reports the size it could reach instead of looping forever")

    def test_originals_are_never_overwritten_and_target_must_differ(self):
        with tempfile.TemporaryDirectory() as d:
            make_photo(Path(d, "a.png"))
            with self.assertRaises(SystemExit):
                prep.main([d, d])

    def test_target_reached_through_a_symlink_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "photos")
            src.mkdir()
            make_photo(src / "01.jpg", size=(320, 240))
            before = (src / "01.jpg").read_bytes()
            link = Path(d, "output-link")
            try:
                os.symlink(src, link, target_is_directory=True)
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are not available here")
            with self.assertRaises(SystemExit):
                prep.main([str(src), str(link)])
            with self.assertRaises(SystemExit):
                prep.main([str(link), str(src)])
            self.assertEqual((src / "01.jpg").read_bytes(), before)

    def test_main_writes_nothing_when_an_output_links_to_an_original(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "photos"), Path(d, "out")
            src.mkdir()
            out.mkdir()
            make_photo(src / "01.jpg", size=(320, 240))
            make_photo(src / "02.jpg", size=(320, 240))
            before = {n: (src / n).read_bytes() for n in ("01.jpg", "02.jpg")}
            try:
                os.symlink(src / "02.jpg", out / "01.jpg")
            except (OSError, NotImplementedError):
                self.skipTest("symlinks are not available here")
            with self.assertRaises(SystemExit):
                prep.main([str(src), str(out)])
            self.assertEqual(before, {n: (src / n).read_bytes() for n in before})
            self.assertFalse((out / "02.jpg").exists(), "nothing is written once a clash is found")

    def test_same_folder_spelled_in_another_case_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "photos")
            src.mkdir()
            make_photo(src / "01.jpg", size=(320, 240))
            alias = Path(d, "PHOTOS")
            if not alias.is_dir():
                self.skipTest("this filesystem is case-sensitive")
            before = (src / "01.jpg").read_bytes()
            with self.assertRaises(SystemExit):
                prep.main([str(src), str(alias)])
            self.assertEqual(before, (src / "01.jpg").read_bytes())

    def test_target_inside_the_source_is_refused(self):
        with tempfile.TemporaryDirectory() as d:
            make_photo(Path(d, "a.png"))
            with self.assertRaises(SystemExit):
                prep.main([d, str(Path(d, "out"))])

    def test_main_numbers_the_outputs_and_skips_a_corrupt_file(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "src"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")
            (src / "b.jpg").write_bytes(b"not a picture")
            make_photo(src / "c.png", colour=(10, 20, 30))
            self.assertEqual(prep.main([str(src), str(out)]), 0)
            self.assertEqual(sorted(os.listdir(out)), ["01.jpg", "03.jpg"])

    def test_empty_folder_exits_with_a_message(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "src")
            src.mkdir()
            with self.assertRaises(SystemExit):
                prep.main([str(src), str(Path(d, "out"))])


if __name__ == "__main__":
    unittest.main()

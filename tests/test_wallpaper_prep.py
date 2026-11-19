"""tools/wallpaper_prep.py: size, darkening, numbering, the size limit and the refusals."""
import importlib.util
import contextlib
import io
import os
import subprocess
import sys
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

try:
    from PIL import ImageCms, features
    HAVE_CMS = features.check("littlecms2")
except ImportError:
    HAVE_CMS = False


def make_photo(path, size=(400, 300), colour=(220, 200, 180)):
    Image.new("RGB", size, colour).save(path)


def swapped_primaries_profile():
    """An RGB test profile that describes red samples as blue, generated without a profile asset."""
    profile = bytearray(ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes())
    tags = {}
    for index in range(int.from_bytes(profile[128:132], "big")):
        entry = 132 + 12 * index
        tags[bytes(profile[entry:entry + 4])] = (
            int.from_bytes(profile[entry + 4:entry + 8], "big"),
            int.from_bytes(profile[entry + 8:entry + 12], "big"))
    # Matrix RGB profiles store each primary in an XYZ tag. Exchanging the two complete
    # tags makes the expected colour change unambiguous while leaving a valid ICC profile.
    red, red_size = tags[b"rXYZ"]
    blue, blue_size = tags[b"bXYZ"]
    assert red_size == blue_size
    profile[red:red + red_size], profile[blue:blue + blue_size] = (
        profile[blue:blue + blue_size], profile[red:red + red_size])
    return bytes(profile)


@unittest.skipUnless(HAVE_CMS, "Pillow with LittleCMS is needed for colour profiles")
class ColourProfileTest(unittest.TestCase):
    def test_missing_colour_management_warns_and_still_writes_the_photo(self):
        script = ROOT / "tools" / "wallpaper_prep.py"
        # ImageCms can import successfully without its native core and defer ImportError
        # until profile parsing, so exercise both missing-module shapes in fresh processes.
        for missing in ("PIL.ImageCms", "PIL._imagingcms"):
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as tmp:
                source, output = Path(tmp, "photos"), Path(tmp, "out")
                source.mkdir()
                photo = source / "a.png"
                Image.new("RGB", (64, 36), (220, 40, 20)).save(
                    photo, icc_profile=swapped_primaries_profile())
                original = photo.read_bytes()
                code = ("import runpy,sys; missing,tool=sys.argv[1:3]; "
                        "sys.modules[missing]=None; sys.argv=sys.argv[2:]; "
                        "runpy.run_path(tool,run_name='__main__')")
                result = subprocess.run([sys.executable, "-c", code, missing, str(script), str(source),
                                         str(output), "--size", "64x36", "--brightness", "1", "--gradient", "0"],
                                        capture_output=True, text=True, timeout=30)
                self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
                self.assertIn("1 of 1 pictures written", result.stdout)
                self.assertIn("warning:", result.stderr)
                self.assertIn("LittleCMS", result.stderr)
                with Image.open(output / "01.jpg") as image:
                    red, green, blue = image.getpixel((32, 18))
                self.assertGreater(red, 200)
                self.assertLess(blue, 30)
                self.assertEqual(photo.read_bytes(), original)

    def test_embedded_rgb_profiles_are_applied_before_darkening(self):
        for mode, suffix in (("RGB", ".png"), ("RGBA", ".png"), ("P", ".png"), ("RGB", ".jpg")):
            with self.subTest(mode=mode, suffix=suffix), tempfile.TemporaryDirectory() as tmp:
                source, output = Path(tmp, "photo" + suffix), Path(tmp, "wallpaper.jpg")
                photo = Image.new("RGB", (64, 36), (220, 40, 20))
                # Keep the exact sample in palette mode, rather than quantizing to the web palette.
                photo = photo.quantize(colors=1) if mode == "P" else photo.convert(mode)
                photo.save(source, icc_profile=swapped_primaries_profile())
                original = source.read_bytes()
                prep.prepare(str(source), str(output), (64, 36), 0.5, 0, 95, 300)
                with Image.open(output) as image:
                    red, green, blue = image.getpixel((32, 18))
                    self.assertEqual((image.format, image.size), ("JPEG", (64, 36)))
                # The profile swaps red/blue; brightness then halves those sRGB values.
                for actual, expected in zip((red, green, blue), (10, 20, 110)):
                    self.assertAlmostEqual(actual, expected, delta=3)
                self.assertEqual(source.read_bytes(), original)

    def test_profile_conversion_keeps_the_exif_rotation(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, output = Path(tmp, "photo.png"), Path(tmp, "wallpaper.jpg")
            image = Image.new("RGB", (40, 60), (220, 40, 20))
            image.paste((20, 220, 40), (0, 30, 40, 60))
            exif = image.getexif()
            exif[274] = 6  # 90 degrees clockwise: green on the left, profiled blue on the right.
            image.save(source, exif=exif, icc_profile=swapped_primaries_profile())
            prep.prepare(str(source), str(output), (60, 40), 1, 0, 95, 300)
            with Image.open(output) as result:
                left, right = result.getpixel((10, 20)), result.getpixel((50, 20))
            self.assertGreater(left[1], 200)
            self.assertGreater(right[2], 200)
            self.assertLess(right[0], 30)

    def test_a_bad_profile_preserves_an_earlier_wallpaper_and_does_not_stop_other_photos(self):
        with tempfile.TemporaryDirectory() as tmp:
            source, output = Path(tmp, "photos"), Path(tmp, "out")
            source.mkdir()
            output.mkdir()
            make_photo(source / "b.png")
            Image.new("RGB", (64, 36), "red").save(source / "a.png", icc_profile=b"not an ICC profile")
            old = output / "01.jpg"
            old.write_bytes(b"previous wallpaper")
            log = io.StringIO()
            with contextlib.redirect_stdout(log):
                self.assertEqual(prep.main([str(source), str(output), "--size", "64x36"]), 0)
            self.assertEqual(old.read_bytes(), b"previous wallpaper")
            with Image.open(output / "02.jpg") as result:
                self.assertEqual(result.size, (64, 36))
            self.assertIn("skipped a.png:", log.getvalue())
            self.assertIn("1 of 2 pictures written", log.getvalue())
            self.assertEqual(sorted(p.name for p in output.iterdir()), ["01.jpg", "02.jpg"])


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

    def test_a_picture_just_over_the_limit_is_reported_at_its_real_size(self):
        # prepare() rounds the size it returns, so 40.23 KB came back as 40: the over-limit note compared
        # that rounded number and said nothing, and a wallpaper over its budget printed as exactly at it -
        # the one thing themes/README.md's "under 300 KB" is about. The file's own size is what is judged.
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "photos"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")

            def write_over(path, out_path, *rest):
                Path(out_path).write_bytes(b"x" * 41_200)  # 40.23 KB, over a 40 KB limit
                return 40, 48  # what round() of that size says

            output = io.StringIO()
            with mock.patch.object(prep, "prepare", write_over), contextlib.redirect_stdout(output):
                self.assertEqual(prep.main([str(src), str(out), "--max-kb", "40"]), 0)
            self.assertIn("40.2 KB, quality 48  (over 40 KB even at quality 48)", output.getvalue())

    def test_a_picture_under_the_limit_keeps_the_plain_size_and_no_note(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "photos"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")

            def write_under(path, out_path, *rest):
                Path(out_path).write_bytes(b"x" * 20_480)  # 20 KB
                return 20, 84

            output = io.StringIO()
            with mock.patch.object(prep, "prepare", write_under), contextlib.redirect_stdout(output):
                self.assertEqual(prep.main([str(src), str(out), "--max-kb", "40"]), 0)
            self.assertIn("20 KB, quality 84\n", output.getvalue())
            self.assertNotIn("over 40 KB", output.getvalue())

    def test_a_limit_no_picture_can_meet_is_refused_before_anything_is_read(self):
        # Every picture is written over a limit of 0 or less and the run still exited 0, so `--max-kb 0` -
        # what a script meaning "no limit" passes - read as a clean run whose pictures are all over budget.
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "photos"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")
            for limit in ("0", "-40"):
                with self.subTest(limit=limit):
                    err = io.StringIO()
                    with contextlib.redirect_stderr(err), self.assertRaises(SystemExit) as caught:
                        prep.main([str(src), str(out), "--max-kb", limit])
                    self.assertEqual(caught.exception.code, 2)
                    self.assertIn("--max-kb must be at least 1 KB", err.getvalue())
                    self.assertFalse(out.exists(), "nothing is created for a limit that is refused")

    def test_originals_are_never_overwritten_and_target_must_differ(self):
        with tempfile.TemporaryDirectory() as d:
            make_photo(Path(d, "a.png"))
            with self.assertRaises(SystemExit):
                prep.main([d, d])

    def test_a_target_that_is_a_file_is_refused_in_words(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "photos")
            src.mkdir()
            make_photo(src / "01.jpg", size=(320, 240))
            target = Path(d, "out.txt")
            target.write_text("keep")
            with self.assertRaises(SystemExit) as caught, contextlib.redirect_stderr(io.StringIO()) as err:
                prep.main([str(src), str(target)])
            self.assertEqual(caught.exception.code, 2)
            self.assertIn("is a file, not a folder", err.getvalue())
            self.assertEqual(target.read_text(), "keep")

    def test_a_target_under_a_file_is_named_not_a_traceback(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "photos")
            src.mkdir()
            make_photo(src / "01.jpg", size=(320, 240))
            Path(d, "out.txt").write_text("keep")
            with self.assertRaises(SystemExit) as caught:
                prep.main([str(src), str(Path(d, "out.txt", "sub"))])
            self.assertIn("cannot create the folder", str(caught.exception.code))
            self.assertIn("Nothing was written", str(caught.exception.code))

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

    def test_case_aliased_descendants_are_refused_without_changing_the_source_tree(self):
        with tempfile.TemporaryDirectory() as d:
            src, alias = Path(d, "photos"), Path(d, "PHOTOS")
            src.mkdir()
            if not alias.is_dir():
                self.skipTest("this filesystem is case-sensitive")
            make_photo(src / "a.png", size=(32, 18))

            def snapshot():
                return {str(p.relative_to(src)): None if p.is_dir() else p.read_bytes()
                        for p in src.rglob("*")}

            for reversed_case in (False, True):
                for existing in (False, True):
                    with self.subTest(reversed_case=reversed_case, existing=existing):
                        source, target_root = (alias, src) if reversed_case else (src, alias)
                        target = target_root / f"album-{reversed_case}-{existing}" / "output"
                        if existing:
                            target.mkdir(parents=True)
                            make_photo(target / "01.jpg", size=(32, 18), colour=(10, 20, 30))
                        before = snapshot()
                        with mock.patch.object(prep, "prepare", wraps=prep.prepare) as convert, \
                                contextlib.redirect_stderr(io.StringIO()), \
                                contextlib.redirect_stdout(io.StringIO()), \
                                self.assertRaises(SystemExit) as caught:
                            prep.main([str(source), str(target), "--size", "32x18"])
                        self.assertEqual(caught.exception.code, 2)
                        convert.assert_not_called()
                        self.assertEqual(snapshot(), before)

    def test_a_separate_prefix_sibling_or_missing_external_tree_still_converts(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "photos")
            src.mkdir()
            make_photo(src / "a.png", size=(32, 18))
            original = (src / "a.png").read_bytes()
            for target in (Path(d, "PHOTOS-output"), Path(d, "elsewhere", "nested", "output")):
                with self.subTest(target=target):
                    with contextlib.redirect_stdout(io.StringIO()):
                        self.assertEqual(prep.main([str(src), str(target), "--size", "32x18"]), 0)
                    with Image.open(target / "01.jpg") as image:
                        self.assertEqual((image.format, image.size), ("JPEG", (32, 18)))
                    self.assertEqual((src / "a.png").read_bytes(), original)

    def test_main_numbers_the_outputs_and_skips_a_corrupt_file(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "src"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")
            (src / "b.jpg").write_bytes(b"not a picture")
            make_photo(src / "c.png", colour=(10, 20, 30))
            self.assertEqual(prep.main([str(src), str(out)]), 0)
            self.assertEqual(sorted(os.listdir(out)), ["01.jpg", "03.jpg"])

    def test_a_photo_the_console_cannot_encode_is_printed_with_replacement_characters(self):
        with tempfile.TemporaryDirectory() as d:
            src, out = Path(d, "src"), Path(d, "out")
            src.mkdir()
            make_photo(src / "a.png")
            make_photo(src / "b-\u5199\u771f.png")
            raw = io.BytesIO()
            console = io.TextIOWrapper(raw, encoding="cp1252", newline="")
            real_stdout = sys.stdout
            self.addCleanup(setattr, sys, "stdout", real_stdout)
            sys.stdout = console
            self.assertEqual(prep.main([str(src), str(out), "--size", "32x18"]), 0)
            console.flush()
            self.assertEqual(sorted(os.listdir(out)), ["01.jpg", "02.jpg"])
            self.assertIn("02.jpg  <- b-??.png", raw.getvalue().decode("cp1252"))

    def test_empty_folder_exits_with_a_message(self):
        with tempfile.TemporaryDirectory() as d:
            src = Path(d, "src")
            src.mkdir()
            with self.assertRaises(SystemExit):
                prep.main([str(src), str(Path(d, "out"))])


if __name__ == "__main__":
    unittest.main()

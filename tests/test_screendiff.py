"""tools/screendiff.py: "no visible change" has to mean no pixel changed outside the masked regions.

A performance PR claims Home looks the same before and after. The comparison must leave out only
what the person masked (the clock moves on), count every other pixel that changed, and say where;
the picture it writes must hide what the person blurred (the Wi-Fi name) in both screenshots. Box
arguments are checked before anything is read. The image tests need Pillow, which CI does not
install (it is optional, in tools/requirements-perf.txt), so they skip without it.

`--out` and `--diff` are judged the same way, before the screenshots are opened and before Pillow is
imported: exit status 1 is the page's word for "the two screenshots differ", so a folder typed wrong, a
folder given in place of a file name, a file in the middle of the path and a name with no extension are
all exit 2 with the path named. A write that fails once it has started - Pillow answers that with OSError,
and an extension it does not know with ValueError - is also named, not left as a traceback with exit 1.

The same rule covers the screenshots themselves: `Image.open()` reads the header only, so a picture cut
short - a `tv.py --screenshot` interrupted, a file pulled before the write finished - opened fine and
failed at the first pixel, inside the comparison: an OSError traceback with exit 1, the status the page
reads as "N pixels differ, find out why". Both files are decoded in full before anything is compared now,
and one that cannot be read is named as the before or the after screenshot, exit 2, nothing compared.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import argparse
import base64
import contextlib
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
spec = importlib.util.spec_from_file_location("cyt_screendiff", ROOT / "tools" / "screendiff.py")
screendiff = importlib.util.module_from_spec(spec)
spec.loader.exec_module(screendiff)
HAVE_PIL = importlib.util.find_spec("PIL") is not None


class BoxTest(unittest.TestCase):
    def test_a_box_is_four_ordered_numbers(self):
        self.assertEqual(screendiff.box("90,40,900,220"), (90, 40, 900, 220))
        for bad in ("1,2,3", "a,b,c,d", "10,10,5,20", "10,10,20,10"):
            with self.subTest(bad=bad), self.assertRaises(argparse.ArgumentTypeError):
                screendiff.box(bad)

    def test_bad_options_are_usage_errors(self):
        for argv in (["a", "b", "--scale", "2"], ["a", "b", "--labels", "one"], ["a", "b", "--threshold", "300"]):
            with self.subTest(argv=argv), contextlib.redirect_stderr(io.StringIO()), \
                    self.assertRaises(SystemExit) as caught:
                screendiff.parse(argv)
            self.assertEqual(caught.exception.code, 2)


@unittest.skipUnless(HAVE_PIL, "needs Pillow (tools/requirements-perf.txt)")
class CompareTest(unittest.TestCase):
    def setUp(self):
        from PIL import Image, ImageDraw
        self.Image, self.ImageDraw = Image, ImageDraw
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)

    def shot(self, name, clock="12:00", tile=(30, 30, 30), noise=0):
        """A 200x100 'Home': a clock at the top left, a tile at the bottom right."""
        im = self.Image.new("RGB", (200, 100), (10 + noise, 10, 10))
        draw = self.ImageDraw.Draw(im)
        draw.text((5, 5), clock, fill="white")
        draw.rectangle([150, 60, 190, 90], fill=tile)
        path = os.path.join(self.tmp.name, name)
        im.save(path)
        return path

    def run_tool(self, argv):
        with contextlib.redirect_stdout(io.StringIO()) as out, contextlib.redirect_stderr(io.StringIO()) as err:
            code = screendiff.main(argv)
        return code, out.getvalue() + err.getvalue()

    def test_a_masked_clock_is_left_out(self):
        a, b = self.shot("a.png", "12:00"), self.shot("b.png", "12:01")
        self.assertEqual(self.run_tool([a, b])[0], 1)
        code, text = self.run_tool([a, b, "--mask", "0,0,60,20"])
        self.assertEqual(code, 0)
        self.assertIn("identical (1 region masked)", text)

    def test_a_change_outside_the_mask_is_found_and_located(self):
        a, b = self.shot("a.png"), self.shot("b.png", tile=(200, 30, 30))
        code, text = self.run_tool([a, b, "--mask", "0,0,60,20"])
        self.assertEqual(code, 1)
        self.assertIn("inside 150,60,191,91", text)

    def test_a_masked_only_change_writes_an_empty_diff(self):
        a, b = self.shot("a.png", "12:00"), self.shot("b.png", "12:01")
        out = os.path.join(self.tmp.name, "diff.png")
        for masks in (["--mask", "0,0,60,20"], ["--mask", "0,0,60,20", "--mask", "0,0,60,20"]):
            with self.subTest(masks=masks):
                code, text = self.run_tool([a, b, *masks, "--diff", out])
                self.assertEqual(code, 0)
                self.assertIn("identical", text)
                with self.Image.open(out) as diff:
                    self.assertEqual(set(diff.convert("RGB").getdata()), {(0, 0, 0)})

    def test_diff_pixels_match_the_report_in_every_channel_and_at_the_threshold(self):
        before = self.Image.new("RGB", (9, 4), "black")
        after = before.copy()
        for point, colour in (((1, 1), (255, 0, 0)), ((3, 1), (255, 255, 255)),
                              ((4, 1), (9, 0, 0)), ((5, 1), (0, 9, 0)), ((6, 1), (0, 0, 9)),
                              ((7, 1), (8, 8, 8)), ((8, 1), (7, 0, 0))):
            after.putpixel(point, colour)
        a, b, out = [os.path.join(self.tmp.name, name) for name in ("a.png", "b.png", "diff.png")]
        before.save(a)
        after.save(b)
        code, text = self.run_tool([a, b, "--mask", "0,0,4,4", "--threshold", "8", "--diff", out])
        self.assertEqual(code, 1)
        self.assertIn("3 pixels differ by more than 8, inside 4,1,7,2", text)
        with self.Image.open(out) as exported:
            diff = exported.convert("RGB")
        self.assertEqual(set(diff.getdata()), {(0, 0, 0), (255, 255, 255)})
        self.assertEqual(diff.getbbox(), (4, 1, 7, 2))
        self.assertEqual(sum(pixel != (0, 0, 0) for pixel in diff.getdata()), 3)
        for point in ((4, 1), (5, 1), (6, 1)):
            self.assertEqual(diff.getpixel(point), (255, 255, 255))

    def test_compare_keeps_its_three_value_result_and_does_not_change_the_inputs(self):
        before = self.Image.new("RGB", (8, 4), "black")
        after = before.copy()
        after.putpixel((1, 1), (255, 0, 0))
        after.putpixel((5, 1), (0, 9, 0))
        original = before.tobytes(), after.tobytes()
        self.assertEqual(screendiff.compare(before, after, [(0, 0, 4, 4)], 8), (1, (5, 1, 6, 2), 9))
        self.assertEqual((before.tobytes(), after.tobytes()), original)

    def test_compression_noise_under_the_threshold_is_ignored(self):
        a, b = self.shot("a.png"), self.shot("b.png", noise=3)
        self.assertEqual(self.run_tool([a, b])[0], 0)
        self.assertEqual(self.run_tool([a, b, "--threshold", "1"])[0], 1)

    def test_size_mismatch_and_boxes_outside_the_picture_are_errors(self):
        a = self.shot("a.png")
        small = os.path.join(self.tmp.name, "small.png")
        self.Image.new("RGB", (100, 50)).save(small)
        self.assertEqual(self.run_tool([a, small])[0], 2)
        self.assertEqual(self.run_tool([a, a, "--blur", "0,0,300,10"])[0], 2)

    def test_the_pair_blurs_the_same_region_in_both(self):
        a, b = self.shot("a.png", "SECRET"), self.shot("b.png", "SECRET")
        out = os.path.join(self.tmp.name, "pair.png")
        self.run_tool([a, b, "--blur", "0,0,60,20", "--out", out, "--scale", "1"])
        pair = self.Image.open(out).convert("RGB")
        self.assertEqual(pair.size, (430, 160))
        original = self.Image.open(a).convert("RGB").crop((0, 0, 60, 20))
        for left in (0, 230):
            blurred = pair.crop((left, 60, left + 60, 80))
            self.assertNotEqual(list(blurred.getdata()), list(original.getdata()))

    def test_a_run_with_both_pictures_still_writes_both(self):
        a, b = self.shot("a.png"), self.shot("b.png")
        out = os.path.join(self.tmp.name, "pair.png")
        diff = os.path.join(self.tmp.name, "diff.png")
        code, text = self.run_tool([a, b, "--out", out, "--diff", diff])
        self.assertEqual(code, 0)
        self.assertIn(f"{out}: written", text)
        self.assertTrue(os.path.isfile(out) and os.path.isfile(diff))

    def test_output_control_distinct_files_can_be_created_or_overwritten(self):
        for different in (False, True):
            for existing in (False, True):
                with self.subTest(different=different, existing=existing):
                    a, b = self.shot("a.png"), self.shot("b.png", noise=20 if different else 0)
                    originals = {p: Path(p).read_bytes() for p in (a, b)}
                    out, diff = [Path(self.tmp.name) / name for name in ("pair.png", "diff.png")]
                    for path in (out, diff):
                        path.unlink(missing_ok=True)
                        if existing:
                            self.Image.new("RGB", (3, 3), "red").save(path)
                    code, _ = self.run_tool([a, b, "--out", str(out), "--diff", str(diff)])
                    self.assertEqual(code, int(different))
                    self.assertEqual({p: Path(p).read_bytes() for p in originals}, originals)
                    with self.Image.open(out) as pair:
                        self.assertEqual(pair.size, (230, 110))
                    with self.Image.open(diff) as mask:
                        self.assertEqual(mask.size, (200, 100))
                        self.assertEqual(mask.mode, "L")
                        self.assertEqual(mask.getbbox() is not None, different)

    def test_output_control_the_same_input_can_be_compared_with_itself(self):
        a = self.shot("a.png")
        original = Path(a).read_bytes()
        out, diff = [os.path.join(self.tmp.name, name) for name in ("pair.png", "diff.png")]
        code, text = self.run_tool([a, a, "--out", out, "--diff", diff])
        self.assertEqual(code, 0)
        self.assertIn("identical", text)
        self.assertEqual(Path(a).read_bytes(), original)
        with self.Image.open(out) as pair:
            self.assertEqual(pair.size, (230, 110))
        with self.Image.open(diff) as mask:
            self.assertIsNone(mask.getbbox())

    def test_a_write_that_cannot_start_is_named_not_a_difference(self):
        if not os.path.exists("/dev/full"):
            self.skipTest("needs /dev/full, a file whose writes fail with ENOSPC")
        a, b = self.shot("a.png"), self.shot("b.png")
        out = os.path.join(self.tmp.name, "full.png")
        os.symlink("/dev/full", out)
        code, text = self.run_tool([a, b, "--out", out])
        self.assertEqual(code, 2, "a failed write must not read as 'the screenshots differ'")
        self.assertIn(f"cannot write {out}", text)
        self.assertIn("No space left on device", text)
        self.assertNotIn("Traceback", text)
        self.assertNotIn("identical", text)
        self.assertNotIn("pixels differ", text)

    def test_an_extension_pillow_does_not_know_is_named(self):
        a, b = self.shot("a.png"), self.shot("b.png")
        out = os.path.join(self.tmp.name, "pair.txt")
        code, text = self.run_tool([a, b, "--out", out])
        self.assertEqual(code, 2)
        self.assertIn(f"cannot write {out}", text)
        self.assertIn("unknown file extension", text)
        self.assertNotIn("Traceback", text)
        self.assertFalse(os.path.exists(out))

    def cut_short(self, name, part=3):
        """A real screenshot with its bytes cut off: the header opens, the pixels cannot be read."""
        full = self.shot(name)
        return self.cut_short_bytes(full, "cut-" + name, part)

    def cut_short_bytes(self, full, name, part=3):
        data = Path(full).read_bytes()
        short = os.path.join(self.tmp.name, name)
        Path(short).write_bytes(data[:max(32, len(data) // part)])
        return short

    def test_a_cut_short_screenshot_is_unreadable_not_a_difference(self):
        """A --screenshot interrupted, or a file pulled before the write finished, is not a finding."""
        good = self.shot("good.png")
        jpeg = os.path.join(self.tmp.name, "sound.jpg")
        self.Image.new("RGB", (200, 100), (10, 10, 10)).save(jpeg, quality=80)
        pairs = ((self.cut_short("cut.png"), good, "before"),
                 (self.cut_short("cut2.png"), good, "after"),
                 (self.cut_short_bytes(jpeg, "cut-sound.jpg", 2), good, "after"))
        for bad, other, which in pairs:
            with self.subTest(file=bad, which=which):
                before, after = (bad, other) if which == "before" else (other, bad)
                with contextlib.redirect_stdout(io.StringIO()) as out, \
                        contextlib.redirect_stderr(io.StringIO()) as err:
                    code = screendiff.main([before, after])
                self.assertEqual(code, 2, f"exit 1 reads as 'the screenshots differ': {err.getvalue()}")
                self.assertIn(f"cannot read the {which} screenshot {bad}", err.getvalue())
                self.assertIn("Nothing was compared", err.getvalue())
                self.assertNotIn("Traceback", err.getvalue())
                self.assertEqual(out.getvalue(), "")

    def test_a_pair_that_reads_is_still_compared(self):
        """The decode is not allowed to swallow the comparison it guards."""
        a, b = self.shot("a.png", "12:00"), self.shot("b.png", "12:01")
        self.assertEqual(self.run_tool([a, b])[0], 1)
        self.assertEqual(self.run_tool([a, b, "--mask", "0,0,60,20"])[0], 0)


class OutputPathTest(unittest.TestCase):
    """A --out or --diff that cannot be written is exit 2, not the 1 the page reads as 'they differ'."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.dir = Path(self.tmp.name)

    def run_tool(self, argv):
        with contextlib.redirect_stdout(io.StringIO()) as out, contextlib.redirect_stderr(io.StringIO()) as err:
            code = screendiff.main(argv)
        return code, out.getvalue(), err.getvalue()

    def test_a_folder_that_is_not_there_is_named(self):
        out = self.dir / "nope" / "pair.png"
        code, stdout, stderr = self.run_tool(["before.png", "after.png", "--out", str(out)])
        self.assertEqual(code, 2)
        self.assertEqual(stdout, "")
        self.assertIn(f"--out {out}: there is no folder {out.parent}", stderr)
        self.assertIn("Nothing was read or written", stderr)
        self.assertFalse(out.parent.exists())

    def test_the_diff_path_is_checked_the_same_way(self):
        diff = self.dir / "nope" / "diff.png"
        code, stdout, stderr = self.run_tool(["before.png", "after.png", "--diff", str(diff)])
        self.assertEqual(code, 2)
        self.assertEqual(stdout, "")
        self.assertIn(f"--diff {diff}: there is no folder", stderr)

    def test_a_folder_as_the_pair_is_refused(self):
        code, _, stderr = self.run_tool(["before.png", "after.png", "--out", str(self.dir)])
        self.assertEqual(code, 2)
        self.assertIn(f"--out {self.dir} is a folder", stderr)
        self.assertIn("--out shots/before-after.png", stderr)

    def test_a_file_in_the_middle_of_the_path_is_named(self):
        afile = self.dir / "shots"
        afile.write_text("", encoding="utf-8")
        out = afile / "pair.png"
        code, _, stderr = self.run_tool(["before.png", "after.png", "--out", str(out)])
        self.assertEqual(code, 2)
        self.assertIn(f"{afile} is a file, not a folder", stderr)

    def test_a_path_with_no_extension_is_refused(self):
        out = self.dir / "pair"
        code, _, stderr = self.run_tool(["before.png", "after.png", "--out", str(out)])
        self.assertEqual(code, 2)
        self.assertIn(f"--out {out} has no extension", stderr)
        self.assertIn(f"name it {out}.png", stderr)

    def test_the_paths_are_judged_before_the_screenshots_are_opened(self):
        # Neither argument exists, so anything that opened them would say so first.
        code, _, stderr = self.run_tool(["missing-before.png", "missing-after.png",
                                        "--out", str(self.dir / "nope" / "pair.png")])
        self.assertEqual(code, 2)
        self.assertIn("there is no folder", stderr)
        self.assertNotIn("cannot read a screenshot", stderr)

    def test_the_paths_are_judged_before_pillow_is_read(self):
        """CI has no Pillow, and the folder a reader typed wrong is the likelier failure either way."""
        with tempfile.TemporaryDirectory() as shadow:
            blocker = Path(shadow) / "PIL"
            blocker.mkdir()
            (blocker / "__init__.py").write_text('raise ImportError("No module named \'PIL\'")\n',
                                                 encoding="utf-8")
            env = dict(os.environ, PYTHONPATH=shadow)
            out = self.dir / "nope" / "pair.png"
            run = subprocess.run([sys.executable, str(ROOT / "tools" / "screendiff.py"),
                                  "before.png", "after.png", "--out", str(out)],
                                 cwd=ROOT, env=env, capture_output=True, text=True)
        self.assertEqual(run.returncode, 2)
        self.assertIn("there is no folder", run.stderr)
        self.assertNotIn("requirements-perf.txt", run.stderr, "Pillow was reported first")
        self.assertNotIn("Traceback", run.stderr)
        self.assertEqual(run.stdout, "")


class CollisionTest(unittest.TestCase):
    """Unsafe destinations are refused without Pillow, and cannot change even one fixture byte."""

    # Two generated 8x8 RGB PNGs. Keeping the bytes here lets preflight run without optional Pillow.
    PNGS = tuple(base64.b64decode(data) for data in (
        "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFElEQVR4nGPkEpFjwAaYsIoOWgkANVwATIkpP+sAAAAASUVORK5CYII=",
        "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAFElEQVR4nGM8ISLHgA0wYRUdtBIAyQ4BCkxcDfgAAAAASUVORK5CYII=",
    ))

    def files(self, different, existing):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        directory = Path(tmp.name)
        (directory / "before.png").write_bytes(self.PNGS[0])
        (directory / "after.png").write_bytes(self.PNGS[int(different)])
        (directory / "sub").mkdir()
        if existing:
            (directory / "other.png").write_bytes(self.PNGS[0])
        return directory

    def assert_collision(self, directory, argv, *needles):
        names = set(directory.rglob("*"))
        originals = {p: p.read_bytes() for p in names if p.is_file()}
        real_import = __import__

        def no_pillow(name, *args, **kwargs):
            if name == "PIL" or name.startswith("PIL."):
                self.fail("Pillow was imported before refusing the collision")
            return real_import(name, *args, **kwargs)

        for mode in (("preflight", "images") if HAVE_PIL else ("preflight",)):
            with self.subTest(mode=mode):
                try:
                    with contextlib.ExitStack() as stack:
                        if mode == "preflight":
                            stack.enter_context(mock.patch("builtins.__import__", side_effect=no_pillow))
                            read = stack.enter_context(mock.patch.object(
                                screendiff, "screenshot", side_effect=AssertionError("a screenshot was opened")))
                        out = stack.enter_context(contextlib.redirect_stdout(io.StringIO()))
                        err = stack.enter_context(contextlib.redirect_stderr(io.StringIO()))
                        code = screendiff.main(argv)
                        if mode == "preflight":
                            read.assert_not_called()
                    self.assertEqual(code, 2, err.getvalue())
                    for needle in needles:
                        self.assertIn(needle, err.getvalue())
                    self.assertIn("same file", err.getvalue())
                    self.assertIn("Nothing was read or written", err.getvalue())
                    self.assertNotIn("Traceback", err.getvalue())
                    self.assertEqual(out.getvalue(), "")
                finally:
                    self.assertEqual(set(directory.rglob("*")), names, "the run created an output")
                    self.assertEqual({p: p.read_bytes() for p in originals}, originals)

    def input_collisions(self, alias):
        for different in (False, True):
            for existing in (False, True):
                for option in ("--out", "--diff"):
                    for which in ("before", "after"):
                        with self.subTest(different=different, existing=existing, option=option, which=which):
                            directory = self.files(different, existing)
                            target = alias(directory, directory / (which + ".png"))
                            other = "--diff" if option == "--out" else "--out"
                            argv = [str(directory / "before.png"), str(directory / "after.png"),
                                    option, str(target), other, str(directory / "other.png")]
                            self.assert_collision(directory, argv, option, which)

    def output_collisions(self, alias, existing_values=(False, True)):
        for different in (False, True):
            for existing in existing_values:
                with self.subTest(different=different, existing=existing):
                    directory = self.files(different, existing)
                    target = directory / "other.png"
                    argv = [str(directory / "before.png"), str(directory / "after.png"),
                            "--out", str(target), "--diff", str(alias(directory, target))]
                    self.assert_collision(directory, argv, "--out", "--diff")

    def test_direct_collisions(self):
        self.input_collisions(lambda directory, path: path)
        self.output_collisions(lambda directory, path: path)

    def test_relative_and_absolute_collisions(self):
        previous = Path.cwd()
        try:
            os.chdir(tempfile.gettempdir())  # The checkout and temporary files may be on different drives.
            self.input_collisions(lambda directory, path: os.path.relpath(path))
            self.output_collisions(lambda directory, path: os.path.relpath(path))
        finally:
            os.chdir(previous)

    def test_dot_collisions(self):
        alias = lambda directory, path: str(directory) + os.sep + "." + os.sep + path.name
        self.input_collisions(alias)
        self.output_collisions(alias)

    def test_dotdot_collisions(self):
        alias = lambda directory, path: directory / "sub" / ".." / path.name
        self.input_collisions(alias)
        self.output_collisions(alias)

    def test_symlink_collisions(self):
        def alias(directory, path):
            link = directory / "alias.png"
            try:
                link.symlink_to(path.name)
            except (OSError, NotImplementedError) as e:
                self.skipTest(f"this filesystem cannot create a symlink: {e}")
            return link
        self.input_collisions(alias)
        self.output_collisions(alias)  # Includes two aliases for a destination not yet created.

    def test_hardlink_collisions(self):
        def alias(directory, path):
            link = directory / "alias.png"
            try:
                os.link(path, link)
            except (OSError, NotImplementedError) as e:
                self.skipTest(f"this filesystem cannot create a hardlink: {e}")
            return link
        self.input_collisions(alias)
        self.output_collisions(alias, existing_values=(True,))

    @unittest.skipUnless(os.name == "nt", "Windows case-insensitive path spelling")
    def test_windows_case_collisions(self):
        directory = self.files(False, False)
        if not (directory / "BEFORE.PNG").exists():
            self.skipTest("this directory is case-sensitive")
        alias = lambda directory, path: path.with_name(path.name.upper())
        self.input_collisions(alias)
        self.output_collisions(alias)


class DecodeBeforeCompareTest(unittest.TestCase):
    """The pixels are read before the comparison, so an unreadable screenshot is exit 2, not 'different'.

    Pillow is optional (`tools/requirements-perf.txt`) and CI does not install it, so the run that needs a
    cut-short picture is above; this reads the same ordering out of the source.
    """

    SOURCE = (ROOT / "tools" / "screendiff.py").read_text(encoding="utf-8")

    def test_both_screenshots_are_read_before_the_comparison(self):
        read = self.SOURCE.split("count, bbox, worst, diff = _compare(", 1)[0]
        self.assertIn('screenshot("before", args.before), screenshot("after", args.after)', read,
                      "the two screenshots are no longer read before the comparison")

    def test_the_read_is_a_full_decode_inside_the_guard(self):
        body = self.SOURCE.split("def screenshot(", 1)[1].split("\ndef ", 1)[0]
        self.assertIn("image = Image.open(path)", body)
        self.assertIn("image.load()", body,
                      "Image.open() reads the header only; a cut-short file fails at the first pixel")
        self.assertIn("except OSError as e:", body)
        self.assertIn("Nothing was compared", body)

    def test_a_file_it_cannot_read_stops_before_the_comparison(self):
        after_read = self.SOURCE.split("        a, b = screenshot(", 1)[1]
        refusal = after_read.split("\n    if a.size", 1)[0]
        self.assertIn("return 2", refusal,
                      "an unreadable screenshot falls through to the comparison, whose exit 1 reads as "
                      "'the screenshots differ'")


if __name__ == "__main__":
    unittest.main()

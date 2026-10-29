"""tools/screendiff.py: "no visible change" has to mean no pixel changed outside the masked regions.

A performance PR claims Home looks the same before and after. The comparison must leave out only
what the person masked (the clock moves on), count every other pixel that changed, and say where;
the picture it writes must hide what the person blurred (the Wi-Fi name) in both screenshots. Box
arguments are checked before anything is read. The image tests need Pillow, which CI does not
install (it is optional, in tools/requirements-perf.txt), so they skip without it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import argparse
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest

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


if __name__ == "__main__":
    unittest.main()

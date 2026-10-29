"""tools/flamechart.py: what it highlights, which run it draws, and the window it draws.

A flame chart in a pull request is only honest if the magenta total counts each piece of work once
(a highlighted slice's children are inside it, not extra), if a folder of runs is drawn by its
median run rather than its best, and if both panels share one time scale. The trace queries run
against an in-memory SQLite table laid out like a trace, as in test_trace_report.py, so these need
neither Perfetto's query engine nor a TV. Drawing needs Pillow, so that test skips without it.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import os
from pathlib import Path
import re
import sqlite3
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
spec = importlib.util.spec_from_file_location("cyt_flamechart", ROOT / "tools" / "flamechart.py")
flamechart = importlib.util.module_from_spec(spec)
spec.loader.exec_module(flamechart)

PKG = "com.gathin.tvhome"
MS = 1_000_000


def fake_trace():
    """The launcher brought to the front at 1000 ms: performStart, onResume with nested work, a traversal, a frame."""
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript("""
        create table process (upid integer, pid integer, name text);
        create table thread (utid integer, upid integer, tid integer, name text);
        create table thread_track (id integer, utid integer);
        create table slice (id integer primary key, ts integer, dur integer, depth integer, name text,
                            track_id integer, parent_id integer);
        create table actual_frame_timeline_slice (ts integer, dur integer, upid integer, jank_type text);
        insert into process values (2, 100, 'com.gathin.tvhome');
        insert into thread values (1, 2, 100, 'com.gathin.tvhome'), (2, 2, 101, 'RenderThread');
        insert into thread_track values (10, 1), (20, 2);
    """)
    rows = [(1, 1000, 5, 0, "performStart:x", 10, None), (2, 1005, 30, 0, "activityResume", 10, None),
            (3, 1010, 10, 1, "inflate", 10, 2), (4, 1040, 20, 0, "traversal", 10, None),
            (5, 1050, 4, 1, "RV OnBindView", 10, 4), (6, 1055, 10, 0, "DrawFrames 1", 20, None)]
    db.executemany("insert into slice values (?, ?, ?, ?, ?, ?, ?)",
                   [(i, ts * MS, dur * MS, depth, name, track, parent) for i, ts, dur, depth, name, track, parent in rows])
    return lambda sql: [dict(r) for r in db.execute(sql)]


class HighlightTest(unittest.TestCase):
    def test_nested_work_is_counted_once_and_drawn_magenta(self):
        q = fake_trace()
        rows = flamechart.lanes(q, PKG, ["main"], 0, 2000 * MS)[0][1]
        total, hit = flamechart.highlighted(rows, re.compile("^(activityResume|RV OnBindView)$"), 0, 2000 * MS)
        self.assertEqual(total, 34)                 # onResume 30 + binding 4; inflate inside onResume adds nothing
        self.assertEqual(hit, {2, 3, 5})

    def test_only_the_part_inside_the_window_counts(self):
        rows = flamechart.lanes(fake_trace(), PKG, ["main"], 0, 2000 * MS)[0][1]
        total, _ = flamechart.highlighted(rows, re.compile("^activityResume$"), 1020 * MS, 2000 * MS)
        self.assertEqual(total, 15)


class WindowTest(unittest.TestCase):
    def test_first_frame_window_spans_start_to_frame_drawn(self):
        t0, t1 = flamechart.window(fake_trace(), PKG, "first-frame", nav=False)
        self.assertEqual((t0, t1), (1000 * MS - 500_000, 1065 * MS + 500_000))

    def test_start_window_has_a_fixed_length(self):
        t0, t1 = flamechart.window(fake_trace(), PKG, "start:1500", nav=False)
        self.assertEqual(t1 - (t0 + 500_000), 1500 * MS)

    def test_all_and_nav_take_the_whole_process(self):
        self.assertEqual(flamechart.window(fake_trace(), PKG, "all", nav=False), (1000 * MS, 1065 * MS))
        self.assertEqual(flamechart.window(fake_trace(), PKG, "first-frame", nav=True), (1000 * MS, 1065 * MS))

    def test_a_trace_without_the_launcher_is_refused(self):
        with self.assertRaises(ValueError):
            flamechart.window(fake_trace(), "com.example.other", "first-frame", nav=False)

    def test_lanes_by_name_and_a_missing_thread_stays_empty(self):
        names = [label for label, _ in flamechart.lanes(fake_trace(), PKG, ["main", "RenderThread", "nope"], 0, 2000 * MS)]
        self.assertEqual(names, ["main thread", "RenderThread", "nope"])


class MedianRunTest(unittest.TestCase):
    def median(self, runs):
        """median_run over runs = {name: metrics}, with trace_report.measure answering from the dict."""
        measure = lambda paths, package, open_trace: [(p, runs[p]) for p in paths]
        with mock.patch.object(flamechart.trace_report, "measure", measure):
            return flamechart.median_run(list(runs), PKG, None)

    def test_the_median_run_is_drawn_not_the_best(self):
        runs = {k: {"first frame": v} for k, v in {"a": 90, "b": 300, "c": 80, "d": 95, "e": 85}.items()}
        self.assertEqual(self.median(runs), ("a", "time to first frame"))
        del runs["d"]
        self.assertEqual(self.median(runs)[0], "e")      # 80 85 90 300: the lower middle

    def test_nav_without_frame_timeline_uses_draw_time(self):
        # #951's report: older Android has no FrameTimeline, so #953 leaves frame time out; the first file
        # used to be drawn and called the median.
        draws = {"nav-1": 300, "nav-2": 90, "nav-3": 80, "nav-4": 95, "nav-5": 85}
        runs = {k: {"frames": 20, "draw median": v} for k, v in draws.items()}
        self.assertEqual(self.median(runs), ("nav-2", "median RenderThread draw"))

    def test_a_measure_only_some_runs_have_is_not_mixed_with_another(self):
        runs = {"a": {"frame median": 30, "draw median": 9}, "b": {"draw median": 7},
                "c": {"frame median": 10, "draw median": 8}}
        self.assertEqual(self.median(runs), ("c", "median RenderThread draw"))

    def test_a_set_without_a_shared_measure_is_refused(self):
        with self.assertRaises(ValueError):
            self.median({"a": {"frames": 3}, "b": {"frames": 4}})

    def test_one_trace_is_drawn_as_it_is(self):
        self.assertEqual(flamechart.median_run(["only"], PKG, None), ("only", None))


class ArgumentTest(unittest.TestCase):
    def test_bad_window_regex_and_threads_are_refused(self):
        for argv in (["a", "--out", "x.png", "--window", "soon"], ["a", "--out", "x.png", "--highlight", "("],
                     ["a", "--out", "x.png", "--threads", "main;rm"]):
            with self.subTest(argv=argv), self.assertRaises(SystemExit):
                with open(os.devnull, "w") as null, mock.patch("sys.stderr", null):
                    flamechart.parse(argv)

    def parse_error(self, argv):
        with contextlib.redirect_stderr(io.StringIO()) as err, self.assertRaises(SystemExit) as caught:
            flamechart.parse(argv)
        self.assertEqual(caught.exception.code, 2)
        return err.getvalue()

    def test_an_out_that_cannot_be_written_is_refused_before_the_traces_are_opened(self):
        # im.save() threw from draw(), after Perfetto's query engine had started and every trace had been
        # read, so a typo in --out read as a chart that failed.
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "charts"
            folder.mkdir()
            for out, expected in ((str(folder), "is a folder"),
                                  (str(folder / "chart"), "has no extension"),
                                  (str(Path(tmp) / "nothere" / "chart.png"), "there is no folder")):
                with self.subTest(out=out):
                    self.assertIn(expected, self.parse_error(["a", "--out", out]))
            through = Path(tmp) / "file.png"
            through.write_text("not a folder")
            self.assertIn("is a file, not a folder", self.parse_error(["a", "--out", str(through / "chart.png")]))
            self.assertIn("--out is empty", self.parse_error(["a", "--out", ""]))

    def test_a_png_in_a_folder_that_is_there_is_still_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "chart.png")
            self.assertEqual(flamechart.parse(["a", "b", "--out", out]).out, out)
            self.assertEqual(flamechart.parse(["a", "--out", "chart.png"]).out, "chart.png")   # the checkout


class OutputArgumentTest(unittest.TestCase):
    def assert_rejected_before_trace_work(self, argv, out, flag):
        with (mock.patch.dict(sys.modules, {"PIL": None, "perfetto": None}),
              mock.patch.object(flamechart.trace_report, "opener") as opener,
              mock.patch.object(flamechart.trace_report, "expand") as expand,
              mock.patch("builtins.__import__", wraps=__import__) as imports,
              contextlib.redirect_stderr(io.StringIO()) as err,
              self.assertRaises(SystemExit) as caught):
            flamechart.main(argv)
        self.assertEqual(caught.exception.code, 2)
        self.assertIn(flag, err.getvalue().splitlines()[-1])
        self.assertFalse(out.exists())
        opener.assert_not_called()
        expand.assert_not_called()
        self.assertFalse(any(call.args[0].split(".")[0] in ("PIL", "perfetto")
                             for call in imports.call_args_list))

    def test_non_png_output_is_rejected_before_trace_work(self):
        with tempfile.TemporaryDirectory() as tmp:
            for suffix in (".txt", ".svg", ".jpg"):
                with self.subTest(suffix=suffix):
                    out = Path(tmp, "chart" + suffix)
                    self.assert_rejected_before_trace_work(["nav-1.pftrace", "--out", str(out)], out, "--out")

    def test_unusable_width_is_rejected_before_trace_work(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp, "chart.png")
            for width in (-1, 0, 1, 244, 245):
                with self.subTest(width=width):
                    self.assert_rejected_before_trace_work(
                        ["nav-1.pftrace", "--out", str(out), "--width", str(width)], out, "--width")

    def test_png_paths_and_usable_widths_are_accepted(self):
        with tempfile.TemporaryDirectory() as tmp:
            for suffix in (".png", ".PNG"):
                for width in (None, 246):
                    with self.subTest(suffix=suffix, width=width):
                        out = str(Path(tmp, "chart" + suffix))
                        argv = ["nav-1.pftrace", "--out", out]
                        if width is not None:
                            argv += ["--width", str(width)]
                        args = flamechart.parse(argv)
                        self.assertEqual(args.out, out)
                        self.assertEqual(args.width, 1600 if width is None else width)


@unittest.skipUnless(importlib.util.find_spec("PIL"), "drawing needs Pillow (tools/requirements-perf.txt)")
class DrawTest(unittest.TestCase):
    def test_draws_both_panels_on_one_scale_with_magenta(self):
        q = fake_trace()
        panel = lambda label, t1: {"label": label, "note": "", "t0": 1000 * MS, "t1": t1,
                                   "lanes": flamechart.lanes(q, PKG, ["main", "RenderThread"], 1000 * MS, t1)}
        with tempfile.TemporaryDirectory() as tmp:
            out = os.path.join(tmp, "chart.png")
            size = flamechart.draw([panel("Before", 1065 * MS), panel("After", 1030 * MS)],
                                   re.compile("^activityResume$"), "t", "s", out, width=1000)
            from PIL import Image
            im = Image.open(out).convert("RGB")
            self.assertEqual(im.size, size)
            self.assertIn(flamechart.MAGENTA, set(im.getdata()))

    def test_main_writes_png_at_default_and_minimum_width(self):
        from PIL import Image
        with tempfile.TemporaryDirectory() as tmp:
            trace = Path(tmp, "nav-1.pftrace")
            trace.write_bytes(b"")
            for width in (None, 246):
                with self.subTest(width=width):
                    out = Path(tmp, "chart.PNG" if width is not None else "chart.png")
                    argv = [str(trace), "--out", str(out)]
                    if width is not None:
                        argv += ["--width", str(width)]
                    open_trace = lambda path: (fake_trace(), lambda: None)
                    with mock.patch.object(flamechart.trace_report, "opener", return_value=open_trace), \
                            contextlib.redirect_stdout(io.StringIO()):
                        flamechart.main(argv)
                    with Image.open(out) as im:
                        self.assertEqual(im.format, "PNG")
                        self.assertEqual(im.width, 1600 if width is None else width)
                        im.verify()


if __name__ == "__main__":
    unittest.main()

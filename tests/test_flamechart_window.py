"""The full-process flame chart axis uses the same completed slices as its lanes.

SQLite fixtures exercise the real queries; the main-path check mocks only the trace
opener and renderer. No Perfetto engine or physical TV is used.
"""
import contextlib
import importlib.util
import io
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
SPEC = importlib.util.spec_from_file_location("flamechart_window", ROOT / "tools/flamechart.py")
chart = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(chart)
PKG = "com.gathin.tvhome"
MS = 1_000_000


class WholeProcessWindowTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.row_factory = sqlite3.Row
        self.db.executescript("""
            create table process (upid integer, pid integer, name text);
            create table thread (utid integer, upid integer, tid integer, name text);
            create table thread_track (id integer, utid integer);
            create table slice (ts integer, dur integer, track_id integer, id integer,
                                depth integer, name text, parent_id integer);
            insert into process values (1, 100, 'com.gathin.tvhome');
            insert into thread values (1, 1, 100, 'com.gathin.tvhome'), (2, 1, 101, 'RenderThread');
            insert into thread_track values (10, 1), (20, 2);
            insert into slice (ts,dur,track_id) values (1000000000, 10000000, 10), (1020000000, 5000000, 20);
        """)

    def q(self, sql):
        return [dict(row) for row in self.db.execute(sql)]

    def test_unfinished_and_instant_slices_do_not_stretch_the_axis(self):
        for ts, dur in ((500 * MS, -1), (10_000 * MS, -1), (20_000 * MS, 0)):
            self.db.execute("delete from slice where dur <= 0")
            self.db.execute("insert into slice (ts,dur,track_id) values (?, ?, 10)", (ts, dur))
            for spec, nav in (("all", False), ("first-frame", True)):
                with self.subTest(ts=ts, dur=dur, spec=spec):
                    self.assertEqual(chart.window(self.q, PKG, spec, nav), (1000 * MS, 1025 * MS))

    def test_no_drawable_slices_are_refused(self):
        for dur in (None, -1, 0):
            self.db.execute("delete from slice")
            if dur is not None:
                self.db.execute("insert into slice (ts,dur,track_id) values (?, ?, 10)", (1000 * MS, dur))
            for spec, nav in (("all", False), ("first-frame", True)):
                with self.subTest(dur=dur, spec=spec), self.assertRaises(ValueError):
                    chart.window(self.q, PKG, spec, nav)

    def test_completed_work_on_both_threads_still_sets_the_window(self):
        self.assertEqual(chart.window(self.q, PKG, "all", False), (1000 * MS, 1025 * MS))

    def test_main_closes_an_empty_trace_without_rendering_a_chart(self):
        self.db.execute("delete from slice")
        with tempfile.TemporaryDirectory() as folder:
            trace, output = Path(folder, "nav-1.pftrace"), Path(folder, "chart.png")
            trace.write_bytes(b"")
            close = mock.Mock()
            with mock.patch.dict(sys.modules, {"PIL": mock.MagicMock()}), \
                    mock.patch.object(chart.trace_report, "opener", return_value=lambda _: (self.q, close)), \
                    mock.patch.object(chart, "draw") as draw, \
                    contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
                chart.main([str(trace), "--out", str(output)])
            self.assertIn("no completed slices", str(caught.exception.code))
            close.assert_called_once_with()
            draw.assert_not_called()
            self.assertFalse(output.exists())

"""tools/trace_report.py: the numbers it takes from a trace, and how it reports runs and changes.

The report reads traces with Perfetto's query engine, which CI never downloads. Its SQL is plain
SQLite, though, so these tests run the same queries against a small in-memory database laid out
like a trace (process, thread, thread_track, slice, actual_frame_timeline_slice) and check the
numbers by hand. A test that needs the real engine runs only when the optional perfetto package is
installed and CLEANYOURTV_TRACE names a trace recorded with tools/trace.py.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("cyt_trace_report", ROOT / "tools" / "trace_report.py")
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)

PKG = "com.gathin.tvhome"
MS = 1_000_000


def fake_trace(cold=False, frames=(), draws=(), missed=1, gpu=()):
    """A database shaped like a trace of the launcher coming to the front at t = 1000 ms."""
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.executescript("""
        create table process (upid integer, pid integer, name text);
        create table thread (utid integer, upid integer, tid integer, name text);
        create table thread_track (id integer, utid integer);
        create table slice (id integer primary key, ts integer, dur integer, name text, track_id integer);
        create table actual_frame_timeline_slice (ts integer, dur integer, upid integer, jank_type text);
        insert into process values (1, 90, 'com.gathin.tvhome'), (2, 100, 'com.gathin.tvhome'), (3, 200, 'other');
        insert into thread values (1, 2, 100, 'com.gathin.tvhome'), (2, 2, 101, 'RenderThread'),
                                  (3, 1, 90, 'com.gathin.tvhome');
        insert into thread_track values (10, 1), (20, 2), (30, 3);
    """)
    main = [("performStart:com.gathin.tvhome.MainActivity", 1000, 5), ("activityResume", 1005, 30),
            ("traversal", 1040, 20), ("traversal", 1200, 5)]
    if cold:
        main = [("bindApplication", 900, 40), ("activityStart", 945, 50)] + main
    for name, ts, dur in main:
        db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, ?, 10)", (ts * MS, dur * MS, name))
    db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, 'performStart:old', 30)", (100 * MS, MS))
    for ts, dur in [(1055, 10)] + list(draws):
        db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, 'DrawFrames 1', 20)", (ts * MS, dur * MS))
    for ts, dur in gpu:
        db.execute("insert into slice (ts, dur, name, track_id) values (?, ?, 'waiting for GPU completion 5', 20)",
                   (ts * MS, dur * MS))
    for i, dur in enumerate(frames):
        jank = "App Deadline Missed" if i < missed else "Buffer Stuffing"
        db.execute("insert into actual_frame_timeline_slice values (?, ?, 2, ?)", ((1060 + 20 * i) * MS, dur * MS, jank))
    q = lambda sql: [dict(r) for r in db.execute(sql)]
    q.close = db.close
    return q


class StartMetricsTest(unittest.TestCase):
    def test_warm_return_from_performstart_to_the_first_frame_drawn(self):
        m = report.start_metrics(fake_trace(frames=[16, 16, 16]), PKG)
        self.assertEqual(m["first frame"], 65)        # DrawFrame ends at 1065, after the traversal's 1060
        self.assertEqual(m["onResume"], 30)
        self.assertEqual(m["first traversal"], 20)
        self.assertEqual(m["frames"], 1)
        self.assertEqual(m["missed"], 1)               # buffer stuffing is not a missed deadline
        self.assertNotIn("bindApplication", m)

    def test_cold_start_counts_from_bindapplication(self):
        m = report.start_metrics(fake_trace(cold=True), PKG)
        self.assertEqual(m["first frame"], 165)
        self.assertEqual((m["bindApplication"], m["activityStart"]), (40, 50))

    def test_unfinished_first_draw_does_not_establish_a_completed_first_frame(self):
        for drawn_at in (1055, 1100):
            with self.subTest(drawn_at=drawn_at):
                q = fake_trace(cold=True)
                self.addCleanup(q.close)
                q(f"update slice set ts = {drawn_at * MS}, dur = -1 where name like 'DrawFrame%'")
                m = report.start_metrics(q, PKG)
                self.assertEqual((m["onResume"], m["first traversal"]), (30, 20))
                self.assertNotIn("first frame", m)
                self.assertEqual(m["frames"], 0)
                self.assertEqual(report.start_window(q, PKG), (900 * MS, None))

    def test_unfinished_lifecycle_durations_are_unavailable(self):
        for name, metric in (("bindApplication", "bindApplication"), ("activityStart", "activityStart"),
                             ("performStart%", "onStart"), ("activityResume", "onResume"),
                             ("traversal", "first traversal")):
            with self.subTest(name=name):
                q = fake_trace(cold=True)
                self.addCleanup(q.close)
                q(f"update slice set dur = -1 where name like '{name}'")
                m = report.start_metrics(q, PKG)
                self.assertNotIn(metric, m)
                self.assertTrue(all(value >= 0 for value in m.values()))
                self.assertEqual(report.start_window(q, PKG)[0], 900 * MS)
                if name in ("activityResume", "traversal"):
                    self.assertNotIn("first frame", m)
                    self.assertIsNone(report.start_window(q, PKG)[1])
                else:
                    self.assertEqual(m["first frame"], 165)

    def test_missing_render_thread_or_draw_keeps_the_traversal_fallback(self):
        for missing in ("thread", "draw"):
            with self.subTest(missing=missing):
                q = fake_trace(cold=True)
                self.addCleanup(q.close)
                if missing == "thread":
                    q("delete from thread where name = 'RenderThread'")
                else:
                    q("delete from slice where name like 'DrawFrame%'")
                m = report.start_metrics(q, PKG)
                self.assertEqual(m["first frame"], 160)
                self.assertEqual(report.start_window(q, PKG), (900 * MS, 1060 * MS))

    def test_frames_outside_the_window_are_not_counted(self):
        m = report.start_metrics(fake_trace(draws=[(1500, 8), (2600, 8)]), PKG)
        self.assertEqual(m["frames"], 2)

    def test_without_frame_timeline_keeps_start_metrics_but_not_missed_deadlines(self):
        for other_app_frames in (False, True):
            with self.subTest(other_app_frames=other_app_frames):
                q = fake_trace(draws=[(1100, 8)])
                if other_app_frames:
                    q("insert into actual_frame_timeline_slice values (1060000000, 16000000, 3, 'None')")
                m = report.start_metrics(q, PKG)
                self.assertEqual(m["first frame"], 65)
                self.assertEqual(m["frames"], 2)
                self.assertNotIn("missed", m)

    def test_frame_timeline_can_measure_zero_missed_deadlines(self):
        m = report.start_metrics(fake_trace(frames=[16, 16], missed=0), PKG)
        self.assertEqual(m["missed"], 0)

    def test_unfinished_frame_timeline_does_not_count_as_a_missed_completed_frame(self):
        q = fake_trace(frames=[16, 16], missed=0)
        self.addCleanup(q.close)
        q("update actual_frame_timeline_slice set dur = -1, jank_type = 'App Deadline Missed' "
          "where ts = 1080000000")
        self.assertEqual(report.start_metrics(q, PKG)["missed"], 0)
        q("update actual_frame_timeline_slice set dur = -1")
        self.assertNotIn("missed", report.start_metrics(q, PKG))

    def test_completed_timeline_outside_the_start_window_does_not_make_its_deadlines_available(self):
        for completed_at in (800, 2400, 3000):
            with self.subTest(completed_at=completed_at):
                q = fake_trace(cold=True, frames=[16])
                self.addCleanup(q.close)
                q("update actual_frame_timeline_slice set dur = -1")
                q(f"insert into actual_frame_timeline_slice values ({completed_at * MS}, {16 * MS}, 2, 'None')")
                m = report.start_metrics(q, PKG)
                self.assertNotIn("missed", m)
                self.assertEqual(m["first frame"], 165)

    def test_newest_launcher_process_is_used(self):
        self.assertEqual(report.threads(fake_trace(), PKG), (2, 1, 2))

    def test_a_trace_without_the_launcher_gives_nothing(self):
        self.assertEqual(report.start_metrics(fake_trace(), "com.example.other"), {})


class NavMetricsTest(unittest.TestCase):
    def test_frame_times_draws_gpu_and_missed_deadlines(self):
        q = fake_trace(frames=[10, 12, 14, 16, 40], missed=2, draws=[(1100, 6), (1120, 8)], gpu=[(1101, 2), (1121, 4)])
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 5)
        self.assertEqual(m["frame median"], 14)
        self.assertEqual(m["frame p90"], 40)
        self.assertEqual(m["draw median"], 8)
        self.assertEqual(m["gpu median"], 3)
        self.assertEqual(m["missed"], 2)

    def test_without_frame_timeline_counts_draws_but_not_full_frame_times_or_deadlines(self):
        for other_app_frames in (False, True):
            with self.subTest(other_app_frames=other_app_frames):
                q = fake_trace(draws=[(1100, 6), (1120, 8)], gpu=[(1101, 2), (1121, 4)])
                if other_app_frames:
                    q("insert into actual_frame_timeline_slice values (1060000000, 16000000, 3, 'None')")
                m = report.nav_metrics(q, PKG)
                self.assertEqual(m["frames"], 3)
                self.assertEqual(m["draw median"], 8)
                self.assertEqual(m["gpu median"], 3)
                self.assertNotIn("frame median", m)
                self.assertNotIn("frame p90", m)
                self.assertNotIn("missed", m)

    def test_frame_timeline_can_measure_zero_missed_deadlines(self):
        m = report.nav_metrics(fake_trace(frames=[16, 16], missed=0), PKG)
        self.assertEqual(m["frames"], 2)
        self.assertEqual(m["frame median"], 16)
        self.assertEqual(m["missed"], 0)

    def test_unfinished_draw_and_gpu_wait_do_not_improve_medians(self):
        q = fake_trace(draws=[(1100, 6)], gpu=[(1060, 4), (1100, 2)])
        self.addCleanup(q.close)
        q("update slice set dur = -1 where ts = 1100000000")
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 1)
        self.assertEqual(m["draw median"], 10)
        self.assertEqual(m["gpu median"], 4)

    def test_only_unfinished_draws_and_waits_have_no_measured_durations(self):
        q = fake_trace(gpu=[(1060, 4)])
        self.addCleanup(q.close)
        q("update slice set dur = -1 where track_id = 20")
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 0)
        self.assertNotIn("draw median", m)
        self.assertNotIn("gpu median", m)

    def test_completed_zero_duration_slices_are_kept(self):
        q = fake_trace(frames=[0], missed=0, gpu=[(1060, 4)])
        self.addCleanup(q.close)
        q("update slice set dur = 0 where track_id = 20")
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 1)
        self.assertEqual(m["frame median"], 0)
        self.assertEqual(m["frame p90"], 0)
        self.assertEqual(m["draw median"], 0)
        self.assertEqual(m["gpu median"], 0)

    def test_unfinished_frame_timeline_does_not_improve_frame_times_or_counts(self):
        q = fake_trace(frames=[16, 40], missed=0)
        self.addCleanup(q.close)
        q("update actual_frame_timeline_slice set dur = -1, jank_type = 'App Deadline Missed' "
          "where ts = 1080000000")
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 1)
        self.assertEqual(m["frame median"], 16)
        self.assertEqual(m["frame p90"], 16)
        self.assertEqual(m["missed"], 0)

    def test_only_unfinished_frame_timeline_keeps_the_draw_fallback(self):
        q = fake_trace(frames=[16])
        self.addCleanup(q.close)
        q("update actual_frame_timeline_slice set dur = -1")
        m = report.nav_metrics(q, PKG)
        self.assertEqual(m["frames"], 1)
        self.assertEqual(m["draw median"], 10)
        for metric in ("frame median", "frame p90", "missed"):
            self.assertNotIn(metric, m)


class ReportTest(unittest.TestCase):
    RUNS = [("home-1", {"first frame": 90, "onResume": 40}), ("home-2", {"first frame": 300, "onResume": 290}),
            ("home-3", {"first frame": 80, "onResume": 35})]
    AFTER = [("home-1", {"first frame": 50, "onResume": 2}), ("home-2", {"first frame": 45, "onResume": 3})]

    def test_runs_with_median_range_and_a_starred_outlier(self):
        text = report.report(self.RUNS, None, False, nav=False)
        row = next(l for l in text.splitlines() if l.startswith("start to first frame"))
        self.assertIn("300*", row)
        self.assertIn("90", row.split()[-2])        # median
        self.assertIn("80–300", row)
        self.assertIn("more than twice the median", text)

    def test_comparison_shows_the_change_in_the_median(self):
        text = report.report(self.RUNS, self.AFTER, True, nav=False)
        self.assertIn("| start to first frame drawn (ms) | 90 | 48 | -47% |", text)
        self.assertIn("|---|---|---|---|", text)

    def test_metrics_no_run_has_are_left_out(self):
        text = report.report(self.RUNS, None, False, nav=False)
        self.assertNotIn("bindApplication", text)

    def test_unavailable_missed_deadlines_are_distinct_from_measured_zero(self):
        for nav in (False, True):
            with self.subTest(nav=nav):
                measure = report.nav_metrics if nav else report.start_metrics
                runs = [("without-timeline", measure(fake_trace(), PKG)),
                        ("with-timeline", measure(fake_trace(frames=[16], missed=0), PKG))]
                text = report.report(runs, None, True, nav=nav)
                missed = next(row for row in text.splitlines() if row.startswith("| frames that missed"))
                self.assertEqual(missed.split(" | ")[1:], ["–", "0", "0", "0–0 |"])


class FilesTest(unittest.TestCase):
    def test_folders_expand_in_run_order(self):
        with tempfile.TemporaryDirectory() as tmp:
            for n in (1, 2, 10):
                Path(tmp, f"home-{n}.pftrace").write_bytes(b"")
            self.assertEqual([os.path.basename(p) for p in report.expand([tmp])],
                             ["home-1.pftrace", "home-2.pftrace", "home-10.pftrace"])

    def test_scenario_comes_from_the_sidecar_then_the_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "run-1.pftrace")
            path.with_suffix(".json").write_text('{"scenario": "nav"}')
            self.assertEqual(report.scenario_of(str(path)), "nav")
            self.assertEqual(report.scenario_of(str(Path(tmp, "cold-3.pftrace"))), "cold")

    def test_non_object_sidecars_fall_back_to_the_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "nav-1.pftrace")
            path.write_bytes(b"")
            for text in ("[]", "null", '"nav"', "42"):
                with self.subTest(sidecar=text):
                    path.with_suffix(".json").write_text(text, encoding="utf-8")
                    self.assertEqual(report.scenario_of(str(path)), "nav")

    def test_object_sidecars_preserve_the_scenario_value(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "nav-1.pftrace")
            path.write_bytes(b"")
            for data, expected in (({"scenario": "cold"}, "cold"), ({}, ""),
                                   ({"scenario": ""}, ""), ({"scenario": None}, None)):
                with self.subTest(sidecar=data):
                    path.with_suffix(".json").write_text(json.dumps(data), encoding="utf-8")
                    self.assertEqual(report.scenario_of(str(path)), expected)

    def test_missing_and_malformed_sidecars_fall_back_to_the_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "nav-1.pftrace")
            path.write_bytes(b"")
            self.assertEqual(report.scenario_of(str(path)), "nav")
            path.with_suffix(".json").write_text("{", encoding="utf-8")
            self.assertEqual(report.scenario_of(str(path)), "nav")

    def test_unreadable_sidecars_fall_back_to_the_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "nav-1.pftrace")
            path.write_bytes(b"")
            path.with_suffix(".json").write_text('{"scenario": "cold"}', encoding="utf-8")
            with mock.patch("builtins.open", side_effect=PermissionError("unreadable sidecar")):
                self.assertEqual(report.scenario_of(str(path)), "nav")

    def test_missing_perfetto_says_what_to_install(self):
        with mock.patch.dict(sys.modules, {"perfetto": None, "perfetto.trace_processor": None}), \
                contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as caught:
            report.opener()
        self.assertIn("requirements-perf.txt", str(caught.exception.code))


class MainTest(unittest.TestCase):
    def open_trace(self, path):
        q = fake_trace(frames=[16])
        return q, q.close

    def trace(self, folder, name, scenario=None):
        path = Path(folder, name + ".pftrace")
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"")
        if scenario is not None:
            path.with_suffix(".json").write_text(json.dumps({"scenario": scenario}))
        return str(path)

    def assert_rejected_before_opening_engine(self, argv):
        with mock.patch.object(report, "opener", return_value=self.open_trace) as opener, \
                contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(SystemExit, "nav.*start.*separately"):
                report.main(argv)
            opener.assert_not_called()

    def test_mixed_folder_is_rejected_in_either_input_set(self):
        with tempfile.TemporaryDirectory() as tmp:
            mixed, starts = Path(tmp, "mixed"), Path(tmp, "starts")
            self.trace(mixed, "cold-1")
            self.trace(mixed, "nav-1")
            self.trace(starts, "home-1")
            for argv in ([str(mixed)], [str(starts), "--vs", str(mixed)]):
                with self.subTest(argv=argv):
                    self.assert_rejected_before_opening_engine(argv)

    def test_vs_rejects_nav_against_start_in_both_directions(self):
        with tempfile.TemporaryDirectory() as tmp:
            nav = self.trace(tmp, "nav-1")
            start = self.trace(tmp, "cold-1")
            for before, after in ((nav, start), (start, nav)):
                with self.subTest(before=before):
                    self.assert_rejected_before_opening_engine([before, "--vs", after])

    def test_sidecar_can_make_matching_file_names_incompatible(self):
        with tempfile.TemporaryDirectory() as tmp:
            cold = self.trace(tmp, "cold-1")
            renamed_nav = self.trace(tmp, "cold-2", scenario="nav")
            self.assert_rejected_before_opening_engine([cold, renamed_nav])

    def test_sidecar_can_make_different_file_names_a_valid_nav_set(self):
        with tempfile.TemporaryDirectory() as tmp:
            nav = self.trace(tmp, "nav-1")
            renamed_nav = self.trace(tmp, "cold-2", scenario="nav")
            with mock.patch.object(report, "opener", return_value=self.open_trace), \
                    contextlib.redirect_stdout(io.StringIO()) as out:
                report.main([nav, renamed_nav, "--markdown"])
            self.assertIn("| frame time, median (ms) |", out.getvalue())
            self.assertNotIn("| frames drawn in 1.5 s |", out.getvalue())

    def test_matching_nav_and_start_sets_still_report_and_compare(self):
        for scenarios, label in ((["nav"], "| frame time, median (ms) |"),
                                 (["cold", "home", "coldsys", "custom"], "| frames drawn in 1.5 s |")):
            with self.subTest(scenarios=scenarios), tempfile.TemporaryDirectory() as tmp:
                before, after = Path(tmp, "before"), Path(tmp, "after")
                for folder in (before, after):
                    for name in scenarios:
                        self.trace(folder, name + "-1")
                open_trace = mock.Mock(side_effect=self.open_trace)
                with mock.patch.object(report, "opener", return_value=open_trace) as opener, \
                        contextlib.redirect_stdout(io.StringIO()) as out:
                    report.main([str(before), "--vs", str(after), "--markdown"])
                opener.assert_called_once_with(None)
                self.assertEqual(open_trace.call_count, 2 * len(scenarios))
                self.assertIn(label, out.getvalue())
                self.assertIn("Change in the median:", out.getvalue())


@unittest.skipUnless(os.environ.get("CLEANYOURTV_TRACE") and importlib.util.find_spec("perfetto"),
                     "needs the optional perfetto package and CLEANYOURTV_TRACE=<a trace from tools/trace.py>")
class RealTraceTest(unittest.TestCase):
    def test_the_real_engine_runs_the_same_queries(self):
        path = os.environ["CLEANYOURTV_TRACE"]
        q, close = report.opener()(path)
        try:
            metrics = report.nav_metrics(q, PKG) if report.scenario_of(path) == "nav" else report.start_metrics(q, PKG)
        finally:
            close()
        self.assertIn("frames", metrics)


if __name__ == "__main__":
    unittest.main()

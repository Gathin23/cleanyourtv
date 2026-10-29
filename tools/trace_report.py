#!/usr/bin/env python3
"""The standard launcher numbers from traces recorded with tools/trace.py: per run, median and range.

    tools/trace_report.py traces/before                       one set of runs
    tools/trace_report.py traces/before --vs traces/after     before against after, with the change
    tools/trace_report.py ... --markdown                      tables to paste into a pull request

TRACES are .pftrace files or folders of them. Needs the optional `perfetto` package
(tools/venv/bin/pip install -r tools/requirements-perf.txt). Its first run downloads Perfetto's query
engine; --engine PATH uses a trace_processor_shell you downloaded yourself. Read tools/trace.md first.

For a start (cold, coldsys, home and custom traces) it reports the time from the launcher starting
(bindApplication in a new process, else performStart) to its first frame drawn, the lifecycle steps
inside that, and the frames drawn and deadlines missed in the 1.5 s after. For nav traces it reports
frame times and missed deadlines over the whole recording.
"""
import argparse
import glob
import json
import math
import os
import re
import statistics
import sys

PACKAGE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
WINDOW_NS = 1_500_000_000
MS = 1e6

START_METRICS = [("first frame", "start to first frame drawn (ms)"), ("bindApplication", "bindApplication (ms)"),
                 ("activityStart", "onCreate (activityStart, ms)"), ("onStart", "onStart (performStart, ms)"),
                 ("onResume", "onResume (activityResume, ms)"), ("first traversal", "first layout and draw (ms)"),
                 ("frames", "frames drawn in 1.5 s"), ("missed", "frames that missed their deadline in 1.5 s")]
NAV_METRICS = [("frames", "frames drawn"), ("frame median", "frame time, median (ms)"),
               ("frame p90", "frame time, 90th percentile (ms)"), ("draw median", "RenderThread draw, median (ms)"),
               ("gpu median", "waiting for the GPU, median (ms)"), ("missed", "frames that missed their deadline")]


def one(q, sql, default=None):
    rows = q(sql)
    if not rows:
        return default
    value = next(iter(rows[0].values()))
    return default if value is None else value


def threads(q, package):
    """(upid, main thread utid, RenderThread utid) of the newest process named after the package."""
    upid = one(q, f"select upid from process where name = '{package}' order by upid desc limit 1")
    if upid is None:
        return None, None, None
    main = one(q, f"select utid from thread join process using(upid) where thread.upid = {upid} and thread.tid = process.pid")
    render = one(q, f"select utid from thread where upid = {upid} and name = 'RenderThread' order by utid limit 1")
    return upid, main, render


def slices(utid, where, columns="s.ts, s.dur"):
    return (f"select {columns} from slice s join thread_track tt on s.track_id = tt.id "
            f"where tt.utid = {utid} and {where} order by s.ts")


def start_metrics(q, package):
    """The numbers for a launcher start: cold, coldsys, home or a custom trace that brings Home up."""
    return _start(q, package)[0]


def start_window(q, package):
    """(start, first frame drawn) in ns for a launcher start; either is None when the trace lacks it."""
    return _start(q, package)[1:]


def _start(q, package):
    """(metrics, start ns, first frame drawn ns) for a launcher start."""
    upid, main, render = threads(q, package)
    if main is None:
        return {}, None, None
    bind = q(slices(main, "s.name = 'bindApplication'"))
    start = q(slices(main, "s.name like 'performStart%'"))
    anchor = bind[0]["ts"] if bind else (start[0]["ts"] if start else None)
    if anchor is None:
        return {}, None, None
    end = None
    resume = q(slices(main, f"s.name = 'activityResume' and s.ts >= {anchor}"))
    out = {}
    if bind and bind[0]["dur"] >= 0:
        out["bindApplication"] = bind[0]["dur"] / MS
    created = q(slices(main, f"s.name = 'activityStart' and s.ts >= {anchor}"))
    if created and created[0]["dur"] >= 0:
        out["activityStart"] = created[0]["dur"] / MS
    if start:
        first_start = [s for s in start if s["ts"] >= anchor] or start
        if first_start[0]["dur"] >= 0:
            out["onStart"] = first_start[0]["dur"] / MS
    if resume and resume[0]["dur"] >= 0:
        out["onResume"] = resume[0]["dur"] / MS
        resumed = resume[0]["ts"] + resume[0]["dur"]
        traversal = q(slices(main, f"s.name = 'traversal' and s.ts >= {resumed}"))
        if traversal and traversal[0]["dur"] >= 0:
            t = traversal[0]
            out["first traversal"] = t["dur"] / MS
            end = t["ts"] + t["dur"]
            if render is not None:
                drawn = q(slices(render, f"s.name like 'DrawFrame%' and s.ts >= {t['ts']}"))
                if drawn:
                    # Perfetto uses -1 when a slice has no end; it is not a completion time.
                    end = max(end, drawn[0]["ts"] + drawn[0]["dur"]) if drawn[0]["dur"] >= 0 else None
            if end is not None:
                out["first frame"] = (end - anchor) / MS
    if render is not None:
        out["frames"] = one(q, f"select count(*) from slice s join thread_track tt on s.track_id = tt.id "
                               f"where tt.utid = {render} and s.name like 'DrawFrame%' and s.dur >= 0 "
                               f"and s.ts >= {anchor} and s.ts < {anchor + WINDOW_NS}", 0)
    if one(q, f"select 1 from actual_frame_timeline_slice where upid = {upid} and dur >= 0 "
              f"and ts >= {anchor} and ts < {anchor + WINDOW_NS} limit 1"):
        out["missed"] = one(q, f"select count(*) from actual_frame_timeline_slice where upid = {upid} "
                               f"and dur >= 0 and jank_type like '%App Deadline Missed%' "
                               f"and ts >= {anchor} and ts < {anchor + WINDOW_NS}", 0)
    return out, anchor, end


def nav_metrics(q, package):
    """Frame numbers over a whole nav recording."""
    upid, main, render = threads(q, package)
    if upid is None:
        return {}
    frames = sorted(r["dur"] / MS for r in q(f"select dur from actual_frame_timeline_slice "
                                            f"where upid = {upid} and dur >= 0"))
    out = {}
    if frames:
        out["frames"] = len(frames)
        out["frame median"] = statistics.median(frames)
        out["frame p90"] = frames[math.ceil(0.9 * len(frames)) - 1]
    if render is not None:
        draws = [r["dur"] / MS for r in q(slices(render, "s.name like 'DrawFrame%' and s.dur >= 0", "s.dur"))]
        if not frames:
            out["frames"] = len(draws)
        if draws:
            out["draw median"] = statistics.median(draws)
        waits = [r["dur"] / MS for r in q(f"select s.dur from slice s join thread_track tt on s.track_id = tt.id "
                                           f"join thread t using(utid) where t.upid = {upid} "
                                           f"and s.name like 'waiting for GPU completion%' and s.dur >= 0")]
        if waits:
            out["gpu median"] = statistics.median(waits)
    if frames:
        out["missed"] = one(q, f"select count(*) from actual_frame_timeline_slice where upid = {upid} "
                               f"and dur >= 0 and jank_type like '%App Deadline Missed%'", 0)
    return out


def scenario_of(path):
    """The scenario trace.py wrote next to the trace, or the start of its file name."""
    sidecar = path[:-len(".pftrace")] + ".json" if path.endswith(".pftrace") else None
    if sidecar and os.path.exists(sidecar):
        try:
            with open(sidecar) as f:
                data = json.load(f)
                if isinstance(data, dict):
                    return data.get("scenario", "")
        except (OSError, ValueError):
            pass
    return os.path.basename(path).split("-")[0]


def expand(items):
    paths = []
    for item in items:
        if os.path.isdir(item):
            paths += sorted(glob.glob(os.path.join(item, "*.pftrace")), key=_run_order)
        else:
            paths.append(item)
    return paths


def _run_order(path):
    m = re.search(r"-(\d+)\.pftrace$", path)
    return (re.sub(r"-\d+\.pftrace$", "", path), int(m.group(1)) if m else 0)


def opener(engine=None):
    """open(path) -> q(sql) returning rows as dicts, plus close(), using Perfetto's query engine."""
    try:
        from perfetto.trace_processor import TraceProcessor, TraceProcessorConfig
    except ImportError:
        sys.exit("trace_report.py needs the optional perfetto package: "
                 "tools/venv/bin/pip install -r tools/requirements-perf.txt (see tools/trace.md)")

    def open_trace(path):
        tp = TraceProcessor(trace=path, config=TraceProcessorConfig(bin_path=engine))

        def q(sql):
            result = tp.query(sql)
            names = result.column_names
            return [dict(zip(names, (getattr(row, n) for n in names))) for row in result]
        return q, tp.close
    return open_trace


def measure(paths, package, open_trace):
    """[(name, metrics)] for each trace, in run order."""
    out = []
    for path in paths:
        q, close = open_trace(path)
        try:
            metrics = nav_metrics(q, package) if scenario_of(path) == "nav" else start_metrics(q, package)
        finally:
            close()
        out.append((os.path.basename(path)[:-len(".pftrace")], metrics))
    return out


def summary(values):
    values = [v for v in values if v is not None]
    if not values:
        return None, None, None
    return statistics.median(values), min(values), max(values)


def fmt(value):
    if value is None:
        return "–"
    return f"{value:.0f}" if abs(value) >= 20 or float(value).is_integer() else f"{value:.1f}"


def table(runs, names, markdown):
    """Metric rows, one column per run, then the median and range. A run more than twice the median is starred."""
    head = ["", *[r[0] for r in runs], "median", "range"]
    rows = []
    for key, label in names:
        values = [r[1].get(key) for r in runs]
        if all(v is None for v in values):
            continue
        med, lo, hi = summary(values)
        cells = [fmt(v) + ("*" if v is not None and med and key == "first frame" and v > 2 * med else "") for v in values]
        rows.append([label, *cells, fmt(med), f"{fmt(lo)}–{fmt(hi)}"])
    return render(head, rows, markdown)


def compare(before, after, names, markdown):
    rows = []
    for key, label in names:
        b = summary([r[1].get(key) for r in before])[0]
        a = summary([r[1].get(key) for r in after])[0]
        if b is None and a is None:
            continue
        change = f"{100 * (a - b) / b:+.0f}%" if a is not None and b else "–"
        rows.append([label, fmt(b), fmt(a), change])
    return render(["", "before (median)", "after (median)", "change"], rows, markdown)


def render(head, rows, markdown):
    if markdown:
        lines = ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
        return "\n".join(lines + ["| " + " | ".join(r) + " |" for r in rows])
    widths = [max(len(str(r[i])) for r in [head] + rows) for i in range(len(head))]
    return "\n".join("  ".join(str(c).ljust(w) for c, w in zip(r, widths)).rstrip() for r in [head] + rows)


def report(before, after, markdown, nav):
    """The tables for one set of runs, or for two with the change in the medians."""
    names = NAV_METRICS if nav else START_METRICS
    parts = [("Before" if after else "Runs") + f" ({len(before)}):", table(before, names, markdown)]
    if after:
        parts += ["", f"After ({len(after)}):", table(after, names, markdown), "",
                  "Change in the median:", compare(before, after, names, markdown)]
    if _has_outlier(before) or (after and _has_outlier(after)):
        parts += ["", "* more than twice the median: often memory pressure on the TV (tools/trace.md). "
                      "Report it rather than dropping it."]
    return "\n".join(parts)


def _has_outlier(runs):
    med = summary([m.get("first frame") for _, m in runs])[0]
    return bool(med) and any((m.get("first frame") or 0) > 2 * med for _, m in runs)


def parse(argv):
    p = argparse.ArgumentParser(description="Launcher numbers from tools/trace.py traces. See tools/trace.md.")
    p.add_argument("traces", nargs="+", help=".pftrace files or folders of them")
    p.add_argument("--vs", nargs="+", metavar="TRACES", help="the traces to compare against the first set")
    p.add_argument("--package", default="com.gathin.tvhome", help="the launcher's package (default com.gathin.tvhome)")
    p.add_argument("--engine", metavar="PATH", help="a trace_processor_shell to use instead of downloading one")
    p.add_argument("--markdown", action="store_true", help="print Markdown tables")
    args = p.parse_args(argv)
    if not PACKAGE.match(args.package):
        p.error(f"not a package name: {args.package!r}")
    return args


def main(argv=None):
    args = parse(sys.argv[1:] if argv is None else argv)
    sets = [expand(args.traces)] + ([expand(args.vs)] if args.vs else [])
    for paths in sets:
        if not paths:
            sys.exit("no .pftrace files found; record some with tools/trace.py")
        missing = [p for p in paths if not os.path.isfile(p)]
        if missing:
            sys.exit("not a file: " + ", ".join(missing))
    families = [{"nav" if scenario_of(p) == "nav" else "start" for p in paths} for paths in sets]
    if any(len(family) > 1 for family in families):
        sys.exit("cannot mix nav and start traces in one set; report them separately")
    if len(families) > 1 and families[0] != families[1]:
        sys.exit("cannot compare nav and start traces with --vs; report them separately")
    open_trace = opener(args.engine)
    measured = [measure(paths, args.package, open_trace) for paths in sets]
    nav = families[0] == {"nav"}
    print(report(measured[0], measured[1] if len(measured) > 1 else None, args.markdown, nav))


if __name__ == "__main__":
    main()

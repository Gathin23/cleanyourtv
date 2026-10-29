#!/usr/bin/env python3
"""A before/after flame chart, as a PNG for a pull request, from traces recorded with tools/trace.py.

One panel per trace, one lane per thread, every slice drawn on the same time scale, so the faster run is
visibly shorter. Slices whose name matches --highlight are drawn in magenta, and their time is summed
(nested slices once), with the change against the first panel.

    tools/flamechart.py traces/before traces/after --highlight '^(activityResume|RV OnBindView)$' \\
        --title 'Back to Home: Home press to first frame' --out chart.png

BEFORE and AFTER are .pftrace files or folders; for a folder the median run is drawn, by the first of
time to first frame, median frame time and median RenderThread draw that every run has. Needs the optional packages in
tools/requirements-perf.txt. Read tools/flamechart.md first.
"""
import argparse
import colorsys
import hashlib
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import trace_report  # noqa: E402  the same trace queries the report uses

MAGENTA = (214, 51, 132)
ROW = 16
LEFT = 150
MS = 1e6


def window(q, package, spec, nav):
    """(t0, t1) in ns: first-frame (launcher start to first frame drawn), start:MS, or all."""
    upid, main, _ = trace_report.threads(q, package)
    if main is None:
        raise ValueError(f"no {package} process in the trace")
    if spec == "all" or nav:
        row = q(f"select min(s.ts) as t0, max(s.ts + s.dur) as t1 from slice s join thread_track tt on s.track_id = tt.id "
                f"join thread t using(utid) where t.upid = {upid}")[0]
        return row["t0"], row["t1"]
    anchor, end = trace_report.start_window(q, package)
    if anchor is None:
        raise ValueError("no launcher start in the trace (bindApplication or performStart); try --window all")
    if spec.startswith("start:"):
        return anchor - 500_000, anchor + int(float(spec[6:]) * MS)
    if end is None:
        raise ValueError("no first frame after the start; try --window start:1500")
    return anchor - 500_000, end + 500_000


def lanes(q, package, names, t0, t1):
    """[(label, slices)] for each lane: main, RenderThread, or any thread name in the launcher's process."""
    upid, main, render = trace_report.threads(q, package)
    out = []
    for name in names:
        if name == "main":
            utid = main
        elif name == "RenderThread":
            utid = render
        else:
            rows = q(f"select utid from thread where upid = {upid} and name = '{name}' order by utid limit 1")
            utid = rows[0]["utid"] if rows else None
        if utid is None:
            out.append((name, []))
            continue
        rows = q(f"select s.id, s.ts, s.dur, s.depth, s.name, s.parent_id from slice s join thread_track tt "
                 f"on s.track_id = tt.id where tt.utid = {utid} and s.ts < {t1} and s.ts + s.dur > {t0} "
                 f"and s.dur > 0 order by s.ts")
        out.append(("main thread" if name == "main" else name, rows))
    return out


def highlighted(rows, rx, t0, t1):
    """(ms, ids): time inside the window of slices matching rx, a match's children counted once; and the ids drawn magenta."""
    hit, total = set(), 0
    for s in sorted(rows, key=lambda r: (r["depth"], r["ts"])):
        if s["parent_id"] in hit:
            hit.add(s["id"])
        elif rx.search(s["name"]):
            hit.add(s["id"])
            total += min(s["ts"] + s["dur"], t1) - max(s["ts"], t0)
    return total / MS, hit


# What a folder's median run is chosen by: the first of these that every run in the set has.
MEDIAN_BASES = (("first frame", "time to first frame"), ("frame median", "median frame time"),
                ("draw median", "median RenderThread draw"))


def median_run(paths, package, open_trace):
    """(path, basis): the middle run of the set by a measure every run has, and that measure's name.

    One trace is drawn as it is (basis None). A set with no measure common to every run is refused:
    picking one of its runs and calling it the median would be wrong (#951). Nav traces from TVs without
    FrameTimeline have no frame time, only RenderThread draw time (#953).
    """
    if len(paths) == 1:
        return paths[0], None
    measured = trace_report.measure(paths, package, open_trace)
    for key, label in MEDIAN_BASES:
        scores = [metrics.get(key) for _, metrics in measured]
        if all(score is not None for score in scores):
            ranked = sorted(zip(scores, paths))
            return ranked[(len(ranked) - 1) // 2][1], label
    raise ValueError("no measure every run has (time to first frame, frame time or draw time); "
                     "pass a single trace to draw it as it is")


def colour(name):
    """A warm, muted colour per slice name, numbers ignored, so the same work looks the same in both panels."""
    h = int(hashlib.md5(re.sub(r"\d+", "", name).encode()).hexdigest()[:6], 16) / 0xFFFFFF
    r, g, b = colorsys.hls_to_rgb(0.08 + 0.12 * h, 0.72, 0.55)
    return int(r * 255), int(g * 255), int(b * 255)


def lane_height(rows):
    return (max((r["depth"] for r in rows), default=0) + 1) * ROW


def tick_step(span_ms):
    """Axis ticks every 10 ms on a short chart, up to every second on a long one."""
    for limit, step in ((120, 10), (300, 25), (700, 50), (1600, 100), (4000, 250)):
        if span_ms <= limit:
            return step
    return 1000


def draw(panels, rx, title, subtitle, out, width=1600):
    """panels: [{label, note, t0, t1, lanes}]. Writes the PNG and returns its size."""
    from PIL import Image, ImageDraw, ImageFont

    def font(size):
        return ImageFont.load_default(size=size)
    small, normal, bold, big = font(11), font(13), font(15), font(19)
    span = max((p["t1"] - p["t0"]) / MS for p in panels)
    plot = width - LEFT - 95
    heights = [52 + sum(lane_height(rows) + 26 for _, rows in p["lanes"]) + 10 for p in panels]
    im = Image.new("RGB", (width, 90 + sum(heights) + 30), "white")
    d = ImageDraw.Draw(im)
    d.text((20, 14), title, fill="black", font=big)
    d.text((20, 42), subtitle, fill="#555", font=normal)
    d.rectangle([width - 430, 18, width - 416, 32], fill=MAGENTA)
    d.text((width - 410, 18), "highlighted: " + rx.pattern[:52], fill="#333", font=small)
    totals = [sum(highlighted(rows, rx, p["t0"], p["t1"])[0] for _, rows in p["lanes"]) for p in panels]
    y = 80
    for i, p in enumerate(panels):
        dur = (p["t1"] - p["t0"]) / MS
        d.text((20, y), p["label"], fill="black", font=bold)
        info = f"{dur:.1f} ms shown  ·  highlighted {totals[i]:.1f} ms" + (f"  ·  {p['note']}" if p.get("note") else "")
        d.text((20 + d.textlength(p["label"], font=bold) + 14, y + 1), info, fill="#444", font=normal)
        if i and totals[0] > 0:
            change = f"highlighted {totals[i] - totals[0]:+.1f} ms ({100 * (totals[i] - totals[0]) / totals[0]:+.0f}%) " \
                     f"vs {panels[0]['label'].lower()}"
            d.text((width - 20 - d.textlength(change, font=bold), y), change, fill=MAGENTA, font=bold)
        y += 26
        x_end = LEFT + plot * dur / span
        d.line([LEFT, y + 6, x_end, y + 6], fill="#999")
        step, t = tick_step(span), 0
        while t <= dur + 1e-6:
            x = LEFT + plot * t / span
            d.line([x, y + 2, x, y + 10], fill="#999")
            d.text((x + 2, y - 8), f"{t:g}", fill="#777", font=small)
            t += step
        d.text((x_end + 6, y), f"{dur:.0f} ms", fill="#222", font=bold)
        y += 18
        for label, rows in p["lanes"]:
            h = lane_height(rows)
            d.text((20, y + 2), label, fill="#222", font=normal)
            d.rectangle([LEFT, y, x_end, y + h], fill="#f6f6f6")
            _, hit = highlighted(rows, rx, p["t0"], p["t1"])
            for s in rows:
                a, b = max(s["ts"], p["t0"]), min(s["ts"] + s["dur"], p["t1"])
                x0 = LEFT + plot * (a - p["t0"]) / MS / span
                x1 = LEFT + plot * (b - p["t0"]) / MS / span
                if x1 - x0 < 0.6:
                    continue
                yy = y + s["depth"] * ROW
                d.rectangle([x0, yy, max(x0 + 1, x1 - 1), yy + ROW - 2], fill=MAGENTA if s["id"] in hit else colour(s["name"]))
                if x1 - x0 > 46:
                    text = s["name"]
                    while text and d.textlength(text, font=small) > x1 - x0 - 6:
                        text = text[:-2]
                    d.text((x0 + 3, yy + 1), text, fill="white" if s["id"] in hit else "#222", font=small)
            y += h + 26
        y += 10
    im.save(out)
    return im.size


def parse(argv):
    p = argparse.ArgumentParser(description="Before/after flame chart PNG from tools/trace.py traces. See tools/flamechart.md.")
    p.add_argument("traces", nargs="+", metavar="TRACE", help="before, then after: .pftrace files or folders (median run)")
    p.add_argument("--out", required=True, help="the PNG to write")
    p.add_argument("--highlight", default="^(activityResume|RV OnBindView)$",
                   help="regex of slice names to draw in magenta and total (default: onResume and tile binding)")
    p.add_argument("--window", default="first-frame",
                   help="first-frame (start to first frame drawn, default), start:MS, or all")
    p.add_argument("--threads", default="main,RenderThread", help="lanes, comma-separated (default main,RenderThread)")
    p.add_argument("--labels", default="Before,After", help="panel labels, comma-separated")
    p.add_argument("--title", default="Launcher: start to first frame", help="the chart's title")
    p.add_argument("--subtitle", default="", help="a line under the title: the TV, the build, what magenta means")
    p.add_argument("--package", default="com.gathin.tvhome")
    p.add_argument("--engine", metavar="PATH", help="a trace_processor_shell to use instead of downloading one")
    p.add_argument("--width", type=int, default=1600)
    args = p.parse_args(argv)
    if len(args.traces) > 4:
        p.error("at most four traces (panels)")
    if not re.fullmatch(r"first-frame|all|start:\d+(\.\d+)?", args.window):
        p.error("--window is first-frame, all or start:MS")
    if not trace_report.PACKAGE.match(args.package):
        p.error(f"not a package name: {args.package!r}")
    try:
        args.rx = re.compile(args.highlight)
    except re.error as e:
        p.error(f"--highlight is not a regex: {e}")
    names = [n.strip() for n in args.threads.split(",") if n.strip()]
    if not names or not all(re.fullmatch(r"[A-Za-z0-9 _.:#-]+", n) for n in names):
        p.error("--threads takes thread names such as main,RenderThread")
    args.thread_names = names
    return args


def main(argv=None):
    args = parse(sys.argv[1:] if argv is None else argv)
    try:
        import PIL  # noqa: F401
    except ImportError:
        sys.exit("flamechart.py needs Pillow: tools/venv/bin/pip install -r tools/requirements-perf.txt")
    open_trace = trace_report.opener(args.engine)
    labels = [l.strip() for l in args.labels.split(",")]
    panels = []
    for i, item in enumerate(args.traces):
        paths = trace_report.expand([item])
        if not paths:
            sys.exit(f"no .pftrace files in {item}")
        try:
            path, basis = median_run(paths, args.package, open_trace)
        except ValueError as e:
            sys.exit(f"{item}: {e}")
        nav = trace_report.scenario_of(path) == "nav"
        q, close = open_trace(path)
        try:
            t0, t1 = window(q, args.package, args.window, nav)
            data = lanes(q, args.package, args.thread_names, t0, t1)
        except ValueError as e:
            sys.exit(f"{path}: {e}")
        finally:
            close()
        note = f"median of {len(paths)} runs by {basis} ({os.path.basename(path)})" if basis else os.path.basename(path)
        panels.append({"label": labels[i] if i < len(labels) else f"Run {i + 1}", "note": note,
                       "t0": t0, "t1": t1, "lanes": data})
    size = draw(panels, args.rx, args.title, args.subtitle, args.out, args.width)
    print(f"{args.out}: {size[0]}x{size[1]}")


if __name__ == "__main__":
    main()

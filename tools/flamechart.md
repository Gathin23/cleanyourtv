# tools/flamechart.py

Draw a before/after flame chart PNG from `tools/trace.py` traces: one lane per thread, both panels on one
time scale, the work your change targets in magenta with its total. Use it to show *where* a difference
from `tools/trace_report.py` comes from; attach the report's table next to it.

```bash
tools/venv/bin/pip install -r tools/requirements-perf.txt     # adds Pillow
tools/venv/bin/python tools/flamechart.py traces/before traces/after \
    --highlight '^(activityResume|RV OnBindView)$' \
    --title 'Back to Home: Home press to first frame' \
    --subtitle 'TCL C755, Google TV 14 · magenta: onResume and tile binding' \
    --out chart.png
```

| Option | Default | Notes |
|---|---|---|
| `TRACE ...` | | Before, then after (up to 4). A folder draws its median run, by the first of time to first frame, median frame time and median RenderThread draw that every run has; the panel names the run and the measure. A folder with none of them in every run is refused. |
| `--highlight REGEX` | `^(activityResume\|RV OnBindView)$` | Slice names to draw magenta and total. Children of a match count once. |
| `--window` | `first-frame` | `first-frame`: launcher start to first frame drawn. `start:MS`: a fixed span from the start. `all`: the whole recording (always for `nav`). |
| `--threads` | `main,RenderThread` | Lanes, by thread name in the launcher's process. |

A Home change usually needs two charts:
- **The start:** the defaults, with `--highlight` set to the slices you removed.
- **The frames after it:** `--window start:1500 --threads RenderThread --highlight '^DrawFrame'`.

## Rules

- **Choose `--highlight` before you look.** Name the slices your change should remove, then draw. A regex
  fitted to whatever shrank proves nothing.
- **Pass folders, not single runs.** The tool picks the median run. If you pass single traces, say why.
- **Magenta is main and render thread time only.** Work moved to another thread or to the GPU isn't gone:
  add that thread as a lane, or use the `nav` GPU numbers.

The whole before/after method for a pull request is in [`docs/workflows/perf-ab.md`](../docs/workflows/perf-ab.md).

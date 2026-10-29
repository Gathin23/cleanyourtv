# tools/flamechart.py

Draw a before/after flame chart PNG from `tools/trace.py` traces: one lane per thread, every panel on one
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

`--out` is the PNG to write; a folder, a name with no extension and a folder that is not there are refused
before the traces are opened, so a typo cannot read as a chart that failed.

| Option | Default | Notes |
|---|---|---|
| `TRACE ...` | | Before, then after (up to 4). A folder draws its median run, by the first of time to first frame, median frame time and median RenderThread draw that every run has; the panel names the run and the measure. A folder with none of them in every run is refused. |
| `--highlight REGEX` | `^(activityResume\|RV OnBindView)$` | Slice names to draw magenta and total. Children of a match count once. |
| `--window` | `first-frame` | `first-frame`: launcher start to first frame drawn. `start:MS`: a fixed span from the start. `all`: the whole recording (always for `nav`). |
| `--threads` | `main,RenderThread` | Lanes, by thread name in the launcher's process. |
| `--package` | `com.gathin.tvhome` | The launcher's package. A fork built under another one - [SHOWCASE.md](../SHOWCASE.md) lists several - draws nothing without it: the lanes are that package's threads, so the run stops at `no <package> process in the trace`. |
| `--out` | | The PNG to write; required. |
| `--labels` | `Before,After` | The panels' labels, comma-separated and in the order of the traces; a panel past the last one is named `Run 3`, `Run 4`. |
| `--title` | `Launcher: start to first frame` | The title, top left. |
| `--subtitle` | | A line under the title: the TV, the build, what magenta means here. |
| `--engine` | | A `trace_processor_shell` to read the traces, instead of the one it downloads; the same option as `tools/trace_report.py`'s ([tools/trace.md](trace.md)). |
| `--width` | `1600` | The PNG's width in pixels; its height follows the lanes. |

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

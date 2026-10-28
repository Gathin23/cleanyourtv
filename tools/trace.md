# tools/trace.py and tools/trace_report.py

Record Perfetto traces of the launcher on the TV, then report where its time goes. For headline numbers
only (key latency, jank, cold start, PSS), use `tools/bench.py` instead: it needs no extra install. To draw the
result for a pull request, use `tools/flamechart.py` ([guide](flamechart.md)).

## Setup

```bash
tools/venv/bin/pip install -r tools/requirements-perf.txt
```

Recording needs only `tools/requirements.txt` and a TV on Android 9 or later. The report's first run
downloads Perfetto's query engine (about 13 MB, SHA-256 checked) to `~/.local/share/perfetto`. To avoid
that, download `trace_processor_shell` yourself and pass `--engine PATH`.

On Android 9 and 10, the TV's tracing services must be enabled first; they are enabled by default only
from Android 11. See Perfetto's [tracing-service setup](https://perfetto.dev/docs/quickstart/android-tracing#starting-the-tracing-services)
for the `persist.traced.enable` prerequisite on non-Pixel devices. This tool does not change that setting.
It sends a binary trace config, which Android 9 accepts without the `--txt` option added in Android 10.

## Record

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/trace.py home --runs 5 --out traces/before
```

`--out` is the folder the traces are written into, and it is created if it is not there. A file where the
folder goes, a path through a file and an empty `--out` are refused before the TV is asked anything.

| Scenario | Recorded | Use for |
|---|---|---|
| `home` | HOME pressed while another app is in front | Most Home changes |
| `cold` | `am start -W` after a force-stop, another app in front | Start-up work (`onCreate`, config, fonts) |
| `coldsys` | `am force-stop` with Home in front; Android restarts it | The restart after Home was killed for memory |
| `nav` | 24 D-pad moves, 0.35 s apart | Focus, scrolling, drawing |
| `custom` | `--step CMD` (repeatable) after `--setup CMD` | Anything else; `sleep N` steps set the length |

- **Output:** each run writes `<scenario>-<n>.pftrace` (10–20 MB) and a `.json` with the commands and the
  TV's model and build. `traces/` is gitignored.
- **The other app:** `--app package/activity`, or by default the TV's Settings. Both this tool and
  `bench.py` bring an existing instance to the front across runs, rather than stacking new Settings
  activities. They do not clear the other app's data or force-stop it. Check that it is responsive
  after a long session; an already accumulated history is not removed by this flag.
- **The package:** `--package` names the launcher, `com.gathin.tvhome` unless your build has another one
  (a fork: [SHOWCASE.md](../SHOWCASE.md) lists several). It decides which process the recording follows and
  which one the report reads, so a fork has to pass it to `trace.py`, `trace_report.py` and
  `flamechart.py` alike: with the wrong name the report prints `–` for every step instead of saying the
  package was not in the trace, and the chart stops at `no <package> process in the trace`.

## Report

Keep `nav` traces separate from start traces (`home`, `cold`, `coldsys`, `custom`), since their frame
counts use different time windows. Each input set and both sides of `--vs` must use the same family.
For a folder containing both, pass matching files such as `traces/nav-*.pftrace` instead of the folder.

```bash
tools/venv/bin/python tools/trace_report.py traces/before --vs traces/after --markdown
```

| Scenario | Numbers |
|---|---|
| Starts (`home`, `cold`, `coldsys`, `custom`) | Start to first frame drawn; the steps inside it (`bindApplication`, `onCreate`, `onStart`, `onResume`, first layout and draw); then the frames drawn and missed deadlines in the next 1.5 s |
| `nav` | Frame time (median, 90th percentile); RenderThread draw; GPU wait; missed deadlines |

With `--vs`, a third table shows the change in each median. A run over twice the median is starred.

Slices still open when the recording ends have no measured duration. The report leaves those durations
out and counts only completed draws. If the first RenderThread draw is unfinished, the first-frame time
is unavailable too; completed lifecycle steps are still reported.

Frame times and missed deadlines need [FrameTimeline](https://perfetto.dev/docs/data-sources/frametimeline),
available on Android 12 and later. Without its data for the launcher process, these metrics are omitted
(or shown as `–` beside measured runs). `nav` still counts RenderThread draws and reports their duration
and GPU waits where available; a draw's duration is not the full frame time.

## Rules

- **Repeat runs.** Record 5 runs per build, on the same TV with the same config, theme and wallpaper.
  Compare medians.
- **Keep starred runs.** They're usually memory pressure on a 1 GB TV. Report them; don't drop them.
- **Don't run `am start` after a force-stop with Home in front.** Android has already restarted Home, so
  `onResume` appears twice. Use `cold` or `coldsys`.
- **Cold starts use the HOME intent.** `bench.py` and the `cold` trace send `MAIN` with the `HOME`
  category. Starting only the component can create a regular task beside the Home task, so a later
  Home press creates another instance. Compare runs recorded with the same launch command.
- **Check the remote afterwards.** A force-stop switches off the accessibility helpers. `trace.py` puts the
  list back after the last run, but on some TCL sets a helper still won't bind (`docs/TROUBLESHOOTING.md`).
- **Look in the right place for GPU savings.** Less overdraw shows as GPU wait in `nav`, not as main-thread
  time.
- **Expect no latency gain from off-main-thread work.** It removes work, but doesn't shorten the time to
  the first frame.
- **Never commit the `.pftrace` files.** They hold the TV's process list.

The whole before/after method for a pull request is in [`docs/workflows/perf-ab.md`](../docs/workflows/perf-ab.md).

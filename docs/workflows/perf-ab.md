# Workflow: measure a launcher performance change, before and after

**When to use this:** any change under `launcher/`. The regression check below is optional for most of them
(about six minutes on one TV), but welcome. A change meant to make the launcher faster or lighter, or one
whose check fails, then follows the full method
#666 used, to show by how much and where the time went.

## The regression check, for any launcher change (optional)

1. **Before you change anything,** install the base build with `launcher/build-and-install.sh` and run:

   ```bash
   TV_HOST=<tv ip> tools/venv/bin/python -I tools/bench.py --out-dir work/bench-before
   ```

2. Make the change, install your build the same way, and run
   `tools/venv/bin/python -I tools/bench.py --out-dir work/bench-after`.
3. Compare the two `result.json` files. Leave out any field that is `null` in both:

   | Field | Fails when the after value is |
   |---|---|
   | `response_ms.p50` | more than 10% and 5 ms above before |
   | `return_to_home_ms.p50` | more than 10% and 10 ms above before |
   | `cold_start_ms.p50` | more than 10% and 30 ms above before |
   | `jank_pct` | more than 1 point above before |
   | `pss_mb` | more than 2 MB above before |
   | `idle_launcher_cpu_pct` | more than 0.5 points above before |

   Two more numbers sit in the same file without a limit here. `movement_ms` is the end of the frames
   a key set off, so a change that makes the focus settle later - a longer highlight slide, an added
   bounce, a slower scroll - moves it while `response_ms` stays where it was: compare it the same way
   as `response_ms` when your change touches motion. `idle_cpu_pct` is the whole TV's idle CPU, not
   the launcher's (that is `idle_launcher_cpu_pct` above), so other apps move it; read it as context.

4. **If a field fails,** follow the method below with that scenario (`home`, `cold` or `nav`) to find
   the cost. Fix it and go back to step 2. Fixes that usually work:
   - doing the work after the first frame;
   - skipping work when nothing changed;
   - decoding images at their display size;
   - keeping results instead of recomputing them.
5. Put the six fields, before and after, in the PR, plus `movement_ms` when your change touches
   motion. If a feature has to cost something, give the number and the reason. Once it works, spend
   one more pass on its cost.

Some notes on the numbers:
- **Noise:** two runs of the same build on a TCL C755 differed by 0 ms in key response, up to 45 ms in
  cold start and 1 MB in PSS, and the limits sit above that. Rerun both builds if a field lands near
  its limit.
- **Run time:** a full run takes about 6 minutes.
- **TVs that don't log injected keys:** `return_to_home_ms` is `null`, and `response_ms` includes the ADB
  round trip. It still compares two builds on the same TV.

## The full method

Tools: [`tools/trace.py` and `tools/trace_report.py`](../../tools/trace.md),
[`tools/flamechart.py`](../../tools/flamechart.md) and [`tools/screendiff.py`](../../tools/screendiff.md).
Install them once:

```bash
tools/venv/bin/pip install -r tools/requirements-perf.txt
```

Copy this checklist into your notes and tick it off:

```
- [ ] 1. Write down the scenario and the slices you expect to shrink
- [ ] 2. Trace the base build, 5 runs
- [ ] 3. Trace your build, 5 runs
- [ ] 4. Report; if the change isn't there, go back to your code
- [ ] 5. Draw the flame charts
- [ ] 6. Screenshots identical, or the visual change described
- [ ] 7. Check the behaviour your change could break
- [ ] 8. Write it up
```

## 1. Decide before you measure

Write down two things:
- **The scenario:** `home` (the return to Home), `cold` (start-up) or `nav` (D-pad, drawing).
- **The slice names** your change should remove or shorten, e.g. `activityResume`, `RV OnBindView`.

They become `trace.py`'s scenario and `flamechart.py`'s `--highlight`. Choosing them after looking at
the results proves nothing.

## 2–3. Trace both builds

Install each build with `launcher/build-and-install.sh`, using the same config, theme and wallpaper.
Let Home sit for a minute after each install, then record:

```bash
tools/venv/bin/python -I tools/trace.py home --runs 5 --out traces/before   # base build installed
tools/venv/bin/python -I tools/trace.py home --runs 5 --out traces/after    # your build installed
```

## 4. Report

```bash
tools/venv/bin/python tools/trace_report.py traces/before --vs traces/after --markdown
```

- **No change, or the wrong steps moved:** fix the code before drawing anything.
- **A starred run** (one over twice the median) stays in the table, named as an outlier.

## 5. Draw

```bash
tools/venv/bin/python tools/flamechart.py traces/before traces/after \
    --highlight '<the slices from step 1>' \
    --subtitle '<TV>, <Android version> · magenta: <what they are>' --out chart.png
```

For a change to Home, add the frames after the start:

```bash
tools/venv/bin/python tools/flamechart.py traces/before traces/after \
    --window start:1500 --threads RenderThread --highlight '^DrawFrame' --out frames.png
```

## 6. Screenshots

Take the same screenshot on both builds, then compare:

```bash
tools/venv/bin/python -I tools/tv.py --screenshot shots/before.png HOME DPAD_RIGHT   # each build
tools/venv/bin/python tools/screendiff.py shots/before.png shots/after.png \
    --mask <clock box> --blur <network name box> --out shots/before-after.png
```

`identical` backs "no visible change". If it says `different`, find out why before posting.

## 7. Behaviour

On the TV, check what your change could break, and note what you saw. For the return to Home, for
example:
- an app disabled while another app was in front is gone on return;
- focus comes back to the tile you left from;
- Home on Home goes to the first tile.

## 8. The pull request

- **Tested on:** the TV, Android version and firmware.
- **The tables:** `trace_report.py --markdown` before, after and the change, with outliers named.
- **The charts:** each with its `--highlight` stated.
- **The screenshots:** the pair from `screendiff.py` and its result.
- **The behaviour checks:** what you checked and what you saw.
- **Leave out** `traces/` and raw screenshots: they're large and hold your TV's details.

# TCL BeyondTV2 (RTD2851, R851T02 V660): rendering notes

Checked on a real TV on 2026-10-21, one session, with a launcher derived from this project (Eddy TV,
TCL-only fork). These are properties of the GPU and firmware, not of one launcher, so they are
recorded for anyone tuning a Home on this board.

## Device identifiers

| Field | Observed value |
|---|---|
| Model / device | `BeyondTV2` / `BeyondTV` |
| Board | `RTD2851` |
| Android / API | Android TV 9 / 28, 32-bit userspace |
| Build fingerprint | `TCL/BeyondTV2/BeyondTV:9/PPR1.180610.011/AP08:user/release-keys` |
| Firmware | `V8-R851T02-LF1V660` (client `TCL-LA-RT2851-S1`, Latin America) |
| Reported memory | 2 GB (`2097152 kB`), zRAM ~800 MB |
| Remote | `TCL_RC802VLA` (Bluetooth, mic) + IR |

This is the V660 firmware, the same build, client and fingerprint as the list in
[`examples/tcl-beyondtv2-latin-america/`](../../examples/tcl-beyondtv2-latin-america/debloat.txt): use that
list for the debloat. This note only covers what the launcher's drawing costs on this GPU.

## Why this note

While adding an opt-in news banner (1680x500) to the Home, a few ordinary drawing choices cost far
more than they should on this board. Each row below is an A/B on the same APK with one thing
changed, measured as **janky frames** (`dumpsys gfxinfo`, frames above 16.7 ms) during 20 D-pad
presses after the Home had settled.

| What | Cost on this board | What replaced it |
|---|---:|---|
| `canvas.clipPath` (rounded rect) over the banner | ~8 points of janky frames | fill a pre-built rounded `Path` with a `BitmapShader` |
| `setClipToOutline(true)` on the same view | 83% janky frames | not used: it forces a layer per frame |
| Full-bleed `<shape>` gradient, 1700x250, redrawn each frame | ~42 points | constant scrim baked into the bitmap once |
| `android:shadowRadius` on four TextViews | ~34 points | removed; the scrim gives the contrast |
| Two `LinearGradient` objects built inside `onDraw` | ~1.5 ms per frame | built in `onSizeChanged` |
| A static banner behind the grid's spring animation | ~2-3 ms per frame of the spring | hardware layer on the banner (`setLayerType`) |

Two conclusions that surprised us:

- **`clipPath` is a software fallback here.** A rounded clip on a large view turned every frame into
  a CPU rasterisation of the whole banner, photo included. It looked like "the photo is expensive"
  until the photo was tested without any photo at all: the cost stayed.
- **A layer is cheaper than being redrawn.** The static banner was re-recorded on every frame of the
  grid's focus spring even though nothing in it changed; `LAYER_TYPE_HARDWARE` dropped those frames
  back to the same numbers as with the banner absent.

## Method

- Steady state, not "since process start": `force-stop`, open Home, wait 20 s for decode/cache/layout,
  `dumpsys gfxinfo reset`, 20 D-pad presses 0.4 s apart, read after 3 s.
- Frames are matched to keys through `framestats`; columns are 1-based in `awk` (`$1` Flags, `$2`
  IntendedVsync, `$14` FrameCompleted). `OldestInputEvent` is `INT64_MAX` for key events and cannot
  be used as the frame's start.
- Five or more runs per side. A single run can catch a rotation/refresh and read 10 points higher;
  the late frames cluster in bursts, which is how a transition is told apart from a per-frame cost.

## One firmware quirk

`am send-trim-memory <pid> <level>` refuses background levels on this firmware when an accessibility
service keeps the launcher process at `curProcState=2`, even with another app in front. So
`onTrimMemory` for background levels can only be exercised by real system pressure here, not by the
shell command.

Full numbers, the code and the seven fixes:
<https://github.com/eddysangalli/CleanMyTCL-Android9/blob/main/benchmarks/results/2026-10-21-rss-row.md>

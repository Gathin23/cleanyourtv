# Frames Counter

A small offline FPS overlay with a dark glass-style settings panel, four corner previews and one
Enable/Stop button. **Standalone mode runs inside the app, without a computer, shell helper, root
or Shizuku after setup.** The package stays `com.lionlev.tv.fps` so updates keep existing settings.

This measures presented Android app-layer buffers, not game-engine FPS, HDMI FPS, decoded-video
metadata or display refresh rate. A single live video SurfaceView is preferred to UI frames. Missing,
ambiguous or unreadable layers stay hidden. Protected/vendor paths may not expose counters.

## One-time standalone setup

Build with JDK 17 and Android SDK 34:

```sh
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease
```

Install the APK and allow display over other apps. **Review the Permissions section before granting
access:** DUMP and Usage Access are broader than the specific queries this app makes. From this folder,
using the repository Python environment with `tools/requirements.txt` installed and `TV_HOST` set:

```sh
python setup-standalone.py             # read-only permission status
python setup-standalone.py --grant     # explicit, one-time diagnostic and Usage Access grants
```

The setup tool stops the legacy helper first. These grants are intended to persist across reboot until
revoked or the app is removed. After reboot, open Frames Counter and choose Enable on the TV; no
computer command or privileged helper startup is needed. No boot receiver automatically enables it.
Firmware restrictions can differ; standalone access must be verified on each TV.

Use the TV menu to choose a physical screen corner. Readings use small LTR text such as `50.0 FPS`,
with a translucent background/border. Home, the settings screen and standby hide the counter and pause
frame collection. Stop removes it and stops sampling. The overlay never intercepts touch or remote keys.
Setup details live in the confirmation alerts, not paragraphs on the main menu.

The app uses public `Debug.dumpService` calls for fixed SurfaceFlinger queries and power state.
Usage Access identifies the recent foreground activity; only recent events are read, in memory.
A single background worker serializes sampling and cleanup, approximately once per second. UI work
stays on the main thread. No reflection, arbitrary shell commands or per-frame polling is used.
Diagnostic output is read through private temporary cache files deleted after each query.

## Revoke access

Stop the counter, then run:

```sh
python setup-standalone.py --revoke
```

The tool stops measurement before revoking DUMP and resetting Usage Access. Overlay permission can
also be disabled in Android settings. Revoking access does not uninstall anything or change the launcher.

## Permissions

- `SYSTEM_ALERT_WINDOW`: the owner-approved counter over an app.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE`: measurement only after Enable.
- `DUMP`: optional persistent development grant via ADB. Allows broad system diagnostics; this app
  uses only fixed SurfaceFlinger frame queries and power state. Not granted automatically on install.
- `PACKAGE_USAGE_STATS`: owner-approved Usage Access. Can expose app-usage history; this app uses
  recent foreground events only, stores no history and never transmits them.
- No `INTERNET`, network state, accessibility, location, storage, root or boot-start permission.
  No ads, telemetry or new launcher permission. Diagnostic/usage grants should be reviewed explicitly.

The optional legacy provider and sample receiver require the sender to hold `android.permission.DUMP`.
They expose an enabled bit, samples and a fixed Stop action, never shell commands or writable settings.
External samples are ignored while standalone mode is active.

## Optional legacy helper

For firmware that refuses standalone access, the original shell helper remains available:

```sh
python start-helper.py
python start-helper.py --stop
```

This fallback requires restarting the helper from a computer after each reboot. The computer can
otherwise disconnect. It uses a shell-private directory and fixed commands, no socket listener.
Do not run it or legacy `monitor.py` alongside another profiler: TimeStats is a shared system collector,
and none of these samplers clears its history. The helper remains idle when standalone mode is available.

## Validation

Tested on Android TV 12 (device details private). Real streaming-app video-layer readings are around
50 FPS, with expected sampling jitter. Other apps may not expose readable video-layer counters.
JVM tests exercise the standalone selector and rate calculations. Python/AWK tests cover the legacy
backend and bootstrap failure paths; add-on contract checks verify isolation and permission disclosure.

```sh
python -m unittest discover -p 'test_*.py' -v
```

Verified on the test TV after a full reboot: both approved grants persisted, the helper was absent,
and the owner enabled the counter using only the remote and confirmed FPS appeared in a streaming app.
Launcher configuration, remote-helper setting and screensaver selection remained unchanged.

No HDMI support. Focused corner cards can expand beyond their rows without clipping the rounded border.

# KUNFT K8415H40F (Changhong redwood, Android TV 11)

| Field | Observed value |
|---|---|
| Android model / device / product | `AI PONT` / `redwood` / `DVB92KT`, manufacturer Changhong |
| Android / API | 11 / 30 |
| Firmware | `RTM9.241015.127` |
| Board | MediaTek `mt5867` |
| RAM | `MemTotal: 934540 kB`, about 1 GB |
| Display | 1280×720, 213 dpi |
| Stock Home | `com.google.android.tvlauncher/.MainActivity` |
| Live TV / HDMI player | `com.mediatek.wwtv.tvcenter` |

One set, tested on 2026-11-02: three batches with a screenshot after each and three reboots. This was not a
several-day soak.

## Apply and undo

```sh
TV_HOST=<tv ip> tools/disable.sh examples/kunft-k8415h40f/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/kunft-k8415h40f/debloat.txt com.google.android.tvlauncher
```

## Launcher

`launcher/build-and-install.sh` with `HOME_KEY_SERVICE=1` installed the reference launcher, set it as Home
and disabled `tvlauncher`. `cmd package query-activities` then listed only `com.gathin.tvhome` and the
framework's `FallbackHome`, and `mFocusedApp` named the new launcher. Inputs lists HDMI 1, HDMI 2,
HDMI 3, AV and Live TV. HDMI 1 and Live TV both open `com.mediatek.wwtv.tvcenter`. No HDMI device was
connected, so a picture from an HDMI source was not checked.

`config.json` holds the test set's dock and the power-on settings below. Push it with
`tools/theme.sh examples/kunft-k8415h40f`, or set the same values in
`launcher/app/src/main/res/values/config.xml` before building.

## Power-on lands on Live TV, and the held-volume trade

Every cold boot ends in Live TV: `FallbackHome`, then the Home app, then
`com.homwee.factory/com.gdch.hotel.ui.ProtocolActivity`, then `com.mediatek.wwtv.tvcenter` about 15 s
later. This set's firmware does it by itself; nobody opened the factory package.

Two settings were tried here, each with the helper bound (`HOME_KEY_SERVICE=1`):

| Settings | Power-on | Holding Volume Up on the real remote |
|---|---|---|
| `power_on_home` true, `key_filter` true | Home (Live TV sent back within a second) | one step per press |
| `power_on_home_always` true, `power_on_home` false, `key_filter` false | Home (Live TV sent back within a second, checked after a reboot) | ramps, as with no helper |

The first row is the trade in `docs/devices/tcl-55p71b-g09-key-repeat.md`. The second row is the setting
`examples/chiq-ai-pont/README.md` uses on the Android 11 CHiQ, and it worked on this set too. Do not
combine `power_on_home` with `key_filter` false: the CHiQ note explains why.

With `key_filter` false, the remote's Home, Input and app buttons still worked, pressed by the owner on
the real remote. On this firmware the TV's own services answer those buttons, not the helper.

## The gear button

The gear sends `KEYCODE_SETTINGS` (scancode 92). `com.mediatek.wwtv.tvcenter` handles it and starts
`com.android.tv.settings/.MainSettingsSecond`, a short *Device Preferences* panel with only Picture and
Sound. The full Settings menu is the launcher's Settings pill. Remapping the gear with
`settings_key_keycodes` would need `key_filter`, and that would cost the volume ramp.

## Memory

Measured about 12 minutes after a restart, just after pushing a theme with `advanced_effects` on:
`com.gathin.tvhome` at 55 MB PSS, and `MemAvailable` at 316 MB.

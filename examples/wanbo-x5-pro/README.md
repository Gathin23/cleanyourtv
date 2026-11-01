# Wanbo X5 Pro Projector (Skyworth HP4609 platform, Amlogic, Google TV 14)

Notes for the Google TV stick built into the Wanbo X5 Pro. The stick is a **Skyworth HP4609** reference
design (`HP4609-Skyworth`), also sold on its own as a "4K Google TV Stick", so a set from another badge
with the same `ro.product.name` very likely carries the same package set.

Everything below was read from the device at Android 14 / `UKG3.260128.001.C3.3.5_20260723`.

## Device information

| Field | Value |
|---|---|
| Model | `4K Google TV Stick` (Wanbo X5 Pro's built-in stick) |
| Brand / manufacturer | `Skyworth` / `SkyworthDigital` |
| Product / device | `HP4609-Skyworth` / `YYT` |
| Board / platform | `HP46BY4` / `HP46B` (Amlogic) |
| SoC | Amlogic, Mali GPU (`ro.hardware.egl=mali`, `ro.hardware.vulkan=amlogic`) |
| Android / SDK | `14` / `34` |
| Build | `UKG3.260128.001` / `C3.3.5_20260723`, `user/release-keys` |
| **First API level** | **`30`** — the stick shipped on Android 11 and was upgraded to 14 |
| Userspace ABI | `armeabi-v7a` only (`ro.product.cpu.abilist64` is empty) |
| RAM | ~1.9 GB, 320 dpi, 1920×1080 |
| OTA | A/B partitions (`ro.product.ab_ota_partitions` lists `boot`, `system`, `vendor`, `vbmeta`, …) |

The first-API-level of 30 is why notes written before the 14 update describe this device as Android TV
11. The build id does not change between the two units tested (Australia, Mexico).

## There are no HDMI inputs and no tuner at the Android layer

The projector has HDMI ports; the **stick does not see them**. They are handled by the projector's own
MCU, so nothing in Android exposes them:

```
$ adb shell dumpsys tv_input
  mHdmiDeviceList:
  mHdmiInputIdMap: id -> inputId
```

Both are empty, and there is no `TvInputService` for a tuner. Consequences for the launcher:

- The **Inputs pill has nothing to list** and can be hidden with `show_inputs_pill: false`.
- There is no `live_tv_package` to set, and no `boot_to_live_tv`.
- A "Sources" style card that reads `TvInputManager` will render empty on this device.

Switching to an HDMI source is done with the projector's own remote, outside Android.

## Projector controls exposed as global settings

The Skyworth firmware exposes the projector's hardware through `settings global`:

| Key | Value seen | Notes |
|---|---|---|
| `sdt_projector_show_vol` | `1` | Confirmed: makes the **projector's own volume OSD** appear instead of Android's |
| `sdt_set_volume` | `-1` | Routed to the MCU with `show_vol` |
| `sdt_volume_muted` | `-1` | Same |
| `sdt_projector_screensaver` | `0` | Value found on the device; not differently tested |
| `sdt_projector_dis_lan` | `1` | Value found on the device; not differently tested |
| `sdt_projector_flag` | `1` | Value found on the device; not differently tested |
| `sdt_projector_set_timeout` | `1` | Value found on the device; not differently tested |
| `sdt_set_sleep_timeout` | `1` | Value found on the device; not differently tested |

Only `show_vol` is confirmed to change what is on screen. The rest are recorded because they are not
documented anywhere else and are the first place to look when a projector behaves oddly after a reset.

## Volume goes through the projector, not HDMI-CEC

```
$ adb shell dumpsys hdmi_control
  mCecLocalDevices: 4
  mHdmiCecVolumeControlEnabled: 0
  mOneTouchPlayEnabled: true
  mAutoDeviceOffEnabled: true
```

CEC volume control is **off** on this firmware, which is consistent with the `sdt_*` volume globals
above: volume is drawn by the projector's own OSD rather than by Android. `logcat` shows the projector
on the CEC bus as a device named `Wanbo` at physical address `0x2000`.

## Screensaver

Ambient Mode (`com.google.android.apps.tv.dreamx`) is disabled outside the list (see Undo) and replaced by a sideloaded dream:

```
$ adb shell settings get secure screensaver_components
  <your.package>/<your.package>.ClockDream
```

Two things worth knowing on this firmware:

- `screensaver_timeout` is `null`, so the idle delay is the framework default.
- If the replacement dream is uninstalled, `screensaver_components` keeps pointing at it and the screen
  simply never sleeps. Repoint it at `com.google.android.apps.tv.dreamx/.DreamActivity` (after
  re-enabling the package) instead.

## Firmware updates

`com.sdt.ota` (2.6.0, with a `BootReceiver`) and `com.sdt.upgrade` are the Skyworth updaters. The list
leaves both **enabled** on purpose. Because the partitions are A/B, an update installs to the inactive
slot; if it fails to boot, the device falls back on the next boot. What an update does undo is the
debloat — run `tools/check.py` after one.

## Debloat drift

Play Store updates re-enable packages the list had disabled. Seen on this device on 2026-10-29:
`com.google.android.katniss` (Google Assistant, updated that morning) and
`com.google.android.marvin.talkback` came back on, which `check.py` reported as `drift: 2 of 29` and
`--fix` restored. Expect `katniss` to drift again with Assistant updates; a periodic `check.py` run is
the cheap guard.

## Remote keys

The keycodes for the four app buttons and the Settings gear are in the header of `debloat.txt`. They
were captured on the second unit after `launcherx` was disabled, which is when the firmware starts
sending raw keycodes instead of routing to the stock launcher.

The projector's physical autofocus and keystone buttons go through `com.sdt.projector.observer` and
work with any launcher. The list keeps that package for exactly this reason.

## Performance on this device

Measured with `dumpsys gfxinfo` on Home, eight D-pad presses, twice per build.

- **Idle costs nothing:** three seconds with no input renders **0 frames**.
- The UI thread measures **0.1 ms** per frame. The launcher's own code is not the bottleneck.
- Frames land around **42 ms**, which is ~2.5 vsync intervals at 60 Hz — the display is confirmed
  60.00 Hz, so this is not a 24 Hz mode being mistaken for jank.
- **The ceiling is the display pipeline, not the GPU:**

```
$ adb shell dumpsys SurfaceFlinger | grep -E "HWC missed|GPU missed"
HWC missed frame count: 3494
GPU missed frame count: 9
```

  `HWC missed` counts frames the hardware composer failed to present. When it dwarfs `GPU missed`, app
  side optimisation is spent; `docs/TROUBLESHOOTING.md` has the entry on what that means for tuning.

The one change that moved the number on this device was the Skia backend:

```
$ adb shell setprop debug.hwui.renderer skiavk   # p50 42 ms -> 34 ms, jank 13.7% -> 10.5%
```

It is a debug property and resets on reboot.

## Undo

`tools/restore.sh examples/wanbo-x5-pro/debloat.txt com.google.android.apps.tv.launcherx` re-enables the
list and the stock launcher. Two packages are disabled outside the list and are **not** in it:
`com.google.android.apps.tv.dreamx` (Ambient Mode) and `com.google.android.tv.axel` (disabled at the
factory). Re-enable those by hand if you want them back.

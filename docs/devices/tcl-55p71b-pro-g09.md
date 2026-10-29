# TCL 55P71B Pro: second G09 unit, key filtering kills remote key repeat

Checked on a real TV in one session on 2026-10-22 (India, on Wi-Fi), after a factory reset,
then re-verified the next day. Companion list: [`examples/tcl-g09-google-tv/`](../../examples/tcl-g09-google-tv),
whose packages and remote-button table matched this unit.

## Identify the firmware, not just the generic model name

| Field | Observed value |
|---|---|
| Retail model | `55P71B Pro`, supplied by the owner; not exposed by `ro.product.model` |
| `ro.product.manufacturer` / `brand` | `TCL` |
| `ro.product.model` | `Smart TV Pro` |
| `ro.product.name` (product) | `G09_4K_GB` |
| `ro.product.device` | `G09` |
| `ro.hardware` / `ro.board.platform` | `amlogic` / `t5m` |
| `ro.product.board` | `TC8000` |
| Android / SDK | `12` / `31` |
| `ro.build.display.id` / firmware | `STT2.230203.001.AS23 release-keys` |
| Security patch | `2025-09-05` |
| `ro.product.cpu.abilist` | `armeabi-v7a,armeabi` only (32-bit; no `arm64`, no `x86`) |
| `ro.adb.secure` | `1` |

The Brazil unit in `examples/tcl-g09-google-tv/` reports the same product name on firmware
`STT2.230203.001 AS24`, so this is the same firmware family one build earlier. `Smart TV Pro` and
`G09` alone do not pin the retail model — several TCL ranges share them.

## Held-down volume does not repeat while any key-filtering service is bound

The finding this note exists for, and the reason for `key_filter` in `launcher/` (issue #763):

- `HomeKeyService` + `InputKeyService` enabled → holding volume up/down on the remote steps once per
  press.
- Either one alone → still broken.
- All accessibility services off (`enabled_accessibility_services` empty, `accessibility_enabled 0`)
  → repeat works again.

On this build the `AccessibilityInputFilter` path drops key auto-repeat for any service that requests
`FLAG_REQUEST_FILTER_KEY_EVENTS`; it is all-or-nothing. `RemoteKeys.applyKeyFilter` lets the services
stay bound with the flag stripped (`"key_filter": false` in a theme), which is the configuration this
unit ended on — the tradeoff is the remote buttons below stay dead, the user's pick.

## The volume tick is gone with the stock launcher

The beep the TV played on each volume press disappeared once launcherx was disabled, and nothing got it
back: every debloated package was re-enabled in batches (`pm enable`), the TV rebooted, and no logcat
line even attempted a sound on a press (`com.tcl.systemui.shared` still draws the volume bar).
`STREAM_SYSTEM` reports `streamVolume:1` on this build regardless of `volume_system`, but that is a
diagnostic dead end rather than the mechanism — the tick appears to have been produced by the stock
home, not any single package.

## Remote buttons

| Button | Keycode | Scancode | Note |
|---|---|---|---|
| Gear (Settings) | 83 (`KEYCODE_NOTIFICATION`) | 561 | dead without a helper; stock home opened its panel |
| Netflix | 4062 | 472 | IR; `com.tcl.ttvs`'s `KeyDownBehaviorReceiver` logs `keyDown：keyString=4062 type=IR` but launches nothing |
| Prime Video | 4099 | 473 | same path (from the G09 example's table) |
| YouTube | 4065 | 569 | same path |

Captured with `tools/tv.py 'logcat -d | grep handleKeyBeforeQueueing'` while pressing the real remote —
injected keys never reach accessibility filtering. Volume keys arrive as `source=0x301, deviceId=6`,
scanCode 115.

## Home handover

Same as the G09 example: `launcherx` owns Home (priority 2), `setupwraith/.RecoveryActivity` claims it
the moment launcherx is disabled, and the saved Wi-Fi network belonged to setupwraith after the reset —
`dumpsys wifi | grep -o 'cname=[^ ]*'` showed `cname=com.google.android.tungsten.setupwraith` until the
network was forgotten and re-joined, after which it read `cname=com.android.tv.settings`. Only then was
setupwraith disabled and `build-and-install.sh` re-run; its own recovery check caught the failed
handover and put launcherx back the first time, exactly as designed.

## What was kept that the example disables

Owner's choice, verified working: `com.google.android.apps.mediashell` (Chromecast), `com.tcl.miracast`
+ `com.tcl.magiconnectfree` (mirroring), `com.tcl.airplay2` + `com.amlogic.airplay` (AirPlay),
`com.google.android.katniss` (Assistant), `com.tcl.hearaid`, `com.tcl.ttvs` (firmware updates; also owns
`KeyDownBehaviorReceiver`), `com.google.android.tv.remote.service`, `com.google.android.feedback`.
The example's own disabled set applied cleanly here too.

## Limits

- One unit, two days; the keep-list above is preference, not a recommendation either way.
- The tick was not recovered; the mechanism is unknown, so do not assume the same packages are
  responsible on other G09 builds.
- `key_filter` was verified on the launcher's own build from `main` plus the patch under #763;
  it does not exist in older builds — the manual alternative is disabling both services.

# TCL Smart TV Pro G09 (Google TV, Android 12)

UK firmware `G09_4K_GB` (STT2.230203.001 AS24, Amlogic T5M, 2 GB RAM), set up in Brazil. Both halves
work on this set: the debloat list and the Clean Your TV launcher as Home. Tested on 19 October 2026,
including a reboot.

| File | What it is |
|---|---|
| `debloat.txt` | The 36 packages that were disabled, grouped and commented |
| `config.json` | Dock order, hidden apps, Live TV app, columns; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`. USB debugging must be on while you
run them (Settings → System → Developer options).

## Install, from scratch or again

The order matters: the setup wizard (setupwraith) is disabled while Google TV's home still owns Home,
then `build-and-install.sh` starts the new launcher and only then disables launcherx.

```bash
TV_HOST=<tv-ip> tools/disable.sh examples/tcl-g09-google-tv/debloat.txt
INPUT_KEY_SERVICE=1 TV_HOST=<tv-ip> launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/tcl-g09-google-tv
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
```

The last line swaps the Ambient screensaver (`dreamx`, disabled by the list) for the launcher's Clock.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/tcl-g09-google-tv/debloat.txt com.google.android.apps.tv.launcherx
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop'
```

The launcher stays installed but is no longer Home. Never re-enable just
`com.google.android.tungsten.setupwraith` on its own while the new launcher is Home; `restore.sh` brings
Google TV's home back first.

## Re-enable one app

```bash
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'pm enable com.tcl.ui_mediaCenter'
```

Comment its line out in `debloat.txt` so the next run leaves it alone. Without a computer: Settings →
Apps → See all apps → Show system apps → pick the app → Enable. The two off by choice are
`com.google.android.apps.mediashell` (Chromecast built-in) and `com.tcl.ui_mediaCenter` (USB media
player).

## Change the dock order

Edit `dock_apps` in `config.json`: package names, left to right; an app that isn't installed is skipped.
`tools/tv.py 'pm list packages -3'` lists the installed ones. Push it again with the `theme.sh` line
above, no rebuild needed. `com.tcl.tv` and `com.tcl.suspension` are in `hidden_apps` because the Inputs
pill already covers Live TV and the inputs. All keys: [`themes/README.md`](../../themes/README.md).

## Remote app buttons

With Google TV's home disabled, the Netflix, Prime Video and YouTube buttons on the TCL remote do nothing:
they send TCL's own keycodes, which TCL's shortcut manager hands to the stock home. `config.json` maps them
back through the launcher's `InputKeyService`, which is why the install line above passes
`INPUT_KEY_SERVICE=1`. Re-pass it on every reinstall; a reinstall drops the service from the accessibility list.

**Ask for that only if you want the buttons more than a ramping volume.** Once a key-filtering service is
bound, holding volume up/down moves one step instead of repeating, on the whole TV, and one helper or both
makes no difference - see [the G09 key-repeat note](../../docs/devices/tcl-55p71b-g09-key-repeat.md) for the
bisect and for the third option (leave launcherx enabled). The Input button below needs no helper.

| Button | Keycode | Scancode | Note |
|---|---|---|---|
| Netflix | 4062 | 472 | `app_keys` |
| Prime Video | 4099 | 473 | `app_keys` |
| YouTube | 4065 | 569 | `app_keys` |
| Disney+ | | | keeps working on its own |
| TCL Channel | 4020 | 456 | opens `com.tcl.waterfall.overseas`, which the list disables; map it to any app you like |
| Shield (TCL Guard) | 4140 | 660 | opens TGuard, which the list disables; free to map |

Found with `logcat` while pressing each button: TCL logs every press as `handleKeyBeforeQueueing event=KeyEvent
{ ... keyCode=..., scanCode=... }` from `WindowManager`. Keys injected over ADB never reach `InputKeyService`,
so check with the real remote.

## `INPUT_KEY_SERVICE=1` and volume repeat

On a second unit (55P71B Pro, same `G09_4K_GB` product, firmware `STT2.230203.001.AS23`, India) any bound
key-filtering accessibility service breaks key auto-repeat: long-pressing volume steps once per press,
with either helper alone or both. It is all-or-nothing on this build — either the app buttons above work,
or volume repeat does. `key_filter` in the launcher (`"key_filter": false` in `config.json`) lets the
service stay bound with filtering off, which is the repeat-keeping half of that choice; see
[docs/TROUBLESHOOTING.md](../../docs/TROUBLESHOOTING.md) and
[the unit's note](../../docs/devices/tcl-55p71b-pro-g09.md). The same unit also lost the volume beep for
good when launcherx was disabled — re-enabling every debloated package did not bring it back.

## Notes for this set

- The remote's Input button keeps working without Google TV's home: TCL draws that panel itself.
  `INPUT_KEY_SERVICE` is only needed for the app buttons above.
- Also verified on `G09_4K_GB` with firmware `STT2.230203.001.AS24` in India with the same debloat list.
- Same lists and buttons on a second unit with firmware STT2.230203.001 AS10 (G09_4K_GB_32BIT).
- Also verified on a TCL 55V6C (product `G09_4K_GB_NF`, firmware `STT2.230203.001.AS24`) in Colombia,
  including a reboot. This unit also ships `com.tcl.magiconnectfree` (MagiConnect, TCL's phone companion
  app), which the list disables. If a TCL soundbar is connected over HDMI eARC, keep
  `com.tcl.UpdatePeripheral` enabled so the soundbar gets firmware updates.
- AS24 also loses volume repeat. On the 55V6C, with `HomeKeyService` and `InputKeyService` bound, the gear
  and app buttons work, but holding volume up moves one step. Unbinding both restores the repeat.
- On the 55V6C, the gear button does nothing after `build-and-install.sh` and works after the first restart,
  with no helper bound. The Netflix, Prime Video, and YouTube buttons stay unmapped.
- The 55V6C Wi-Fi is a MediaTek module on an internal USB 2.0 bus (`btmtk_usb`). Expect about 100 to
  125 Mbps in both directions, which is enough for 4K. Speed tests in the TV's browser read lower because
  the CPU limits them.
- Firmware updates come through Play Services and are untouched.
- Screenshots come out black while HDMI or Live TV is on screen; press Home first.
- Switch USB debugging off when you're done; the launcher keeps working without it.
- With `com.tcl.ttvs` disabled, TvSettings falls back to TCL's own app tracker: a foreground service
  (`com.tcl.awareness.component.services.LoopTaskService`) that reads the open app every 2 s and keeps
  TvSettings (77-90 MB) pinned in memory for good. Enabling ttvs again is no cure, since it runs the same
  polling and turns its BI reporting back on. Instead, take the foreground away from TvSettings:
  `adb shell appops set com.android.tv.settings START_FOREGROUND ignore`. The service then runs as a plain
  one, TvSettings drops to cached and Android can reclaim it; TCL's quick menu (the remote's Menu key)
  still opens. Undo with `allow` instead of `ignore`. Details in #887.

### UI animation scaling (0.5x)

On this 2 GB Amlogic T5M chipset, navigation and app-switching latency can be noticeably reduced by halving the default 1.0x animation scales:

```bash
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put global window_animation_scale 0.5'
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put global transition_animation_scale 0.5'
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put global animator_duration_scale 0.5'
```

To reset to default 1.0x:

```bash
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put global window_animation_scale 1.0'
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put global transition_animation_scale 1.0'
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'settings put global animator_duration_scale 1.0'
```

### Stock YouTube RAM reclamation

If you replace the stock YouTube app with an alternative client like SmartTube (`org.smarttube.stable`, ~75 MB RAM), disabling stock `com.google.android.youtube.tv` reclaims nearly ~500 MB of RAM (~395 MB app process + ~100 MB recommendation worker) on this 2 GB set:

```bash
TV_HOST=<tv-ip> tools/venv/bin/python -I tools/tv.py 'pm disable-user --user 0 com.google.android.youtube.tv'
```

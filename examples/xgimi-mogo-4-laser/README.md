# XGIMI MoGo 4 Laser (Google TV 14 portable projector)

Model `WK03K`, device `altai`, MediaTek, 1.8 GB RAM, firmware `UKNV.260910.001.16312074`, French
unit. Both halves work: the debloat list and the Clean Your TV launcher as Home.

**Tested on 25 October 2026, one session of a few hours, then a reboot** — not the several days
[CONTRIBUTING](../../CONTRIBUTING.md) asks for. Every entry was applied and checked on the real unit;
what is not proven is that nothing surfaces after days of use.

This is the laser version of the [`xgimi-mogo-4`](../xgimi-mogo-4/) (WK08K, LED): same board, a newer
build, the same packages, and the same list apart from
`com.mstar.android.tv.disclaimercustomization`, kept here in case it is the laser safety notice. The
unit arrived on Android 11 (`RTMA.250416.085`) and took the Google TV 14 update before the list.

| File | What it is |
|---|---|
| `debloat.txt` | The 17 packages it disables (the tested unit also had the phone-remote service off), grouped and commented, with what to keep and why |
| `config.json` | Dock order, 5 columns, no tuner, `stock_home_redirect` and `key_filter` false; push it with `tools/theme.sh` |

All commands run from the repo root, with the projector's IP in `TV_HOST`.

## The laser

Nothing in the package list is laser-specific: the only XGIMI packages the MoGo 4 list does not
name are resource overlays. The light source, eye protection, autofocus and keystone run in the
XGIMI services listed under "DO NOT DISABLE", and none of the disabled packages was running a
service (`dumpsys activity services`). After the reboot the owner held a hand in front of the lens
with eye protection on, and the image dimmed as it should.

## Install

1. Check who owns the Wi-Fi network in use before setupwraith goes:

   ```bash
   TV_HOST=<projector-ip> tools/tv.py "dumpsys wifi | grep -E 'Currently Connected|cname='"
   ```

   On this unit the connected entry belonged to `com.google.android.tungsten.setupwraith`. Forget the
   network on the projector and join it again; the entry then shows `android.uid.system` and
   `tools/disable.sh` disables setupwraith without dropping Wi-Fi. Wireless debugging may need turning
   back on afterwards.

2. Run:

```bash
TV_HOST=<projector-ip> tools/disable.sh examples/xgimi-mogo-4-laser/debloat.txt
TV_HOST=<projector-ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<projector-ip> tools/theme.sh examples/xgimi-mogo-4-laser
```

`build-and-install.sh` disables launcherx, sees it come straight back (`com.xgimi.xrmservice`, as on
the MoGo 4) and stops with "launcherx owns Home again" before it enables the helper. The launcher is
installed at that point; switch the helper on by hand:

```bash
TV_HOST=<projector-ip> tools/tv.py "settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1"
```

Then press the **real** Home button: an injected `input keyevent HOME` landed on the stock launcher
every time, the remote's button opened the new one.

## `key_filter` false: the volume ramp comes back

With the helper filtering keys, holding volume up moves one step, as the MoGo 4 README describes.
`consume_volume_keys` does not help here: `dumpsys audio` logs every step as
`adjustSuggestedStreamVolume ... from android/WindowManager`, so the volume keys are handled before
the helper ever sees them, as on the TCL G08 in
[`docs/devices/tcl-55p71b-g09-key-repeat.md`](../../docs/devices/tcl-55p71b-g09-key-repeat.md).

`"key_filter": false` clears `FLAG_REQUEST_FILTER_KEY_EVENTS` while the helper stays bound. Home is
held by `stock_home_redirect`, which watches window changes, not keys, so it keeps working. Pressed on
the real remote with that config:

| Check | Result |
| --- | --- |
| Hold volume up / down | ramps continuously |
| Home | opens the new launcher |
| YouTube, Netflix, Prime Video buttons | open the app |
| Gear / Settings | works |
| Home after a reboot | the new launcher |

The app and gear buttons are handled by the firmware there, since launcherx and `xrmservice` stay
enabled, so `app_keys` and `settings_key_keycodes` are left out of `config.json`: with the filter off
the helper never sees them. The raw codes from `getevent -lt`, for anyone who turns the filter back on:
YouTube `KEY_RED`, Netflix `KEY_GREEN`, Prime Video `KEY_BLUE`, gear `KEY_F8`; the Android keycodes they
map to were not checked.

## Memory

Measured with `dumpsys meminfo` in the same session: the new launcher at about 57 MB PSS, launcherx
at about 125 MB once opened. There is no whole-system figure from before the list.

## Notes on `config.json`

The dock is Netflix, YouTube, Disney+ and Prime Video; with `columns` 5 the fifth slot fills itself
from the grid. Reorder from the projector with a long press on the Settings pill.

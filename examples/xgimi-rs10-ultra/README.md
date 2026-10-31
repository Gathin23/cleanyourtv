# XGIMI RS10 Ultra projector (Android 9)

Tested on an XGIMI RS10 Ultra projector (`ro.product.model=XGIMI TV`, `ro.product.device=pig_chivalrous`)
on the MediaTek m7642 board, Android 9 / SDK 28, build `PPR2.180905.006.A1` (`xgimi10101741`), China
GMUI 6 firmware. 4 GB RAM (`dumpsys meminfo` reports 3,714,636 kB total), 106 packages, none disabled
from the factory.

**How much testing is behind it:** one session on 2026-10-31 with four reboots, not the several days
[CONTRIBUTING](../../CONTRIBUTING.md) asks for. Every package was applied in five batches with a
screenshot between them; the launcher, HDMI 1/2, Settings, Wi-Fi and the app library were checked after
the reboot.

## What this folder contains

| File | What it is |
|---|---|
| `debloat.txt` | The 21 packages that were disabled, grouped and commented; `com.xgimi.upgrade` is commented out so the default list keeps OTA |
| `config.json` | The author's dock (SmartTube, Netflix, VidHub), 4 columns, no fill, `stock_home_redirect`, `key_filter` false, the HDMI input action and the two LAUNCHER-only tiles |
| `tv.json` | Entry for the supported-TV index |
| `README.md` | Notes for this firmware: ADB, Settings, Home handover, dock |

## Critical notes for this TV

1. **No working ADB toggle.** Developer options and the factory menu do not expose a working ADB
   switch on this build. ADB was opened with a local helper running the two `setprop` commands below.
   Do not launch the factory packages (`mediatek.factorymenu.ui`, `com.xgimi.minitvfactory`) to look
   for one; the repository's rules forbid starting factory/test packages. The firmware reports
   `ro.adb.secure=0`: while adbd runs, any device on the LAN has a shell. Stop it with
   `setprop ctl.stop adbd` or a reboot when you are done.
2. **The stock home comes back.** `com.xgimi.xrmservice` re-enables `com.xgimi.home` on its remote
   keys. `stock_home_redirect=com.xgimi.home` with `HOME_KEY_SERVICE=1` sends its home screen back to
   the new launcher; the stock launcher itself is disabled by `launcher/build-and-install.sh`.
3. **`HomeKeyService` is dropped on every boot.** `restore_key_services: true` did not put it back
   (four reboots); the author's BOOT_COMPLETED helper re-adds it. Without that helper, re-add it over
   ADB after a reboot (command below).
4. **Settings is a service window, not an Activity.** The launcher's Settings pill needs a small
   helper activity in `settings_fallback_components`; that key is `config.xml` only, so a theme push
   cannot carry it.
5. **HDMI:** `input_action=com.xgimi.action.hdmiPlayer` opens
   `com.xgimi.xhplayer/.InputActivity`. `xhplayer` is hidden from Home in `config.json` but the
   Inputs menu still opens it.
6. **LAUNCHER-only apps:** 极米投屏 and File Manager have a LAUNCHER entry but no LEANBACK entry;
   `library_apps` seeds them or the launcher does not list them.
7. **OTA:** `com.xgimi.upgrade` is commented out so the default list keeps updates; the author's TV
   disables it by choice (uncomment it to match).
8. **Screensaver:** `com.xgimi.atmosphere` is disabled in the list; the author uses Aerial Views, with
   the launcher's Clock screensaver as the fallback.

## Apply

```sh
TV_HOST=<tv ip> tools/disable.sh examples/xgimi-rs10-ultra/debloat.txt
```

Windows:

```powershell
tools\disable.ps1 -TvHost <tv ip> examples\xgimi-rs10-ultra\debloat.txt
```

## Launcher and Home

Install the launcher after the list. The stock home is disabled by the install script, not by the list:

```sh
TV_HOST=<tv ip> HOME_KEY_SERVICE=1 RESTORE_KEY_SERVICES=1 STOCK_LAUNCHER=com.xgimi.home launcher/build-and-install.sh
```

`stock_home_redirect=com.xgimi.home` in `config.json` and `HOME_KEY_SERVICE=1` hold Home:
`com.xgimi.xrmservice` re-enables `com.xgimi.home` on its remote keys, and the redirect sends its home
screen back to the new launcher. The stock home is
`com.xgimi.home/com.xgimi.module.cellview.home.ui.HomeActivity`.

## ADB on this firmware

Developer options and the factory menu do not expose a working ADB switch on this build. ADB was
opened by running these two commands from a small local helper activity (not part of this repository):

```sh
setprop service.adb.tcp.port 5555
setprop ctl.start adbd
```

The unit then answers `adb connect <projector ip>:5555` and `tools/tv.py`. Do not launch the factory
packages (`mediatek.factorymenu.ui`, `com.xgimi.minitvfactory`) to look for a switch; the repository's
rules forbid starting factory/test packages. The firmware reports `ro.adb.secure=0`: while adbd is
running, any device on the LAN has a shell. Stop it with `setprop ctl.stop adbd` or a reboot when you
are done.

## Settings is a service window

`com.android.newsettings` has no `android.settings.SETTINGS` activity; its Settings UI is a floating
service window:

```sh
am startservice -n com.android.newsettings/com.xgimi.newsettings.SettingService \
  -a com.xgimi.settings.SETTINGS -d 'Settings://com.xgimi.settings/main'
```

The launcher's Settings pill falls back to `settings_fallback_components`; that key is `config.xml` only,
so set it to a small helper activity that starts the service above before building. The author used a
local helper; the app library still lists the Settings app, and the XGIMI settings entry can also be
opened from the app library.

## HomeKeyService on every boot

GMUI 6 drops `com.gathin.tvhome/.HomeKeyService` from `enabled_accessibility_services` on every boot.
`restore_key_services: true` did not put it back (tested across four reboots); the author's helper
re-adds it from a BOOT_COMPLETED receiver. Without that helper, after a reboot re-enable it over ADB:

```sh
settings put secure enabled_accessibility_services "com.xgimi.duertts/com.xgimi.duertts.MonitorService:com.gathin.tvhome/.HomeKeyService"
settings put secure accessibility_enabled 1
```

`com.xgimi.duertts` is the existing voice/TTS service; keep it in the list.

## Inputs and the dock

- HDMI 1 (connected) and HDMI 2 open in `com.xgimi.xhplayer/.InputActivity`;
  `input_action=com.xgimi.action.hdmiPlayer`.
- `config.json` pins SmartTube, Netflix and VidHub; `fill_dock=false` leaves the fourth 4-column slot
  empty. Dangbei Market is not in the dock.
- `library_apps` seeds 极米投屏 (`com.xgimi.wirelessscreen`) and File Manager
  (`com.xgimi.filemanager`): both have a LAUNCHER entry but no LEANBACK entry, so the launcher would
  otherwise not list them.
- The author uses Aerial Views as the system screensaver; `com.xgimi.atmosphere` is disabled in the
  list. The launcher's own Clock screensaver remains a fallback.

## OTA

`com.xgimi.upgrade` is commented out in `debloat.txt` so the default list keeps firmware updates. The
author's TV disables it by choice; uncomment the line and re-run `tools/disable.sh` for the same. The
XGIMI store/account/payment packages are disabled by the owner's choice in the list.

## Restore

```sh
TV_HOST=<tv ip> tools/restore.sh examples/xgimi-rs10-ultra/debloat.txt com.xgimi.home
```

Windows:

```powershell
tools\restore.ps1 -TvHost <tv ip> examples\xgimi-rs10-ultra\debloat.txt -Stock com.xgimi.home
```

The Undo line in `debloat.txt` passes the same `com.xgimi.home`.

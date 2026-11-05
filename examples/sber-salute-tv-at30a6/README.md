# Sber Salute TV 43" (StarOS on Android 13)

CVTE board `TP.T962D4.PC756` (Amlogic T962D4, platform `t5w`, Mali-G31), 2 GB RAM, 8 GB eMMC with a 3 GB data
partition, panel `LE43VA_V430DJ1_Q01`, codename `at30a6`, `ro.cvte.customer=SBER`. Firmware StarOS
1.101.84-B28350 on Android 13, build `TP1A.220905.004.A2` from 15 February 2026. No Google services.

Both halves work: the debloat list and the Clean Your TV launcher as Home. Tested 19–23 October 2026: the
content apps and screen sharing since the 19th, the rest of the list since the 21st, with several restarts.
The steps were done by hand over `adb`, with the same commands the scripts below run.

| File | What it is |
|---|---|
| `debloat.txt` | The 26 packages to disable, grouped and commented; the two firmware-update packages the tested unit also disabled are left as comments |
| `config.json` | Hidden apps, no Live TV entry, the app library on, Russian names for three Sber apps; push it with `tools/theme.sh` |

All commands run from the repo root, with the TV's IP in `TV_HOST`.

## Turning ADB on

Developer options are locked: pressing OK on the build number seven times does nothing.

Sber documents its own switch ([Подключаем ADB](https://developers.sber.ru/docs/ru/va/native/testing/adb)): sign
in to Studio at developers.sber.ru with the same Sber ID as the TV, open Settings → Service settings → SmartApp →
My devices, add the TV by its serial number from About and turn ADB on. The SmartApp section only appears once the
personal space has a SmartApp project, so create an empty one first. On this unit the switch turned on, but port
5555 stayed closed through several restarts and no prompt appeared on the TV.

What worked is the CVTE factory menu, which has its own ADB switch:

1. Install the `FactoryMenu` shortcut (`com.stb.factorymenu` 1.2, 16 KB) from
   [the 4PDA post](https://4pda.to/forum/index.php?showtopic=1042253&view=findpost&p=114157954) that the CV9632
   thread links under "Сервисное меню / Меню разработчика / Активация ADB". StarOS installs APKs from a USB
   stick through the file manager, or from your own upload at apps.sber.ru/my. The shortcut only starts the
   firmware's own factory menu (`com.cvte.fac.menu`, through its `TvMenuWindowManagerService` with the
   `factory_menu` command); it holds no permissions.
2. Open it, go to **Debug** and turn **ADB Enable** on. Port 5555 opened within seconds; accept "Allow USB
   debugging?" with "Always allow".
3. Leave everything else in that menu alone: it also holds panel, picture and test-tool settings
   (`/system/etc/menu_config_xml/fac_menu_tree.xml` lists them).

Afterwards `adb_enabled=1`, `persist.sys.usb.config=adb` and `service.adb.tcp.port=5555`, so ADB listens on Wi-Fi.
The TCP port is not persistent; to keep it across restarts while you work:

```bash
TV_HOST=<tv-ip> tools/tv.py 'setprop persist.adb.tcp.port 5555'
```

Clear it with `setprop persist.adb.tcp.port ""` and switch debugging off when you are done.

The factory menu on CVTE boards is risky ([Troubleshooting](../../docs/TROUBLESHOOTING.md), the Input key and CEC).
On this unit, after it had been opened to turn ADB on, `sys.factorymenu` stayed unset, `hdmi_control_enabled`
stayed 1 and HDMI and the remote kept working. Don't open the other `com.cvte.*` apps.

## Install

```bash
TV_HOST=<tv-ip> tools/disable.sh examples/sber-salute-tv-at30a6/debloat.txt
TV_HOST=<tv-ip> STOCK_LAUNCHER=ru.sberdevices.starlauncher launcher/build-and-install.sh
TV_HOST=<tv-ip> tools/theme.sh examples/sber-salute-tv-at30a6
TV_HOST=<tv-ip> tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
```

The last line matters: while `screensaver_default_component` points at Sber's screensaver, StarOS re-enabled
`ru.sberdevices.starscreensaver` at boot once. With the launcher's clock as the screensaver it stayed disabled.

Disabling does not free the space an updated system app takes in `/data`: Zvuk's update alone is about 100 MB, and
the screensaver keeps about 220 MB of cached artwork. On the tested unit the list, with those updates removed and
the screensaver's data cleared, took free space on `/data` from 406 MB to 968 MB.

## App "rotation": why apps vanish on their own

StarOS uninstalls ("rotates") apps, keeping their card, whenever free space on `/data` drops below a limit. The
check runs every 10 minutes:

```
SPS/SmartApp.SmartAppWidgetManagerV2: availableSpace 407 Mb more then freeSpaceLimit = 355 Mb
```

The limit is Android's low-storage threshold (`sys_storage_threshold_percentage`, 5 % by default) plus 200 MB from
Sber's remote config, 355 MB on this partition. On the tested unit three sideloaded apps had already been rotated
away before the debloat. Lowering Android's part to 1 % brings the limit to 231 MB after the next restart (the
`SystemLimits` line in logcat shows it):

```bash
TV_HOST=<tv-ip> tools/tv.py 'settings put global sys_storage_threshold_percentage 1'
```

Undo with `settings delete global sys_storage_threshold_percentage`.

## The Sber platform service

`ru.sberdevices.services` runs as the system uid. It holds the Salute voice assistant (`AssistantService` and the
`:assistant_bar` process), Sber ID sign-in, the rotation above and the reinstalling of Sber's "mandatory" apps.
It cannot be taken apart from ADB:

- `pm disable-user` on the package is undone within a second by `ru.sberdevices.services.core`
  (`SPS_CORE/WatchdogManagerImpl`), and the processes come straight back.
- Disabling single components is refused for the shell on Android 13:
  `Shell cannot change component state for ComponentInfo{ru.sberdevices.services/...}`.

So it stays, and with it the voice assistant, which still works with everything in the list disabled. The remote
is a Bluetooth LE HID device; its microphone appears to Android as `AUDIO_DEVICE_IN_BUILTIN_MIC` named `RC In`.

## Launcher notes

- **Inputs** works without extra config: `ru.sberdevices.tvinputs` handles `VIEW` on `content://android.media.tv`
  passthrough URIs, so the pill lists HDMI1, HDMI2, HDMI3 and AV1 and switches to them. Sber's Live TV has no
  leanback entry, so `live_tv_package` is empty.
- **Settings** opens the Android TV settings side panel (network, picture, sound, remotes).
- Sber's own apps have no leanback entry. The app library that `config.json` turns on is where to add the old
  Sber home ("Салют ТВ") or the app catalogue if you keep them enabled.
- Home stays with the launcher across restarts. The remote's Home key goes to it too.

## Undo everything

```bash
TV_HOST=<tv-ip> tools/restore.sh examples/sber-salute-tv-at30a6/debloat.txt ru.sberdevices.starlauncher
TV_HOST=<tv-ip> tools/tv.py 'settings delete global sys_storage_threshold_percentage'
TV_HOST=<tv-ip> tools/tv.py 'settings delete secure screensaver_components'
```

Removed system-app updates come back through the Sber app catalogue. The launcher stays installed but is no
longer Home.

## What stays

The platform service and the assistant (above), `ru.sberdevices.tvinputs` (HDMI and AV playback),
`ru.sberdevices.app.starkeyboard` (the only keyboard), `ru.sberdevices.webview` (the WebView provider), both
system UIs, `SberDevicesServiceSystem`, the location provider, the setup app, Sber's settings, the overlays and
resources, every `com.droidlogic.*` package (tuner, HDMI, picture) and every `com.cvte.*` package (TV API, media
centre, factory apps).

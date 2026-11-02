# X.Vision 50XYU765 (CVTE TP.MT9632.PC752, MediaTek m7332), Android 11: notes

One set in Iran, two days of normal use, debloat and the reference launcher. Settings → System Info shows only "Production Model XYU Series",
"Firmware Version 20221215_115723", Android 11, 4 cores and a Mali-G52; `tools/tv.py --probe` gives manufacturer
`MAADIRAN`, device `m7332`, build `m7332_eu-userdebug`. The factory menu names the board `TP.MT9632.PC752`. The same
CVTE software family as `examples/sankey-smart-tv-hi3751v350/` and `examples/vision-2024vision43stv/`, on a MediaTek
chip. The same `m7332_eu` board, under Yandex TV instead of CVTE's own launcher and with a different factory-menu key
sequence, is in `docs/devices/tuvio-tq55ufgsv1.md`.

## Turn on ADB

There is no Developer options screen and no Build number to press: the only settings apps are `com.cvte.settings`
and `com.cvte.tv.setting`, and `com.android.settings` is not installed at all. ADB is in CVTE's factory menu:

1. Switch to a source first (TV or HDMI). The menu does not open from the home screen.
2. Press **MENU**, then **1 1 4 7** quickly.
3. **DEBUG → ADB ENABLE → ON**, then leave with Back. The cursor opens on **SHIPPING INIT**, a factory reset: do not
   press OK there. Rows marked `>>>` (SHIPPING INIT, AGING MODE, SW UPGRADE, BOARD INIT) run at once; rows marked
   `--->` open a submenu. Touch nothing but DEBUG.

On the tested set **ADB ENABLE was already ON from the factory**. `ro.adb.secure` is empty on this userdebug build,
so port 5555 answers with no "Allow USB debugging?" prompt: anyone on the network gets a shell while it is on.
Switch it **OFF** in the same menu when you are done and check with `tools/adb-closed.py <tv ip>`. While it is on,
`sys.usb.config` is `adb`, and on a similar CVTE MediaTek board (`docs/devices/mtek-mk32cs1nb.md`) USB devices were
not seen until it was off and the set had been unplugged once.

Opening the menu this way did not trip a factory mode: the source menu, the inputs and the remote kept working
through several reboots. Never start the factory packages (`com.cvte.fac.menu`, `com.cvte.factory.*`) over ADB.

## Things this firmware does not have, or has differently

- **Play Store and Play Services were disabled before anything was changed** (`enabled=3`, the user-disabled state),
  and **enabled again on their own the same evening**: neither this list nor the owner touched them, and they stayed
  enabled through the two days. Treat their state as the firmware's, not as part of the list. The owner uses
  Cafe Bazaar and NetStore. Sideloaded stores need the install permission, and with no Android Settings
  app there is no screen for it: grant it over ADB, e.g.
  `appops set com.farsitel.bazaar.tv REQUEST_INSTALL_PACKAGES allow`, then `appops write-settings`.
- **No App info page and no app manager.** `APPLICATION_DETAILS_SETTINGS`, `MANAGE_APPLICATIONS_SETTINGS` and
  `MANAGE_UNKNOWN_APP_SOURCES` resolve to nothing. The only on-TV uninstall is the stock Apps grid, which is
  `com.stark.store`; that is why the list keeps the store by default.
- **No exFAT.** `/proc/filesystems` lists vfat, ntfs and ext4 and nothing loads exFAT; format USB drives as FAT32
  or NTFS.
- **No FallbackHome.** `com.cvte.tv.launcher.space` is the only HOME activity, so it stays enabled.
- **Two Home intents.** `ro.CVT_EN_COMMON_HOME_INTENT=1`: Home and boot send `MAIN` + `HOME` + `COMMON_HOME`.
  `ro.CVT_EN_HOME_KEY_ENTER_TV=1`: Home pressed while a home screen is in front switches to the last source, so the
  first Home press from Home lands on live TV and the second comes back.
- **Inputs are registered** with TvInputManager (HDMI 1-3 are `HW6`-`HW8`, AV `HW2`, the tuners `HW0` and `HW1`),
  but nothing on the set handles the standard passthrough channel URI. Inputs only switch through CVTE's TIF player:
  `am start -a com.cvte.intent.ACTION_TIF_PLAYER -n com.cvte.tv.api.impl/com.cvte.tv.api.vendor.common.base.tif.TvSupportActivity --ez tune true --ei sourceId N --es toActivity com.cvte.tv.setting.TvSettingActivity`,
  with N the number after `/HW`: DTV 0, ATV 1, AV 2, HDMI 1-3 6, 7, 8 (all checked against `dumpsys tv_input`).

## HDMI-CEC

CEC ships switched off (`settings get global hdmi_control_enabled` is 0), although the board has it
(`ro.CVT_EN_CEC=1`). Turned on with `settings put global hdmi_control_enabled 1`, Android's CEC service works on this
set: a PlayStation 5 answered with its name, and TvInputManager listed it as a child input of its port
(`com.mediatek.tvinput/.hdmi.HDMIInputService/HDMI100004`), which the launcher's glass Inputs dropdown shows as
"PlayStation 5 · HDMI 1" with a Connected dot. With this folder's `config.json` the Inputs pill opens CVTE's picker
instead (see "Inputs pill" below), so the names only show where the dropdown is used. What was seen on the tested set:

- **Ports match the labels.** The PS5 reported `1.0.0.0`, `2.0.0.0` and `3.0.0.0` in the sockets labelled HDMI 1, 2
  and 3, which are `HW6`, `HW7` and `HW8`.
- **A moved cable can leave the old port.** In socket 3 the PS5 announced `3.0.0.0` only in `<Active Source>`, which
  does not update Android's device list, so the dropdown kept "HDMI 1" until a rescan:
  `settings put global hdmi_control_enabled 0`, then `1` (a cold boot does the same). After that it read "HDMI 3".
- **Wake and standby are two more switches, both off by default.** `hdmi_control_auto_wakeup_enabled` lets a device
  wake the TV: with it on, the PS5's `<Text View On>` arrived when it was switched on.
  `hdmi_control_auto_device_off_enabled` makes the TV broadcast `<Standby>` when it is switched off; the TV sent it,
  but this PS5 stayed on. On the next wake the TV sends `<Request Active Source>` and a PS5 that is still on answers
  with `<Active Source>`, but the set stays on Home: CVTE's own handler did not switch input, so pick the PS5 from
  Inputs. Waking the TV from the PS5 also came up on Home.
- **The remote goes to the device.** On a CEC device's input the TV forwards the remote's keys as
  `<User Control Pressed>`; the PS5 ignored them, so the remote looked dead there. Home is not forwarded and returns
  to Home; the PS5 itself is driven with its controller.

## The reference launcher

Install it with the stock launcher left alone and the Home helper on, then push this folder's `config.json`:

```sh
TV_HOST=<tv ip> STOCK_LAUNCHER=none HOME_KEY_SERVICE=1 launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/xvision-50xyu765
```

- **Home chooser.** `set-home-activity` covers plain `HOME` only, so the first Home press shows Android's chooser
  ("LauncherSpace4" or "Home"). Pick **Home**, then **Always**, on the real remote: an injected OK on the chooser was
  taken by CVTE's key handling and switched to live TV instead. After that both intents resolve to the new launcher,
  and the choice survived reinstalls and reboots.
- **Power-on.** The new Home is not direct-boot-aware, so the set still boots to the stock launcher (#1201, #1448).
  `stock_home_redirect` with `HOME_KEY_SERVICE=1` sends it on: the stock home shows for a few seconds, then the new
  launcher comes up, on every reboot tested.
- **`key_filter` off.** With the Home helper filtering keys, held Volume Up stepped once per press instead of
  ramping, the trade `docs/devices/tcl-55p71b-g09-key-repeat.md` describes. `stock_home_redirect` works from window
  events, not key filtering, so `config.json` sets `key_filter` to false: the volume ramps again (checked on the real
  remote) and the redirect still worked through a reboot. The price is the helper's button remaps, which this set
  does not need.
- **Inputs pill.** With no Live TV package set it opens CVTE's own source picker (`source_panel_service`). The
  launcher's glass dropdown would list the HDMI ports, but its rows use the passthrough URI, which nothing answers
  here (see above).
- **TV and Files tiles.** `com.cvte.tv.setting` (live TV) and `com.android.documentsui` (Android's file manager) have
  no leanback entry, so `library_apps` puts them on Home, with `app_labels` and the bundled `banner_tv` artwork. The TV
  tile opens the last source; the remote's channel keys work there.
- `com.android.traceur` (System Tracing) shows up as a tile and cannot open without Developer options, so
  `hidden_apps` drops it.

Screenshots work on the stock home screen and the new launcher; live TV and HDMI give a blank frame.

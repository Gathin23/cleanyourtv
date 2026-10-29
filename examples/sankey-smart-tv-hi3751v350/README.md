# Sankey SMART_TV (CVTE, HiSilicon Hi3751V350), Android 12: notes

One set, one day, debloat and the reference launcher. Settings → System info shows only "Firmware version
20231115_180213_01", "Production model SMART_TV", Android 12, 4 cores and Mali-450; `tools/tv.py --probe` gives
`Hi3751V350` / `Hi3751V352E_DMO`, manufacturer Hisilicon, and every app around the TV is `com.cvte.*`.

## Turn on ADB

There is no Developer options screen and no Build number to press, and Activity Launcher finds no development
activity: the only settings apps are `com.cvte.settings` and `com.cvte.tv.setting`. ADB is in CVTE's factory menu:

1. Switch to a source first (TV or HDMI). The menu does not open from the launcher.
2. Press **MENU**, then **1 1 4 7** quickly.
3. **DEBUG → ADB ENABLE → ON**, then leave with Back. Touch nothing else in that menu.

`ro.adb.secure` is empty: port 5555 answers with no "Allow USB debugging?" prompt, so anyone on the network gets a
shell while it is on. Switch it **OFF** in the same menu when you are done and check with `tools/adb-closed.py <tv ip>`.

Opening the menu this way did not trip a factory mode: `sys.factorymenu` stayed 0, `persist.sys.AgingMode` 0, and
Play services, Play Store and YouTube were still enabled after four reboots. `com.cvte.fac.menu` runs from boot
anyway; it shows in `dumpsys meminfo` whether or not the menu was opened. Never start it over ADB.

## Things this firmware does not have

- **No App info page and no app manager.** `android.settings.APPLICATION_DETAILS_SETTINGS` and
  `MANAGE_APPLICATIONS_SETTINGS` resolve to nothing. The only way to uninstall an app on the TV is the stock
  launcher's Apps grid, which is `com.stark.store/.ui.select.AppSelectActivity`, so keep `com.stark.store` while you
  use the stock launcher. Settings and Wi-Fi do resolve, to `com.cvte.settings`.
- **No FallbackHome.** `com.cvte.tv.launcher.space` is the only HOME activity: never disable it (the Vision CVTE notes
  in `vision-2024vision43stv/` show what happens). `ro.crypto.state` is `unsupported`, but Android still starts Home
  before the user unlock, so the stock launcher (direct-boot-aware) is what comes up at power-on.
- **Two Home intents.** `ro.CVT_EN_COMMON_HOME_INTENT=1`: the Home key and boot send `MAIN` + `HOME` +
  `COMMON_HOME`. `ro.CVT_EN_HOME_KEY_ENTER_TV=1`: Home pressed on the stock home screen switches to the last source.

## The reference launcher

Install it with the stock launcher left alone:
`launcher\build-and-install.ps1 -TvHost <tv ip> -StockLauncher none` (or `STOCK_LAUNCHER=none`). It installs,
starts and becomes the default for plain `HOME`, and since #2219 its HOME filter lists `COMMON_HOME` beside `HOME`,
so the Home key reaches it on a set with `ro.CVT_EN_COMMON_HOME_INTENT=1` too. Power-on still opens the stock
launcher, because the launcher's Home activity is not direct-boot-aware.

On the tested set the power-on half was fixed by a local build (a direct-boot-aware
Home activity that waits on a black screen for `ACTION_USER_UNLOCKED` before reading its settings), then choosing
the launcher with **Always** in Android's home picker once. That is a change to the Home path, tracked in #1448
and #1201 rather than carried here. The firmware's missing App info page is #1202.

`config.json` in this folder makes the **Inputs** pill open the TV's own source picker (DTV, ATV, AV, HDMI 1-3):
the HDMI ports are not registered with TvInputManager, only an IPTV app's input is. It also hides System Tracing
(`com.android.traceur`), which shows up as a tile and cannot open without Developer options. Push it with
`tools/theme.sh examples/sankey-smart-tv-hi3751v350`.

Screenshots work on the stock home screen too, live TV preview included.

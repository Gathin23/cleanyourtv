# Next NEK7

A MECOOL-built Amlogic box sold in Turkey as the Next NEK7: Android TV 9 (SDK 28), build
`PPR1.180610.011` / `V9.20221214.151020`, product `franklin`, board `newton`, a `userdebug` build with
`ro.adb.secure=0`. No HDMI input, and it does have a DVB-S/S2 tuner whose driver this firmware never loads, so
out of the box it behaves like the streaming box this list was written for: see "The satellite tuner" below
before setting `live_tv_package` in `launcher/app/src/main/res/values/config.xml`.

The stock home is Droidlogic's `com.droidlogic.mboxlauncher`. The Google launcher
(`com.google.android.leanbacklauncher`) looks like a second way back, but on this firmware the APK is a stub:
604 KB with no activities at all, so it can never come up as Home. See "Home when the launcher is missing"
below before turning both launchers off.

## Apply

```bash
TV_HOST=<box ip> tools/disable.sh examples/next-nek7/debloat.txt
TV_HOST=<box ip> launcher/build-and-install.sh
```

```bash
TV_HOST=<box ip> launcher/build-and-install.sh
```

`com.droidlogic.mboxlauncher` is one of the stock launchers the script recognises by name, so this box's Home
is disabled without being named; `STOCK_LAUNCHER=<package>` still overrides what it would pick.

Home was confirmed twice over: `dumpsys window | grep mCurrentFocus` named the new launcher, and after one
reboot `pm list packages -d` still held the same packages and Home was still the new one.

## Undo

```bash
TV_HOST=<box ip> tools/restore.sh examples/next-nek7/debloat.txt com.droidlogic.mboxlauncher
```

## Home when the launcher is missing

Two things on this firmware make a broken Home worse than usual, and both were measured on a second NEK7.

First, the safety net never appears: `com.android.tv.settings/.system.FallbackHome` is the only Home left once
the launcher is off, and it draws nothing. The screen goes to a plain dark panel with no text, and
`uiautomator dump` returns an empty `FrameLayout` with no children. Nothing on screen can be pressed to fix it.

Second, `com.google.android.leanbacklauncher` cannot take over. It was enabled with
`pm enable --user 0 com.google.android.leanbacklauncher com.google.android.leanbacklauncher.partnercustomizer`,
and in that enabled state `pm list packages -e` showed both, `dumpsys package` said `enabled=1`, and still neither
turned up in `cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME`; with
the working launcher disabled, Home landed on the empty FallbackHome instead. The installed APK explains it:

```bash
adb pull /system/priv-app/LeanbackLauncher/LeanbackLauncher.apk
$ANDROID_HOME/build-tools/34.0.0/aapt2 dump xmltree --file AndroidManifest.xml LeanbackLauncher.apk | grep -c 'E: activity'
# 0
$ANDROID_HOME/build-tools/34.0.0/aapt dump badging LeanbackLauncher.apk | head -2
# package: name='com.google.android.leanbacklauncher' versionCode='1901105100' versionName='90.11.5-100-4440948' ...
# (no application, no launchable-activity)
```

So turning that stub off costs nothing, and it is not a way back. With both stock Home packages off, ADB is the
only recovery path, and it works:

```bash
adb shell pm enable --user 0 com.gathin.tvhome
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
```

No reboot and no `set-home-activity` were needed: FallbackHome finishes itself within seconds of a real Home
being enabled, and the stored Home preference survives a disable/enable cycle, so the launcher comes back as
default on the next Home press and after a reboot. A process crash is also self-healing: `kill -9` on the
launcher left a new pid with the same Home focused a few seconds later.

For a real fallback, a tiny third-party Home is enough. Tested on this box: `tw.hungmi.justalauncher`
([just-a-launcher](https://github.com/hungmi/just-a-launcher), MIT, 34 KB APK, minSdk 21, `HOME` and
`LEANBACK_LAUNCHER`, no ads, no telemetry, no services). Installed beside the launcher and left enabled but not
default, it costs nothing until it is needed, then it draws an app grid instead of a blank screen:

```bash
adb install just-a-launcher.apk
# leave com.gathin.tvhome as the default Home; the fallback only runs if that one is gone
adb uninstall tw.hungmi.justalauncher   # to remove it again
```

Measured on the same box, both as Home and idle: just-a-launcher 33.7 MB PSS and 0% CPU, the launcher 52.2 MB
PSS and 0% CPU. Disabling the launcher and pressing Home brought just-a-launcher up and its grid rendered;
enabling the launcher brought the launcher back without extra commands.

## ADB on this box

Network debugging is the only transport, on port 5555, and it survives reboots. `ro.adb.secure=0` means
there is no "Allow USB debugging?" prompt: any device on the same network gets a shell from this box while
debugging is on, so switch it off when the work is done.

## screencap returns 0 bytes on this firmware

`tools/tv.py --screenshot` writes an empty PNG on this box, on every screen, including Settings and the new
Home — not only Live TV or HDMI like the note in the prompt. It fails the same way as root, and logcat says
why:

```bash
tools/tv.py 'logcat -c; screencap -p /data/local/tmp/e.png; logcat -d -t 30'
# W SurfaceFlinger: FB is protected: PERMISSION_DENIED
```

So nothing can be verified from pixels on this set. This is per unit, not per build: a second NEK7 on the same
firmware returns full PNGs (`screencap` writes 268 KB, `tools/tv.py --screenshot` works), so check the unit before
falling back to the text checks. Two text checks stand in for it and agree with what the
screen shows:

```bash
tools/tv.py 'dumpsys window | grep mCurrentFocus'
tools/tv.py 'uiautomator dump /data/local/tmp/ui.xml; cat /data/local/tmp/ui.xml'
```

The dump carried the greeting, the clock, the date, the Wi-Fi name and the pills, so the launcher's own views
are reachable that way. Taps for the pills are taken from the dump's `bounds` and sent with `input tap <x> <y>`:
the Settings pill opened `com.android.tv.settings/.MainSettings`, and `KEYCODE_HOME` came back to the new Home
from Settings and from YouTube.

## The satellite tuner

The box has a DVB-S/S2 frontend (an AVL6221C demod, with `/dev/dvb0.*` beside it), and the list above keeps
`th.dtv` enabled for it. What never happens is the `insmod`: `/vendor/build.prop` sets
`ro.platform.vs.dtv=1`, while `/vendor/etc/init/hw/init.amlogic.board.rc` loads `vs_frontend_fe.ko` only when
that property is 0, so `/dev/dvb0.frontend0` is missing and the live TV app reports no signal on every
transponder.

```bash
tools/tv.py 'getprop ro.platform.vs.dtv'          # 1
tools/tv.py 'ls /dev/dvb0.*'                      # ca0 ca1 demux0-2 dvr0-2 net0-2, and no frontend0
```

Loading the module is a root step that writes a system partition, which nothing in this repo does, so
`live_tv_package` stays empty here and the box is debloated as the streaming set the list describes. With the
module loaded the frontend registers — `DVB: registering adapter 0 frontend 0 (vs_frontend)`, `Detect demod type
[ DVBS2/S2X AVL6221C ]` — and `th.dtv` tunes, on one app-side condition: the antenna setup's LNB supply has to
be On. Until it is, `logcat` repeats `lock tp freq` with `voltage:0` and `Sat Tuner[0] ---> Unlocked`; with it
On, the same transponder answers `Sat Tuner[0] ---> Locked` (11747 H 27500, 64% signal and 49% quality on the
first unit). A scan on that transponder still stored 0 services, which fits the current Türksat 4A list:
11747 H 27500 is no longer on it, and 12380 V 27500 or 12423 H 30000 are the network-search transponders to
try instead.

Both units also leave the network on their own. The UWE5621 driver fails in power save
(`sprdwl_tx_ba_mgmt ... tx_addba failed, reason=1, lut_index=6, tid=0, map=64`) and the link dies with it;
`iw dev wlan0 set power_save off` holds it, but Android turns power save back on, so a NEK7 that drops off
Wi-Fi is behaving as its driver does, not as something the list did.

## What the list turns off

21 packages: the factory test and burn-in test apps, a vendor "TVOptimizer" shipped as
`com.android.core.process` with a `BOOT_COMPLETED` receiver, an ad consent SDK shipped as an app, an eco-saver
daemon, the Play auto-install list, feedback and bug-report senders, two recommendation services, three
screensavers, `printspooler`, `camera2` and the stock music player, the two Google sync adapters, the discontinued
Play Music, and the preloaded "MoviePlayer" / Film Oynatıcı app while MX Player stays. The
owner's own streaming apps, D-Smart Go, the YouTube app and Next's own IPTV app, are in the list commented out with "this set has them
off; uncomment to disable": they were off on this box, but a shared list leaves the next owner's own apps alone. One receiver the owner does not use goes as well:
AirPin PRO (`com.waxrain.airplaydmr`), which starts at boot, holds `INSTALL_PACKAGES` and `DELETE_PACKAGES` and
listened on 49153, 5000, 7000, 7100, 7700 and 8888. Miracast (`com.droidlogic.miracast`) is left on, so screen
mirroring from an Android phone still works, and so is the tool server the owner runs
(`dev.vodik7.atvtoolsserver`, on 15020 and 19002-19005). Forcing AirPin to stop left port 8888, which belongs to
the vendor `supercam` service and is not part of this list.

Kept on purpose: every `com.droidlogic.*` service (settings, file browser, subtitle service, Bluetooth remote,
Miracast), the firmware updater `com.droidlogic.vsota`, the vendor store and the sideload
drawer, `th.dtv`, Next's own IPTV app, the owner's IPTV apps, Netflix and its hook,
the Assistant, Play Store and Play Services.

## Apps the grid does not show

Not every installed app appears on Home. The grid is filled from `LEANBACK_LAUNCHER`, and on this firmware some apps
declare only the phone `LAUNCHER` category, so they stay invisible: Miracast (`com.droidlogic.miracast`, its only
entry is `.WiFiDirectMainActivity`), the owner's `com.ceyhan.sets`, and the vendor store `com.vs.market`.
Re-checked on a second NEK7: `com.ceyhan.sets` has one `category.LAUNCHER` and no `LEANBACK_LAUNCHER`,
`com.droidlogic.miracast`'s only activity carries `category.LAUNCHER` alone. Neither is missing by accident. They can be
brought in through the launcher's App library, whose choices live in its private `library_apps.txt`, or durably
through `library_apps` in `launcher/app/src/main/res/values/config.xml` (or a theme's `config.json`). Miracast was
left enabled deliberately: it is the screen mirroring that still works once AirPin is off.

## Dock

The dock in this folder's session was four slots: SmartTube, the owner's IPTV app, CloudStream and the
satellite app (`th.dtv`), which took the fourth slot once the YouTube app was disabled: `fill_dock` puts the
next grid app there instead of leaving a gap. Change `dock_apps` and `columns` in
`launcher/app/src/main/res/values/config.xml` before running `launcher/build-and-install.sh` again, or push a
theme with `tools/theme.sh`, which does not need a rebuild.

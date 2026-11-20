# Next NEK7

A MECOOL-built Amlogic box sold in Turkey as the Next NEK7: Android TV 9 (SDK 28), build
`PPR1.180610.011` / `V9.20221214.151020`, product `franklin`, board `newton`, a `userdebug` build with
`ro.adb.secure=0`. No tuner and no HDMI input, so it is a streaming box: leave `live_tv_package` empty in
`launcher/app/src/main/res/values/config.xml`.

The stock home is Droidlogic's `com.droidlogic.mboxlauncher`, and the Google launcher
(`com.google.android.leanbacklauncher`) is installed beside it and left enabled as the way back.

## Apply

```bash
TV_HOST=<box ip> tools/disable.sh examples/next-nek7/debloat.txt
TV_HOST=<box ip> launcher/build-and-install.sh
```

The second command needs `STOCK_LAUNCHER=com.droidlogic.mboxlauncher`: `com.droidlogic.mboxlauncher` is not
one of the four stock launchers `launcher/build-and-install.sh` recognises by name, so without it the script
stops before installing anything and asks for the package instead of guessing.

```bash
TV_HOST=<box ip> STOCK_LAUNCHER=com.droidlogic.mboxlauncher launcher/build-and-install.sh
```

Home was confirmed twice over: `dumpsys window | grep mCurrentFocus` named the new launcher, and after one
reboot `pm list packages -d` still held the same packages and Home was still the new one.

## Undo

```bash
TV_HOST=<box ip> tools/restore.sh examples/next-nek7/debloat.txt com.droidlogic.mboxlauncher
```

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

So nothing can be verified from pixels on this set. Two text checks stand in for it and agree with what the
screen shows:

```bash
tools/tv.py 'dumpsys window | grep mCurrentFocus'
tools/tv.py 'uiautomator dump /data/local/tmp/ui.xml; cat /data/local/tmp/ui.xml'
```

The dump carried the greeting, the clock, the date, the Wi-Fi name and the pills, so the launcher's own views
are reachable that way. Taps for the pills are taken from the dump's `bounds` and sent with `input tap <x> <y>`:
the Settings pill opened `com.android.tv.settings/.MainSettings`, and `KEYCODE_HOME` came back to the new Home
from Settings and from YouTube.

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
entry is `.WiFiDirectMainActivity`), the owner's `com.ceyhan.sets`, and the vendor store `com.vs.market`. They can be
brought in through the launcher's App library, whose choices live in its private `library_apps.txt`, or durably
through `library_apps` in `launcher/app/src/main/res/values/config.xml` (or a theme's `config.json`). Miracast was
left enabled deliberately: it is the screen mirroring that still works once AirPin is off.

## Dock

The dock in this folder's session was four slots: SmartTube, the owner's IPTV app, CloudStream and the
satellite app (`th.dtv`), which took the fourth slot once the YouTube app was disabled: `fill_dock` puts the
next grid app there instead of leaving a gap. Change `dock_apps` and `columns` in
`launcher/app/src/main/res/values/config.xml` before running `launcher/build-and-install.sh` again, or push a
theme with `tools/theme.sh`, which does not need a rebuild.

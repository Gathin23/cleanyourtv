# SunWind SUN-LED32XS305 (Yandex TV / YaOS, Android 11)

32" HD set sold in Russia. CVTE board `TP.MT9256.PB721` (MediaTek MT9256, the chip of
[sber-salute-tv-pb766](../sber-salute-tv-pb766/)), Android 11 / API 30, build `RP1A.200720.011 dev-keys`,
`armeabi-v7a`, no file-based encryption (`ro.crypto.type` empty). YaOS: no Play Store, Yandex app store only.
The app set matches [artel-43d1200b](../artel-43d1200b/), which this list follows.

## Getting ADB

Developer options appear after pressing Build seven times, but YaOS removed the Debugging section, and opening
`com.android.tv.settings/.system.development.DevelopmentActivity` directly (with Activity Launcher from F-Droid,
installed from a USB stick) shows the same cut page. `com.yandex.io.sdk`'s `UsbDebuggingActivity` is only the
"Allow USB debugging?" prompt, not a switch.

ADB is **Debug > ADB ENABLE** in the full CVTE factory menu (`com.cvte.fac.menu`). On Android 11 that package has
no launchable activity, so it was opened with a small community shortcut APK that starts the menu's service, as
[artel-cvte-mt5863](../artel-cvte-mt5863/debloat.txt) describes for another CVTE board. Change nothing else in
that menu: it also holds backlight, panel and factory-mode settings. Network ADB then answered on 5555.

`ro.adb.secure=0` on this firmware: there is no "Allow" prompt and any device on the network gets a shell, so
close it when done:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put global adb_enabled 0'
tools/venv/bin/python -I tools/adb-closed.py <tv ip>
```

## Debloat

```bash
TV_HOST=<tv ip> tools/disable.sh examples/sunwind-sun-led32xs305/debloat.txt
```

Applied in two batches with a cold boot (power cable out) after each; `tools/check.py` found nothing re-enabled, also four days later.
Kinopoisk, VK Video, Rutube, Flex (`innova.films.android.tv`), Yandex Music and Yandex Browser kept working.
Kinopoisk, Efir and Yandex Music have no launcher entry of their own: they open from the Yandex Home tile, so
`com.yandex.tv.home` stays enabled and sits in the dock.

## Launcher

Built with `tools/docker-build.sh` and installed with `STOCK_LAUNCHER=none`, as the CVTE rule asks:

```bash
SKIP_BUILD=1 STOCK_LAUNCHER=none TV_HOST=<tv ip> launcher/build-and-install.sh
TV_HOST=<tv ip> tools/theme.sh examples/sunwind-sun-led32xs305
```

`set-home-activity` took: the remote's Home key opens the new launcher, also after a cold boot. Right after
power-on the Yandex Home is shown once; one press of Home switches to the launcher. The Yandex Home was not
disabled, as no list for this board has tried that boot.

## Quirks

- The Yandex YouTube client (`com.yandex.tv.ytplayer`, a Cobalt build) did not load in Russia while other apps
  reached YouTube; [SmartTube](https://github.com/yuliskov/SmartTube) (`org.smarttube.stable`) is in the dock instead.
- With `com.yandex.tv.daydream` disabled there is no screensaver until another is set:
  `TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'`.
- The Miracast receiver (`com.mediatek.androidbox`, shown as "KindLink") is hidden from Home but kept.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/sunwind-sun-led32xs305/debloat.txt
```

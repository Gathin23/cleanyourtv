# Haier HRA962X2 (HRA962X2_2G_ALI6G_A)

A China-market Haier set on the Aliyun YunOS stack (Ali6G board), Android 9, 2 GB of RAM,
firmware PPR1.180610.011.20220314. The stock Home is Youku's launcher; the debloat list
disables 38 packages: YunOS ad and telemetry services, the Aligenie voice stack, AirPlay and
Miracast receivers, the Haier U+ IoT apps and preloaded content; the Youku home lines are commented out
until your launcher build answers the firmware's Home intent (below). The full adaptation report is
[docs/devices/haier-hra962x2-ali6g-cn.md](../../docs/devices/haier-hra962x2-ali6g-cn.md).

## Read this before installing the launcher

**This firmware intercepts the remote's Home key.** `YunOSTVPolicy` (in the system server)
does not send the standard HOME broadcast; it reads `settings system currenthome` and starts
that mode's app directly, sending `yunostv_homeshell://start_home` for mode 3 (the Youku
home). A stock `launcher/build-and-install.sh` run therefore installs and takes Home fine,
but the remote's Home key then does nothing, because no installed launcher answers that URI.

The mode values on this set: 1 and 4 `com.yunos.tv.homeshell`, 2 a child home, 3 the Youku
home, 5 an education home (not installed). Keep the setting at 3.

The way out, on this firmware, is an intent filter for that URI on the launcher's
`MainActivity`:

```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="yunostv_homeshell" android:host="start_home" />
</intent-filter>
```

That is a source change to `launcher/app/src/main/AndroidManifest.xml`, so it is not shipped
here: build the launcher from a checkout with this filter added, and the Home key opens it.
`cmd package set-home-activity` alone does not help: it reports `Success` while
`resolve-activity` still names Haier's provision app, and only an explicitly started launcher
holds Home.

## Other things this set does

- **The idle screen is an ad.** The YunOS screensaver (`com.android.dreams.phototable`,
  "Yun Huabao") rotates sponsored pictures with no setting to turn it off in the TV menus.
  `settings put secure screensaver_enabled 0` stops it; the launcher's own clock screensaver
  (`home_idle_clock_minutes` in `config.xml` or a theme) replaces it.
- **Sideloads need an on-TV confirmation.** Installing an APK over ADB raises an
  `AliAlertDialog` "安全风险" (security risk) prompt that ADB key presses do answer: the
  "continue" button is one DPAD up from the default focus. `pm install` also runs minutes on
  this set and often times out the ADB connection while still succeeding - check
  `dumpsys package <pkg> | grep lastUpdateTime` before retrying.
- **The updater pushes preloads.** With `com.yunos.osupdate` enabled it installed
  `com.haier.aitv` (an Aligenie content app) on its own, which is why the list disables it.
  The apps it had pushed on this unit (`com.haier.tv.hm`, `com.haier.haiertv.ims`,
  `com.haier.aitv`, `com.yunos.tv.bluray`) were disabled first and are in the list.
- **Almost nothing declares a leanback entry point.** Fresh out of the box only Youku and the
  stock home answer `LEANBACK_LAUNCHER`, so a new Home comes up nearly empty: turn on
  `app_library` and seed `library_apps` in `launcher/app/src/main/res/values/config.xml` with
  the set's own apps (`com.hrtvbic.usb.mm` is the local media player, `com.xiaobaifile.tv`
  the file manager) before building.
- **No signal at boot.** After a reboot the firmware opens its "no signal" overlay
  (`com.haier.tv.menusetting/.RootActivity`) over the last input; one press of Home goes to
  the launcher. That is stock behaviour, not a debloat side effect.

The dock this set was set up with lives in `config.json` beside this file: the local media
player and the file manager, both installed on the set.

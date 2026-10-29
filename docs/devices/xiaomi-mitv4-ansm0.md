# Xiaomi Mi TV 4S 75" (L75M5-4S, MiTV4-ANSM0), Android 6.0.1

Tested on a 小米电视4S 75寸, retail L75M5-4S, `MiTV4-ANSM0` / `xmen`, board `gxbaby`,
Android 6.0.1 (API 23), `armeabi-v7a`, firmware `20.3.6.2057`, 1920×1080 at 320 dpi.
China PatchWall build. This board has no official Android 8, 9 or 11 image.

This is not a disable list. `pm disable-user` and `pm hide` are refused for the shell
user (`SecurityException`, `MANAGE_USERS`). Do not run `tools/disable.sh` here, and do
not run a full `tools/restore.sh`. Nothing in this note uninstalls a package.

## Launcher

The launcher used on this set is [小米电视4S 75寸 L75M5-4S（MiTV4-ANSM0），Android 6.0.1](https://github.com/qqwersde/my-tv-signal-android6)
(`qqwersde/my-tv-signal-android6`). It is this repo's launcher at `bc4e1094` plus an API 23 build that lives in that fork.
In a clone of the fork, pass `-PlauncherMinSdk=23`; this repo's launcher has no such flag and needs Android 8 (API 26). No internet
permission. Outfit is used from API 26 only; Android 6 uses the system sans-serif styles.

```bash
cd launcher && ./gradlew assembleRelease -PlauncherMinSdk=23
adb install -r --user 0 launcher/app/build/outputs/apk/release/app-release.apk
```

The HOME intent filter priority is 999. Stock home is `com.mitv.tvhome`
(`.MainActivityUserMode`, priority 100). It is an updated system app and can return on
boot and take the Home key. Leave it installed; this note does not remove it.

The activity is also a `LAUNCHER`, so Xiaomi's app list on Android 6 can show it.
The Inputs button opens `com.xiaomi.mitv.tvplayer/.ExternalSourceActivity` with integer
extra `input`: HDMI 1 is 23, HDMI 2 is 24, HDMI 3 is 25, AV is 2. The standard TV input
URI has no handler on this set.

The dock pins 米家 (`com.xiaomi.smarthome.tv`) and 云视听小电视 (`com.xiaodianshi.tv.yst`).
Holding OK on a tile opens a menu: 卸载 for a downloaded app that is not a system app,
and 应用信息 for every app. A short OK still launches the app. Reordering stays on the
settings button's long-press menu. This settings app does not handle the standard
application-details intent, so 应用信息 may not open.

## Left out of the grid, still installed

`com.mediatek.wwtv.tvcenter`, `com.mitv.gallery`, `com.xiaomi.mitv.appstore` (应用中心),
`com.xiaomi.mitv.tvmanager` (电视管家), `com.mitv.alarmcenter` (定时提醒),
`com.xiaomi.mitv.smartshare` (无线投屏), `com.duokan.airkan.tvbox`,
`com.mi.miplay.mitvupnpsink`, `com.xiaomi.upnp`, `com.xiaomi.miplay`.

Voice on the remote, 米家, 云视听小电视, system update, the cleaner, the HDMI player,
settings, Sogou IME and the Xiaomi account were kept.

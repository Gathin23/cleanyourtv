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

## A second set: Mi TV 4A 50" (MiTV4-ANSM0, build 3390)

From @xialeizhou77's report in #1185: a 小米电视4A 50英寸, also `MiTV4-ANSM0` / `xmen`, Android 6.0.1 (API 23),
build `3390`, stock PatchWall from firmware 1.3.130. The same refusals as above, so no list here either.

What the shell user (uid 2000, groups `shell input log adb sdcard_rw sdcard_r net_bt_admin net_bt inet
net_bw_stats`) may not do on this build:

| Command | Result |
|---|---|
| `pm disable-user --user 0 <pkg>`, `pm disable`, `pm disable-until-used` | `SecurityException: Permission Denial: attempt to change component state` |
| `pm hide <pkg>` | `SecurityException: Neither user 2000 nor current process has android.permission.MANAGE_USERS.` |
| `pm trim-caches` | `SecurityException: ... has no android.permission.CLEAR_APP_CACHE.` |

So `tools/disable.sh` cannot run (it needs `pm disable-user`) and neither can `tools/restore.sh` (`pm enable`).
Android 6 also has no `cmd` binary, so `cmd package set-home-activity` and `cmd notification allow_listener` do
not exist. This repo stays disable-only, so there is no supported debloat route on this generation.

**Keep these, whatever else you try on this firmware:**

- `com.sohu.inputmethod.sogou.tv`: the only keyboard; without it no search field can be typed into.
- `com.xiaomi.mitv.tvpush.tvpushservice`: carries `GetInstallerWhiteList`, and the firmware has an install
  blacklist (`pi_config`), so without it APKs may no longer install.
- `com.xiaomi.mitv.settings`, `com.xiaomi.mitv.systemui`, `com.xiaomi.mitv.providers.settings`: the firmware
  registers none of the standard `android.settings.*` actions, so these carry the whole settings UI.
- `mitv.service`, `com.mi.miplay.mitvupnpsink`, `com.xiaomi.miplay`, `com.duokan.airkan.tvbox`: remote and casting.
- `com.xiaomi.mitv.tvplayer`, `com.mitv.mivideoplayer`, `com.mitv.videoplayer`, `com.mitv.codec.update`: playback.

**The boot ad.** `boot_ad_switch` was unset (`boot_ad=0`, `boot_ad_switch_show=0`). After
`settings put system boot_ad_switch 0` and a full power cycle the 15-second sponsored boot video was gone and the
Xiaomi logo (about 10 s) remained; the value survived a reboot. The same key works on other China Xiaomi builds
([`xiaomi-l43m7-ea-antl0.md`](xiaomi-l43m7-ea-antl0.md)). Undo: `settings delete system boot_ad_switch`.

**The ad SDK.** `com.miui.systemAdSolution`, the system ad SDK of this generation, lives in `/data/app` on this
build; none of the Xiaomi lists in `examples/` name it. On Android 4.4 `pm block` reaches it
([`xiaomi-mitv3s-48-hancock-follow-up.md`](xiaomi-mitv3s-48-hancock-follow-up.md)); on this Android 6 build the
shell cannot disable, hide or block it.

**HOME without the allowlist.** With stock Home removed for user 0 (a route this repo does not use, so it is
not described here), HOME resolved to a sideloaded third-party launcher by itself, and the Home key reached it
3 times out of 3 with no accessibility helper. The package-name
HOME allowlist that later China firmware applies ([`docs/TROUBLESHOOTING.md`](../TROUBLESHOOTING.md)) did not show
on this Android 6 build. The launcher in this repo still needs Android 8, so this only matters for the fork above.

### Cross-check: Mi TV 4A (MiTV4A, matrix)

From @QiKe-007's comment in #1185: `ro.product.model=MiTV4A`, `ro.product.device=matrix`, MIUI TV `8.8.20.1275`,
Android 6.0.1, zh-CN, PatchWall the only HOME. The refusals are exactly the same, for system-uid and app-uid
packages alike; `pm suspend` and `pm block` do not exist, and `adb root` answers `adbd cannot run as root in
production builds`. So the refusal belongs to the generation, not one build.

- `com.miui.systemAdSolution` is in `/data/app` here too.
- `com.mitv.screensaver` (`/system/priv-app/TVSmartScreenSaver/`), `com.xiaomi.statistic`
  (`/system/vendor/app/MiTVStat/`) and `com.xiaomi.devicereport` (`/system/vendor/app/DeviceReport/`) are on
  read-only partitions. AOSP's dream is already off (`screensaver_enabled=0`); the Mi screensaver runs on its own.
- **`appops` works from the shell and sticks.** Marketing push notifications go quiet while the push service
  (and its installer allowlist) stays enabled:

  ```bash
  tools/venv/bin/python -I tools/tv.py 'appops set com.xiaomi.mitv.tvpush.tvpushservice POST_NOTIFICATION deny'
  # check: appops get com.xiaomi.mitv.tvpush.tvpushservice    undo: ... POST_NOTIFICATION allow
  ```

- **adbd takes one client at a time.** `tools/tv.py` timed out (`TcpTimeoutException: Reading from <tv ip>:5555
  timed out`) while the platform `adb` server held a connection to the same port. `adb kill-server` first.

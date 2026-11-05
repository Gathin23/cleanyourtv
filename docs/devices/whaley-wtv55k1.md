# Whaley WTV55K1 (MStar helios, China, Android 5.0.2): `pm hide` is the only debloat channel

This is an owner report from one set, **not a debloat list for the repo
tools**. On this firmware the tools cannot apply or undo a package list at
all (see the next section), so there is deliberately no `examples/` entry
for this TV. What follows is what the owner did on their own set, and what
it changed, so another owner of the same firmware can judge for themselves.

Whaley (Micro Whale) folded around 2018–2020; this firmware has had no
update since December 2018 and none is expected.

| Field | Observed value |
|---|---|
| Retail model | `WTV55K1` (55") |
| Android model / device | `WTV55K1` / `helios` (MStar 6A828 board) |
| Android / API | `5.0.2` / `21` |
| Build | `Fri Dec 7 08:47:15 CST 2018` |
| TV software version | `HELIOSR-02.08.03.1849508`, WUI `02.08.03.21` (About screen) |
| Storage / RAM | 8 GB eMMC (7.28 GiB total: 1 G /system + 1 G OTA backup + 4.95 G /data + 64 M /cache + vendor partitions), 1 GB-class RAM |
| Stock home | `com.helios.launcher` |

## `pm disable-user` is refused on this firmware

From the adb shell, `pm disable-user --user 0 <pkg>` fails for **every**
package, including packages the shell does not own:

```text
java.lang.SecurityException: Permission Denial: attempt to change component state
```

`pm block` no longer exists on Android 5, and this is a `user` build, so
there is no root route either. The repo's `tools/disable.sh` and
`tools/restore.sh` only disable and enable, which is why a list file for
this TV could not be applied or undone with the repo's tools.

## What the owner did instead: `pm hide`

The one working, reversible channel on this set is:

```text
adb shell pm hide <pkg>     # hide a package
adb shell pm unhide <pkg>   # bring it back
```

Hidden state survives reboot. Nothing was uninstalled; every package below
can be restored with `pm unhide`.

### Packages hidden (17)

```text
cn.whaley.adsys                  # Whaley ad system
com.helios.ad.service            # ad service
com.helios.cleantools            # "cleanup" nag tool
com.helios.sports                # sports feed
com.helios.kids                  # kids mode
com.helios.globalmusic           # global music
com.xiami.tv                     # Xiami music (service dead since 2019)
com.helios.message               # push messages
cn.whaley.remotehelp.tv          # remote assistance
com.helios.remoteassisthelper    # remote assistance helper
com.helios.facerecognition       # face recognition
com.aliyun.ams.tyid              # Aliyun device-ID telemetry
com.aliyun.ic                    # Aliyun telemetry
cn.whaley.mobile.tv              # phone companion link
com.helios.appstore              # app store (apps go in via adb)
com.helios.guide                 # first-run guide
tv.whaley.usbinfostatics         # USB usage statistics reporting
```

### Left enabled on purpose

- `com.helios.launcher` — stock home.
- `com.helios.setting`, `com.helios.mediaplayer`, `com.helios.vod` —
  launcher dependencies.
- `com.iflytek.xiri`, `com.iflytek.xiri.recorder.svc`, `com.helios.voice`,
  `com.helios.heliosvoice` — the remote's voice key is bound to this vendor
  chain and cannot be re-bound without root.
- `com.yunos.*` / payment services — left alone.
- `com.mstar.*` — board services.

## What went quiet (DNS evidence)

Verified live with AdGuard Home query logs on 2026-10-24. From boot, the
stock firmware queries these at **30+ DNS queries/hour**:

- `*.aginomoto.com` — `ad.`, `wlslog.`, `u.`, `member.`, `appstore.`,
  `kids.`, `vod.`, `passport.`, `parse.`, `parse2.`, `parse3.`
- `scs.openspeech.cn` (iFlytek)
- `ic.tv.aliyun.com`

After the packages above were hidden and the set rebooted, all of it
stopped: **zero queries**, re-verified after a factory reset with the
packages hidden again.

## Other quirks, all checked on the set

- `pm trim-caches` needs a suffix: `1G` works, a bare number fails with
  "Invalid suffix".
- Overwrite-installing a **running** app is cancelled by the ROM;
  `am force-stop <pkg>` first.
- The shell cannot read `/data/app` or other packages' data dirs, so no
  per-app size audit is possible.
- Some on-device toybox applets are limited (no `head`/`wc`/`grep -E` in
  places); pipe output to the host instead.
- `/sdcard` is empty on this unit (no adoptable storage; the public cache
  dir falls back to internal).
- ADB on port 5555 is **not** forwarded to the WAN on a stock iKuai router
  (checked from an external vantage point); LAN-only exposure as usual.

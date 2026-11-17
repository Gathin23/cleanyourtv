# Sony BRAVIA KD-55X80J - China firmware

Tested on the retail KD-55X80J, reported by Android as `BRAVIA 4K VH2`,
product `BRAVIA_VH2`, MediaTek MT5895, Android 12 / API 31,
firmware `BRAVIA_VH2_M_CN-user 12 STT2.230505.001.S102 681621`, 18 GB `/data`.

This set has no Play Store and no Google apps, so the Google TV lists in this repo
do not match it row for row: [KD-65X80J](../sony-kd-65x80j/debloat.txt) was tested on
Google TV / Android 10 firmware and [KD-55X8000C](../sony-kd-55x8000c/debloat.txt)
on Android TV 7.0.

- The [debloat list](debloat.txt) disables 28 packages. Every entry is a package this
  set has, and every one of them was disabled on it on 2026-11-01.
- That run was reboot-checked the same day: Home stayed on the new launcher and the
  disabled state survived the reboot. The set was restored to its factory state
  afterwards, so this list is a record of that run, not of the set's present state.
- The stock home is `com.dangbei.TVHomeLauncher` (当贝桌面, `system_ext/priv-app/ChinaHome`).
  It is not in `build-and-install.sh`'s known stock launchers, so install the reference
  launcher with `STOCK_LAUNCHER=none` and set Home with `cmd package set-home-activity`.
  The Undo line below passes the package, so the original Home can be put back.
- Set `live_tv_package` to `com.sony.dtv.tvx`. Keep it and the TV input services:
  TVX handles HDMI as well as television playback.
- Casting here is 乐播投屏 (HappyCast, `com.hpplay.happyplay.aw`), not a Sony package.
  The list keeps it and takes down Sony's own DLNA stacks instead.

Apply and undo:

```sh
TV_HOST=<tv ip> tools/disable.sh examples/sony-kd-55x80j/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/sony-kd-55x80j/debloat.txt com.dangbei.TVHomeLauncher
```

Two packages on this set are off from the factory and are deliberately not in the
list: `com.sony.dtv.b2b.softap` and `com.sony.dtv.b2b.vendorprotocol` (the shell
cannot toggle the latter, so a list naming it would fail on every run).
`com.sony.dtv.appassistant` was off during that run; this list leaves it enabled.

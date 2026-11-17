# Xiaomi MiTV-MSSP2 (volver) on the Android 11 firmware

`tools/tv.py --probe` reports `ro.product.model = MiTV-MSSP2`, `ro.product.device = volver`, Android 11
(SDK 30), `ro.build.display.id = RTT2.211214.001.933 release-keys`, `ro.adb.secure = 1`, a low-RAM build, on
Wi-Fi. The list for the same model on Android 9 is in `examples/xiaomi-mitv-mssp2/debloat.txt`; this firmware
has a different set of Xiaomi packages, so it has its own folder.

**Status:** applied 2026-11-05 in three batches (telemetry; PatchWall and content rows; preloads), with a
screenshot of Home after each, then `launcher/build-and-install.sh` with the dock set to Netflix, Prime Video,
YouTube and Disney+. Two reboots: Home came up as `com.gathin.tvhome` straight after the boot screen, with no
Live TV in between. On the real remote: Home, the Netflix button, Input and an HDMI port, Settings and the
Google Assistant voice search all worked with the stock launcher disabled, with no key helper switched on.
Updated 2026-11-12: a week of daily use with no problems reported by the owner. ADB was closed after the
first day, so the list was not checked again over ADB.

Live TV channel keys on this firmware: see the notes in `examples/xiaomi-mitv-mssp2/README.md`, from another
unit on the same `RTT2.211214.001.933` build.

The debloat list disables 21 packages. Prime Video and YouTube Music are commented out because this owner uses
them.

## The updater re-enables PatchWall and Mi TV+

The Xiaomi updater (`com.xiaomi.mitv.updateservice`) was kept for firmware updates. In the first hour it
installed a new PatchWall (09:12) and a new Mi TV+ (09:20), and each update put the package back to enabled:
`dumpsys package com.mitv.tvhome.atv` showed a fresh `lastUpdateTime` and the updater as `lastDisabledCaller`.
Both stayed disabled after a check with `--fix` and a reboot. Run the check now and then:

```
TV_HOST=<tv ip> tools/venv/bin/python -I tools/check.py --fix examples/xiaomi-mitv-mssp2-android11/debloat.txt com.gathin.tvhome
```

## Undo

```
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv-mssp2-android11/debloat.txt
```

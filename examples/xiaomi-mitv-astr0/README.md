# Xiaomi MiTV-ASTR0 (blueplanet): HyperOS TV 3, two units

This folder carries the lists for two sizes of the same model code, both China firmware and
shell-only (no root, nothing uninstalled).

- `debloat.txt` is the 55-inch Redmi Smart TV A Pro 55" 2025 Energy-saving on
  OS3.0.106.0.USTAATV.
- `debloat-50-2025.txt` is the 50-inch Redmi Smart TV A Pro 50" 2025 Energy-saving on
  OS3.0.119.0.USTAATV. `debloat-50-2025.txt` disables 18 packages.

The 50-inch unit's preload set differs from the 55-inch one: it has no WPS, calendar, health,
weather, Xiaomi store or Pictorial, and it ships CIBN, Tencent, Bilibili, Xunlei, NetEase,
Dangbei Market, Shafa Market and Filmly/Yamby instead. That is why it needs its own list; the
packages the 55-inch list disables that are absent here are left out.

## Apply and undo

```sh
TV_HOST=<tv ip> tools/disable.sh examples/xiaomi-mitv-astr0/debloat-50-2025.txt
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv-astr0/debloat-50-2025.txt com.mitv.tvhome

TV_HOST=<tv ip> tools/disable.sh examples/xiaomi-mitv-astr0/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mitv-astr0/debloat.txt com.mitv.tvhome
```

`tools/restore.sh` puts Home back on `com.mitv.tvhome` (PatchWall), which both lists leave
enabled and only the launcher handover below disables. The 50-inch unit also needed
`cmd periodic enable false`: on OS3.0.119 the firmware's periodic cleaner re-enables disabled
packages and kills background apps, and that setting survives reboots.

## Home: the allowlist identity is part of the recipe

On both units `cmd package set-home-activity` and `cmd role add-role-holder` answer `Success`
and change nothing: the firmware filters third-party Home candidates by a package-name
allowlist. The sequence that works, in this order, is the one in
[the device notes](../../docs/devices/xiaomi-mitv-astr0-blueplanet.md):

1. Build the launcher as the allowlisted identity:
   `-PlauncherApplicationId=com.ts.launcher`, with `launcher/build-and-install.sh`'s preflight
   checks (the Android 8.0 minimum, `pm install` accepted).
2. Install it and start `com.ts.launcher/com.gathin.tvhome.MainActivity`.
3. Set that component Home and confirm with
   `cmd package resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME`.
4. Only then disable `com.mitv.tvhome`. The line stays commented out in both lists.

Home then resolves to `com.ts.launcher/com.gathin.tvhome.MainActivity` and still does after a
reboot. Recovery is `pm enable --user 0 com.mitv.tvhome` plus a reboot. Do not disable PatchWall
first: without an allowlisted home, Home lands on
`com.xiaomi.mitv.settings/.entry.FallbackHome` with a black picture.

## Inputs: `EXTSRC_PLAY` and the port numbers

No activity on this firmware answers the standard passthrough channel URI, so the Inputs rows
do nothing out of the box. The vendor entry is
`com.xiaomi.mitv.tvplayer/.ExternalSourceActivity` with the integer extra `input`. The 50-inch
unit's mapping, read from `dumpsys tv_input`, is the one in the folder's `config.json`:

| Port | Framework input id | `input` |
| --- | --- | --- |
| HDMI 1 | `com.droidlogic.tvinput/.services.Hdmi1InputService/HW5` | 23 |
| HDMI 2 | `com.droidlogic.tvinput/.services.Hdmi2InputService/HW6` | 24 |
| AV 1 | `com.droidlogic.tvinput/.services.AV1InputService/HW1` | 2 |

`config.json` in this folder also sets the dock (Bilibili, CIBN, Dangbei Market, Tencent) and
hides the TV player from the grid. `tools/theme.sh` hardcodes `com.gathin.tvhome` for the files
directory, so for this identity push the file by hand:

```sh
TV_HOST=<tv ip> tools/tv.py --push examples/xiaomi-mitv-astr0/config.json /sdcard/Android/data/com.ts.launcher/files/config.json
TV_HOST=<tv ip> tools/tv.py 'am force-stop com.ts.launcher'
TV_HOST=<tv ip> tools/tv.py 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n com.ts.launcher/com.gathin.tvhome.MainActivity'
```

The config is the 50-inch unit's; the 55-inch list does not need it.

## What the 50-inch unit kept

Tencent, Bilibili, CIBN, Filmly, Yamby, the three app markets (Xiaomi, Dangbei, Shafa), XiaoAI
voice, casting and Mi Home, TV Manager, the Sogou IME, Bluetooth and the TV input stack. The
firmware updater is off on this unit at the owner's request, and its line is commented out of
the list so applying it leaves OTA on elsewhere; run
`pm enable --user 0 com.xiaomi.mitv.upgrade` before checking for updates.

If this unit is later updated to OS4.0.1.0, PatchWall has to stay enabled: with it disabled
every volume-key press crashes SystemUI (see the device notes). The Home route above is for the
OS3.0.106 / OS3.0.119 builds.

## ADB

ADB over TCP closes on every boot on both units: Settings > 账号与安全 > ADB 调试 re-opens it.
This unit also keeps `ro.adb.secure=1`, so a new computer has to accept the RSA prompt once.

## What was tested

One 50-inch unit on 2026-11-17, over network ADB: the 18 packages disabled, plus the firmware
updater turned off by hand at the owner's request, one reboot,
`tools/venv/bin/python -I tools/check.py` reported no drift, Home and the clock and date drew,
the Home key returned from Settings, volume keys raised no SystemUI crash, D-pad moved through
the top bar, dock and grid, and the Inputs menu switched to HDMI 1 and AV 1.

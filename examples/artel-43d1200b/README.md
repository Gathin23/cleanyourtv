# Artel 43D1200B (Yandex TV / YaOS, Android 9)

CVTE board, MediaTek `m5621`, Android 9 / API 28, build `PPR2.180905.006.A1`, incremental `2951076306`
(2025-03-21), about 0.9 GB RAM (`low_ram`), armeabi-v7a, Uzbekistan. The first Yandex TV set with a
several-day list here; [`docs/devices/hi-vhix-43f169msy.md`](../../docs/devices/hi-vhix-43f169msy.md) is
the same platform with notes only. IR remote, Ethernet; no Play Store: YouTube (`com.yandex.tv.ytplayer`)
and Kinopoisk come and update through `com.yandex.launcher.updaterapp`.

Tested on one set for a week, used only for YouTube and Kinopoisk. No HDMI or antenna source was
connected, so the Inputs rows were not tried with a signal.

## Connect

Network ADB on port 5555 was open from the factory (`persist.adb.tcp.port=5555`, `ro.adb.secure=1`:
the TV asks to allow the computer once). After `adb_enabled` was cleared from the shell, USB debugging in
Settings > Developer options (tap Build seven times to show them) did not open 5555 again, even through a
restart; the port came back once the network debugging switch in the same menu was turned on. As in
[TROUBLESHOOTING](../../docs/TROUBLESHOOTING.md) (#2632), trust `tools/adb-closed.py <tv ip>`, not the menu.

The TV gets its address by DHCP, and it changed during the week: check it in Settings > Network.

## Debloat

```bash
TV_HOST=<tv ip> tools/disable.sh examples/artel-43d1200b/debloat.txt
```

Five Yandex apps that a household may use (Alice, Browser, Music, the video player, Efir channels) and
the USB player and Miracast receiver are commented out: they were off on the tested set by the owner's
choice. The Yandex system services stay: the Yandex account behind the Kinopoisk sign-in lives there.

Nothing came back after reboots, after the Yandex system apps updated themselves (3.245 to 3.367), or a
week later (`tools/check.py`).

## Launcher

As the CVTE rule says: the launcher was made Home with `cmd package set-home-activity`, the TV rebooted, Home
and ADB came back, and only then was `com.yandex.tv.home` disabled (the last line of the list). The real
remote's Home, Input and Settings buttons all open the launcher's screens with no key service turned on.
[`config.json`](config.json) puts YouTube and Kinopoisk in the dock, hides the stock Home tile and sets
`input_uri_scheme` to `live-tv`, which the YaOS source player answers.

One minute after boot, about 335 MB was available before the stock Home was disabled and about 380 MB after
(`dumpsys meminfo`, not a benchmark).

## Quirks

- Screensaver: `screensaver_components` names the Yandex video screensaver, so after the list there is none
  until another is set, for example the launcher's clock:
  `settings put secure screensaver_components com.gathin.tvhome/.ClockDream`.
- Power: a short press of the power key is MStar suspend-to-RAM standby, not power off; a long press opens a
  menu with a real power off. `tools/tv.py --reboot` leaves the set in standby: press power on the remote.
- The time zone shipped as `Asia/Oral` (the same UTC+5 as Uzbekistan's `Asia/Tashkent`), with automatic time zone on.

## Close ADB afterwards

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'settings put global adb_enabled 0'
tools/venv/bin/python -I tools/adb-closed.py <tv ip>
```

The port stayed closed after a reboot, and the set stopped announcing `_adb._tcp` over mDNS.

## Undo

```bash
TV_HOST=<tv ip> tools/restore.sh examples/artel-43d1200b/debloat.txt com.yandex.tv.home
```

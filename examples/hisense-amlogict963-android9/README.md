# Hisense VIDAA_TV / AMLOGICT963 (China, Android 9)

This is a conservative debloat list for one China-market set. Its retail model could not be
established from the TV software. Match the device and build before applying it; the existing
[Home handover note](../../docs/devices/hisense-amlogict963-android9.md) has the observed build
fingerprint and explains why MT9653 lists are not interchangeable with this one.

| Field | Observed value |
|---|---|
| Android model / device | `VIDAA_TV` / `AMLOGICT963` |
| Android / build | 9 (API 28) / `PPR1.180610.011.07.02.70.02` |
| TV software | `V0011.00.01L.P0926` |
| Stock Home / Live TV | `com.jamdeo.tv.vod` / `com.jamdeo.tv.livetv` |

The five packages in `debloat.txt` were disabled for user 0, not uninstalled.
The ad service was disabled before the 2026-10-27 batch; the other four were applied with
`tools/disable.sh` in two
small batches that day. The owner checked Live TV/HDMI picture, the physical remote, voice,
casting, Bluetooth and the system update page. After two reboots, ADB and the selected Home
returned, and the packages stayed disabled. On 2026-11-03, the owner reported no problems
after one week of ordinary use; a read-only check again found the same packages disabled.

The stock launcher and TV/input services remain enabled. The voice, casting, Bluetooth and
firmware update packages also remain enabled. `com.android.bluetooth` was already disabled
on this unit before the trial and is deliberately absent from this list; the owner reported
that Bluetooth peripherals still worked. Do not copy that pre-existing disabled state to
another set.

```bash
TV_HOST=<tv ip> tools/disable.sh examples/hisense-amlogict963-android9/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/hisense-amlogict963-android9/debloat.txt com.jamdeo.tv.vod
```

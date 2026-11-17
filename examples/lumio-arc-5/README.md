# Lumio Arc 5 Projector (Google TV on MediaTek board)

Notes and debloat list for the **Lumio Arc 5 Projector** (SDMC himalaya, MediaTek MT9630), running Google TV on Android 14 (firmware build `UKNV.260904.001`). Tested on the real device; all 16 disabled packages survived a cold reboot with zero drift.

The [debloat list](debloat.txt) disables 16 packages: Google feedback and telemetry, background backup services, ad attribution, and unused preloads.

## Files in this folder

| File | What it is |
|---|---|
| `debloat.txt` | The 16 packages that were disabled, grouped and commented |
| `tv.json` | Model metadata for the TV list tables |
| `README.md` | This page |

## Critical Projector, Sideloaded Apps & USB Safeguards

1. **Projector Hardware & Sensors**:
   Do **not** disable packages managing optical engine settings, ToF (Time-of-Flight) distance sensors, autofocus steppers, STR8 auto-keystone correction, obstacle avoidance, screen fit, or picture calibration. Always inspect unfamiliar OEM/MediaTek packages using `dumpsys package` before changing them.
2. **Lumio Apps & System Updates**:
   Keep all official Lumio packages (including TLDR), system OTA update services, and user-installed or sideloaded apps (`pm list packages -3`) enabled.
3. **USB Storage & Media Playback**:
   To play local movies and video files from external flash drives or hard drives, preserve all USB storage, OTG, and media indexing components (`com.android.externalstorage`, `com.android.documentsui`, `com.android.providers.media`, `com.android.providers.downloads`, and MediaTek USB/file managers).
4. **Restoring Physical USB Power**:
   At the end of the debloating session, ensure both USB debugging and Wireless/Network ADB debugging are completely turned off and reboot. Some hardware cuts power or limits USB bus current while USB debugging is active, which can disable connected flash drives or external drives.

## Applying the debloat list

Connect over ADB and apply with `tools/disable.sh`:

```bash
TV_HOST=<tv ip> tools/disable.sh examples/lumio-arc-5/debloat.txt
```

To re-enable all packages disabled by this list:

```bash
TV_HOST=<tv ip> tools/restore.sh examples/lumio-arc-5/debloat.txt
```

## Installing the Launcher

Build and install `com.gathin.tvhome` with `launcher/build-and-install.sh`:

```bash
TV_HOST=<tv ip> launcher/build-and-install.sh
```

## Closing ADB Afterwards

After completing the setup, disable USB debugging and Wireless debugging in Developer Options, reboot the projector, and verify that port 5555 is closed using `tools/adb-closed.py`:

```bash
python3 tools/adb-closed.py <tv ip>
```

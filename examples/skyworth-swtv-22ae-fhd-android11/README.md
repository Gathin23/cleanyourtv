# Skyworth SWTV-22AE-FHD (umeda), Android 11

Debloat list for the Android 11 firmware of this model. The Android 14 firmware is covered in
`examples/skyworth-swtv-22ae-fhd/`; the package set differs, so this list is filtered to what exists on
Android 11.

## Device info
- **Model:** SWTV-22AE-FHD (Motorola-branded)
- **Codename:** umeda
- **Manufacturer:** skyworth
- **OS:** Google TV, Android 11 (SDK 30)
- **Firmware:** RTM7.230903.221
- **Stock launcher:** `com.google.android.apps.tv.launcherx`

## Tested
- Length of use: about a month of daily use, as of 2026-11-21.
- The first `launcher/build-and-install.sh` run failed its Home check: Home resolved to
  `com.google.android.tungsten.setupwraith/.RecoveryActivity`. After disabling `setupwraith` the install
  succeeded and Home resolved to `com.gathin.tvhome`.
- The saved Wi-Fi network was owned by `android.uid.system`, so disabling `setupwraith` did not drop the
  connection.
- Remote: app button, gear button and held volume all worked.
- Reboot: Home, Wi-Fi and apps came back as expected.
- ADB closed afterwards (`tools/adb-closed.py` reported the port closed).

## Package presence
This list was built from the Android 14 and other Skyworth lists in this repo, keeping only packages that are
installed on this firmware. Not installed here: the Android 14 components (`adservices`,
`ondevicepersonalization`, `federatedcompute`, Health Connect).

## Apply / Restore
```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --probe
TV_HOST=<tv ip> tools/disable.sh examples/skyworth-swtv-22ae-fhd-android11/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/skyworth-swtv-22ae-fhd-android11/debloat.txt com.google.android.apps.tv.launcherx
```

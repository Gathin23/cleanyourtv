# Xiaomi Mi Box 4S Pro (MiTV-AZFP0 / frozen)

China-market Android 9 / API 28, firmware `23.5.10.1035`. This box is different
from the global Mi Box S (`MIBOX4` / `oneday`) and Mi Box 3 (`MIBOX3` / `once`).

This follows the [initial device report](../../docs/devices/xiaomi-mi-box-4s-pro-frozen.md)
and [PR #2691](https://github.com/Gathin23/cleanyourtv/pull/2691).
The first on-device session was 2026-10-30. On 2026-11-02 the owner reported two
days of intensive weekend use without problems; on 2026-11-05 the owner also
confirmed successful casting and physical-remote standby/wake testing.

## Scope and recovery

The list contains 12 packages: four were already disabled before the session,
and eight were disabled in small batches with the project's tools. The game
SDK, recorder, calendar and gallery are owner choices; comment out any feature
you need. No package was uninstalled and no app data was cleared.

Run from the repository root:

```sh
TV_HOST=<tv ip> tools/disable.sh examples/xiaomi-mi-box-4s-pro-frozen/debloat.txt
TV_HOST=<tv ip> tools/restore.sh examples/xiaomi-mi-box-4s-pro-frozen/debloat.txt com.mitv.tvhome
```

Keep the applied list and recovery record. Restore returns the recorded baseline:
a package already disabled before applying this list stays disabled. An earlier
session must use its original list and record; this cumulative list does not
replace the recovery record for the original eight-package session.

Stock Home `com.mitv.tvhome/.MainActivityUserMode` was kept enabled. Xiao AI,
Xiaomi app store, OTA, casting services, Bluetooth, Sogou keyboard, Android
WebView, package installer, TV Manager, TVPush and installed playback apps were
also kept enabled. No replacement launcher is needed for this list. A pre-existing
Aerial 1.30 installation remained present, but Home takeover was not established
and the project's reference launcher was not installed or tested.

## What was checked

- All twelve disabled-package states were read back after one full reboot on
  2026-10-30. Xiaomi Home returned; the owner confirmed Xiao AI, video picture
  and audio. The project's checker reported no drift for the eight new changes.
- Xiaomi settings and the app-store home page opened.
- Restore/reapply was exercised for TV QS, cloud control, web content and the
  shopping plugin. The four earlier disabled packages stayed disabled. The later
  four owner-choice apps were recorded, but their restoration was not separately
  exercised.
- The owner reported two days of intensive use without problems on 2026-11-02,
  then confirmed casting and standby/wake worked on 2026-11-05. Casting source
  device/app/protocol and standby/wake cycle count were not recorded; no broader
  protocol-coverage or repeated-cycle claim is made.

## Limits and updates

Package-state evidence is from the 2026-10-30 reboot; a read-only follow-up
connection on 2026-11-06 was reset before state readback. No new state or firmware
readback is claimed for that day. App download/installation and OTA installation
were not tested. No overall performance benchmark or speed-up percentage is claimed.

The shopping plugin was enabled when version `6.8.24` was inspected, despite an
earlier disable on a different version; the cause is unknown. It was disabled
again and stayed off through the tested reboot. Recheck after an app or firmware
update rather than assuming disabled states persist. Turn network debugging off
when finished.

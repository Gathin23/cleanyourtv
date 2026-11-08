# Xiaomi Mi Box 4S Pro (MiTV-AZFP0, frozen)

China-market streaming box, read on 2026-10-30 before any change. This page is
that single-session device report; the list it grew into is
[`examples/xiaomi-mi-box-4s-pro-frozen/`](../../examples/xiaomi-mi-box-4s-pro-frozen/debloat.txt) -
same box, same build, twelve packages, with the owner's later reports behind it. Its README is where
the shipped list, the recovery line and what is still untested live.

## Device and scope

| Field | Observed value |
| --- | --- |
| Model | `MiTV-AZFP0` |
| Device / board | `frozen` |
| Android | 9 / API 28 |
| Firmware display ID | `23.5.10.1035` |
| Fingerprint build fields | Android 9, `PPR1.180610.011`, incremental `1035`, `user/release-keys` |
| Application ABI | `armeabi-v7a,armeabi` |
| Memory | `MemTotal: 2056184 kB` (about 2 GB) |
| Network used for testing | Wi-Fi |
| Stock Home | `com.mitv.tvhome/.MainActivityUserMode` |

This is not `MIBOX3` / `once` or `MIBOX4` / `oneday`. The main reference for
package selection was the [China-firmware MFTP0 report](../../examples/redmi-mitv-mftp0/debloat.txt),
with each proposed package checked on this box. No firmware was flashed, no
system package was uninstalled and no app data was cleared.

## Observed disabled packages

The following states were read back after one full reboot on this firmware.
The first four packages were already disabled at the start of the session;
the remaining eight were applied using `tools/disable.sh` in small batches.
This table records observations, not an instruction to apply another model's list.

| Package | Reason / validation boundary |
| --- | --- |
| `com.miui.tv.analytics` | TV analytics; already disabled before this session |
| `com.miui.analytics` | Analytics host; already disabled before this session |
| `com.xiaomi.statistic` | Statistics upload; already disabled before this session |
| `com.xiaomi.mitv.shop` | TV shopping; already disabled before this session |
| `com.xiaomi.tvqs` | Quality reports: log/upload/DropBox receivers and a resident `performance.MitvPerfService` |
| `com.mitv.cloudcontrol` | Cloud control: network/boot receivers and a resident `DownloadService` |
| `com.xm.webcontent` | Promotional web container; its `FetchReceiver` handles screensaver-start and boot events. Not the core Android WebView |
| `com.mitv.shoplugin` | Taobao shopping plugin, version `6.8.24`; enabled at the start despite an earlier disable on a different version. The cause of that change is unknown |
| `com.xiaomi.gamecenter.sdk.service.mibox` | Game SDK, unused by this owner |
| `com.xiaomi.screenrecorder` | Screen recorder, unused by this owner |
| `com.xiaomi.mitv.calendar` | Calendar, unused by this owner |
| `com.mitv.gallery` | Xiaomi gallery and its push service, unused by this owner; photo data retained |

Before the first batch, TV QS used about 9.1 MiB PSS and cloud control about
4.7 MiB PSS. The web container and shopping plugin had no running process in
that sample. After disabling, the service dump no longer listed TV QS,
cloud control or gallery services. No repeatable overall latency/CPU benchmark
was made; these observations do not establish a fixed memory saving or a
speed-up percentage.

## Kept enabled

- Core vendor service `mitv.service`, stock Home and `com.droidlogic`.
- Xiaomi settings, Bluetooth, Sogou keyboard, Android WebView, package installer
  and VPN dialogs.
- Voice control, TV Manager and TVPush. The MFTP0 report's installer-whitelist
  and voice dependencies were a reason to leave them alone, not proof that the
  same dependency was established on this firmware.
- Xiaomi app store and `com.xiaomi.mitv.upgrade` (firmware updates).
- Casting: `com.xiaomi.mitv.smartshare`, `com.xiaomi.miplay`,
  `com.duokan.airkan.tvbox` and `com.xiaomi.wfdsinkhelperservice`.
- All installed video/streaming apps and Mi Home.

## Checks and remaining work

- Xiaomi settings opened; the owner confirmed Xiao AI voice control worked.
- One real reboot completed (`sys.boot_completed=1`, uptime reset); all twelve
  packages above remained disabled and stock Home was the focused window.
- `tools/check.py` reported no drift for the eight changes made in this session.
- After reboot, the owner confirmed video picture and audio in a usual streaming
  app. The Xiaomi app store home page loaded, but installing/downloading an app
  was not tested.
- `tools/restore.sh` was exercised on the first batch (TV QS, cloud control,
  web container and shopping plugin), then that batch was reapplied. Package
  states were read back; the four previously disabled packages stayed disabled.
  The later four owner-choice packages were recorded by the same recovery
  mechanism, but their restoration was not separately exercised.
- Casting and physical-remote standby/wake were confirmed by the owner on
  2026-11-05, and two days of intensive use were reported without problems on
  2026-11-02; the folder's README records what was not - the casting source
  device, app and protocol, the standby/wake cycle count, app download and OTA
  installation. Keeping a package enabled is not a functional pass for it.

## Home and ADB

Stock Home was never disabled. A pre-existing Aerial 1.30 installation and its
accessibility/notification services were retained, so this session did not
measure the reference launcher's performance or establish Home takeover.
Earlier, `set-home-activity` for Aerial returned `Success` while Home still
resolved to Xiaomi; the owner also saw the physical Home key return to Xiaomi.
No allowlisted application-ID build was tested on this box.

The stock Google ADB transport was offline while the box's ADB TCP port was
reachable; the project's Python client connected successfully. Turn network
debugging off on the box when you are done.

Backups and the recovery record are private. Future changes should continue to
use the same recorded list with `tools/disable.sh` and `tools/restore.sh`, not a
blanket enable of every package or another model's Home-disable procedure.

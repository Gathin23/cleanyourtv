# SkyworthDigital HP4416-TVplus / TVPLUS BOX

Owner report by [gggg-unit](https://github.com/gggg-unit), checked on Android TV 14 / API 34,
device HP44H, firmware `UKG3.260404.001.C02.00.085_20260723`, security patch 2026-06-05.
The box uses an Amlogic S905X4, approximately 2 GB RAM and 32-bit `armeabi-v7a` Android.

This is a short on-device report, not a several-day tested debloat list. The operator branding
does not make the SDMC DV6069Y Android 10 list applicable to this firmware.

## Home behavior

The factory Google launcher claims HOME at priority 2; Projectivy claims it at 0. An accessibility
helper can display a different launcher from the one selected by Android's package resolver.
After the reference launcher was installed and the box rebooted, the physical Home button still
opened Google's factory screen. Keep the factory launcher and setup wizard enabled; an explicit
activity launch or an injected key does not establish that the physical Home button was redirected.

## Operator management is more than one Android package

The installed `com.sdt.android.tr369` APK is version 1.0.39 (versionCode 10039), under
`/system/app/SdtTr369/`, with the shared system UID. At the owner's request it was disabled for
Android user 0 using the existing guarded disable tool, with its recovery record retained.
The APK and data were kept for restoration. This box has only that Android user.

After a reboot, the package remained disabled-user with no process or active service. The owner
confirmed normal physical remote controls, playback, sound and Wi-Fi. The factory launcher,
casting components, remote services and separate `com.sdt.ota` updater were kept enabled.
Operator-driven update and remote-support workflows were not tested after the change.

A read-only process audit nevertheless found these independent firmware components still running:

- `skyworth_tr069_hidl`, a native process running as root;
- the `com.skyworth.android.tr069` Android service;
- TR369-named logging scripts started by `init.tr369.scripts.logcat.rc`, writing rotating local
  logcat files under `/data/tcpdump/`.

Disabling the TR369 APK therefore does not stop all operator management or firmware logging.
Actual upload of those local log files was not demonstrated. No root access, init edits, system
APK changes or native-daemon termination were used.

An external network trial also found an ACS-hostname-correlated TCP 8010 connection, while vendor
configuration named TCP 8086 for TR069. A temporary 8010 rule received a natural connection attempt;
controlled IPv4 and IPv6 checks verified the 8086 rule. Blocking only 8086 would have missed the
observed 8010 traffic. These observations do not identify every management channel or decode its
commands, and router filtering is not part of this repository's cleanup procedure.

Disabling this agent is an optional owner decision for this exact firmware: it removes that
agent's support, diagnostics and update-coordination channel. Keeping the separate updater enabled
does not prove every OTA workflow still works. Use the existing [restore procedure](../restore.md)
with the same saved list and recovery record to undo a disable; the restore tool also reapplies
the Home and animation settings saved in that record. Recheck package state after firmware updates.

No general disable list is provided. Raw APKs, captures, credentials and unique device identifiers
are excluded from this report.

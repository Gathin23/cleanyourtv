# FFALCON 鹤6 PRO (tcl_mt5879_cn, China): the reference launcher installs through a packageinstaller window and survives a cold boot

The launcher half of this repo on the China `tcl_mt5879_cn` platform. The first report is
[#376](https://github.com/Gathin23/cleanyourtv/issues/376) on a 75R685C; this note is a second unit,
an FFALCON (雷鸟) 鹤6 PRO **65S585C Pro**, on an **older firmware** than any record on this platform
had tested (`2025062001`, 2025-06, against the lists' `2026012200`). One owner, one same-day session
with three full reboots. The debloat list from
[`examples/ffalcon-65s585c-pro/`](../../examples/ffalcon-65s585c-pro/debloat.txt) was applied the same
day and its packages stayed disabled across every reboot; that list's own reporter had already
reboot-checked it on the newer firmware. Not days of use — the launcher and the two fixes below were
exercised the day they were installed.

## Identify the firmware

| Field | Reported value |
|---|---|
| Retail name | FFalcon (雷鸟) 鹤6 PRO 65S585C Pro (China) |
| `ro.product.model` / `ro.product.device` | `tcl_mt5879_cn` |
| Android | Android TV 11, SDK 30 |
| Build | `RQ3A.210705.001` / incremental `2025062001` (2025-06) |
| Stock launcher | `com.tcl.cyberui`, the only HOME claimant |

ADB comes up the same way as the [`tcl-q10g-pro`](../../examples/tcl-q10g-pro/debloat.txt) header
describes — 关于本机 → 本机信息, then UP DOWN LEFT RIGHT on the remote — and stays on across reboots.

## The install gate, and the window

A bare `pm install -r` of the HOME-declaring APK answers `INSTALL_FAILED_VERIFICATION_FAILURE` on this
firmware too: the `LauncherStrategy` gate the `tcl-q10g-pro` header documents is present and active
(`InstallConfig` in `content://com.tcl.providers.config/all` shows `launcherStrategy` enabled with an
empty package list).

The window from [#376](https://github.com/Gathin23/cleanyourtv/issues/376) works here as well. All
three commands in **one** shell call, so the verifier is out of the loop for only a couple of seconds:

```sh
pm disable-user --user 0 com.android.packageinstaller
pm install -r /data/local/tmp/tvhome.apk
pm enable        com.android.packageinstaller
```

`pm install` answered `Success`. The re-enable is not optional; afterwards
`pm list packages -e com.android.packageinstaller` showed the installer enabled again, and installs
and a full reboot after that behaved normally. Nothing is patched and no property changes.

## Home handover

- `cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity` answered `Success`, and
  `resolve-activity` for MAIN+HOME then named the new package. A Home press from any app opened it.
- The two TCL app ops from #376 are what move Home on this build, and both survived the reboot:
  `cmd appops set com.gathin.tvhome APP_AUTO_START allow`,
  `cmd appops set com.gathin.tvhome APP_ASSOC_START allow`, then `appops write-settings`.
  The bare `AUTO_START` name (G08/AU02 builds) is **unknown** here: `appops set` answers
  `Unknown operation string`. `APP_AUTO_START` is the name on this build.
- `com.tcl.cyberui` stayed **enabled** the whole time, as the safety net; nothing in this note
  disables it.

## Cold boot: two fixes beyond #376

With only `set-home-activity`, this unit did **not** land on the new launcher at boot — not even the
brief flash #376 describes: every cold boot came up in `com.tcl.cyberui` and stayed there until a
Home press. Two findings, both needed here:

**1. The firmware resets the accessibility list at boot — and dead entries were in it.** After the
first reboot the launcher's `HomeKeyService` was gone from
`settings get secure enabled_accessibility_services`, and `accessibility_enabled` was back to `0`.
The list it reset to still carried the two `com.tcl.walleve` `AiAccessibilityService` entries — and
`com.tcl.walleve` was one of the packages the debloat list disables (voice off by choice). Cleaning
the list to only the launcher's service and switching accessibility back on bound it
(`dumpsys accessibility` showed the service under `Bound services`), and it then **survived the next
reboot**. If you disable `com.tcl.walleve` with the ffalcon list, remove its two entries from
`enabled_accessibility_services` the same way; a list that names services of a disabled package is
what this firmware reacted to. (#376's unit kept walleve enabled and never saw the reset.)

**2. `stock_home_redirect` is what makes cold boot land on the launcher.** With the service bound, a
theme pushed by `tools/theme.sh` carrying just one key does it — no rebuild:

```json
{ "stock_home_redirect": "com.tcl.cyberui" }
```

The service then subscribes to `TYPE_WINDOW_STATE_CHANGED` (visible in `dumpsys accessibility`),
`probeAtBoot` covers the case where the stock home was already on screen before the bind, and cyberui
coming up sends it straight back to the new launcher. Verified across two further full reboots: HOME
resolved to `com.gathin.tvhome/.MainActivity`, `mFocusedApp` after boot was the new launcher, and the
owner confirmed Home key, standby/wake, volume-key repeat and app launches on the real remote.

## What was not tested

- Days of use. Everything here is same-day; standby overnight, casting and a week of presses are not
  covered.
- The window with an install failing inside it (never happened; the batch structure and an alive ADB
  session are the mitigation).
- `pm uninstall com.gathin.tvhome` on this firmware; the rollback path exercised here is
  `set-home-activity` back to `com.tcl.cyberui/.MainActivity`, which puts Home on the stock launcher
  with the new launcher still installed.
- The app-store / USB channels; only the ADB path with the packageinstaller window was tried.
- `launcher/build-and-install.sh` end to end: its `pm install` step hits the gate and stops, so the
  install was done by hand as above, then the grants and `set-home-activity` the script would have
  done.

## Before you apply anything here

Probe the firmware first (`tools/tv.py --probe`) and compare the incremental: this note is
`2025062001`, the lists were verified on `2026012200`. Keep `com.tcl.cyberui` enabled throughout —
it is the only thing that draws Home if the handover goes wrong. Keep the window in one shell call
with ADB alive, and check `pm list packages -e com.android.packageinstaller` afterwards. Network ADB
is temporary maintenance access: turn debugging back off when you are done.

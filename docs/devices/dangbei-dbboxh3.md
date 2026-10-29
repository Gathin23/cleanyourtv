# Dangbei DBBOXH3 (当贝盒子 H3, Rockchip `rk356x_box`)

Android 11 (API 30), build `RD2A.211001.002`, stock launcher `com.dangbei.leard.leradlauncher`. One
session, reported in [#2286](https://github.com/Gathin23/cleanyourtv/issues/2286); no debloat list was
kept. The note is here for one package: its name reads like a promo downloader any list would drop, and
on this firmware dropping it takes `system_server` with it.

## Identify the firmware

| Field | Value |
|---|---|
| Retail name | Dangbei 当贝盒子 H3 (Dangbei Box H3) |
| `ro.product.model` | `DBBOXH3` |
| Board | `rk356x_box` (Rockchip) |
| Android / SDK | 11 / 30 |
| Build | `RD2A.211001.002` |
| Stock launcher | `com.dangbei.leard.leradlauncher` |

## `com.dangbei.dbdownloadrefs` has to stay enabled

The package looks like the kind of thing a debloat list exists for: "db download refs", a database of
download references, the update/promo plumbing of a Chinese box. It ships in
`/system/priv-app/DBdownloadrefs`, runs as `android.uid.system` (persistent), and exposes
`com.dangbei.dbdownloadrefs/.db.DatabaseProvider`. It is none of those things on this build: the vendor's
own `ProcessManagementService`, inside `system_server` under the OptEngine name, asks it for the list of
packages to force-stop every time the launcher comes back to the front.

Disabled, the provider is not there, the query answers null, and `forceStopPackages` iterates it without
a null check. `system_server` dies and the framework soft-reboots: the kernel's uptime keeps counting, the
screen goes black and comes back.

So on this box it goes on the keep-enabled list, next to the core packages: the same reason, different
owner of the failure. `tools/disable.sh`, `disable.ps1` and `check.py --fix` cannot answer it for you -
`recovery.core_packages()` refuses Android's own packages and the TV middleware, and `com.dangbei.*` is
neither - so the answer here is a human one: read `dumpsys package com.dangbei.dbdownloadrefs` before a
generic list takes it, and leave it out of the list you apply.

## The crash

Four consecutive deaths, each with a fresh `system_server` pid (436 → 3116 → 5202 → 7323), roughly one
per Home press:

```
FATAL EXCEPTION IN SYSTEM PROCESS: OptEngine-ProcessManagementService
java.lang.NullPointerException: Attempt to invoke interface method
'java.util.Iterator java.util.List.iterator()' on a null object reference
  at com.dangbei.server.utils.SystemUtilsManagerService.forceStopPackages(SystemUtilsManagerService.java:626)
  at com.dangbei.server.utils.SystemUtilsManager.forceStopPackages(SystemUtilsManager.java:342)
  at com.android.server.dangbei.optengine.ProcessManagementService$ProcessManagementHandler.handleMessage(ProcessManagementService.java:68)
```

The chain is: Home key → launcher resumed → OptEngine cleanup → `DatabaseProvider` unavailable → the
query answers null → `forceStopPackages(null)` → NPE → `system_server` dies. The same NPE is reachable
whenever that provider is unavailable for any other reason - a corrupted database, a killed provider, a
full `/data` - which is why the reporter reads it as a firmware robustness bug rather than a debloat-only
one; that half was not tested, and this note only records the disabling route that was.

## How it was reproduced, and the way back

Both commands were run on the set; they are the hazard, not a step to copy:

```bash
adb shell pm disable-user --user 0 com.dangbei.dbdownloadrefs
adb shell input keyevent KEYCODE_HOME   # crash within a second
```

The way back is `pm enable`, and on this build that alone is not enough: the package keeps the leftover
`stopped` user flag, so it stays in the stopped bucket across a reboot and the provider is still not
reachable. `cmd package set-stopped-state` does not exist on this build; the reporter cleared the flag by
starting one of the package's own components once:

```bash
adb shell pm enable com.dangbei.dbdownloadrefs
adb shell am broadcast -n com.dangbei.dbdownloadrefs/.receiver.BootReceiver -a com.dangbei.probe.PING
```

After that, nine consecutive Home presses left the `system_server` pid unchanged, the uptime monotonic
and no new crash entries in `logcat`. Whether a reboot would have cleared the flag just as well was not
tried.

## What this note does not cover

Nothing else on the box was tested: there is no list, no Home handover to another launcher, no HDMI or
remote check, and `com.dangbei.leard.leradlauncher` is named here only because it is the launcher the
crash chain starts from. The one claim is the dependency, and it is the reporter's measurement on that
set.

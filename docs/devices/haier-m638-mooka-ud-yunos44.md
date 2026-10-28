# Haier (M638_MOOKA_UD): a YunOS 4.4 observed-state note

Observed-state note from a Haier MOOKA set running Alibaba's YunOS on top of Android 4.4. This is  
**not** a `debloat.txt` and the unit is not a candidate for `examples/`: `pm disable-user`,  
`pm disable` and `pm block` are all silently swallowed by the framework on this firmware, so the  
project's disable-only toolchain (`tools/disable.sh`, `disable.ps1`, `check.py --fix`) no-ops on  
every package. The note exists for the narrower purpose the maintainer asked for in the issue  
thread: record what this firmware does and does not respond to, so the next YunOS owner doesn't  
spend an evening rediscovering it.

The unit's debloat half is effectively unreachable from the shell on YunOS 4.4. The launcher half  
is unreachable for the same reason `PROMPT.md` already spells out for sets below `minSdk 26`:  
this firmware is API 19. Both halves of the project therefore decline on this set, and the  
practical guidance is the TV's own privacy toggles plus router-level DNS blocking, as  
`PROMPT.md` already points to for Fire OS 5 sets with the same `pm disable-user` failure mode.

## Identify the firmware

| Field                      | Value                                              |
| -------------------------- | -------------------------------------------------- |
| Retail name                | Haier MOOKA U55A5M (55-inch)                       |
| `ro.product.model`         | `M638_MOOKA_UD`                                    |
| `ro.product.device`        | `M638_MOOKA_UD`                                    |
| `ro.product.manufacturer`  | `Haier`                                            |
| SoC family                 | MStar MST6A638 (not MediaTek/CVTE)                 |
| Android / SDK              | 4.4.4 / 19                                         |
| `ro.build.version.release` | `4.4.4-R-20200803.1840`                            |
| `ro.build.display.id`      | `KTU84P test-keys`                                 |
| Firmware                   | `L_MST6A638_MOOKA_U55A5M1P1` (compiled 2020-08-03) |
| `ro.adb.secure`            | `0`                                                |
| `ro.debuggable`            | `0`                                                |
| Network ADB                | port 5555 open; no Wireless debugging              |

The firmware's `MBOOT` is `TVOS-3.a.12` (Jun 22 2016). The Android layer is YunOS, Alibaba's  
fork — a deeply customised Android 4.4 used on Chinese-market TV sets, not Google-certified  
Android TV or Google TV.

## Related units in the repo

- [`examples/haier-android-tv-dvb2k/`](../../examples/haier-android-tv-dvb2k/debloat.txt) —  
  HaierATV**T56**DVB, Android TV 11, MediaTek/CVTE `broadway`.
- [`examples/haier-h32s80efx/`](../../examples/haier-h32s80efx/debloat.txt) — HRT56EU, Google TV  
  14, CVTE `deto`.
- [`examples/haier-hr9216eu/`](../../examples/haier-hr9216eu/debloat.txt) — MatrixTV GE, Google  
  TV 14.
- [`docs/devices/haier-haieratvt32dvb.md`](haier-haieratvt32dvb.md) — Haier/CVTE `dupont` on  
  Google TV 14.
- [`docs/devices/haier-hra962x2-ali6g-cn.md`](haier-hra962x2-ali6g-cn.md) — Ali6G, China market.

All five are CVTE/MediaTek Google-TV-or-Android-TV sets. None of them is YunOS, so the package  
overlap with this unit is partial — apply any of those lists only after checking each name with  
`dumpsys package` on the specific unit, and do not assume the disable half works on YunOS.

## Home stack

`dumpsys activity activities` shows the foreground on this unit as  
`com.youku.taitan.tv/com.youku.tv.home.activity.HomeActivity` — Youku Tatong, the YunOS home  
carrier for this set. `dumpsys package` reports exactly one `android.intent.category.HOME`  
candidate:

```
android.intent.action.MAIN:
    com.yunos.tv.provision/.DefaultActivity filter 42a37090
      Action: "android.intent.action.MAIN"
      Category: "android.intent.category.HOME"
      Category: "android.intent.category.DEFAULT"
      mPriority=1, mHasPartialTypes=false
```

So the YunOS home stack on this build is `com.yunos.tv.provision/.DefaultActivity` as the HOME  
fallback (priority 1), which then hands the foreground to Youku Tatong. Youku Tatong itself  
declares only `android.intent.category.DEFAULT`, not HOME. Disabling `provision` is the same  
class of risk as disabling `setupwraith` on Google TV (a HOME fallback that owns something  
load-bearing), and on this firmware `pm disable-user` does not work on it anyway.

## What YunOS 4.4 silently refuses

`pm`, `pm disable`, `pm disable-user --user 0`, and `pm block` all return exit 0, print nothing,  
and leave the package's `enabled` state unchanged. Confirmed against both a system package and a  
third-party `/data/app` package:

```
$ pm disable-user --user 0 com.yunos.advert.service
(no output; exit 0)

$ pm disable com.yunos.advert.service
(no output; exit 0)

$ pm block com.yunos.advert.service
(no output; exit 0)

$ dumpsys package com.yunos.advert.service | grep "User 0:"
    User 0:  installed=true blocked=false stopped=false notLaunched=false enabled=1
```

Same no-op on `com.cibn.tv` (a third-party `/data/app` package, CIBN):

```
$ pm disable-user --user 0 com.cibn.tv
(no output)

$ dumpsys package com.cibn.tv | grep "User 0:"
    User 0:  installed=true blocked=false stopped=false notLaunched=false enabled=1
```

`pm hide` does not exist on this firmware's `pm` (Android 4.4 predates it). The full `pm` help  
prints the standard 4.4 subcommand list (`disable`, `disable-user`, `disable-until-used`,  
`block`, `unblock`, `enable`, etc.), and the `/system/bin/pm` launcher is the stock one:

```
$ cat /system/bin/pm
base=/system
export CLASSPATH=$base/framework/pm.jar
exec app_process $base/bin com.android.commands.pm.Pm "$@"
```

So YunOS patches `PackageManagerService.setEnabledSetting` (and the `block` equivalent) to  
silently drop shell-originated calls. This is the same failure mode `PROMPT.md` describes for  
Fire OS 5, but on every package — not just system ones, and including third-party `/data/app`  
packages.

The shell identity is `uid=2000(shell)`. There is no `su` binary, and `adb root` is rejected  
because `ro.debuggable=0`, so there is no escalation path on this firmware. One package  
(`com.yunos.tv.remoteassistant`) shipped from the factory in `enabled=2` (DISABLED) state, so  
*something* in the system can set this flag — just not the shell, which is consistent with a  
PMS-layer guard rather than a runtime permission issue.

## What YunOS 4.4 does respond to

`pm uninstall -k` on third-party `/data/app` packages **does** work — YunOS patches  
`setEnabledSetting` but not `deletePackageAsUser` for the shell on data-app packages. `pm clear`  
on telemetry packages also works. Both are intentionally out of scope for this repo: the  
maintainer's position is disable-only, and `pm uninstall -k` is not added to the toolchain. The  
point of recording them here is so the next YunOS owner knows what does and does not respond,  
without having to spend an evening rediscovering it.

The fallback on a set where the disable half is unreachable is the one `PROMPT.md` already  
points to for Fire OS 5 with the same `pm disable-user` failure: the TV's own privacy toggles in  
Settings, plus router-level DNS blocking of the ad/telemetry domains. YunOS 4.4 predates Android  
9's Private DNS, so DNS filtering has to happen upstream of the TV, not on it.

## Broken adbd filesync

The unit's adbd filesync module is also broken. `tools/tv.py --pull /data/app/<pkg>-N.apk
local.apk` returns `No such file or directory` even though `pm path` reports the path and  
`cat /data/app/<pkg>-N.apk > /dev/null` succeeds from the shell. `ls /data/app` itself is  
`Permission denied` (opendir), but `cat` on a known path works. `tools/b64push.py` and any  
filesync-based tool will hit the same wall on this firmware.

A working escape hatch is shell + `busybox base64` (`busybox` is on this firmware), chunked with  
`dd` and verified with `busybox md5sum`. The shape of it, in case a future `tools/pull.py`  
generalises it:

```python
# pull an APK from a set whose adbd filesync is broken, via shell + busybox base64
import base64, hashlib
path = run(dev, f"pm path {pkg}").split("package:", 1)[1].strip()
size = int(run(dev, f"busybox stat -c %s '{path}'"))
md5_remote = run(dev, f"busybox md5sum '{path}'").split()[0]
chunk = 4 * 1024 * 1024
h = hashlib.md5()
with open(out, "wb") as f:
    for skip in range(0, size, chunk):
        b64 = run(dev, f"dd if='{path}' bs={chunk} skip={skip//chunk} count=1 2>/dev/null | busybox base64")
        data = base64.b64decode("".join(b64.split()))
        f.write(data); h.update(data)
assert h.hexdigest() == md5_remote
```

This pulled five 18–34 MB APKs on the unit this note was written from, MD5 matched on every  
one. Worth a paragraph in `TROUBLESHOOTING.md` if a future `tools/pull.py` is added; not  
included in this note because the maintainer asked for a docs/devices/ record, not a tool.

## Factory menu trap

The unit exposes a Haier factory menu reached from the standard Settings → About → Build number  
sequence. The menu's `ADB 调试开关` ("ADB debugging switch") entry is the only network-ADB toggle  
on this firmware; there is no Developer options → Network debugging or Wireless debugging entry.  
The same menu contains `工厂初始化` ("factory init"), `EEP初始化` ("EEP init"), `整机工厂`  
("whole-unit factory"), `OPC调整`, `设置屏参配置` and `设置 MAC 地址` entries, which is the  
same class of trap `PROMPT.md` warns about for `com.cvte.tv.hkcfac` and `com.at.factory.*` on  
CVTE boards: opening them can flip factory-mode flags that disable Input, CEC, Play services or  
the tuner. Do not enter them on this set; the ADB switch is the only entry that is safe to  
toggle.

The package behind the factory menu on this unit is `mstar.factorymenu.ui` (in the package  
list). It was not launched during this note. `com.yunos.engineermode` is also present and was not  
launched either.

## Validation scope

- One unit, one probe, on 2026-10-26 over ADB on the LAN. No reboot campaign was run; the  
  "reboot re-enables some packages" behaviour `PROMPT.md` describes for TCL Android 9 was not  
  tested, since the disable half does not take effect in the first place on this firmware.
- The factory-disabled state of `com.yunos.tv.remoteassistant` is reported as shipped; whether  
  it was set at the factory or by a prior owner could not be distinguished on a second-hand set.
- The five third-party packages uninstalled during the probe were backed up to local APKs with  
  the busybox-base64 workaround above before the uninstall; no system package was modified or  
  removed. `pm clear` was run on four telemetry packages (`com.haier.activeuser`,  
  `com.yunos.tv.probe`, `com.alira.info`, `com.haier.yushuo`); their accumulated data was  
  cleared but the packages themselves remain installed and enabled.


- The YunOS PMS patch is inferred from behaviour (silent exit 0 + unchanged `enabled` state on  
  every disable variant), not from reading framework source; the exact mechanism inside  
  `PackageManagerService` was not confirmed.
- Network ADB on this firmware is `ro.adb.secure=0`, which means port 5555 gives a shell to any  
  device on the LAN without the "Allow" prompt. The ADB switch in the factory menu is the only  
  way to close it; `tools/adb-closed.py <tv ip>` verifies the port refuses afterwards.

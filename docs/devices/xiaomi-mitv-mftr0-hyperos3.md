# Xiaomi MiTV-MFTR0 on HyperOS TV 3 (OS3.0.28, Android 14): what changed

From @sxp01001's report in #1355. The [`xiaomi-mitv-mftr0`](../../examples/xiaomi-mitv-mftr0/debloat.txt) list and
[its Home notes](xiaomi-mitv-mftr0-debloat-home.md) were made on HyperOS TV 2 (`OS2.0.23.0.RFTMATV`, Android 11).
This set took a silent OTA to HyperOS TV 3 (`persist.os2.upgraded=true`, boot reason `reboot,ota`), and Home
behaves differently there. Debloating still works; the Home handover below did not.

| Field | Observed value |
|---|---|
| Retail model | Xiaomi TV S55 Mini LED |
| System model / codename | `MiTV-MFTR0` / `freeguy`, product_id `dl4c` |
| SoC | MediaTek `mt9653`, `armeabi-v7a`, 4 GB |
| Android / API | Android 14 / 34 |
| Build | `OS3.0.28.0.UFTMATV` (HyperOS TV 3, China) |

The reporter tested with a third-party launcher (Aerial, `app.orchard.tv`, a valid `MAIN`/`HOME`/`DEFAULT` app),
not this repo's launcher, to isolate the Home question.

## Home stays on PatchWall

`cmd role add-role-holder --user 0 android.app.role.HOME <pkg>` took (`get-role-holders` lists it) and
`cmd package set-home-activity` printed `Success`, yet `resolve-activity` for `MAIN`/`HOME` still answered
`com.mitv.tvhome/.MainActivityUserMode`, and every Home press over 8 seconds landed there.

`dumpsys package com.mitv.tvhome` shows why: a persistent preferred activity for `MAIN` + `HOME` with
`mAlways=true`, chosen from PatchWall and `com.xiaomi.mitv.settings/.entry.FallbackHome`. It outranks the HOME role
and `set-home-activity`, and `query-activities` for HOME did not list the third-party launcher at all, only
PatchWall (priority 0) and FallbackHome (priority -1000). `clear-package-preferred-activities` is gone on
Android 14 (`Unknown command`).

The stock Home activity is also protected: starting the HOME intent from the shell fails with
`Permission Denial ... requires com.mitv.tvhome.permission.HOME_STATE`, a vendor permission the shell and
third-party apps do not hold.

## Do not disable PatchWall with an ordinary launcher

`pm disable-user --user 0 com.mitv.tvhome` is accepted, but the `mAlways` preference is not cleaned up: Home then
left the TV with `mCurrentFocus=null` and no usable window until PatchWall was re-enabled:

```bash
tools/venv/bin/python -I tools/tv.py 'pm enable --user 0 com.mitv.tvhome'
tools/venv/bin/python -I tools/tv.py 'cmd package set-home-activity --user 0 com.mitv.tvhome/.MainActivityUserMode'
```

On HyperOS TV 2 and on other HyperOS TV 3 sets (MiTV-ASTR0, MiTV-MFFU0) the route that worked is the
allowlist identity, `-PlauncherApplicationId=com.ts.launcher`, installed **before** PatchWall is disabled
([`xiaomi-mitv-astr0-blueplanet.md`](xiaomi-mitv-astr0-blueplanet.md),
[`xiaomi-mitv-mftr0-debloat-home.md`](xiaomi-mitv-mftr0-debloat-home.md)). It was not tried on this OS3.0.28 set,
so whether it gets past the `mAlways` preference and the `HOME_STATE` permission is open here. On a third
unit of this model on `OS3.0.103.0.UFTMATV` it took Home and survived a reboot; see
[the model note](xiaomi-mitv-mftr0-debloat-home.md#a-third-unit-on-os30103-the-allowlist-route-works). Keep the
two recovery lines above at hand.

## Other OS3.0 limits

- **No Accessibility entry.** 设置 > 通用设置 > 系统 has only 语言, 输入法, 位置信息, 通知管理, 快应用服务框架,
  恢复默认出厂设置 and 无操作自动关机. `android.settings.ACCESSIBILITY_SETTINGS` and the
  `com.android.tv.settings/.oemlink.Accessibility*` activities are intercepted; Xiaomi's settings stay in front.
- **Accessibility does not survive a reboot.** Values written over ADB come back as
  `enabled_accessibility_services=null` and `accessibility_enabled=0` after a cold start, so `HomeKeyService` and
  `InputKeyService` would need re-enabling after every boot.
- **`WRITE_SECURE_SETTINGS` is revoked at reboot.** A `pm grant` reads back `granted=false` after a restart.
- **Locked verified boot.** `/proc/cmdline` has `androidboot.vbmeta.device_state=locked` and
  `androidboot.veritymode=enforcing`; there are A/B boot slots but no `bootctl` and no fastboot, so going back to
  OS2.0 is not possible from the shell.

## A second set on the same build

From @csloz's comment in #1355: an L55MA-SPL (4 GB, 64 GB, 4K panel, `mt9653`, `OS3.0.28.0.UFTMATV`). On a second
attempt Projectivy stayed Home across a power cycle after the owner's agent disabled Xiaomi TV Home, the Google TV
launcher and Xiaomi Settings' FallbackHome; the same session's own summary still reported TV Home as the current
Home, and the steps were not recorded closely enough to repeat. If that meant disabling
`com.xiaomi.mitv.settings` (FallbackHome's package), do not copy it: the MFTR0 list keeps it untouched. That session also
disabled `com.sohu.inputmethod.sogou.tv`, the only keyboard here, and `com.xiaomi.mitv.upgrade`; the
[`xiaomi-mitv-mftr0`](../../examples/xiaomi-mitv-mftr0/debloat.txt) list keeps the first and this repo keeps
firmware updaters on by default.

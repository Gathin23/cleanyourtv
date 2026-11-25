# XGIMI Z9X 4K (旗舰版) China, `horse_popular`: the stock Home has to stay enabled

One unit, mainland China, read on the projector by its owner and posted as
[#4478](https://github.com/Gathin23/cleanyourtv/issues/4478) on 2026-11-25. This records what that unit did;
nothing here was measured by this repository, and it is not a debloat list.

It is **not** the set in [`examples/xgimi-z9x-china`](../../examples/xgimi-z9x-china/README.md). That unit is
`Product_raptor` / `snake_deep` on Android 12 `v6.13.113`, and its install command passes `com.xgimi.home` to
`launcher/build-and-install.sh`, which disables it. Same retail name, different board, product and firmware:
do not apply that list here.

## Identify this firmware

| Field | Observed value |
|---|---|
| Retail model | XGIMI Z9X 4K (旗舰版), mainland China |
| Manufacturer / brand | `XGIMI` |
| `ro.product.model` / `.name` / `.device` | `XGIMI TV DOHKO` / `Product_dapeng` / `horse_popular` |
| Android / API | Android 14 / SDK 34, GMUI |
| Build | `v6.15.62` |
| Fingerprint | `XGIMI/Product_dapeng/horse_popular:14/UQ1A.240105.004.A1/v6.15.62:user/test-keys` |
| Stock Home | `com.xgimi.home` |
| Also claims Home | `com.android.newsettings/com.xgimi.newsettings.base.FallbackHome` (priority -1000) |

## The symptom: a black screen at every cold boot

Following [`PROMPT.md`](../../PROMPT.md), `com.xgimi.home` was disabled and the reference launcher made Home,
with `HOME_KEY_SERVICE` and `stock_home_redirect=com.xgimi.home`. It worked live. The projector was then only
ever woken from XGIMI's fast standby with the remote's power button, which does not reboot, so about three
weeks passed before the first real cold boot - and that boot, and every one after it, stopped at the XGIMI
logo on blue followed by a black screen. The projector never joined Wi-Fi, so network ADB was unreachable, and
safe mode did not help.

## Why: `com.xgimi.home` is the only Home that can start before boot finishes

Read once ADB was reached over USB:

- `dumpsys user` reports `State: BOOTING` indefinitely and `getprop sys.boot_completed` is empty.
- The home root task exists but is empty (`Task{… #1 type=home … sz=0}`), `mCurrentFocus` is null and no home
  process is running.
- While the user is `BOOTING` the only HOME candidate is
  `com.android.newsettings/com.xgimi.newsettings.base.FallbackHome` (priority -1000), which never starts;
  `am start -a android.intent.action.MAIN -c android.intent.category.HOME` answers "unable to resolve".
- `com.xgimi.home` is `DIRECT_BOOT_AWARE` in its `privateFlags`. It is the only real Home that can start
  before boot finishes; the reference launcher's `MainActivity` is not. With it disabled nothing starts Home,
  so boot never completes and user 0 never leaves `BOOTING`.

Ruled out by the report: encryption (`ro.crypto.state=unsupported`), storage (13 % of `/data` used) and a
crash loop (`system_server` stable, crash buffer empty). `logd` is stopped by this firmware, so `logcat`
prints only `Logcat read failure`.

```shell
$ dumpsys user | grep State:
    State: BOOTING
$ getprop sys.boot_completed
(empty)
$ cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
    com.android.newsettings/com.xgimi.newsettings.base.FallbackHome
$ dumpsys package com.xgimi.home | grep -o 'DIRECT_BOOT_AWARE'
DIRECT_BOOT_AWARE
```

## The way back, without a firmware flash

`pm enable com.xgimi.home` and then starting HOME brought the set up at once:
`boot_completed=1`, `RUNNING_UNLOCKED`, Wi-Fi back, and two later cold boots came up normally. Wait about
20 seconds after the change before rebooting - the same write delay
[`docs/TROUBLESHOOTING.md`](../TROUBLESHOOTING.md) describes: a reboot 3 s after it came back with the package
still disabled (`enabled=3`).

```shell
TV_HOST=<tv ip> tools/tv.py 'pm enable com.xgimi.home'
TV_HOST=<tv ip> tools/tv.py 'am start -W -a android.intent.action.MAIN -c android.intent.category.HOME'
```

The reason the set answers nothing over the network is that it has no address, not that it has no shell, so
`adb connect` has nothing to reach. On this unit `persist.sys.usb.config=adb` was already set, which makes the
projector's USB-A port enumerate as an ADB device before Wi-Fi comes up - and, as `ro.adb.secure` is not
enforcing here, keyless: any host that enumerates it gets a shell. That is the same warning
`tools/disable.sh` prints for a set like this, not an access route to plan on.

## A setup that holds on this board, cold boot verified

- `com.xgimi.home` stays enabled and remains the only HOME. This took a local build of the launcher without
  `MainActivity`'s HOME intent-filter: with both apps claiming HOME, XGIMI's own chooser
  (`com.xgimi.xgimiframeworkui/.resolver.ResolverActivity`) opens on Home and ignores `set-home-activity`.
- The launcher comes to the front through `HomeKeyService` and `stock_home_redirect=com.xgimi.home`.
- The firmware blocks `BOOT_COMPLETED` for third-party apps: the broadcast history shows `FAILURE` for every
  manifest receiver outside `com.xgimi.*`. It also drops third-party accessibility services at boot. The
  system still binds the `GuardRevival` notification listener after boot, so having that put `HomeKeyService`
  back on the list (with `WRITE_SECURE_SETTINGS` granted) brings the launcher back. A receiver that held
  `goAsync()` for about 60 s got its process killed as an ANR, so the revival has to be quick.
- With the stock launcher enabled, XGIMI Settings
  (`com.android.newsettings/com.xgimi.newsettings.SettingService`) shows again; while `com.xgimi.home` was
  disabled that service started but displayed nothing.

## What this changes on any projector

A projector that is only ever woken from fast standby is never cold-booted, so a Home change can look right
for weeks. The reboot check after a Home change has to be a real `reboot` or a power cycle, not standby and
wake.

The report also asks for a warning in `PROMPT.md` itself; that page's safety rules are the maintainer's, so
this note is the record instead.

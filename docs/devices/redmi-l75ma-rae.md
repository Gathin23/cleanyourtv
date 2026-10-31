# Redmi Smart TV A75 2025 energy edition (L75MA-RAE): 2026-firmware Home takeover

Tested on Redmi Smart TV A75 2025 energy edition, sold in China as L75MA-RAE; internal model
`MiTV-ASTP0` (codename `mulan`), Android 9 / API 28, firmware `26.3.17.OS3.0.101.0.PSTAATV`
(HyperOS era, 2026-03), Amlogic, 2 GB RAM. **32-bit only**: `ro.product.cpu.abilist` is
`armeabi-v7a,armeabi`, so arm64-only apps (Tubi, Red Bull TV, Rakuten TV) fail to install. Stock
Home is `com.mitv.tvhome/.MainActivityUserMode`. Debloat list:
[`examples/redmi-l75ma-rae/debloat.txt`](../../examples/redmi-l75ma-rae/debloat.txt).

This record covers the debloat plus what it took to make a third-party launcher usable as the
actual Home screen on this 2026 firmware. The debloat half alone works exactly like the older
[`examples/xiaomi-mitv-astp0`](../../examples/xiaomi-mitv-astp0/debloat.txt).

## What failed and why

- `cmd package set-home-activity --user 0 com.ts.launcher/com.gathin.tvhome.MainActivity` prints
  `Success`, but HOME still resolves to `com.mitv.tvhome`. Unlike MiTV-AETP0 and MiTV-MFTP0
  (where the `com.ts.launcher` application-ID build became Home and survived a reboot), this
  2026-03 build no longer accepts that ID: resolving HOME to it lands in
  `android/com.android.internal.app.ResolverActivity`.
- Letting the Home key through and redirecting later does not work either: the framework defers
  any third-party HOME-category activity start (explicit component included) until the stock
  launcher has fully settled - measured 4.5 s of PatchWall on screen before the deferred start
  ran, with animations off. `window_animation_scale 0` does not change this.
- The deferral is not the MIUI background-start restriction: a shell `am start` of the same
  component in the same moment is instant. It only applies to HOME-category starts (and was seen
  once for a later explicit start while the stock home was still settling).
- `appops` has no `AUTO_START`, `APP_AUTO_START` or `START_ANY_ACTIVITY` operation on this
  firmware, so the MIUI-style grants in `launcher/build-and-install.sh` are no-ops here.

## What works: consume the key, start by explicit component

`HomeKeyService` (HOME_KEY_SERVICE=1) receives the remote's HOME key before the system resolves
it. Two local patches to `launcher/`:

1. The `KEYCODE_HOME` branch consumes the key (returns true) and opens `MainActivity` by
   **explicit component** (`new Intent(this, MainActivity.class)` instead of the
   HOME-category intent). If the service is ever unbound the key falls through to the system and
   Home resolves to the stock launcher, so the TV never loses its Home button.
2. `openHome()` uses the same explicit component, without an animation flag delay.

The result: pressing Home lands on the new launcher instantly, with no PatchWall flash.

## Reaching the stock launcher on demand

Directly starting `com.mitv.tvhome/.MainActivityUserMode` from an app is denied:
it carries the signature-level `com.mitv.tvhome.permission.HOME_STATE` (`dumpsys package`
shows `prot=signature`), and the denial fires for explicit and implicit intents alike, from a
foreground caller too. Shell `am start` bypasses it (uid 2000), an app cannot.

The working path: a tiny bridge app (installed as its own package, icon and label free) starts
`com.ts.launcher/com.gathin.tvhome.GlobalHomeActivity`, a `Theme.NoDisplay` activity that calls
`performGlobalAction(GLOBAL_ACTION_HOME)` on the accessibility service instance. The framework's
global Home action runs HOME resolution itself - it needs no HOME_STATE permission and lands on
the stock launcher, which the system still prefers. From PatchWall, Home comes back through the
key service. Both directions are instant.

## Keeping the key service alive

- Android takes accessibility services off the enabled list when the app is force-stopped, and
  this firmware additionally clears `enabled_accessibility_services` over standby through a
  normal unbind. Two local patches keep `ServiceRepair` effective:
  1. `onUnbind` no longer drops the repair record (the standby clear arrives as an unbind, so
     dropping the record there defeats the repair).
  2. `ServiceRepair.restore()` adds `HomeKeyService` unconditionally instead of trusting the
     prefs record.
- `pm grant com.ts.launcher android.permission.WRITE_SECURE_SETTINGS` plus
  `restore_key_services: true` in the launcher's `config.json` turn the repair on; starting
  `MainActivity` (by hand from the stock launcher's app list, or from a `BOOT_COMPLETED`
  receiver) then writes the service back onto the enabled list.
- `dumpsys deviceidle whitelist +com.ts.launcher` and
  `am set-standby-bucket com.ts.launcher ACTIVE` reduce the chances of the process being killed
  in the first place.

## Miscellaneous

- Startup ad: `settings put system boot_ad_switch 0` (see the debloat header).
- The TV sleeps its Wi-Fi in standby; network ADB closes on every power cycle and must be
  re-enabled under 账号与安全 -> ADB 调试.
- `input keyevent 3` from the shell does **not** go through accessibility key filtering, so it
  lands on the stock launcher even when the service is bound - test the Home key with the real
  remote.
- Sideloads: YouTube TV installs from base + `config.armeabi_v7a` splits via
  `pm install-create`; Netflix installs but the service rejects the uncertified device (both
  11.0.1 and 8.3.11); Pluto TV needs the full XAPK (base + `armeabi_v7a` + resource splits) - the
  base-only APK crashes inflating `androidx.media3.ui.PlayerView` with a
  `Resources$NotFoundException`. Pluto TV works; its region check passed on a non-US
  datacenter exit, so region availability is looser than the app's error screen suggests, but
  check your own exit node.

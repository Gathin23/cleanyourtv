# Xiaomi MiTV-ASTR0: HyperOS 3 screensaver ads, and Home via the allowlist trampoline

Tested on a Redmi Smart TV A Pro 55" 2025 Energy-saving Edition (MiTV-ASTR0, codename
`blueplanet`, `ro.miot.model=xiaomi.tv.rmh1`, Amlogic `AMLT963D4`/T5M, 3 GB RAM,
armeabi-v7a only), Android 14 (SDK 34), CN firmware `OS3.0.106.0.USTAATV` (HyperOS TV 3),
build `UD2A.240505.001.W1`, security patch 2025-06-05. GITV content partner
(`ro.mitv.product.customer=gitv`). Bootloader locked, verified boot green, SELinux
enforcing; everything below is shell-only, no root.

Stock launcher: `com.mitv.tvhome/.MainActivityUserMode` (PatchWall).

## The standby/screensaver ad package on HyperOS TV 3

`com.xiaomi.mitv.hyper.screensaver` — the HyperOS screensaver. It is a **plain Activity**
(`.MainActivity`), not a DreamService, so `settings put secure screensaver_enabled 0`
does not affect it; it fires on the idle timeout `settings put system screen_saver_timeout`
(480000 ms stock). It downloads and caches its own carousel under
`/storage/emulated/0/Android/data/com.xiaomi.mitv.hyper.screensaver/cache/images/` and
plays it with an ambient-music SDK — disabling the usual content sources
(`com.xiaomi.tv.gallery`, `com.xm.webcontent`, `com.duokan.videodaily`) does not stop it.

Evidence technique, useful on any set: while the ad is on screen run
`dumpsys activity activities | grep ResumedActivity` — it names the culprit directly —
plus `logcat -d | grep -iE 'screensaver'` for the cache paths. Then
`pm disable-user --user 0 com.xiaomi.mitv.hyper.screensaver`.

The classic MiTV ad packages `com.mitv.screensaver` and `com.xiaomi.mitv.advertise` do
not exist on this firmware; boot ad is already off here (`boot_ad=0`).

## Home: the allowlist still works on this HyperOS 3 build

- Ordinary (non-system) apps' HOME activities are dropped from resolution, same as
  documented for MiTV-MFFU0: with PatchWall disabled, an installed third-party launcher
  (`com.oversea.aslauncher`, Emotn UI) is never chosen — Home lands on
  `com.xiaomi.mitv.settings/.entry.FallbackHome` (black).
- `cmd package set-home-activity --user 0 <ordinary app>` prints `Success` and changes
  nothing. The preferred-home write itself is refused/overridden.
- **But the package-name allowlist from the AETP0 findings still gates this build.**
  A tiny sideloaded app with the allowlisted identity `com.ts.launcher` (a HOME activity
  whose `onResume` immediately forwards to the launcher the user actually wants) was
  accepted: with PatchWall disabled, `cmd package resolve-activity -c HOME` returns
  `com.ts.launcher/.HomeActivity`, and it survives reboots and power cycles. This was
  verified with a trampoline that forwards to `com.oversea.aslauncher`; the repo's own
  launcher built with `-PlauncherApplicationId=com.ts.launcher` should behave the same.
- Procedure: install the allowlist-identity home first, press Home once to confirm it
  answers, only then `pm disable-user --user 0 com.mitv.tvhome`. Recovery is
  `pm enable com.mitv.tvhome` plus a reboot. Keep PatchWall enabled if you skip this.
- MiTV-MFFU0 (OS3.0.115) first reported that no sideloaded launcher could own Home on
  HyperOS 3, but that was the standard package name. Its launcher built as
  `com.thunderstone.idun.dev`, an allowlisted name there, has been its Home since 2026-10-20, also
  after a full reboot (`examples/xiaomi-mitv-mffu0/debloat.txt`), so both HyperOS 3 builds honor the allowlist.
  The allowlist itself can differ between sets: check the package name on yours.

## Screensaver replacement is not possible on this set (no root)

The AOSP dream pipeline never renders: with `screen_off_timeout` set to 60 s the set
went straight to hard panel-off/suspend at the timeout (adbd frozen, `mCurrentDream`
null throughout), which the user experiences as power-off. `screensaver_enabled=1` plus
`screensaver_components` pointed at a clean DreamService had no trigger path; the
vendor AOD subsystem (`aod_id`, `mitv.settings.db.aod.settings.state`) is private and
off. Keep `screen_off_timeout` at 2147483647 (never); any finite value makes the TV
"shut down" after idle.

## Do not disable these

- `com.xiaomi.mitv.smartpanel` — the Menu-button quick panel (source switcher +
  wireless tiles). Looks like a Mi Home component but is the menu itself; disabling it
  kills the Menu key. (Disabled by mistake, re-enabled, listed here so nobody repeats it.)
- `com.xiaomi.mitv.tvplayer`, `com.xiaomi.mitv.settings`, `com.xiaomi.mitv.systemui`,
  `mitv.service`, the Sogou IME (`com.sohu.inputmethod.sogou.tv`, the only keyboard) —
  same as other CN Xiaomi lists.
- `com.xiaomi.mitv.tvmanager` is disabled in the example list; if XiaoAI's
  "open <app>" stops answering, re-enable it (see the MFFU0 note about
  `com.xiaomi.aicr.action.provider`).

## Other firmware notes

- ADB over TCP (`persist.adb.tcp.port=5555`) is closed by the firmware on every boot:
  re-enable in Settings → 账号与安全 (Account & Security) → ADB 调试. Any real
  suspend/power cycle also freezes adbd while the set is dark (ICMP answers, port 5555
  does not) — only a remote key press revives it.
- `set-home-activity` reporting `Success` while doing nothing is worth knowing when
  reading back test results on CN firmware: always verify with `resolve-activity`
  and an actual HOME keypress.
- Small helper apps that hold icon entry points for hidden system apps (app store,
  file manager, WPS, settings) work fine as LEANBACK_LAUNCHER activities; on Android 14
  a boot autostart helper must not use `setExactAndAllowWhileIdle` without the
  `SCHEDULE_EXACT_ALARM` grant (SecurityException is easy to swallow silently), and
  needs `appops set <pkg> SYSTEM_ALERT_WINDOW allow` to start activities from
  background. A hand-built manifest without `<uses-sdk>` fails to install with
  `INSTALL_FAILED_DEPRECATED_SDK_VERSION`.

## Firmware `OS3.0.119.0.USTAATV`: the periodic cleaner and boot broadcasts

Same set, after an OTA from `.106` to `OS3.0.119.0.USTAATV`. Everything above still holds.
Two further behaviours are worth documenting.

### `PeriodicCleaner` re-enables disabled packages and kills background apps

`pm disable-user --user 0` on `com.xiaomi.wakeupservice` and `com.xiaomi.voicecontrol` does
not survive a reboot on this build, and background services are killed at idle.
The cause is the firmware's periodic cleaner:

    adb shell cmd periodic enable false      # -> periodic cleaner enabled: false

That setting survives reboots. It also took `com.xiaomi.wakeupservice` from ~50% CPU in
`top` down to ~12%; the package still starts and cannot be disabled without root.

### Third-party `BOOT_COMPLETED` is not delivered on this build

A receiver registered for `BOOT_COMPLETED` and `LOCKED_BOOT_COMPLETED` is never invoked,
and `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` is refused. Because
the firmware also clears `adb_enabled` on every boot, ADB cannot be restored from a
broadcast receiver — which is why an approach that works elsewhere does nothing here. ADB staying off
after a reboot is what this repo wants anyway: switch network debugging on by hand when you need it.

### Debloat list note

Leave `com.xiaomi.mitv.shop` enabled if the set's own app store is wanted: the list in
`examples/xiaomi-mitv-astr0/debloat.txt` disables it, and without a store the only ways to
install apps are `adb install` and a USB stick.

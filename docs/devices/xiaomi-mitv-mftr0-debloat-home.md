# Xiaomi MiTV-MFTR0: debloat and third-party Home on HyperOS TV 2 (Android 11)

Companion to [xiaomi-mitv-mftr0-freeguy.txt](xiaomi-mitv-mftr0-freeguy.txt) (input
routes, Xiaomi settings intents, app discovery). This note covers the 2026-10-22
debloat/Home session on a MiTV-MFTR0 (`freeguy`, MediaTek mt9653), **China firmware
`OS2.0.23.0.RFTMATV` (HyperOS TV 2), Android 11 (SDK 30)** — the second CN set from
the same sessions as [MiTV-ASTR0](xiaomi-mitv-astr0-blueplanet.md) (HyperOS TV 3,
Android 14), and the starting point was the ASTR0 findings. Everything below was
checked per-package on this set first.

## Same ad screensaver, one generation earlier

`com.xiaomi.mitv.hyper.screensaver` exists on OS2 with identical settings
(`screen_saver_timeout=480000`, time/weather cards open, AOSP `screensaver_enabled=0`
unrelated). It was disabled without waiting for a live capture: same package, same
settings shape, same firmware family as the ASTR0 evidence. The classic
`com.mitv.screensaver` / `com.xiaomi.mitv.advertise` do not exist here either;
`boot_ad=0` out of the box.

## Home: the allowlist trampoline works on OS2 too

Same sequence as ASTR0, same outcome: with the allowlist-identity home
(`com.ts.launcher`) installed and PatchWall (`com.mitv.tvhome`) disabled, HOME
resolves to the trampoline and lands on the installed third-party launcher
(`com.oversea.aslauncher`, Emotn UI), across reboots — the set boots straight into
it. `cmd package set-home-activity` on an ordinary app is equally cosmetic here
(reports `Success`, changes nothing). So on CN Xiaomi sets the pattern so far:
OS2/Android 11 and OS3/Android 14 both honor the package-name allowlist, MiTV-MFFU0
(OS3.0.115) included. As on ASTRP0-style lists: install
the trampoline first, disable PatchWall second; `pm enable com.mitv.tvhome` plus a
reboot undoes it.

## What differs from the ASTR0 set

- No WPS preload (`cn.wps.moffice_i18n_TV` absent), no `com.mitv.overseaservice`,
  no AOSP adservices/ondevicepersonalization/federatedcompute module packages
  (Android 11 predates them).
- `com.emotn.browser` (Emotn's browser, distinct from their launcher) ships as a
  preload here and was disabled.
- Keep-list verified again: `com.xiaomi.mitv.smartpanel` is the Menu-button panel;
  `com.sohu.inputmethod.sogou.tv` is the only IME; `com.xiaomi.mitv.tvplayer`,
  `com.xiaomi.mitv.settings`, `com.xiaomi.mitv.systemui`, `mitv.service` untouched.

## Two operational notes

- First ADB-over-TCP connect needs the on-TV `允许USB调试` RSA confirmation (tick
  "always allow"); if the dialog never appears, `adb kill-server` + reconnect
  re-triggers the handshake. ADB closes again on every reboot.
- **Classify preloads by `pm path`, not by memory**: an owner-installed Moonlight
  (`com.limelight`) sat in an early draft of the list because a same-named package
  is a factory preload on other sets; `pm path` showed `/data/app/...` (user
  install) and it was dropped from the list and re-enabled. `pm disable-user`
  hides the icon, which reads as "deleted" to the owner; `pm enable` restores it
  with data intact.

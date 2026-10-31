# "Smart TV" (Toptech/Zeasn TitanOS 2 on HiSilicon Hi3751V352E, Android TV 12): a no-friction CleanYourTV set, with the quirks that are easy to miss

Owner-observed note, measured on 2026-10-31 over Wi-Fi ADB with the repo's own tools and a physical
remote for the key checks. Everything below needed no root and survived a reboot. The About page
names the set only "Smart TV"; the bezel carries a Hisense circle emblem, and the firmware is
Toptech/Zeasn (TitanOS 2) on a HiSilicon Hi3751V352E board — the emblems and the firmware tell two
different brand stories, so owners searching for either will land here.

## Device

| Field | Observed value |
|---|---|
| Android model / device / manufacturer | `Smart TV` / `Smart TV` / `Smart TV` (brand string only) |
| Android / API | 12 / 31 |
| Build | `Hi3751V352E_DMO` |
| Security patch | 2021-10-05 |
| Kernel | `5.10.66_s5` (#1196, built 2024-02-27) |
| SoC / RAM | HiSilicon Hi3751V352E, Cortex-A53 quad core, Mali 450MP2, 976 MB DDR |
| Connectivity | Wi-Fi only; the USB ports carried nothing |
| ADB | `ro.adb.secure` empty, `service.adb.tcp.port = 5555` after the Developer options toggle |
| Stock launcher | `com.zeasn.toptech.titanos2.launcher` (TitanOS 2) |

## TL;DR for the next person on this firmware

1. **The whole flow works.** Developer options (About → Build ×7), network ADB on 5555,
   `pm disable-user` accepted, a sideload, module cards and the game_day panel all verified; the
   reference launcher took HOME with `cmd package set-home-activity` and kept it across a reboot.
   No on-TV ADB confirmation prompt ever appeared (`ro.adb.secure` is empty).
2. **The stock launcher ships itself as an app tile labeled "Smart Tv."** Do not disable it —
   hide it. `hidden_apps` with `com.zeasn.toptech.titanos2.launcher` removes the tile while the
   component stays enabled as the safety fallback.
3. **A Netflix build without a TV launcher entry.** This set's `com.netflix.mediaclient` has no
   `LEANBACK_LAUNCHER` category (`cmd package resolve-activity` answers "No activity found"; only
   the phone-style `MAIN/LAUNCHER` activity exists), so the launcher lists no tile for it. The fix
   is `app_library` + `library_apps` seeded with the package — and such a library tile also accepts
   a `dock_apps` pin.

## What was disabled and how it went

Same-session changes, all verified with the repo tools; **not a several-day trial, so no
`examples/` list yet** (per the workflow, one may follow after days of use):

- `my.gov.rtm.tv` (RTM TV, the Malaysian public broadcaster app) — disabled, stayed disabled.
- `com.tiktok.tv` (preloaded TikTok), `com.google.android.katniss` (Google Assistant, a `/data`
  install on this firmware, not a system app) and `com.hisilicon.miracast` (Miracast) were
  disabled briefly, then **uninstalled at the owner's request** — `pm uninstall --user 0` was
  accepted. Undo, which this note documents rather than recommends: `cmd package
  install-existing com.google.android.katniss` / `com.hisilicon.miracast`; TikTok reinstalls
  from its APK.

## Quirks worth knowing

1. Keep, per the PROMPT rules and confirmed here: `com.toptech.keyboard` (the on-screen keyboard),
   `com.toptech.tvmenu` (the source/quick menu), `com.toptech.tv` (the TV service),
   `com.toptech.systemupdate` (firmware updater). Never start `com.toptech.hkcfactorytest` or
   `com.toptech.factorytools` (factory test tools).
2. The bottom-row app store is `cn.zeasn.overseas.tv.toptech.rtk2853.titan2`, labeled "App Store";
   its tile art is a purple shopping bag and should not be confused with Google Play.
3. DRM streaming apps screenshot as near-empty black frames — verify launches with `dumpsys
   activity activities | grep mResumedActivity`, not from the capture.
4. `pm list packages -f` is the honest inventory here: vendor preload lives mixed in `/data/app`
   (TikTok, the Assistant) and `/system/app` (Miracast, the Hi* HiSilicon suite), which decides
   what a `pm uninstall --user 0` actually frees.

## Not evaluated

No several-day debloat list; no OTA-update interaction tested (the updater was kept on and has
not run yet); the CompanionCast-free casting path is untested. This note omits the unit's serial
number, network address and Wi-Fi name.

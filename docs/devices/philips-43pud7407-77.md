# Philips 43PUD7407/77: session notes for the PH0M_EA_T32 family

**Status: 2026-10-29, session-limited observations; no several-day validated debloat list.** These notes cover one Argentine unit. They do not recommend applying another unit's list wholesale or validate current launcher main.

## Identity and existing family

| Field | Observed value |
|---|---|
| Retail model | Philips 43PUD7407/77 |
| Generic Android model | 2020/2021 UHD Android TV |
| Android / SDK | 11 / 30 |
| Hardware / device | m7332 / PH0M_EA_T32 |
| Product | PH0M_LT_T32 |
| Philips firmware | TPM215L_R.105.000.097.000 |
| Build / incremental | RTM2.210929.098 / 200321 |
| Security patch | 2024-11-01 |
| ABI / low-RAM profile | armeabi-v7a, armeabi / true |
| Kernel-visible RAM | 1379860 KiB; not a physical-RAM specification |

The product, device, firmware, build and incremental match the first Chilean unit in
[philips-50pud7406](../../examples/philips-50pud7406/debloat.txt). The other European variants in that file have different identifiers. This unit belongs alongside that family rather than needing a duplicate example folder for the same build. Its deliberately narrower package choices and missing long-term tests do **not** validate the existing list on this retail model. This note follows the [notes-only workflow](../workflows/tv-list.md); no active list or `tv.json` is added.

## Changes actually observed

Each of the following moved from default state 0 to disabled-user state 3 for user 0, with APK and data retained. They were owner-approved individually and checked during the sessions, not lived with for several days:

| Package | Observation |
|---|---|
| `org.droidtv.tv.frameworklogger` | Its process disappeared. The owner felt a small menu improvement but still reported lag; three physical-remote rounds before and after did not show a consistent rendering improvement. |
| `org.droidtv.nettv.market` | The unused Philips App Gallery process and service disappeared. Home, remote, Settings and YouTube were reported working as before. No performance benchmark was run for this change. |
| `org.droidtv.gel` | GatheringService and observed pending GEL alarms were absent afterwards. Android restarted the shared process; DAC CollectionService returned with its protected clients. The same brief functional check passed; no RAM saving or speed-up was quantified. |

No package was re-enabled to repair a reported regression. Prepared package rollbacks were not executed. Returning a HOME assignment to stock during the initial launcher trial was an assignment rollback while stock was already enabled, not a package reactivation.

### DAC was kept

`org.droidtv.candeebug` remained enabled. CollectionService had active bindings from `org.droidtv`, `org.droidtv.icp` and `com.android.tv.settings.otapp`. GEL and DAC shared a process/UID; its entire PSS cannot be counted as memory saved by disabling GEL. A telemetry-sounding name does not demonstrate independence. Core TV/input/HDMI/CEC, remote, OTA, Play services, Cast and voice components were kept. Fitness Buddy was unused but not disabled; no resident benefit justified another change. Manufacturer registration, recommendations, customization and Backdrop were also kept.

## Stock HOME previews and rendering evidence

The stock Home's **Enable video previews** option was visually confirmed on, then switched off with the owner's approval; **Enable audio previews** stayed on. The owner preferred keeping video previews off. Across three identical ADB navigation rounds per condition, the median of round p95 frame durations changed from 31.18 to 27.07 ms, while aggregate jank changed from 79/978 (8.08%) to 87/979 (8.89%). This is mixed evidence, not proof of a general stutter fix. No counter or log clearing was used. The preference was last visually checked in this stage, not re-read after the later stock Home handover.

A separate real-lag episode, after the logger change and before the previews trial, produced 236 janky frames out of 958 new Home frames, 956 valid frame rows, a 556.64 ms maximum and a 476 ms input-acknowledgement wait. Direct reclaim, swap and concurrent load were observed, with instrumentation overhead; no single cause was established. YouTube stutter was in thumbnails/menus while the app was already open. Its gfxinfo exposed no useful frame samples, so no zero-jank result or video-playback diagnosis is inferred.

## Optional HOME trial, not general debloat

The owner separately chose a source-built `com.gathin.tvhome` based on commit `985bbd14d0b01517988724668e310211d72d86fc`, with local manifest restrictions and visual preferences. With stock `com.google.android.tvlauncher` enabled, `set-home-activity` reported success but HOME still resolved to stock (observed candidate priorities 2 and 0). The original assignment was restored. A later separately approved handover left stock disabled-user for user 0, preserving its APK/data, and HOME resolved to `com.gathin.tvhome/.MainActivity`. This records what happened, not instructions to bypass the [guarded official handover](../../README.md) on another unit.

The candidate was running without an active service or special permission grants; the local manifest requested only network-state and Wi-Fi-state access, without INTERNET. This is not validation of the current upstream installer, its optional helpers or current main. The owner preferred the reference appearance with less bounce and quicker focus; these unbenchmarked local choices are not proposed as core defaults here. Global animation scales were left unchanged.

The owner confirmed physical Home return from YouTube, Settings and opening the Inputs menu, and described the chosen appearance as very fluid. Opening Inputs does not establish working HDMI signal or CEC. There is no controlled stock-versus-final-launcher performance comparison or specific feedback after the final timing adjustment.

## Remaining validation

- No several-day stability evidence for the final state; the changes span two dates, with the final launcher revision checked only in the last session.
- No reboot, cold-power-cycle, firmware-update, channel/tuner, actual HDMI/CEC, Cast, voice or complete remote-button regression test.
- No demonstrated fix for YouTube's own menus. The real capture observed IR, not a separate Bluetooth test.
- Thermal telemetry was unavailable on this firmware, so throttling was not ruled out. Final free storage and sustained CPU/RAM savings were not measured.
- Short log excerpts without fatal errors do not prove long-term stability. Package and HOME recovery were prepared but not exercised as end-to-end recovery tests.

Before a future list is proposed, record the exact firmware/package states, use the [official tools and recovery workflow](../../tools/), retain their guards, validate the owner's required functions and use the final state for several days. Review what restore writes, including HOME and animation settings. This report does not attest that debugging was closed or that a reboot check was completed.

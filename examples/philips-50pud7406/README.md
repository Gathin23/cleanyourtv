# Philips PH0M_EA_T32: additional unit notes

## Argentina — Philips 43PUD7407/77

**Status: observations from 2026-10-28–29 only; no several-day or reboot validation.**
Android TV 11 / API 30, device PH0M_EA_T32, product PH0M_LT_T32, firmware
TPM215L_R.105.000.097.000, build RTM2.210929.098 / incremental 200321: the same
build as the first Chilean unit in [debloat.txt](debloat.txt).

This owner used a narrower selection, **not the whole list beside this file**:
`org.droidtv.tv.frameworklogger`, `org.droidtv.nettv.market` (unused App Gallery)
and `org.droidtv.gel` moved from default state 0 to disabled-user state 3 for user 0,
with APK/data retained. No package was re-enabled to repair a regression.

**DAC (`org.droidtv.candeebug`) stayed enabled:** CollectionService had active
bindings from `org.droidtv`, `org.droidtv.icp` and `com.android.tv.settings.otapp`.
Disabling GEL restarted their shared process; DAC returned with those clients.
Its entire PSS must not be counted as memory saved. Recommendations, Backdrop,
Cast, voice, OTA and TV/input components were kept. The list's additional choices
were not validated on this unit.

Stock Home video previews changed ON to OFF; audio stayed ON. The owner preferred
OFF, but three ADB navigation rounds per condition gave mixed results: median
round p95 31.18 to 27.07 ms, aggregate jank 8.08% to 8.89%. Logger rounds likewise
did not establish a consistent rendering improvement.

A separately approved, locally adapted launcher trial based on
`985bbd14d0b01517988724668e310211d72d86fc` ended with `com.gathin.tvhome` as Home
and stock `com.google.android.tvlauncher` disabled-user, APK/data retained.
Initially, assigning the candidate while stock was enabled reported success but
still resolved to stock; that assignment was restored before the later handover.
This is an observation, not validation of current main or a handover recipe.

The owner confirmed Home/remote, Settings and YouTube after the package changes,
and physical Home return from YouTube and opening Inputs after the handover.
The chosen launcher appearance felt very fluid; there was no controlled final
benchmark. Opening Inputs does not test HDMI signal/CEC. Several-day use, reboot,
cold boot, actual HDMI/CEC, channels, OTA, Cast and voice remain untested.
YouTube's own menu stutter was not demonstrated fixed. These session notes do not
make the adjacent list a validated default for this model.

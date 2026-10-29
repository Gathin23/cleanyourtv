# Acer AR55AR2851UDFL 55" (Android TV 14): early second-unit notes

This India-market Acer reports SCBC device `R4`, Realtek `rtd2851m`, Android TV 14 (SDK 34),
build `UKR1.260308` / incremental `3272`. It is close to the
[AR55AR2851UDPRO example](../../examples/acer-ar55ar2851udpro/README.md), but the owner's
package choices and Home handover differed. The changes were made on 2026-10-23, and the
owner reports two days of testing as of 2026-10-24. This is not yet a several-day debloat
list. The owner also reports one full power-off and restart with the clean Home and apps working.

## Packages disabled on this unit

The owner approved and disabled the following 11 packages with `pm disable-user --user 0`.
None were uninstalled. This is a record of this unit, not a list to apply to every R4 TV:

```text
com.apps.function.gdpr
com.apps.eshow
com.android.tv.feedbackconsent
com.google.android.feedback
com.google.android.partnersetup
com.jio.media.stb.ondemand
com.jio.jiogamestv
com.sonyliv
com.google.android.tvrecommendations
com.google.android.tvlauncher
com.offshore.pikachu
```

YouTube, YouTube Music, FastCast, SCast, Play Movies, TalkBack, Assistant, Chromecast, the OTA
updater, HDMI/tuner/input and remote services were kept. In particular,
`com.google.android.tungsten.setupwraith` owns the saved Wi-Fi network and was kept.
The Live TV package is `com.apps.livetv`. The owner's launcher dock has Netflix, YouTube,
Prime Video, and JioHotstar.

## Home and functional checks

The reference launcher `com.gathin.tvhome` remained the focused Home after
`com.google.android.tvlauncher` was disabled and after two **warm ADB reboots**. The owner
checked the physical Home button, the four dock apps, HDMI, voice search, and casting.
The owner subsequently reported a full power-off and restart with the clean Home screen and
apps still working. The disabled-package state was not independently checked after that boot.
The UDPRO example kept stock `tvlauncher` enabled and used `HomeKeyService`; the two units'
Home handovers differed during these observations.

Debugging was turned off, its authorizations revoked, and the TV rebooted. The ADB port was
verified closed. The owner reports two days of testing and working Home and apps after a full
power-off. Longer follow-up and confirmation that the 11 packages remain disabled after that
boot are pending before publishing an example debloat list for AR55AR2851UDFL.

# XGIMI XR13A (kasol), Google TV 14

Owner report read from the projector by the Clean Your TV phone app on 2026-10-24 (app 0.3.4). One unit. The app switched off two packages and left the Google TV home screen in place. The list lived with on this same unit from 2026-10-26 through 2026-10-30 is [examples/xgimi-horizon-20/debloat.txt](../../examples/xgimi-horizon-20/debloat.txt).

The other XGIMI lists are different projectors. [MoGo 3 Pro](../../examples/xgimi-mogo-3-pro/debloat.txt) is also Google TV 14, but its device is `altai` and its build is `UKNV.260707.001`. [Horizon S Pro](../../examples/xgimi-horizon-s-pro/debloat.txt) and [MoGo 2](../../examples/xgimi-mogo-2/debloat.txt) are Android 11. Do not copy their package lines onto this unit.

| Field | Observed value |
|---|---|
| Retail model | XGIMI Horizon 20 (model number XR13A) |
| Manufacturer / brand | `XGIMI` |
| Device / product | `kasol` / `athenamin` |
| Android / API | Android 14 / 34 |
| Build | `UKN6.260116.134`, incremental `15692712`, `user/release-keys` |
| Fingerprint | `XGIMI/athenamin/kasol:14/UKN6.260116.134/15692712:user/release-keys` |
| Google Play services | present |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` (priority 2) |
| Also claims Home | `com.google.android.tungsten.setupwraith/.RecoveryActivity` (priority 1), `com.android.tv.settings/.system.FallbackHome` (priority -1000) |

The phone app matched the XGIMI brand set, not an exact profile. Home handover was blocked because the setup wizard owns the saved Wi-Fi (`WIZARD_WIFI`). Switching that wizard off would drop the network, and without a tested list for this model the app leaves the wizard on. The new home screen was not installed in that pass. A later sitting on the same build did install it; see [Launcher handover](#launcher-handover-2026-10-26).

## What changed

Switched off for the main user, and still off when the report was read:

```text
com.google.android.feedback
com.xgimi.bugreportsender
```

Already disabled from the factory, left as they were:

```text
com.mediatek.dtv.tvinput.atsctuner
com.mediatek.dtv.tvinput.isdbtuner
com.mediatek.tv.oneworld.tvcenter
```

`com.mediatek.dtv.tvinput.dvbtuner` and `com.mediatek.tis` were enabled. So was `com.netflix.ninja` and the Play Store.

## Still on, and not tested off

These XGIMI packages were enabled. On the other XGIMI projectors the hardware, remote and picture ones stay on, and `com.xgimi.xrmservice` re-enables the stock launcher from the remote. Nothing here shows that the same cut is safe on `kasol`. Do not start `com.xgimi.factoryservice` or `com.xgimi.minitvfactory`: on some boards opening a factory package sets a flag the shell cannot undo.

```text
com.xgimi.atvwifiverify
com.xgimi.autoinstall
com.xgimi.autokst
com.xgimi.factoryservice
com.xgimi.gimiplayer
com.xgimi.mateservice
com.xgimi.minitvfactory
com.xgimi.misckey
com.xgimi.ota.accessories
com.xgimi.overseas.xgimiatvbootwizard
com.xgimi.rgbdupgrade
com.xgimi.smartaccessories
com.xgimi.tof
com.xgimi.uiconfiguration
com.xgimi.windowsystem
com.xgimi.xgimiiotserver
com.xgimi.xgimiservice
com.xgimi.xrmservice
```

Also still enabled, and kept by the app's brand set: `com.google.android.apps.tv.launcherx`, `com.google.android.tungsten.setupwraith`, `com.google.android.katniss`, `com.google.android.apps.mediashell`, `com.google.android.apps.tv.dreamx`, `com.google.android.youtube.tv`, `com.google.android.youtube.tvmusic`, `com.google.android.play.games`, `com.google.android.marvin.talkback`, `com.google.android.tv.inputplayer`, `com.google.android.tv.remote.service`, `com.mediatek.tv.retaildemo`, `com.mediatek.tv.systemupdater`.

The report listed 165 system apps and 19 other apps by count only. Those 19 are the owner's own installs and are not candidates for a list.

## Launcher handover, 2026-10-26

Same unit and the same build (`UKN6.260116.134`, incremental `15692712`). One sitting, not a few days, so this is still not a list to apply and there is no `examples/` folder.

`pm install` of the reference launcher succeeded (SDK 34, above `minSdk` 26). The first `launcher/build-and-install.ps1` run started `com.gathin.tvhome`, disabled `com.google.android.apps.tv.launcherx`, then found Home on `com.google.android.tungsten.setupwraith/.RecoveryActivity` and re-enabled `launcherx`. That rollback left the projector on the stock home.

The saved Wi-Fi was then forgotten and joined again in Settings. `dumpsys wifi` showed `cname=android.uid.system:1000` and no `setupwraith` owner. `pm disable-user --user 0 com.google.android.tungsten.setupwraith` after that check left ADB up (`--probe` still answered on port 5555). A second install then left Home on `com.gathin.tvhome/.MainActivity`, confirmed with `mFocusedApp`.

Later, after a theme push, `launcherx` was enabled again and owned Home. `pm disable-user` and `cmd package set-home-activity` put `com.gathin.tvhome` back. `com.xgimi.xrmservice` was left enabled: on other XGIMI boards it puts the stock launcher back, and it was not tested off here. A reboot was not part of this sitting, so an `examples/` list still has to check that Home and ADB survive one.

The owner kept the voice assistant (`com.google.android.katniss`), Chromecast (`com.google.android.apps.mediashell`) and `com.mediatek.tv.systemupdater`. `com.android.tv.settings` stayed enabled. The XGIMI hardware, picture, remote and factory packages in the list above were not touched, and the factory packages were not started.

Also switched off in the 2026-10-26 sitting, on top of the two packages from 2026-10-24. They were still off on the morning of 2026-10-29, except `launcherx`, which was turned back on. The streaming and media apps in this block are this owner's choice, not lines to copy:

```text
com.mediatek.tv.retaildemo
com.android.tv.feedbackconsent
com.google.android.partnersetup
com.android.adservices.api
com.android.providers.calendar
com.google.android.syncadapters.calendar
com.android.providers.contacts
com.android.providers.userdictionary
com.apple.atve.androidtv.appletv
fr.francetv.pluzz
tv.arte.plus7
com.spotify.tv.android
fr.m6.m6replay
com.google.android.youtube.tvmusic
com.google.android.play.games
com.esaba.downloader
info.kodono.pop_files
info.kodono.popvideoplayer
org.videolan.vlc
com.mediatek.wwtv.mediaplayer
com.tcl.browser
com.canal.android.canal
fr.tf1.mytf1
com.google.android.tungsten.setupwraith
com.google.android.apps.tv.launcherx
```

`com.tcl.browser` is the BrowseHere tile (it registers the `browsehere` scheme). `themes/scenes` shows its illustrated wallpapers and leaves the dock from `config.xml`. `themes/nature-calm` replaces the dock and sets six columns. `cinematic_home` was tried and reset; it is the focused app's artwork, not a wallpaper.

## Playback, 2026-10-28

The morning after, Home was still `com.gathin.tvhome` when the set was turned on. Two warnings were on that screen. Netflix and Disney+ both opened, and neither would start an episode. Turning Accueil TV back on (`com.google.android.apps.tv.launcherx`, the French settings name) together with its notification service made both play. Leave `launcherx` enabled on this projector. Other Google TV devices report the same failure as Netflix error `tvq-pb-101` when that package is disabled; this unit was not checked for that code. On a JVC set the notification panel's handler lives in `launcherx` too: with the package off, `TvNotificationPanel` does not open.

`com.google.android.tvrecommendations` is not installed on this projector. The notification service turned back on with Accueil TV is part of `launcherx`.

On the morning of 2026-10-29 the owner reported a normal start. A read-only check the same morning found every package in the block above still disabled except `launcherx`, `setupwraith` still disabled, and `launcherx` still enabled and still Home. Netflix was in front (`com.netflix.ninja/.MainActivity`). Nothing was changed. The morning of 2026-10-30 was the same: a normal start, the same packages, `launcherx` still Home, Netflix in front again. The owner had confirmed the remote the day before.

Keeping our launcher in front, with `launcherx` left enabled, needs `stock_home_redirect` set to `com.google.android.apps.tv.launcherx` and `HomeKeyService` on. On 2026-10-30 the on-device `config.json` had both `stock_home_redirect` and `key_filter` false, and `enabled_accessibility_services` listed `com.gathin.tvhome/.HomeKeyService`. `cmd package resolve-activity` for HOME still answered `launcherx` (priority 2). The owner reports a reboot comes back to the clean launcher. ADB answered on that check. Netflix was in front, so the reboot itself was not watched from here. On other XGIMI boards `key_filter` false is what keeps a held volume key repeating; that hold was not rechecked here.

## The list

[examples/xgimi-horizon-20/debloat.txt](../../examples/xgimi-horizon-20/debloat.txt) is the packages above that stayed off from 2026-10-26 through 2026-10-30, except `launcherx`, which was turned back on. Wi-Fi held with `setupwraith` off. The owner confirmed the remote on 2026-10-29, and on 2026-10-30 HDMI, then focus and keystone: moving the projector reframed the picture, and putting it back restored the frame. The zoom setting reset on that move. The owner reports the same zoom reset already happened before this cleanup. The same day, a reboot came back to the clean launcher, with `launcherx` left enabled and the redirect above in place. Forget the saved Wi-Fi and join it again before any try at switching `com.google.android.tungsten.setupwraith` off, and confirm afterwards that the network is no longer owned by the wizard.

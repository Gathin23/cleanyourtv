# XGIMI XR13A (kasol), Google TV 14

Owner report read from the projector by the Clean Your TV phone app on 2026-10-24 (app 0.3.4). One unit. The app switched off two packages and left the Google TV home screen in place. This is not a several-day tested debloat list, and it is not a list to apply.

The other XGIMI lists are different projectors. [MoGo 3 Pro](../../examples/xgimi-mogo-3-pro/debloat.txt) is also Google TV 14, but its device is `altai` and its build is `UKNV.260707.001`. [Horizon S Pro](../../examples/xgimi-horizon-s-pro/debloat.txt) and [MoGo 2](../../examples/xgimi-mogo-2/debloat.txt) are Android 11. Do not copy their package lines onto this unit.

| Field | Observed value |
|---|---|
| Retail model | XGIMI XR13A |
| Manufacturer / brand | `XGIMI` |
| Device / product | `kasol` / `athenamin` |
| Android / API | Android 14 / 34 |
| Build | `UKN6.260116.134`, incremental `15692712`, `user/release-keys` |
| Fingerprint | `XGIMI/athenamin/kasol:14/UKN6.260116.134/15692712:user/release-keys` |
| Google Play services | present |
| Stock Home | `com.google.android.apps.tv.launcherx/.home.HomeActivity` (priority 2) |
| Also claims Home | `com.google.android.tungsten.setupwraith/.RecoveryActivity` (priority 1), `com.android.tv.settings/.system.FallbackHome` (priority -1000) |

The phone app matched the XGIMI brand set, not an exact profile. Home handover was blocked because the setup wizard owns the saved Wi-Fi (`WIZARD_WIFI`). Switching that wizard off would drop the network, and without a tested list for this model the app leaves the wizard on. The new home screen was not installed.

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

## What a list would still need

A folder under `examples/` needs packages actually disabled on this projector and lived with for a few days: Wi-Fi, the remote, HDMI, focus and keystone, then a reboot. Forget the saved Wi-Fi and join it again before any try at switching `com.google.android.tungsten.setupwraith` off, and confirm afterwards that the network is no longer owned by the wizard.

# Philips 55PUS6432/12 (QM163E, MediaTek MT5890, Android TV 8.0)

Observed on one TV, in France, in a single session. This is a device report, **not a debloat list**: the packages
below were disabled the same evening, so they have not been lived with for several days.

This is the 2016 Philips platform, a year older than the `TPM171E` sets in
[`philips-55pus7503`](../../examples/philips-55pus7503/debloat.txt) and
[`philips-65pus8102`](../../examples/philips-65pus8102/debloat.txt). It was updated to Android 8.0, so the reference
launcher (`minSdk 26`) runs on it. Package names are shared with those lists, but the Live TV app is not.

| Field | Observed value |
|---|---|
| Retail model | Philips `55PUS6432/12` (`persist.sys.set_type`) |
| Product / model | `QM16XE_U` / `QM163E`; `ro.tpvision.product.hwboard` is `2K16EU4_10` |
| SoC / RAM | MediaTek MT5890 (`ro.board.platform` `mt5890`), `armeabi-v7a`; 1.3 GB (`MemTotal: 1375036 kB`), about 50 MB free with the launcher running |
| Android / SDK | `8.0.0` / `26` |
| Firmware | `QM163E.0.26.70.1`, built 2020-05-29, security patch 2019-09-01 |
| Fingerprint | `PHILIPS/QM16XE_U/QM16XE_U:8.0.0/OPR5.170623.014/026701:user/release-keys` |
| Display | 1920x1080 UI at 320 dpi |
| Storage | `/data` 3.6 GB, 84% used before any change; `/system` 96% used |
| Stock Home | `com.google.android.tvlauncher` |
| Live TV | `org.droidtv.zapster/.playtv.activity.PlayTvActivity` (there is no `org.droidtv.playtv`) |

## Connecting

USB debugging alone opened network ADB on 5555, with the usual "Allow USB debugging?" prompt (`ro.adb.secure=1`).

## What was disabled

The first sections of the 55PUS7503 list, plus the Philips sticker and Twitter sharing apps from its commented
options. Every package was present on this firmware; Inscape ACR and Alexa are not installed here.

```
com.google.android.feedback
com.google.android.tvtutorials
com.google.android.tvrecommendations
com.google.android.partnersetup
com.google.android.videos
org.droidtv.demome
org.droidtv.nettvrecommender
org.droidtv.usagelogger
org.droidtv.candeebug
org.droidtv.gel
org.droidtv.tv.frameworklogger
org.droidtv.sticker
org.droidtv.twittershare
com.google.android.tvlauncher
```

`com.google.android.music` and `com.google.android.play.games` were already disabled. Kept on purpose: Google
Assistant (`com.google.android.katniss`), Chromecast, the NetTV store and its web apps, `org.droidtv.nettvbrowser`
(HbbTV), `org.droidtv.eum` (hidden from the launcher instead), `org.droidtv.smarttvhome`, and every tuner, HDMI,
settings and factory package.

Disabling frees no space. Six of these packages carried Play updates in `/data/app` (`tvlauncher`, `videos`,
`play.games`, `tvrecommendations`, `candeebug`, `gel`); removing those updates took free space on `/data` from 593 MB
to 746 MB. On this firmware that **re-enables** each package in its factory version, the stock launcher included,
so each one has to be disabled again straight after, `com.google.android.tvlauncher` first.

## Home

The reference launcher took Home with `cmd package set-home-activity` once `com.google.android.tvlauncher` was
disabled, and kept it after a reboot. `HomeKeyService` was bound after the reboot. The remote's Home button arrives as
`keyCode=3, scanCode=102` (the firmware logs it as `TPVKeys`).

In a `config.json`, `live_tv_package` is `org.droidtv.zapster`.

Pressing Home from Live TV or YouTube brought the launcher back in about 1.3 s. In the first two minutes after a cold
boot, the TV is busy starting its own services: the launcher hit an ANR ("no window has focus") once in that window,
was restarted, and a Home press then took about 6.5 s. After that it answered normally. `loadavg` reads 70 to 80 at
that point and stays there afterwards with the CPUs mostly idle, so it is not a useful figure on this set.

## Remote

With these packages disabled and the reference launcher as Home, the owner checked the remote's Home, TV and Source
buttons: TV opens Live TV and Source opens the Philips sources menu.

## Not checked

The microphone button, playback on each HDMI input and waking from standby were not tested in this session.

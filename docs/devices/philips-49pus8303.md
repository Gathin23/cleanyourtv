# Philips 49PUS8303/12: launcher handover, not a debloat list

Checked on the owner's set in Europe. This is **not a tested debloat list**. The only package disabled was the stock launcher, and only after the replacement launcher was Home. No other package was disabled, and the set was not lived with as a several-day cleanup. Do not copy the 55PUS7503 or 65PUS8102 lists onto this sales model.

## Identify the exact firmware

| Field | Observed value |
|---|---|
| Retail model | Philips `49PUS8303/12`, Europe |
| Board | `TPM17XE_96` |
| Product | `PH7M_EU_5596` |
| SoC | MediaTek MT5891 |
| Android / SDK | Android TV 8.0.0 / 26 |
| Build | `OC` incremental `4718` |
| Android security patch | 2021-01-05 |
| Fingerprint | `Philips/PH7M_EU_5596/PH7M_EU_5596:8.0.0/OC/4718:user/release-keys` |

The retail model comes from the set's own model name, not from `ro.product.model`. The fingerprint family matches [`philips-55pus7503`](../../examples/philips-55pus7503/debloat.txt) and [`philips-65pus8102`](../../examples/philips-65pus8102/debloat.txt): TPM171E, `PH7M_EU_5596`, Android TV 8.0, security patch 2021-01-05, MediaTek MT5891. This is a different sales model, so those lists were not applied.

## Home

The original Home activity was `com.google.android.tvlauncher/.MainActivity`. The reference launcher `com.gathin.tvhome` was built from checkout `575d73f0` (before Launcher 3.0, versionCode 9) with household settings that are not in upstream and are not part of this note. After that launcher was installed and the stock launcher was disabled for user 0, Home resolved to `com.gathin.tvhome/.MainActivity`. The owner confirmed the home screen.

`pm list packages -d` showed only `com.google.android.tvlauncher`. Live TV (`org.droidtv.playtv`), voice, Chromecast, firmware updates, Ambilight/Hue, Play Store, Play Services and the owner's streaming apps stayed enabled. This is not a CVTE board. No factory menu was opened.

## Validation limits

- The model and firmware above were read from the set.
- The replacement launcher was installed and confirmed as Home. One package was disabled: the stock launcher.
- No cold-boot check of a package list, no HDMI or tuner playback check, and no several-day debloat.
- This note does not validate Launcher 3.0.
- ADB was used for setup. Turn debugging off and revoke debugging authorisations when maintenance is finished.

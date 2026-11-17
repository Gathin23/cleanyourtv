# Kenwood K32M15G6: same-day Google TV notes

Observed on one television on 2026-11-05. This is a single-session report with one reboot, not a several-day debloat list. The retail model is K32M15G6; Android identifies it as a KTC-built 2K SMART TV. Compare the earlier [Master-G KTC report](master-g-2k-smart-tv.md), but do not assume its firmware or package choices apply to this set.

| Field | Observed value |
|---|---|
| Retail brand / manufacturer | KENWOOD / KTC |
| Android model / board | 2K SMART TV / jagalchi, MediaTek mt5867 |
| System | Google TV on Android 14, SDK 34, armeabi-v7a |
| Firmware build | UKN6.260116.001 |
| Launcher surface | 1280 × 720, 213 dpi |

## Home and saved Wi-Fi

The saved Wi-Fi network initially belonged to `com.google.android.tungsten.setupwraith`. The owner forgot it and rejoined from Android Settings first. Only after the network owner became the system was setupwraith disabled using the repository's guarded tools. Disabling `com.google.android.apps.tv.launcherx` after confirming the new Home then let CleanYourTV take Home. One reboot retained Home and the disabled states.

Use the existing [Home guards and recovery tools](../../CONTRIBUTING.md); this note adds no force flags or installer. Keep Android Settings, input services, the keyboard, Play services and firmware updates enabled.

## Packages changed in this session

These eight client/service packages were disabled in small batches, in addition to the two Home handover packages above. Nothing was uninstalled. This is an observation, not a shared list validated over several days.

- ACR opt-in client and feedback: `tv.anoki.acr.anokiacroptin`, `com.google.android.feedback`. Disabling the Anoki app is not evidence that every vendor ACR component has stopped.
- Android ad/personalisation services: `com.android.adservices.api`, `com.android.federatedcompute.services`, `com.android.ondevicepersonalization.services`.
- Recommendations/demo: `com.android.tv.feedbackconsent`, `com.mediatek.android.leanbacklauncher.partnercustomizer`, `fusion.android.tv.demo`.

All streaming apps, the media player and manual were kept. The voice assistant `com.google.android.katniss`, Cast receiver `com.google.android.apps.mediashell`, Miracast `com.mediatek.androidbox` and updater were kept. `com.cltv.hybrid` was already disabled before the session; the tools did not disable it. Factory/test packages were never launched.

## Remote: helper services and held volume

Without a key helper, holding either volume button repeats normally. The physical Settings and User buttons did not open their screens from the replacement Home. Their observed scan codes were 397 and 156.

HomeKeyService restored both buttons, but holding Volume + or Volume − then changed only one step. The `consume_volume_keys` option did not restore repetition on this firmware. Turning the helper off restored native repetition; the owner confirmed this with the physical remote. The final setup leaves both key accessibility services off and opens Settings and Accounts from Home instead. Do not enable a helper by default for this model or claim injected volume keys prove physical long-press behavior.

## Inputs, casting and playback

- `live_tv_package` was set to `com.mediatek.wwtv.tvcenter.plus`. Its normal launcher activity delegates to the native MediaTek TV center. HDMI 1, HDMI 2, composite and Live TV were present in Inputs. External HDMI picture/audio was not physically verified.
- Native YouTube playback and held-volume repetition were confirmed by the owner.
- Cast remained enabled after the Home changes. Discovery and a Cast protocol connection worked; the owner then cast YouTube from a phone and confirmed picture and sound. No device name, network details or credentials are included here.
- An RF scan found three digital services, but selected services produced neither picture nor sound. Reception and tuner health remain unresolved; this is not a tuner-fix claim.
- Android advertised HDR10 and HLG, with no forced SDR. An active HDR stream and an HDR HDMI source were not verified.

The dock used seven apps with the remaining apps in the library. The translucent dock and wallpaper rotation used existing theme keys, without changing launcher code, permissions or default behavior for other TVs.

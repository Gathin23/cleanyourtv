# Master-G 2K Smart TV (KTC `jagalchi`, Android 14): a same-day Google TV session

One owner-reported session on 2026-10-22, done with the prompt over Wi-Fi from a Windows PC. It is a note,
not a several-day list: everything below was disabled and checked in one sitting, plus one reboot. A
`debloat.txt` will follow once the set has been lived with for a few days.

| Field | Observed value |
|---|---|
| Brand / maker | `MASTER-G` (retail brand, sold in Chile) / `KTC` |
| Model string | `2K SMART TV`, device `jagalchi`, product `KTC_MASTER_G_2K_SA_25GV1` |
| System | Google TV on Android 14 (SDK 34), `armeabi-v7a`, 1280x720 at 213 dpi |
| Firmware | `MASTER-G/KTC_MASTER_G_2K_SA_25GV1/jagalchi:14/UKN4.250714.046/13878873:user/release-keys` |
| Packages | 147 (139 system, 8 user) |
| Network | Wi-Fi |

## ADB

- Port 5555 was open once USB debugging was on; the first `adb connect` answered `failed to authenticate` and the
  "Allow USB debugging?" prompt then appeared on the TV. Wireless debugging was also on and advertises a second
  random port, which `adb connect` could not use without pairing.
- `adb install` of the launcher worked normally (no confirmation dialog).

## Home takeover

- Stock launcher: `com.google.android.apps.tv.launcherx` (HOME priority 2). HOME claimants on the set:
  `launcherx`, `com.google.android.tungsten.setupwraith/.RecoveryActivity`, the launcher, and
  `com.android.tv.settings.system.FallbackHome`.
- `cmd package set-home-activity` printed `Success` and Home still opened Google TV.
- With only `launcherx` disabled, Home fell to `setupwraith`'s `RecoveryActivity` (it opened the last Settings
  screen). Disabling `setupwraith` too gave the launcher Home.
- The saved Wi-Fi network belonged to `setupwraith` (`cname=com.google.android.tungsten.setupwraith` in
  `dumpsys wifi`). The owner forgot it and joined it again in Settings first, after which the owner was
  `android.uid.system:1000`, and only then was `setupwraith` disabled; the TV stayed on Wi-Fi. This is the
  same Home-safety dance as `aipro2k` and `tcl-75qm751g-qm7`.
- Reboot-checked: nothing re-enabled itself, and the launcher stayed Home.

## What was disabled (`pm disable-user --user 0`, nothing uninstalled)

- Home handover: `com.google.android.apps.tv.launcherx`, `com.google.android.tungsten.setupwraith`.
- Android 14 ad/ML services: `com.android.adservices.api`, `com.android.ondevicepersonalization.services`,
  `com.android.federatedcompute.services`.
- Owner-chosen: `com.electronic.manual` (the on-TV manual), `com.google.android.play.games`, and the apps the
  owner does not use (Prime Video, Netflix, Mercado Play, iQIYI, Jellytelly).

## Left alone on purpose

- Chromecast receiver (`com.google.android.apps.mediashell`), the Play Store, firmware updates, the voice
  assistant (`com.google.android.katniss`, tied to the remote's microphone), the Live TV app
  `com.mediatek.wwtv.tvcenter.plus` (launcher `live_tv_package`) and the media player
  `com.mediatek.wwtv.mediaplayer`.
- KTC factory packages, never launched or disabled: `com.ktc.tv.factory`, `com.ktc.serialport`.
- Already disabled at the factory: `com.cltv.hybrid`, `fusion.android.tv.demo`,
  `com.mediatek.wwtv.tvcenter.overlay`, `com.mediatek.tvinput.overlay`. `com.android.tv.settings` also shows in
  `pm list packages -d` yet Settings opens normally.

## Launcher config used

Dock: `com.google.android.youtube.tv`, `com.disney.disneyplus`, `com.wbd.stream`, `cl.movistarplay`;
`live_tv_package` and the hidden app `com.mediatek.wwtv.tvcenter.plus`. Verified with screenshots (dock, grid,
Settings, Wi-Fi pill). HDMI input switching through the Inputs pill was not exercised.

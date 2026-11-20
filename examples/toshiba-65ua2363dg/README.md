# Second unit: Toshiba BODRUM_Toshiba, Bulgaria

The same product, device and firmware as the list beside this note, bought in Bulgaria. Its retail model
number was not noted. Cleaned in six batches on 2026-10-22, reboot-checked, then in daily use until
2026-11-17 with nothing re-enabled.

| Field | Observed value |
|---|---|
| Retail model | not noted; Bulgaria |
| Android model / product / device | `Android TV` / `BODRUM_Toshiba` / `finch`, MediaTek `m7332` |
| Android / API | Android TV 14 / 34 |
| Firmware | `UKNV.260428.001`, security patch 2026-05-01 (the same as the header unit) |
| RAM | 1.4 GB, `ro.config.low_ram=true` |
| Network | Wi-Fi |
| Stock Home | `com.google.android.tvlauncher` |
| Live TV app | `com.mediatek.wwtv.tvcenter` |

## What differs from the header unit

- **setupwraith owned the saved Wi-Fi network** (`dumpsys wifi` showed
  `cname=com.google.android.tungsten.setupwraith`); on the header unit it belonged to `android.uid.system`.
  The list leaves setupwraith alone, and it stayed enabled here. Check `dumpsys wifi | grep -o 'cname=[^ ]*'`
  before you disable it: `tools/disable.sh` refuses it while it owns the network.
- **Not tried here:** the two CTS shims, `com.android.settings.intelligence` and `com.android.se` stayed
  enabled.
- **Also disabled here, with nothing missed in four weeks:**
  - `com.android.providers.calendar`: the calendar provider, with its sync adapter already in the list.
  - `com.mstar.android.tv.disclaimercustomization`: the vendor's disclaimer pop-ups.
  - `android.autoinstalls.config.vestel.malin`: the `tools/disable.sh` of 2026-10-22 disabled it. The header
    explains why today's skips it.
- **Off by the owner's choice:** Prime Video, YouTube Music, Play Games, Google Assistant (`katniss`), Google
  Speech Services (`tts`) and the phone-app remote (`com.google.android.tv.remote.service`). They are commented
  out at the end of the list. Netflix, YouTube, Spotify, Chromecast built-in and the USB media player stayed.
- **Screensaver:** `addons/photo-screensaver` took over before `com.google.android.backdrop` was disabled,
  instead of the launcher's clock.
- **AirPlay:** `io.github.jqssun.airplay` 0.0.36, built from source for `armeabi-v7a`, installs and runs. It is
  a foreground service of about 35 to 42 MB that starts at boot, and an iPhone casts to it.

## The same as the header unit

- Port 5555 opened with USB debugging alone, and came back after a reboot.
- Screenshots work.
- Home stayed with the new launcher across reboots; the stock launcher was disabled by
  `launcher/build-and-install.sh` once Home had moved.

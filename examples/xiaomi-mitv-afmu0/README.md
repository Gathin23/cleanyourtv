# Xiaomi TV Box S 3rd gen (MiTV-AFMU0, twilight), second unit

Tested with `PROMPT.md` and `examples/xiaomi-mitv-afmu0/debloat.txt` on 2026-10-27.
Model: `MiTV-AFMU0`, codename `twilight`, Amlogic, Google TV on Android 14 (SDK 34).
Firmware: `UKG3.250826.001 / V816.0.11.0.UZFAABX` (HyperOS), 2 GB RAM, `ro.adb.secure=1`.

## What was verified

- **Debloat:** Disabled 24 packages in batches (telemetry, PatchWall, promos, unused preloaded streaming apps).
- **Kept by choice:** Google Play Store, Google Assistant and Bluetooth remote microphone (`com.google.android.katniss`, `com.google.android.tv.remote.service`), Chromecast (`com.google.android.apps.mediashell`), YouTube (`com.google.android.youtube.tv`), Netflix (`com.netflix.ninja`), TrueID TV (`com.truedmp.idtv`), and Xiaomi USB media players (`com.mitv.gallery`, `com.mitv.videoplayer`, `com.xiaomi.mitv.mediaexplorer`).
- **Wi-Fi handover from setupwraith:** During initial wizard setup, Wi-Fi is owned by `com.google.android.tungsten.setupwraith`. Disabling setupwraith while it owns Wi-Fi will cause Android 14 to delete the network and drop ADB. On the TV, forgetting the network and re-joining it in Settings switches ownership to `android.uid.system:1000`. Once owned by system, setupwraith can be disabled safely.
- **Launcher handover:** Built reference launcher (`com.gathin.tvhome`) with platform 34 and minSdk 26. `setupwraith` disabled first, then `com.google.android.apps.tv.launcherx` disabled, and `com.gathin.tvhome/.MainActivity` set as Home activity.

## Remote buttons and key mapping

The Bluetooth remote ("Xiaomi RC", `Vendor_2717_Product_32b9.kl`) behaves as follows once the stock launcher is disabled:

- **Home, Back, D-pad, and Mic:** Work out of the box (Google Assistant kept).
- **Netflix:** Sends `BUTTON_6` (scancode 240 / 416) and starts Netflix directly.
- **YouTube:** Sends `BUTTON_3` (scancode 425), which only the stock launcher answered.
- **Prime Video:** Sends `BUTTON_7` (scancode 207), which only the stock launcher answered.
- **Apps button (bottom right):** Sends `ALL_APPS` (scancode 216).

Enabling `HomeKeyService` (`settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService` and `settings put secure accessibility_enabled 1`) restores all three buttons via `examples/xiaomi-mitv-afmu0/theme/config.json`:
- `ALL_APPS` is caught by `HomeKeyService` and opens Home.
- `app_keys` maps `scan:425` and `KEYCODE_BUTTON_3` to `com.google.android.youtube.tv`.
- `app_keys` maps `scan:207` and `KEYCODE_BUTTON_7` to `com.truedmp.idtv` (TrueID TV).

## Private DNS (AdGuard DoT)
Private DNS (such as AdGuard DoT) works on this set.

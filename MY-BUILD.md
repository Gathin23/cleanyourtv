# My build of CleanYourTV

This fork of [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) adds three things. Everything else
is the upstream project, unchanged.

| Part | Folder | What it adds |
|---|---|---|
| **120 fps** | `launcher/` | The launcher asks Android for the display's fastest refresh rate at its current resolution (`prefer_high_refresh_rate` in `config.xml`, on in this fork). A request only: a TV panel without a 120 Hz mode stays at 60. |
| **Stay Awake** | `addons/stay-awake/` | A small app that sets the screen timeout up to **Never** and switches "stay awake while plugged in". See its README. |
| **TV Remote** | `phone-remote/` | A phone app that works as a Bluetooth keyboard and media remote for the TV. See its README. |

## Getting the APKs (no Android Studio needed)

GitHub builds everything from source, runs the unit tests, and attaches the APKs:

1. Open the repository's **Actions** tab and the newest **Build APKs** run (or press **Run workflow**).
2. Under **Artifacts**, download **apks**. Unzip it. It holds `cleanyourtv-launcher.apk`, `stay-awake.apk`,
   `phone-remote.apk` and `SHA256SUMS.txt`.
3. Copy `cleanyourtv-launcher.apk` and `stay-awake.apk` to the pendrive for the TV, and `phone-remote.apk` to the
   phone.

All three are signed with the debug key so they install from a file. That is fine for your own devices.

## Installing

**TV** (from the pendrive, with a file manager app):

1. Install `cleanyourtv-launcher.apk`, then press Home and choose it as the home app.
   The upstream README explains the optional debloat step; it needs ADB.
2. Install `stay-awake.apk`. From a computer with the TV connected over ADB, give it its two permissions once:

   ```bash
   adb shell appops set io.github.kunalsharma.stayawake WRITE_SETTINGS allow
   adb shell pm grant io.github.kunalsharma.stayawake android.permission.WRITE_SECURE_SETTINGS
   ```
3. Open Stay Awake and set **Screen timeout** to **Never**.

**Phone**: install `phone-remote.apk`, then follow its README (make the phone visible, add it as an accessory on
the TV, connect).

## Status

- Compiles and passes its unit tests on GitHub's runner.
- **Not yet tested on a real TV or phone.** Before showing it, run each part once on your own devices and note
  the TV model and Android version in the READMEs' "Tested on" lines.
- If the screen blinks when the launcher opens, the TV is re-syncing HDMI for the 120 Hz mode: set
  `prefer_high_refresh_rate` to `false` in `launcher/app/src/main/res/values/config.xml` and build again.

## Upstream rules

The upstream project keeps its launcher small and does not take prebuilt APKs or CI release builds in pull
requests. This fork's `build-apks.yml` workflow and these extra apps are for personal use; to send a change
upstream, follow `CONTRIBUTING.md` and `AGENTS.md` (a launcher switch is off by default there).

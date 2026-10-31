# Fengmi / Xiaomi MiProjM05 (Android 9)

Debloated with `debloat.txt` and running this repo's launcher as Home (dock from `theme/config.json`: Netflix, YouTube, Prime Video, Canal+).
Everything is DISABLED, never uninstalled. All commands run from the repo root; `TV_HOST` is the projector's IP.
ADB over Wi-Fi must be on first (Settings > Device Preferences > Developer options > USB debugging).

## Restore everything

    TV_HOST=<ip> tools/restore.sh examples/xiaomi-miprojm05/debloat.txt

Re-enables the packages in the list (not the ones that were already disabled before) and gives Home back to the
stock launcher. One package only: `TV_HOST=<ip> tools/tv.py 'pm enable --user 0 <package>'`.
The raw state from before the first change is in `~/Documents/miprojm05-backup/` (package lists, getprop, settings).

## Re-apply after a firmware update

    TV_HOST=<ip> tools/disable.sh examples/xiaomi-miprojm05/debloat.txt

## Build and install the launcher

    TV_HOST=<ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
    TV_HOST=<ip> tools/theme.sh examples/xiaomi-miprojm05/theme     # dock, hidden Live Channels tile, Live TV package

The remote's gear button is scancode 384; `settings_key_scancodes` is a `config.xml` value that a theme cannot set,
so before building add `<item>384</item>` to that array in `launcher/app/src/main/res/values/config.xml`.

Needs JDK 17 and the Android SDK (`brew install openjdk@17 && brew install --cask android-commandlinetools`, then
`sdkmanager "platforms;android-34" "build-tools;34.0.0"`).

## Change the dock order or the wallpaper

- Dock: edit `dock_apps` in `theme/config.json` and run `TV_HOST=<ip> tools/theme.sh examples/xiaomi-miprojm05/theme`.
- Wallpaper: put a `wallpaper.jpg` in `theme/`, set `"wallpaper": true` and push it the same way. `tools/theme.sh --reset` undoes it.

## Notes from this projector

- Left alone on purpose: `com.fengmi.projector.imaging` (keystone, focus), `com.formovie.gsettings`,
  `com.fengmi.fmsettings`, `com.fengmi.platform`, `com.formovie.fmutils`, `com.android.camera2`, `com.droidlogic*`.
- `com.google.android.tungsten.setupwraith` owns the saved Wi-Fi network; disabling it would delete the network.
- Kept by choice: Chromecast/Miracast, the update service, the Xiaomi smart-home host, TF1+. The voice assistant
  is disabled, so the remote's mic button does nothing.
- Sources lists HDMI and Live TV through the Android TV input framework; screenshots of HDMI/Live TV come back
  empty, use `dumpsys activity activities | grep mResumedActivity`.
- Ad-blocking DNS was offered and declined. To set it: `settings put global private_dns_specifier dns.adguard-dns.com`
  then `settings put global private_dns_mode hostname` (does not block YouTube ads); undo with
  `settings put global private_dns_mode opportunistic`.
- USB debugging may cut power to USB ports on some firmware; nothing was plugged in here. Turn debugging off when done.

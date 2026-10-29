# Sony BRAVIA KD-49X8000G (Hong Kong)

- **Model:** Sony BRAVIA KD-49X8000G (Hong Kong region, `BRAVIA_ATV3_4K_HK`)
- **Board / SoC:** MediaTek quad-core (MT5891 / MT5893 family), same platform as `examples/sony-kd-43x8000g`
- **Firmware build:** `BRAVIA_ATV3_4K_HK-user 9 PTT1.190515.001.S54 672201 release-keys`
- **Android version:** Android TV 9 (API 28, Pie)
- **Tested on:** the household's own set, debloat and reference launcher both reboot-checked

## Notes

- **Stock launcher:** `com.google.android.tvlauncher`, disabled only after the reference launcher was
  confirmed as Home.
- **Region app set:** the HK firmware preloads more than the sibling APAC list
  (`examples/sony-kd-43x8000g`): Amazon Prime Video, Mango TV, a Taiwan anime app, and three separate
  screen-mirroring apps, on top of the same Sony/Samba/B2B/MediaTek packages. See
  `debloat.txt`'s header for exactly what is disabled and what was kept on purpose.
- **TV inputs and HDMI:** HDMI 1-4, ARC and the tuner are handled by `com.sony.dtv.tvx` and
  `com.sony.dtv.tvinput.*`, left untouched. The reference launcher's Inputs pill reads this list
  directly, with Live TV wired to `com.sony.dtv.tvx` in `config.xml`'s `live_tv_package`.
- **Wi-Fi ownership:** `dumpsys wifi` shows Wi-Fi owned by the Android system, not by the setup wizard,
  so debloating and disabling the stock launcher did not touch connectivity.
- **Kept on purpose:** Netflix, YouTube, Chromecast built-in (mediashell), Spotify, Disney+, Google
  Assistant (`com.google.android.katniss`, for the remote's mic button), Play Store/Services, TalkBack,
  the on-screen keyboard, and the Sony system update service.

## Re-enabling a disabled app

Everything was disabled with `pm disable-user`, never uninstalled, so any package can come back:

```bash
# One package:
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --batch <<< "pm enable --user 0 <package>"

# The whole list this TV got:
TV_HOST=<tv ip> tools/restore.sh examples/sony-kd-49x8000g-hk/debloat.txt
```

`restore.sh` re-enables every package in the file, including any you comment out later, so it is an
undo for this exact list. To bring back just one or two apps, either comment out their lines in
`debloat.txt` and re-run `restore.sh` on the whole file, or enable them directly with `pm enable` as
above and leave the file as a record of what you changed.

## Changing the dock order

The dock is 5 slots: `com.netflix.ninja`, `com.google.android.youtube.tv`, `com.disney.disneyplus` and
`com.spotify.tv.android` are pinned, and the 5th slot auto-fills from the next app in the grid
(`fill_dock: true`), currently the Google Play Store.

To change the order or swap an app, edit `~/my-theme/config.json`'s `dock_apps` list (package names, in
the order you want them) and push it again:

```bash
TV_HOST=<tv ip> tools/theme.sh ~/my-theme
```

To pin a 5th app instead of letting it auto-fill, add its package name as the 4th entry in `dock_apps`.
To hide an app from the grid entirely (as was done here with the stock browser,
`com.vewd.core.integration.dia`), add its package name to `hidden_apps`.

Package names for anything installed can be listed with:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --batch <<< "pm list packages -3"
```

## Changing the wallpaper

The background is a theme overlay, not part of the launcher's build, so it can be changed without
rebuilding or reinstalling anything:

```bash
# Replace the picture:
cp /path/to/your/image.jpg ~/my-theme/wallpaper.jpg
TV_HOST=<tv ip> tools/theme.sh ~/my-theme

# Back to plain black, no picture:
TV_HOST=<tv ip> tools/theme.sh --reset
```

`~/my-theme/config.json` currently sets:

```json
{
  "dock_apps": ["com.netflix.ninja", "com.google.android.youtube.tv", "com.disney.disneyplus", "com.spotify.tv.android"],
  "hidden_apps": ["com.sony.dtv.tvx", "com.sony.dtv.tvxlauncher.programguide", "com.vewd.core.integration.dia"],
  "columns": 5,
  "wallpaper": true,
  "wallpaper_dim": 35
}
```

`wallpaper_dim` (0-90) darkens the picture so the clock, pills and dock stay readable; raise or lower it
to taste. The full list of keys a theme can set, including rotating wallpapers and time-of-day
backdrops, is in `themes/README.md`.

## Rebuilding the launcher from source

Only needed after a source change, not for config or wallpaper tweaks:

```bash
cd launcher
TV_HOST=<tv ip> ./build-and-install.sh
```

The script checks the TV's Android SDK level against the launcher's `minSdk` first, builds the release
APK, pushes and installs it, starts it and confirms Home resolves to it before disabling the stock
launcher, and rolls back automatically if any step fails.

## Closing ADB afterwards

This set only exposes USB debugging with a fixed network ADB port (5555), not Wireless debugging
(`adb_wifi_enabled` stayed `null` throughout). Once you are done:

```bash
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --batch <<< "settings put global adb_enabled 0"
# power-cycle the TV, then:
tools/venv/bin/python -I tools/adb-closed.py <tv ip>
```

Only `adb-closed.py` reporting the port closed counts; USB debugging's toggle looking off on screen is
not proof by itself.

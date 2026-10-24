# TCL 43A30 (Indonesia) — G09, Google TV, Android 12

Sold as "TCL 43A30 Android TV 11", but the set reports as **Smart TV Pro**, device `G09`, product
`G09_4K_GB`, Amlogic T5M, **Android 12 (SDK 31)**, firmware `STT2.230203.001.AS24`, security patch
2025-12-05, 2 GB RAM. TCL's own software label: `V8-0013T02-LF1V157.0008111`. Same firmware as
[`examples/tcl-g09-google-tv`](../tcl-g09-google-tv), whose list this copies.

Done on 23 October 2026 over Wi-Fi from a Mac, including a reboot. Before: ~735 MB available RAM
on the stock home with a sponsored banner. After: ~930 MB available, the Clean Your TV launcher as Home.

| File | What it is |
|---|---|
| `debloat.txt` | 30 packages disabled; Chromecast, USB media player and the phone-remote service kept |
| `config.json` | 6-column dock (YouTube, Netflix, Prime Video, Disney+ Hotstar, Vidio, Vision+), hidden apps, `key_filter: false` |

## Install again

```bash
cd ~/Documents/cleanyourtv
export TV_HOST=<tv ip>   # was 10.0.1.32
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
tools/disable.sh examples/tcl-43a30-g09-id/debloat.txt
launcher/build-and-install.sh            # no INPUT_KEY_SERVICE: volume long-press keeps repeating
tools/theme.sh examples/tcl-43a30-g09-id
tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
```

## Undo everything

```bash
tools/restore.sh examples/tcl-43a30-g09-id/debloat.txt com.google.android.apps.tv.launcherx
tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop'
```

The full pre-change state (package list, getprop, every settings namespace) is in
`backups/tcl-43a30-g09-20261023-1728/`.

## Re-enable one app

```bash
tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.videos'
```

or on the TV: Setelan → Aplikasi → Lihat semua aplikasi → Tampilkan aplikasi sistem → pilih → Aktifkan.
Comment its line out in `debloat.txt` afterwards.

## Change the dock or hidden apps

Edit `dock_apps` / `hidden_apps` in `config.json`, then `tools/theme.sh examples/tcl-43a30-g09-id`.
No rebuild. `columns` must match the number of dock slots. Wallpaper: drop a `wallpaper.jpg` next to
`config.json` and push the theme again (see `themes/README.md`).

## Notes for this set

- Remote's Netflix / Prime Video / YouTube buttons do nothing now (they were answered by the stock
  home). Mapping them back needs `INPUT_KEY_SERVICE=1` + `app_keys`, and on this firmware any bound
  key-filtering helper turns volume long-press into one step per press
  (`docs/devices/tcl-55p71b-g09-key-repeat.md`). The owner chose the volume ramp.
- The Input button on the remote works without any helper (TCL draws that panel itself).
- `com.ophion.serpento` is the "LOKLOK TV" tile, hidden in `config.json`; `com.tv.loklok` has no
  leanback entry.
- Screenshots are black while HDMI or Live TV is on screen; press Home first.
- Google Assistant (`com.google.android.katniss`), Chromecast, AirPlay2, Miracast, MagiConnect and
  the USB media player are all untouched.

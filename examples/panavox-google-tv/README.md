# Panavox Google TV (Android TV 14)

Sold only in Uruguay. The board is the same as the AiPro2K already in this repo; only the firmware differs.

## Device

| | |
|---|---|
| Brand | Panavox (Uruguay only) |
| Board | Same as AiPro2K, different firmware |
| Device | Expressluck, `dongnae` |
| Android | Android TV 14 |
| Firmware | UKN6.260116.001 |
| RAM | 1 GB (MemTotal about 930 MB) |
| UI | 1280x720 at 213 dpi, 60 FPS |

## What the TV is like after the cleanup

Everything below stays the same after a reboot.

- **Remote:** every button works except two: the microphone (the voice assistant, `katniss`, is disabled) and the gear button. The launcher has two ways to reach Settings instead.
- **Inputs:** Home → Inputs lists HDMI 1, HDMI 2, HDMI 3 and composite (RCA). The tuner is not listed there, but it still works from the remote.
- **Screensaver:** the Google screensaver is part of the cleanup, so the Clock screensaver is set instead (command below).

## Cleanup procedure

On the TV: Settings → Device Preferences → About → press OK on **Build** seven times → Developer options → turn **USB debugging** on.

```
export TV_HOST=<the TV's IP address>
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt

# check the TV is reachable
tools/venv/bin/python -I tools/tv.py --probe
tools/venv/bin/python -I tools/tv.py 'pm list packages'

# install the launcher first, so the TV always has a Home app
launcher/build-and-install.sh

# apply the cleanup (run it again if the stock launcher entry was skipped)
tools/disable.sh examples/panavox-google-tv/debloat.txt

# optional: the Aurora theme
tools/theme.sh themes/aurora

# the list disables Google's screensaver, so set the Clock screensaver
tools/venv/bin/python -I tools/tv.py "settings put secure screensaver_components com.gathin.tvhome/.ClockDream"
```

When you are done, turn USB debugging off in Developer options.

## Packages disabled

The list is in [debloat.txt](debloat.txt).

## Restore

```
tools/restore.sh examples/panavox-google-tv/debloat.txt com.google.android.apps.tv.launcherx
```

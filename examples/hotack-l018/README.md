# HOTACK L018 Smart Projector (Android 11, Allwinner H713)

Chengdu Hotack's L018, sold as "Smart Projector". The firmware calls itself `ADT-3`, build
`Projector.20260530.1707`, with the LuminaOS launcher (`com.htc.luminaos`). Both halves work: the
debloat list and the Clean Your TV launcher as Home. Applied on 24 October 2026.

| File | What it is |
|---|---|
| `debloat.txt` | The 18 packages that were disabled, grouped and commented, with what to keep and why |
| `config.json` | Hidden tiles and calmer motion; push it with `tools/theme.sh` |

## Install

All commands run from the repo root.

1. Disable the list:
   `TV_HOST=<projector ip> tools/disable.sh examples/hotack-l018/debloat.txt`
2. Point the Settings pill at the projector settings. This firmware answers no `ACTION_SETTINGS`, so
   add the `settings_fallback_components` entry and the `<queries>` line that the header of
   `debloat.txt` gives.
3. Build and install the launcher, keeping LuminaOS enabled:
   `TV_HOST=<projector ip> STOCK_LAUNCHER=none launcher/build-and-install.sh`
   LuminaOS stays because the projector's own settings (network, Bluetooth, projection, keystone)
   live only inside it.
4. Push the config: `TV_HOST=<projector ip> tools/theme.sh examples/hotack-l018`

Done when HOME shows the Clean Your TV launcher and its Settings pill opens the LuminaOS settings screen.

## config.json

It holds only what this projector needs:

- `hidden_apps`: LuminaOS, which stays enabled for its settings and is reached through the Settings
  pill, and `com.softwinner.tv.factorymenu`, hidden so nobody opens the factory menu by accident.
- `reduced_motion` on and `focus_overshoot` off. With the default spring motion, the focus move took
  about 0.8 s and most of its frames missed their deadline: the GPU does not keep up.

The dock and the number of columns are taste, so they stay at the launcher's defaults. Add `dock_apps`
and `columns` to the file for your own apps.

## Notes

- **Projector settings over ADB.**
  `am start -n com.htc.luminaos/com.htc.luminaos.activity.MainSettingActivity` opens them without
  switching Home; HOME still returns to the Clean Your TV launcher.
- **`tools/bench.py`.** This firmware logs `TvWindowManager: key event key = <KEYCODE>` instead of
  `injectKeyEvent`. So `bench.py` does not measure the return to Home here, and times key response from
  just before the key is sent.

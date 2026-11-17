# TCL C755 custom setup

A personal setup for a TCL "Smart TV Pro" (C755, board G08), Android 14 / Google TV, firmware UTT2.250416.001.
It builds on `examples/tcl-c755-google-tv` and the launcher in this repo. Everything is reversible: nothing is
uninstalled from the system, packages are disabled with `pm disable-user`.

## What is here
- `theme/` – the theme: dark wallpapers (from `themes/midnight-glass`, `glass-gallery`, `aurora`, `cinematic`; see
  `theme/CREDITS.md`), a sky gradient that follows the sun, glass dock, advanced effects, 6 columns, Inputs and Wi-Fi pills
  hidden, idle clock after 3 minutes. The cinematic top shelf measured 45% janky frames on this TV and is off.
- `batches/` – the packages disabled, in the order applied (tracking, content/ads, retail demo, preloads). Kept on purpose:
  Assistant, Chromecast, AirPlay/Miracast/T-Cast, firmware updates, YouTube, Netflix, Prime.
- `restore-all.sh` – undo everything (read its header first: it assumes nothing was disabled before).
- `rebuild-and-install.sh` – rebuild with Docker, install, push the theme.

## Launcher changes (commit "Launcher: next-wallpaper button, weather line, HDMI device buttons")
- A next-wallpaper button in the top bar.
- A weather line under the clock and date, and on the clock screensaver (Open-Meteo, every 30 minutes while shown). It
  appears only when `latitude` and `longitude` are set in `launcher/app/src/main/res/values/config.xml`; they are empty in
  this repo, and the same two values give the sky its real sunrise and sunset. This adds the `INTERNET` permission.
- Big top-bar buttons for named HDMI devices: `input_buttons` in `config.xml`, `"match|icon|label"` (icon `tv` or
  `gamepad`). A button is dimmed unless the TV reports that input as connected; pressing it opens the input.
- A shorter date (`EEE d MMM`).
The APK is about 594 KB, over the upstream 576 KB size gate, so these changes are not suitable for an upstream PR as they are.

## Using it
1. Turn on Wireless debugging on the TV, `adb pair`, then `adb connect <ip:port>` with the current connect port.
2. Set your coordinates in `config.xml` if you want the weather line and real sun times.
3. `custom/tcl-c755/rebuild-and-install.sh <ip:port>`.
4. Disable the stock launcher and setup wizard only after the new launcher is Home (see the repo's `PROMPT.md`).
